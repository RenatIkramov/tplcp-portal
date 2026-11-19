SET search_path = dbo;

-- V124. Тексты страницы «О нас» и вписанных контактов (аудит 11.11.2026,
-- A2, D1, D2).
--
-- Страницы «О нас» в портале больше нет: ссылка ведёт на сайт оператора
-- (about.url, V44), а разметка страницы — с восемью картинками в base64
-- на 2,3 МБ — грузилась каждому, и на форме входа тоже. «Контакты»
-- рисует contacts.js из справочников; вписанные в разметку реквизиты,
-- склады, менеджеры, почта feedback@ и кнопка-заглушка «Написать нам»
-- удалены (решение заказчика: убрать). Экран «Доступы менеджера» —
-- остаток группы «Доступы», которой нет с V111.
--
-- Ключи ниже больше нигде не упоминаются: ни в разметке, ни в коде, ни
-- в Java, ни в функциях базы, ни в справочнике колонок (проверено
-- поиском целым ключом 11.11.2026).

DELETE FROM ui_text WHERE text_key IN (
  'admin.button.back_to_manager_access',
  'admin.label.location',
  'common.text.about_the_company',
  'common.text.account_manager_almaty_warehouse',
  'common.text.account_manager_karaganda_warehous',
  'common.text.additional_services',
  'common.text.almaty_warehouse',
  'common.text.almaty_warehouse2',
  'common.text.area',
  'common.text.area_4_200_m_class_b_br_business_h',
  'common.text.area_6_700_m_class_b_br_business_h',
  'common.text.b_cosmetics_b_temperature_and_hum',
  'common.text.b_european_quality_b_full_range_o',
  'common.text.b_household_appliances_b_secure_p',
  'common.text.b_reliable_partner_b_we_work_with',
  'common.text.b_spare_parts_b_clear_labeling_an',
  'common.text.b_textile_b_storage_that_preserve',
  'common.text.b_transparent_pricing_b_we_do_not',
  'common.text.cargo_handling',
  'common.text.feedback_amp_complaints',
  'common.text.have_a_question_or_suggestion',
  'common.text.industry_solutions',
  'common.text.karaganda_warehouse',
  'common.text.office_space',
  'common.text.office_space2',
  'common.text.our_advantages',
  'common.text.our_services',
  'common.text.security',
  'common.text.technology',
  'common.text.tel_8_701_442_17_63_br_email_a_hre',
  'common.text.tel_8_905_895_95_85_br_email_a_hre',
  'common.text.temperature_control',
  'common.text.warehousing_services',
  'common.text.wms_amp_it_support'
);
DELETE FROM ui_text_key WHERE text_key IN (
  'admin.button.back_to_manager_access',
  'admin.label.location',
  'common.text.about_the_company',
  'common.text.account_manager_almaty_warehouse',
  'common.text.account_manager_karaganda_warehous',
  'common.text.additional_services',
  'common.text.almaty_warehouse',
  'common.text.almaty_warehouse2',
  'common.text.area',
  'common.text.area_4_200_m_class_b_br_business_h',
  'common.text.area_6_700_m_class_b_br_business_h',
  'common.text.b_cosmetics_b_temperature_and_hum',
  'common.text.b_european_quality_b_full_range_o',
  'common.text.b_household_appliances_b_secure_p',
  'common.text.b_reliable_partner_b_we_work_with',
  'common.text.b_spare_parts_b_clear_labeling_an',
  'common.text.b_textile_b_storage_that_preserve',
  'common.text.b_transparent_pricing_b_we_do_not',
  'common.text.cargo_handling',
  'common.text.feedback_amp_complaints',
  'common.text.have_a_question_or_suggestion',
  'common.text.industry_solutions',
  'common.text.karaganda_warehouse',
  'common.text.office_space',
  'common.text.office_space2',
  'common.text.our_advantages',
  'common.text.our_services',
  'common.text.security',
  'common.text.technology',
  'common.text.tel_8_701_442_17_63_br_email_a_hre',
  'common.text.tel_8_905_895_95_85_br_email_a_hre',
  'common.text.temperature_control',
  'common.text.warehousing_services',
  'common.text.wms_amp_it_support'
);
