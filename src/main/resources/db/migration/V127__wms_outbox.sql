SET search_path = dbo;

-- =====================================================================
-- V127. Отправка документа в WMS через очередь (аудит 11.11.2026, A1).
--
-- ЧТО БЫЛО. «Подтвердить» звал WMS внутри транзакции базы и потом ставил
-- статус. Когда заглушка станет HTTP-запросом: соединение с базой занято
-- на время ответа WMS; WMS принял, а запись в базе не прошла — документ
-- остаётся черновиком и уйдёт второй раз; два одновременных «Подтвердить»
-- оба отправят. Документ с ошибкой отправки застревал — повторить нельзя.
--
-- КАК ТЕПЕРЬ (решение заказчика 12.11.2026).
--   * «Подтвердить» одной транзакцией: блокирует документ, проверяет,
--     ставит статус 04 «Отправляется» и кладёт задание в wms_outbox.
--   * Сразу после фиксации тот же запрос отправляет задание — вне
--     транзакции. Ответил WMS — 02 «Отправлен в WMS».
--   * Не ответил — задание остаётся, его раз в минуту повторяет портал.
--     После 15-й неудачной попытки (около 15 минут) — 03 «Ошибка отправки»,
--     у документа кнопка «Отправить ещё раз».
--   * Ключ повтора для WMS — doc_id: повторная отправка того же документа
--     не должна заводить его в WMS дважды (это часть контракта этапа 8).
--   * Итог записывает wms_outbox_finish — одна функция на успех и неудачу,
--     с журналом обменов (sync_run). Действие в журнале действий — от
--     имени «wms».
-- =====================================================================

-- ---------- Статус «Отправляется» ------------------------------------------

