SET search_path = dbo;

-- =====================================================================
-- V129. Мёртвые объекты базы (аудит 11.11.2026, D3).
--
-- Ни один не читается ни кодом портала, ни функциями, ни представлениями,
-- ни политиками, ни тестами, ни tools/ (проверено 12.11.2026 поиском по
-- исходникам и по телам функций в базе):
--   * v_users, v_user_access (V10), v_operators (V33) — представления,
--     которые никто не читает;
--   * admin_unlock_locked_columns() (V54) — разовая правка, вызвана в самой
--     V54 и больше нигде;
--   * user_filters_copy(uuid, uuid) (V70, V71) — копирование отборов, экран
--     так и не появился;
--   * расширение pgcrypto (V1) — приложение его не использует: uuid даёт
--     встроенная gen_random_uuid(), хэши паролей — BCrypt в Java, отпечаток
--     токена «запомнить меня» — SHA-256 в Java.
--
-- Оставлены: user_status_code() — её зовёт admin_users (в аудите она
-- ошибочно названа невызываемой); cache_policy — ждёт ответа заказчика;
-- sync_watermark, registered_service, document_photo, v_document_photos,
-- resolve_wms_status() — задел этапов 8–9.
-- =====================================================================

DROP VIEW IF EXISTS v_users;
DROP VIEW IF EXISTS v_user_access;
DROP VIEW IF EXISTS v_operators;

DROP FUNCTION IF EXISTS admin_unlock_locked_columns();
DROP FUNCTION IF EXISTS user_filters_copy(uuid, uuid);

-- Расширение — только если от него ничего не зависит. На чужой базе, где
-- им всё-таки что-то пользуется, миграция не должна ни падать, ни снести
-- зависящее (CASCADE здесь нельзя).
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pgcrypto') THEN
    BEGIN
      DROP EXTENSION pgcrypto;
    EXCEPTION WHEN dependent_objects_still_exist THEN
      RAISE NOTICE 'pgcrypto оставлено: от него зависят объекты базы';
    END;
  END IF;
END
$$;
