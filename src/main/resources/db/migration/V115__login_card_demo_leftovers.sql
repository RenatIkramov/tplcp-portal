SET search_path = dbo;

-- V115. Карточка входа без следов демо (11.11.2026, разбор анализатора IDE).
--
-- Подпись «Демо-доступ — выберите пользователя» и список демо-пользователей
-- под формой входа убраны из разметки (прежде их удалял auth.js при
-- загрузке страницы). Ключ подписи больше никому не нужен.

DELETE FROM ui_text     WHERE text_key = 'common.text.demo_access_select_a_user';
DELETE FROM ui_text_key WHERE text_key = 'common.text.demo_access_select_a_user';
