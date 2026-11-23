/*
  «Справочники → Комплекты» (V154, решение заказчика 22.11.2026).

  Комплекты клиента рядом с «Упаковками»: код, наименование, состав
  («комплектующая × количество») и сколько можно собрать по складам —
  своего остатка у комплекта нет. Щелчок по строке открывает карточку
  товара на вкладке «Состав».

  Пока состав не приходит из WMS, администратор и сотрудник склада задают
  его в карточке; «+ Добавить комплект» — выбрать товар и сразу открыть
  правку состава. Список — GET /api/sku/kit-list.
*/
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  // общие помощники — core.js (аудит A4)
  const {esc, say, messageOf} = window.PORTAL;
  const T_ = (k, p) => (typeof T === 'function' ? T(k, p) : k);

  const KIT_EDITOR_ROLES = ['staff', 'admin'];
  const mayEditKit = () => KIT_EDITOR_ROLES.includes(PORTAL.session ? PORTAL.session.roleCode : '');

  const state = {
    /** @type {any[]} */ rows: [],
    loaded: false,
    adding: false
  };

  const byId = id => document.getElementById(id);

  async function load(){
    if (!PORTAL.kitsEnabled()) return;      // у клиента комплекты выключены (V155)
    try {
      state.rows = await PORTAL.fetchJson('/api/sku/kit-list') || [];
    } catch (e){
      state.rows = [];
      say(messageOf(e));
    }
    state.loaded = true;
    render();
  }

  /** @param {any} k */
  function kitRow(k){
    const components = (k.components || []).map((/** @type {any} */ c) =>
        '<div data-kit-part="' + esc(c.code) + '"><strong>' + esc(c.code) + '</strong> ' + esc(c.name)
        + ' <span style="color:var(--muted);">× ' + esc(c.qty) + (c.unitName ? ' ' + esc(c.unitName) : '') + '</span></div>')
        .join('');
    const assemble = (k.assemble || []).map((/** @type {any} */ a) =>
        '<div data-kit-assemble="' + esc(a.warehouseId) + '">' + esc(a.warehouseName || a.warehouseId)
        + ': <strong>' + esc(a.available) + '</strong></div>').join('');
    return '<tr data-kit="' + esc(k.code) + '"' + PORTAL.call('PORTAL.kits.open', k.skuId) + '>'
      + '<td><strong>' + esc(k.code) + '</strong></td>'
      + '<td>' + esc(k.name) + (k.isActive === false
          ? ' <span class="count">' + esc(T_('partners.text.archived')) + '</span>' : '') + '</td>'
      + '<td style="font-size:13px;">' + (components
          || '<span style="color:var(--muted);">' + esc(T_('references.text.kit_no_composition')) + '</span>') + '</td>'
      + '<td style="font-size:13px;">' + (assemble || '—') + '</td>'
      + '</tr>';
  }

  /** Товары, которые можно сделать комплектом: ещё не комплекты. */
  function kitCandidates(){
    const kits = new Set(state.rows.map(k => k.code));
    return /** @type {any[]} */ (PORTAL.catalog.sku || []).filter(s => !kits.has(s.code) && s.isActive !== false);
  }

  function render(){
    const panel = byId('ref-sub-kits');
    if (!panel) return;
    const editor = mayEditKit();
    const add = editor && state.adding
      ? '<div class="filters" data-kit-add-form>'
        + '<div class="fgroup" style="min-width:320px;"><label for="kitSkuPick">' + esc(T_('references.label.kit_sku')) + '</label>'
        + '<input id="kitSkuPick" list="kitSkuChoices" autocomplete="off"></div>'
        + '<datalist id="kitSkuChoices">' + kitCandidates().map(s =>
            '<option value="' + esc(s.code) + '">' + esc(s.name) + '</option>').join('') + '</datalist>'
        + '<div class="fgroup" style="justify-content:flex-end;"><label>&nbsp;</label><div style="display:flex;gap:8px;">'
        + '<button class="btn primary" type="button" data-kit-pick' + PORTAL.call('PORTAL.kits.pick') + '>'
        + esc(T_('references.button.set_composition')) + '</button>'
        + '<button class="btn" type="button"' + PORTAL.call('PORTAL.kits.cancelAdd') + '>'
        + esc(T_('common.button.cancel')) + '</button></div></div>'
        + '<div class="spacer"></div></div>'
      : '';
    const body = !state.loaded ? ''
      : state.rows.length ? state.rows.map(kitRow).join('')
      : '<tr><td colspan="4" style="color:var(--muted);">' + esc(T_('references.text.kits_empty')) + '</td></tr>';
    panel.innerHTML = '<div class="panel-head list-head">'
      + '<h1><span>' + esc(T_('references.button.kits')) + '</span> <span class="count" id="kitsCount">'
      + (state.loaded ? state.rows.length : '') + '</span></h1>'
      + '<div class="actions">' + (editor
          ? '<button class="btn" type="button" data-kit-add-btn' + PORTAL.call('PORTAL.kits.startAdd') + '>'
            + esc(T_('references.button.add_kit')) + '</button>' : '') + '</div></div>'
      + '<div class="hint" style="margin:0 0 12px;font-size:13px;color:var(--muted);">' + esc(T_('references.hint.kits')) + '</div>'
      + add
      + '<div class="table-card"><div class="table-scroll-x"><table data-kits><thead><tr>'
      + '<th>' + esc(T_('references.column.code')) + '</th>'
      + '<th>' + esc(T_('references.column.name')) + '</th>'
      + '<th>' + esc(T_('references.column.composition')) + '</th>'
      + '<th>' + esc(T_('references.column.can_assemble')) + '</th>'
      + '</tr></thead><tbody id="kitsBody">' + body + '</tbody></table></div></div>';
    const pick = /** @type {HTMLInputElement | null} */ (byId('kitSkuPick'));
    if (pick) pick.focus();
  }

  function pick(){
    const input = /** @type {HTMLInputElement | null} */ (byId('kitSkuPick'));
    const code = input ? input.value.trim() : '';
    const sku = kitCandidates().find(s => s.code === code);
    if (!sku){
      say(T_('references.message.pick_kit_sku'));
      return;
    }
    state.adding = false;
    PORTAL.skuCard.openKit(sku.skuId, true);
  }

  PORTAL.kits = {
    load,
    open: (/** @type {string} */ skuId) => PORTAL.skuCard.openKit(skuId, false),
    startAdd: () => { state.adding = true; render(); },
    cancelAdd: () => { state.adding = false; render(); },
    pick
  };

  /** Смена языка: перерисовать из уже полученного. */
  window.renderKits = render;
})();
