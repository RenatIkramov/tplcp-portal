SET search_path = dbo;

-- V112. Переводы перечислений — в одном месте (11.11.2026).
--
-- Переводы значений перечислений правились в двух местах: вкладкой
-- «Перечисления» в группе «Тексты интерфейса» (V86, сводная таблица)
-- и в окне значения в группе «Перечисления» (V95: имя, обозначение,
-- переводы на все языки, отбор «только без перевода»). Решение
-- заказчика — оставить одно место: окно значения. Вкладка, её API
-- (/api/admin/enum-texts) и сводная функция убраны; enum_text_save
-- остаётся — ею сохраняет окно значения.

DROP FUNCTION enum_text_matrix();

-- Тексты, которые были только у убранной вкладки.
CREATE TEMP TABLE _dead_keys (text_key varchar PRIMARY KEY);
INSERT INTO _dead_keys (text_key) VALUES
  ('admin.hint.enums'),
  ('admin.label.enum'),
  ('admin.option.all_enums'),
  ('admin.placeholder.enum_search'),
  ('admin.placeholder.enum_symbol');
DELETE FROM _dead_keys d
 WHERE EXISTS (SELECT 1 FROM table_column c WHERE c.text_key = d.text_key OR c.group_key = d.text_key);
DELETE FROM ui_text t USING _dead_keys d WHERE t.text_key = d.text_key;
DELETE FROM ui_text_key k USING _dead_keys d WHERE k.text_key = d.text_key;
DROP TABLE _dead_keys;
