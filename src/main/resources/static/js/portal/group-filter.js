
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
  function заполнить(id, значения){
    const select = document.getElementById(id);
    if (!select) return;
    const было = select.value;
    const первый = select.querySelector('option');
    select.innerHTML = первый ? первый.outerHTML : '<option value=""></option>';
    значения.forEach(v => {
      select.insertAdjacentHTML('beforeend',
        '<option value="' + esc(v) + '">' + esc(v) + '</option>');
    });
    if (было && значения.indexOf(было) >= 0) select.value = было;
  }

  async function обновить(){
    let dict;
    try {
      const resp = await fetch('/api/sku/dictionaries', {headers: {'Accept': 'application/json'}});
      if (!resp.ok) return;
      dict = await resp.json();
    } catch (e){
      return;            // фильтр останется с одним пунктом «Все» — не беда
    }
    const имена = kind => ((dict && dict[kind]) || []).map(o => o.name).filter(Boolean);
    const группы = имена('group');
    заполнить('skuFilterGroup', группы);
    заполнить('skuFilterType', имена('type'));
    // В запасах фильтр по группе тот же: регистр показывает группу
    // товара (представление v_stock_lines отдаёт её строкой).
    заполнить('stockFilterGroup', группы);
  }

  // onLogin выполняется и после входа, и после смены поклажедателя
  // (PORTAL.reloadAll прогоняет тот же список), а группы у разных
  // клиентов свои — чужие в фильтре были бы утечкой.
  function install(){
    PORTAL.onLogin(() => { обновить(); });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