INSERT INTO enum_status (id, code, name, sort_order, is_active, allows_transport_edit, is_auto_created)
VALUES (104, '04', 'Sending to WMS', 4, true, false, false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO enum_status_scope (status_id, object_type_id)
SELECT 104, t.id FROM enum_object_type t WHERE t.code IN ('receipts', 'orders', 'transfers')
ON CONFLICT DO NOTHING;

INSERT INTO enum_text (enum_key, value_id, locale_id, name)
SELECT 'enum_status', '104', l.locale_id, 'Отправляется в WMS'
  FROM dictionary_locale l WHERE l.code = 'ru'
ON CONFLICT (enum_key, value_id, locale_id) DO UPDATE SET name = excluded.name;

-- ---------- Очередь ---------------------------------------------------------

CREATE TABLE wms_outbox (
  outbox_id        bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  doc_type         varchar(20)  NOT NULL CHECK (doc_type IN ('receipts', 'orders', 'transfers')),
  doc_id           uuid         NOT NULL,
  depositor_id     uuid         NOT NULL REFERENCES depositor (depositor_id),
  -- в черновике заполнен транспорт: он уходит вслед за документом (V84)
  with_transport   boolean      NOT NULL DEFAULT false,
  state            varchar(10)  NOT NULL DEFAULT 'pending' CHECK (state IN ('pending', 'done', 'failed')),
  created_at       timestamptz  NOT NULL DEFAULT now(),
  attempts         integer      NOT NULL DEFAULT 0,
  next_attempt_at  timestamptz  NOT NULL DEFAULT now(),
  last_error       text,
  finished_at      timestamptz
);
COMMENT ON TABLE wms_outbox IS
  'задания отправки документов в WMS (V127): пишет «Подтвердить», отправляет и повторяет WmsOutbox';

-- Один документ — не больше одного незавершённого задания.
CREATE UNIQUE INDEX wms_outbox_one_pending ON wms_outbox (doc_id) WHERE state = 'pending';
CREATE INDEX wms_outbox_due ON wms_outbox (next_attempt_at) WHERE state = 'pending';

-- Задание кладёт приложение (в транзакции «Подтвердить»), итог пишет только функция.
REVOKE ALL ON wms_outbox FROM portal_app;
GRANT SELECT, INSERT ON wms_outbox TO portal_app;

-- ---------- Итог попытки ----------------------------------------------------

-- p_error NULL — WMS принял документ. p_transport_error имеет смысл только
-- при успехе документа и только если в задании есть транспорт
-- (with_transport): транспорт уходит вслед за документом (V84).
-- Возвращает код статуса документа после попытки; NULL — задание уже
-- завершено другой попыткой.
CREATE FUNCTION wms_outbox_finish(p_outbox_id bigint, p_error text, p_transport_error text)
RETURNS text
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
DECLARE
  o        wms_outbox%ROWTYPE;
  v_status text;
  v_sync   text;
  -- 15 попыток раз в минуту (первая — сразу) — около 15 минут без ответа,
  -- затем ошибка отправки (решение заказчика 12.11.2026). Счёт попыток, а
  -- не часы: перезапуск портала не съедает попытки, а проверке не нужно
  -- ждать 15 минут.
  c_attempts constant integer  := 15;
  c_retry    constant interval := interval '1 minute';
BEGIN
  SELECT * INTO o FROM wms_outbox WHERE outbox_id = p_outbox_id FOR UPDATE;
  IF NOT FOUND OR o.state <> 'pending' THEN
    RETURN NULL;   -- уже завершено другой попыткой
  END IF;

  PERFORM set_config('app.depositor_id', o.depositor_id::text, true);
  PERFORM set_config('app.actor', 'wms', true);

  IF p_error IS NULL THEN
    v_status := '02';
    v_sync := '01';
    UPDATE wms_outbox SET state = 'done', attempts = attempts + 1, last_error = NULL,
                          finished_at = now()
     WHERE outbox_id = p_outbox_id;
  ELSIF o.attempts + 1 >= c_attempts THEN
    v_status := '03';
    v_sync := '02';
    UPDATE wms_outbox SET state = 'failed', attempts = attempts + 1, last_error = p_error,
                          finished_at = now()
     WHERE outbox_id = p_outbox_id;
  ELSE
    v_status := '04';
    v_sync := '03';
    UPDATE wms_outbox SET attempts = attempts + 1, last_error = p_error,
                          next_attempt_at = now() + c_retry
     WHERE outbox_id = p_outbox_id;
  END IF;

  IF v_status <> '04' THEN
    EXECUTE format(
      'UPDATE %I SET status_id = (SELECT id FROM enum_status WHERE code = $1),
                     confirmed_at = CASE WHEN $1 = ''02'' THEN now() ELSE confirmed_at END,
                     updated_at = clock_timestamp()
        WHERE doc_id = $2', o.doc_type)
    USING v_status, o.doc_id;
  END IF;

  IF v_status = '02' AND o.with_transport THEN
    EXECUTE format(
      'UPDATE %I SET transport_state = $1,
                     transport_sent_at = CASE WHEN $2 IS NULL THEN now() ELSE transport_sent_at END,
                     transport_error = $2
        WHERE doc_id = $3', o.doc_type)
    USING CASE WHEN p_transport_error IS NULL THEN '01' ELSE '02' END, p_transport_error, o.doc_id;
  END IF;

  INSERT INTO sync_run (entity, depositor_id, direction, trigger_source, started_at, finished_at,
                        status, rows_received, rows_applied, error_message)
  VALUES (o.doc_type, o.depositor_id, '01', CASE WHEN o.attempts = 0 THEN 'manual' ELSE 'schedule' END,
          now(), now(), v_sync, 1, CASE WHEN v_sync = '01' THEN 1 ELSE 0 END, p_error);
  IF v_status = '02' AND o.with_transport AND p_transport_error IS NOT NULL THEN
    INSERT INTO sync_run (entity, depositor_id, direction, trigger_source, started_at, finished_at,
                          status, rows_received, rows_applied, error_message)
    VALUES (o.doc_type, o.depositor_id, '01', 'manual', now(), now(), '02', 1, 0,
            'transport: ' || p_transport_error);
  END IF;

  RETURN v_status;
END
$$;

REVOKE ALL ON FUNCTION wms_outbox_finish(bigint, text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION wms_outbox_finish(bigint, text, text) TO portal_app;

-- Задания, которым пора: портал без входа (по расписанию) их не видит —
-- документы и поклажедатели под RLS, а очередь им не нужна целиком.
CREATE FUNCTION wms_outbox_due() RETURNS SETOF bigint
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = dbo, pg_temp
AS $$
  SELECT outbox_id FROM wms_outbox
   WHERE state = 'pending' AND next_attempt_at <= now()
   ORDER BY outbox_id
$$;
REVOKE ALL ON FUNCTION wms_outbox_due() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION wms_outbox_due() TO portal_app;

-- ---------- Тексты ----------------------------------------------------------

INSERT INTO ui_text_key (text_key, scope, element, owner, note)
VALUES
  ('common.button.send_again', 'common', 'button', 'documents',
   'карточка документа с ошибкой отправки: отправить в WMS ещё раз (V127)'),
  ('common.message.document_sending', 'common', 'message', 'documents',
   'плашка: WMS не ответил сразу, портал повторяет сам (V127)'),
  ('error.document.not_failed', 'server', 'message', 'documents',
   'повторить отправку можно только документ с ошибкой отправки (V127)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('common.button.send_again', 'ru', 'Отправить ещё раз'),
    ('common.button.send_again', 'en', 'Send again'),
    ('common.message.document_sending', 'ru',
     'WMS пока не ответил — документ {no} отправляется, портал повторит отправку сам'),
    ('common.message.document_sending', 'en',
     'The WMS has not answered yet — document {no} is being sent, the portal will retry by itself'),
    ('error.document.not_failed', 'ru', 'Отправить ещё раз можно только документ с ошибкой отправки'),
    ('error.document.not_failed', 'en', 'Only a document whose sending failed can be sent again')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
