
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  const esc = v => String(v === null || v === undefined ? '' : v)
    .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
  const dash = v => (v === null || v === undefined || v === '') ? '—' : v;
  const say = m => { if (typeof notify === 'function') notify(m); };

  async function api(method, url, body){
    const opt = {method: method, headers: {'Accept': 'application/json'}};
    if (body !== undefined){
      opt.headers['Content-Type'] = 'application/json';
      opt.body = JSON.stringify(body);
    }
    const resp = await fetch(url, opt);
    if (!resp.ok) throw new Error(await PORTAL.responseMessage(resp));
    return resp.status === 204 ? null : resp.json();
  }

  // canEdit приходит с сервера, а не вычисляется по роли на странице:
  // решает всё равно сервер, и два разных правила рано или поздно
  // разошлись бы.
  const state = {rows: [], canEdit: false, form: null, loaded: false};
  PORTAL.units = state;

  /* ================= Разметка вкладки ================= */

  function createTab(){
    if (document.getElementById('admin-sub-units')) return;
    const host = document.getElementById('view-admin');
    if (!host) return;

    // Кнопку вкладки не создаём: ряд собирает admin-tabs.js по своему
    // списку. Прежде вкладка жила в «Справочниках» рядом с товарами
    // клиента — а единицы измерения ведёт администратор, и они одни
    // на весь портал.
    const panel = document.createElement('div');
    panel.className = 'admin-sub';
    panel.id = 'admin-sub-units';
    panel.innerHTML = ''
      + '<div class="panel-head"><h1 data-i18n="references.title.units">'
      + PORTAL.t('references.title.units') + '</h1>'
      + '<div class="actions" id="unitsActions"></div></div>'
      + '<div class="hint" data-i18n="references.hint.units" style="margin:-6px 0 12px;">'
      + PORTAL.t('references.hint.units') + '</div>'
      + '<div class="table-card"><table><thead><tr id="unitsHead"></tr></thead>'
      + '<tbody id="unitsBody"></tbody></table></div>';
    host.appendChild(panel);
  }

  /* ================= Данные ================= */

  async function load(){
    try {
      const response = await api('GET', '/api/units');
      state.rows = response.units || [];
      state.canEdit = response.canEdit === true;
      state.loaded = true;
    } catch (e){
      state.rows = [];
      say(e.message);
    }
    render();
  }

  /* ================= Отрисовка ================= */

  function render(){
    const body = document.getElementById('unitsBody');
    if (!body) return;
    const head = document.getElementById('unitsHead');
    if (head){
      head.innerHTML = '<th>' + PORTAL.t('references.column.mkei_code') + '</th>'
        + '<th>' + PORTAL.t('references.column.unit_symbol') + '</th>'
        + '<th>' + PORTAL.t('references.column.name') + '</th>'
        + '<th class="num">' + PORTAL.t('partners.text.order') + '</th>'
        + '<th>' + PORTAL.t('portal.text.in_use') + '</th>'
        + (state.canEdit ? '<th></th>' : '');
    }

    const row = r => {
      if (state.form && state.form.id === r.unitId){
        return formRow(r.isActive ? 'portal.text.yes' : 'portal.text.no');
      }
      return '<tr><td>' + esc(dash(r.mkeiCode)) + '</td>'
        + '<td><strong>' + esc(r.symbol) + '</strong></td>'
        + '<td>' + esc(r.name) + '</td>'
        + '<td class="num">' + esc(dash(r.sortOrder)) + '</td>'
        + '<td>' + (r.isActive ? PORTAL.t('portal.text.yes')
            : '<span class="count">' + PORTAL.t('portal.text.disabled') + '</span>') + '</td>'
        + (state.canEdit ? '<td style="white-space:nowrap;">'
            + button('portalUnitEdit', r.unitId, 'portal.text.edit2')
            + button('portalUnitToggle', r.unitId,
                r.isActive ? 'portal.text.disable' : 'portal.text.enable')
            + button('portalUnitDelete', r.unitId, 'common.button.delete')
            + '</td>' : '')
        + '</tr>';
    };

    let html = state.rows.map(row).join('');
    if (state.form && state.form.id === null){
      html += formRow('portal.text.yes');
    }
    body.innerHTML = html
      || '<tr><td colspan="6" style="color:var(--muted);">'
         + PORTAL.t('partners.text.directory_is_empty') + '</td></tr>';

    const actions = document.getElementById('unitsActions');
    if (actions){
      actions.innerHTML = state.canEdit
        ? '<button class="btn primary" type="button" id="unitsCreateBtn"'
          + ' data-i18n="admin.button.add">' + T('admin.button.add') + '</button>'
        : '';
      const btn = document.getElementById('unitsCreateBtn');
      if (btn) btn.onclick = () => portalUnitAdd();
    }
  }

  // Принимает КЛЮЧ текста, а не готовую подпись: иначе кнопка осталась бы
  // на том языке, который был в момент отрисовки.
  function button(fn, id, textKey){
    return '<button class="btn ghost" type="button" data-i18n="' + textKey + '"'
      + ' onclick="' + fn + '(\'' + id + '\')">' + esc(T(textKey)) + '</button> ';
  }

  // Длины те же, что у колонок базы: код МКЕИ — три знака,
  // обозначение — десять. Сервер проверяет это сам; здесь поле просто
  // не даёт набрать лишнее, чтобы отказ не приходил после «Сохранить».
  const MAX_LENGTH = {mkeiCode: 3, symbol: 10, name: 255};

  function formRow(flagKey){
    const v = state.form.values;
    const field = (col, num) => '<td><input class="kv-input" type="'
      + (num ? 'number' : 'text') + '" value="'
      + esc(v[col] === null || v[col] === undefined ? '' : v[col]) + '"'
      + (MAX_LENGTH[col] ? ' maxlength="' + MAX_LENGTH[col] + '"' : '')
      + ' data-unit="' + col + '" oninput="portalUnitField(this)"></td>';
    return '<tr>' + field('mkeiCode') + field('symbol') + field('name')
      + field('sortOrder', true) + '<td>' + PORTAL.t(flagKey) + '</td>'
      + '<td style="white-space:nowrap;">'
      + '<button class="btn primary" type="button" data-i18n="portal.text.save"'
      + ' onclick="portalUnitSave()">' + T('portal.text.save') + '</button> '
      + '<button class="btn ghost" type="button" data-i18n="common.button.cancel"'
      + ' onclick="portalUnitCancel()">' + T('common.button.cancel') + '</button>'
      + '</td></tr>';
  }

  /* ================= Действия ================= */

  window.portalUnitField = function(el){
    state.form.values[el.getAttribute('data-unit')] =
        el.value === '' ? null : el.value;
  };

  window.portalUnitAdd = function(){
    state.form = {id: null, values: {}};
    render();
  };

  window.portalUnitEdit = function(id){
    const r = state.rows.find(x => String(x.unitId) === String(id));
    if (!r) return;
    state.form = {id: r.unitId, values:
      {mkeiCode: r.mkeiCode, symbol: r.symbol, name: r.name, sortOrder: r.sortOrder}};
    render();
  };

  window.portalUnitCancel = function(){
    state.form = null;
    render();
  };

  window.portalUnitSave = async function(){
    const form = state.form;
    const r = form.id === null
      ? null : state.rows.find(x => String(x.unitId) === String(form.id));
    const body = {
      mkeiCode: form.values.mkeiCode,
      symbol: form.values.symbol,
      name: form.values.name,
      sortOrder: form.values.sortOrder === null || form.values.sortOrder === undefined
        ? null : Number(form.values.sortOrder),
      // правка не должна менять признак «действует»: он меняется
      // отдельной кнопкой, и иначе правка возвращала бы из архива
      isActive: r ? r.isActive : true
    };
    try {
      const response = form.id === null
        ? await api('POST', '/api/units', body)
        : await api('PUT', '/api/units/' + encodeURIComponent(form.id), body);
      apply(response);
      state.form = null;
      render();
      say(T('portal.text.saved'));
    } catch (e){
      say(e.message);
    }
  };

  window.portalUnitToggle = async function(id){
    const r = state.rows.find(x => String(x.unitId) === String(id));
    if (!r) return;
    try {
      apply(await api('POST', '/api/units/' + encodeURIComponent(id) + '/archive',
                          {active: !r.isActive}));
      render();
    } catch (e){
      say(e.message);
    }
  };

  window.portalUnitDelete = async function(id){
    const r = state.rows.find(x => String(x.unitId) === String(id));
    if (!window.confirm(T('common.message.confirm_delete_named',
        { name: r ? r.name : id }))) return;
    try {
      apply(await api('DELETE', '/api/units/' + encodeURIComponent(id)));
      render();
      say(T('portal.text.deleted'));
    } catch (e){
      say(e.message);
    }
  };

  function apply(response){
    if (!response) return;
    state.rows = response.units || [];
    state.canEdit = response.canEdit === true;
  }

  /* ================= Установка ================= */

  function install(){
    // Справочник единиц один на весь портал и от выбранного клиента
    // не зависит — иначе вкладка появлялась бы только после выбора
    // поклажедателя, а администратору выбор обязателен.
    PORTAL.onSession(() => {
      // Вкладка живёт в администрировании, и раздел этот открыт
      // администратору и разработчику. Поклажедателю единица
      // измерения подписана в товарах и упаковках.
      if (!['admin', 'developer'].includes(PORTAL.session && PORTAL.session.roleCode)){
        return;
      }
      createTab();
      state.form = null;
      state.loaded = false;
      // Права и список — чужие после смены поклажедателя или роли.
      // Сбрасываем сразу, а не при следующем открытии вкладки: иначе
      // на экране остались бы кнопки правки от прошлого входа.
      state.rows = [];
      state.canEdit = false;
      render();
      // Список короткий и меняется редко — читаем при открытии вкладки,
      // а не при входе: незачем спрашивать сервер о том, на что никто
      // не смотрит.
      if (PORTAL.adminTabs) PORTAL.adminTabs.onOpen('units', load);
    });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
