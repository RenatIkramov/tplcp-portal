SET search_path = dbo;

-- =====================================================================
-- V125. Одновременная правка черновика (аудит 11.11.2026, A9).
--
-- Двое открыли один черновик — сохранялся последний, состав второго
-- молча заменял состав первого. Теперь экран присылает версию документа
-- (момент последнего изменения, updated_at), сервер сверяет её под
-- блокировкой строки и отказывает, если документ уже изменил другой.
-- Структура базы не меняется: версия — это updated_at, а правка состава
-- с этой поставки тоже его обновляет. Здесь только текст отказа.
-- =====================================================================

INSERT INTO ui_text_key (text_key, scope, element, owner, note)
VALUES ('error.document.changed_by_other', 'server', 'message', 'documents',
        'черновик уже изменил другой пользователь (V125)')
ON CONFLICT (text_key) DO UPDATE
   SET scope = excluded.scope, element = excluded.element,
       owner = excluded.owner, note = excluded.note;

INSERT INTO ui_text (text_key, locale_id, value)
SELECT v.text_key, l.locale_id, v.value
  FROM (VALUES
    ('error.document.changed_by_other', 'ru',
     'Документ уже изменил другой пользователь. Ваши правки не сохранены — откройте документ заново'),
    ('error.document.changed_by_other', 'en',
     'Another user has already changed this document. Your changes were not saved — please reopen the document')
  ) AS v(text_key, code, value)
  JOIN dictionary_locale l ON l.code = v.code
ON CONFLICT (text_key, locale_id) DO UPDATE SET value = excluded.value;
