-- V8__cache_sync.sql
-- Решение Р4: регистры и справочники, ведущиеся в WMS, — это кэш, а не источник истины.
-- Служебные поля кэша + журнал синхронизации.

-- 1. Служебные поля на кэширующих таблицах.
--    synced_at    — когда запись получена из WMS (показывается пользователю: «данные на 14:32»)
--    wms_version  — версия/ETag записи на стороне WMS, если WMS её отдаёт
DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['stock','registered_service','document_photo','sku'] LOOP
    EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS synced_at timestamptz', t);
    EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS wms_version varchar(64)', t);
    EXECUTE format($f$COMMENT ON COLUMN %I.synced_at IS 'Момент получения записи из WMS'$f$, t);
    EXECUTE format($f$COMMENT ON COLUMN %I.wms_version IS 'Версия записи на стороне WMS'$f$, t);
  END LOOP;
END $$;

-- 2. Журнал синхронизации. Он же источник данных для админского экрана
--    «Ошибки интеграции» (§8.5.5 спецификации).
CREATE TABLE sync_run (
  sync_run_id      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  entity           varchar(50)  NOT NULL,   -- stock, sku, registered_service, ...
  depositor_id     uuid,                    -- NULL = синхронизация по всем поклажедателям
  direction        varchar(20)  NOT NULL,   -- wms_to_portal | portal_to_wms
  trigger_source   varchar(20)  NOT NULL,   -- webhook | schedule | manual
  started_at       timestamptz  NOT NULL DEFAULT now(),
  finished_at      timestamptz,
  status           varchar(20)  NOT NULL DEFAULT 'running', -- running | success | failed | partial
  rows_received    integer,
  rows_applied     integer,
  changed_since    timestamptz,             -- параметр запроса «изменения с момента X»
  error_message    text,
  retry_of         uuid                     -- ссылка на предыдущую неудачную попытку
);
ALTER TABLE sync_run ADD CONSTRAINT fk_sync_run_depositor
  FOREIGN KEY (depositor_id) REFERENCES depositor(depositor_id);
ALTER TABLE sync_run ADD CONSTRAINT fk_sync_run_retry_of
  FOREIGN KEY (retry_of) REFERENCES sync_run(sync_run_id);
CREATE INDEX ix_sync_run_entity_started ON sync_run (entity, started_at DESC);
CREATE INDEX ix_sync_run_status ON sync_run (status) WHERE status <> 'success';
COMMENT ON TABLE sync_run IS 'Журнал обменов с WMS: что синхронизировали, когда, с каким результатом';

-- 3. Точка отсчёта для инкрементальной синхронизации: до какого момента
--    данные по сущности считаются актуальными. Читается перед запросом к API.
CREATE TABLE sync_watermark (
  entity           varchar(50) NOT NULL,
  depositor_id     uuid,
  synced_until     timestamptz NOT NULL,
  last_sync_run_id uuid,
  PRIMARY KEY (entity, depositor_id)
);
ALTER TABLE sync_watermark ADD CONSTRAINT fk_sync_watermark_run
  FOREIGN KEY (last_sync_run_id) REFERENCES sync_run(sync_run_id);
COMMENT ON TABLE sync_watermark IS 'Момент, по который данные сущности получены из WMS';

-- 4. Сроки актуальности кэша. Разные данные устаревают по-разному,
--    единый TTL не годится. Значения — предварительные, уточнить на этапе 8.
CREATE TABLE cache_policy (
  entity           varchar(50) PRIMARY KEY,
  ttl_seconds      integer NOT NULL,
  refresh_on_write boolean NOT NULL DEFAULT false,  -- обновлять принудительно перед записью документа
  note             text
);
INSERT INTO cache_policy (entity, ttl_seconds, refresh_on_write, note) VALUES
  ('stock',              300,  true,  'Остатки. Обновлять принудительно перед созданием заказа на отгрузку'),
  ('sku',               3600,  false, 'Номенклатура, если ведётся в WMS'),
  ('registered_service', 3600, false, 'Оказанные услуги по документам'),
  ('document_photo',     1800, false, 'Метаданные фото из WMS');
COMMENT ON TABLE cache_policy IS 'Сроки актуальности кэша по сущностям (решение Р4)';
