
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  const esc = v => String(v === null || v === undefined ? '' : v)
    .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');

  /**
   * Заполняет один список выбора.
   *
   * Первый пункт — «Все» — остаётся из вёрстки: он размечен ключом
   * перевода, и трогать его незачем. Остальные заменяются.
   * Выбранное значение сохраняем: иначе обновление списка сбрасывало бы
   * фильтр, который человек только что поставил.
   */
  function fill(id, values){
    const select = document.getElementById(id);
    if (!select) return;
    const before = select.value;
    const first = select.querySelector('option');
    select.innerHTML = first ? first.outerHTML : '<option value=""></option>';
    values.forEach(v => {
      select.insertAdjacentHTML('beforeend',
        '<option value="' + esc(v) + '">' + esc(v) + '</option>');
    });
    if (before && values.indexOf(before) >= 0) select.value = before;
  }

  /**
   * Подписи самих фильтров — именем клиента, если он поле переименовал.
   *
   * Фильтр «Группа товара» над списком — это то же поле, что колонка
   * «Группа товара» в таблице. Если клиент переименовал поле, а фильтр
   * над ним остался портальным, человек видит на одном экране два
   * названия одного и того же.
   *
   * Атрибут data-i18n при переименовании СНИМАЕМ: иначе ближайшая
   * смена языка нашла бы этот узел и вернула портальную подпись.
   * Когда переименования нет, атрибут возвращаем на место — подпись
   * снова ведёт себя как обычный переводимый текст.
   */
  const FILTERS = [
    ['skuFilterGroup', 'sku', 'group'],
    ['skuFilterType', 'sku', 'productType'],
    // В запасах фильтр по той же группе товара
    ['stockFilterGroup', 'stock', 'group']
  ];

  function captions(){
    FILTERS.forEach(([selectId, tableKey, columnId]) => {
      const label = document.querySelector('label[for="' + selectId + '"]');
      if (!label) return;
      // ключ запоминаем при первом проходе: дальше его может не быть
      // в атрибуте, если подпись уже заменена именем клиента
      if (!label.dataset.portalKey && label.getAttribute('data-i18n')){
        label.dataset.portalKey = label.getAttribute('data-i18n');
      }
      const own = PORTAL.fieldRename ? PORTAL.fieldRename(tableKey, columnId) : null;
      if (own){
        label.removeAttribute('data-i18n');
        label.textContent = own;
      } else if (label.dataset.portalKey){
        label.setAttribute('data-i18n', label.dataset.portalKey);
        label.textContent = typeof T === 'function'
          ? T(label.dataset.portalKey) : label.dataset.portalKey;
      }
    });
  }

  async function refresh(){
    // Подписи фильтров не зависят от справочников: ставим их сразу,
    // чтобы отказ запроса не оставил портальные названия у клиента,
    // который поля переименовал.
    captions();
    let dict;
    try {
      const resp = await fetch('/api/sku/dictionaries', {headers: {'Accept': 'application/json'}});
      if (!resp.ok) return;
      dict = await resp.json();
    } catch (e){
      return;            // фильтр останется с одним пунктом «Все» — не беда
    }
    const names = kind => ((dict && dict[kind]) || []).map(o => o.name).filter(Boolean);
    const groups = names('group');
    fill('skuFilterGroup', groups);
    fill('skuFilterType', names('type'));
    // В запасах фильтр по группе тот же: регистр показывает группу
    // товара (представление v_stock_lines отдаёт её строкой).
    fill('stockFilterGroup', groups);
    captions();
  }

  // onLogin выполняется и после входа, и после смены поклажедателя
  // (PORTAL.reloadAll прогоняет тот же список), а группы у разных
  // клиентов свои — чужие в фильтре были бы утечкой.
  function install(){
    PORTAL.onLogin(() => { refresh(); });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
