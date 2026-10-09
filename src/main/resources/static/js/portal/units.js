
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  const esc = v => String(v === null || v === undefined ? '' : v)
    .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
  const dash = v => (v === null || v === undefined || v === '') ? '—' : v;
  const say = m => { if (typeof notify === 'function') notify(m); };
  const T_ = k => (typeof T === 'function' ? T(k) : k);

  async function api(method, url, body){
    const opt = {method: method, headers: {'Accept': 'application/json'}};
    if (body !== undefined){
      opt.headers['Content-Type'] = 'application/json';
      opt.body = JSON.stringify(body);
    }
    const resp = await fetch(url, opt);
    if (!resp.ok) throw new Error(await PORTAL.сообщениеОтвета(resp));
    return resp.status === 204 ? null : resp.json();
  }

  // canEdit приходит с сервера, а не вычисляется по роли на странице:
  // решает всё равно сервер, и два разных правила рано или поздно
  // разошлись бы.
  const state = {rows: [], canEdit: false, form: null, loaded: false};
  PORTAL.units = state;

  /* ================= Разметка вкладки ================= */

  function создатьВкладку(){
    const tabs = document.querySelector('#view-references .admin-subtabs');
    if (!tabs || document.getElementById('ref-sub-units')) return;

    const кнопка = document.createElement('button');
    кнопка.className = 'admin-subtab-btn ref-subtab-btn';
    кнопка.type = 'button';
    кнопка.id = 'refSubBtnUnits';
    кнопка.setAttribute('data-refsub', 'units');
    кнопка.textContent = T_('references.button.units');
    кнопка.onclick = () => showReferencesSub('units');
    // Рядом с упаковками: товар, его упаковки, его единицы измерения.
    const после = tabs.querySelector('[data-refsub="packaging"]');
    if (после && после.nextSibling) tabs.insertBefore(кнопка, после.nextSibling);
    else tabs.appendChild(кнопка);

    const панель = document.createElement('div');
    панель.className = 'admin-sub ref-sub';
    панель.id = 'ref-sub-units';
    панель.innerHTML = ''
      + '<div class="panel-head"><h1>' + esc(T_('references.title.units')) + '</h1>'
      + '<div class="actions" id="unitsActions"></div></div>'
      + '<div class="hint" style="margin:-6px 0 12px;">'
      + esc(T_('references.hint.units')) + '</div>'
      + '<div class="table-card"><table><thead><tr id="unitsHead"></tr></thead>'
      + '<tbody id="unitsBody"></tbody></table></div>';
    const рядом = document.getElementById('ref-sub-packaging');
    if (рядом && рядом.parentNode){
      рядом.parentNode.insertBefore(панель, рядом.nextSibling);
    } else {
      document.getElementById('view-references').appendChild(панель);
    }
  }

  /**
   * Переключение подвкладок прототипа перебирает свой список имён
   * и о наших не знает. Поэтому оборачиваем: вкладку единиц
   * показываем сами, остальное отдаём прототипу.
   */
  function перехватитьПереключение(){
    if (!window.showReferencesSub || window.showReferencesSub.__units) return;
    const прежняя = window.showReferencesSub;
    const обёртка = function(sub){
      const наша = sub === 'units';
      const панель = document.getElementById('ref-sub-units');
      if (!наша){
        if (панель) панель.classList.remove('active');
        return прежняя(sub);
      }
      document.querySelectorAll('#view-references .ref-sub').forEach(d => {
        d.classList.toggle('active', d.id === 'ref-sub-units');
      });
      document.querySelectorAll('[data-refsub]').forEach(b => {
        b.classList.toggle('active', b.getAttribute('data-refsub') === 'units');
      });
      загрузить();
    };
    обёртка.__units = true;
    window.showReferencesSub = обёртка;
  }

  /* ================= Данные ================= */

  async function загрузить(){
    try {
      const ответ = await api('GET', '/api/units');
      state.rows = ответ.units || [];
      state.canEdit = ответ.canEdit === true;
      state.loaded = true;
    } catch (e){
      state.rows = [];
      say(e.message);
    }
    нарисовать();
  }

  /* ================= Отрисовка ================= */

  function нарисовать(){
    const body = document.getElementById('unitsBody');
    if (!body) return;
    const head = document.getElementById('unitsHead');
    if (head){
      head.innerHTML = '<th>' + esc(T_('references.column.mkei_code')) + '</th>'
        + '<th>' + esc(T_('references.column.unit_symbol')) + '</th>'
        + '<th>' + T('references.column.name') + '</th><th class="num">' + T('partners.text.order') + '</th><th>' + T('portal.text.in_use') + '</th>'
        + (state.canEdit ? '<th></th>' : '');
    }

    const строка = r => {
      if (state.form && state.form.id === r.unitId){
        return форма(r.isActive ? T('portal.text.yes') : 'нет');
      }
      return '<tr><td>' + esc(dash(r.mkeiCode)) + '</td>'
        + '<td><strong>' + esc(r.symbol) + '</strong></td>'
        + '<td>' + esc(r.name) + '</td>'
        + '<td class="num">' + esc(dash(r.sortOrder)) + '</td>'
        + '<td>' + (r.isActive ? T('portal.text.yes') : '<span class="count">' + T('portal.text.disabled') + '</span>') + '</td>'
        + (state.canEdit ? '<td style="white-space:nowrap;">'
            + кнопка('portalUnitEdit', r.unitId, T('portal.text.edit2'))
            + кнопка('portalUnitToggle', r.unitId, r.isActive ? T('portal.text.disable') : T('portal.text.enable'))
            + кнопка('portalUnitDelete', r.unitId, T('common.button.delete'))
            + '</td>' : '')
        + '</tr>';
    };

    let html = state.rows.map(строка).join('');
    if (state.form && state.form.id === null){
      html += форма(T('portal.text.yes'));
    }
    body.innerHTML = html
      || '<tr><td colspan="6" style="color:var(--muted);">' + T('partners.text.directory_is_empty') + '</td></tr>';

    const actions = document.getElementById('unitsActions');
    if (actions){
      actions.innerHTML = state.canEdit
        ? '<button class="btn primary" type="button" id="unitsCreateBtn">' + T('admin.button.add') + '</button>'
        : '';
      const btn = document.getElementById('unitsCreateBtn');
      if (btn) btn.onclick = () => portalUnitAdd();
    }
  }

  function кнопка(fn, id, подпись){
    return '<button class="btn ghost" type="button" onclick="' + fn + '(\'' + id + '\')">'
      + esc(подпись) + '</button> ';
  }

  // Длины те же, что у колонок базы: код МКЕИ — три знака,
  // обозначение — десять. Сервер проверяет это сам; здесь поле просто
  // не даёт набрать лишнее, чтобы отказ не приходил после «Сохранить».
  const ДЛИНА = {mkeiCode: 3, symbol: 10, name: 255};

  function форма(признак){
    const v = state.form.values;
    const поле = (col, num) => '<td><input class="kv-input" type="'
      + (num ? 'number' : 'text') + '" value="'
      + esc(v[col] === null || v[col] === undefined ? '' : v[col]) + '"'
      + (ДЛИНА[col] ? ' maxlength="' + ДЛИНА[col] + '"' : '')
      + ' data-unit="' + col + '" oninput="portalUnitField(this)"></td>';
    return '<tr>' + поле('mkeiCode') + поле('symbol') + поле('name')
      + поле('sortOrder', true) + '<td>' + esc(признак) + '</td>'
      + '<td style="white-space:nowrap;">'
      + '<button class="btn primary" type="button" onclick="portalUnitSave()">' + T('portal.text.save') + '</button> '
      + '<button class="btn ghost" type="button" onclick="portalUnitCancel()">' + T('common.button.cancel') + '</button>'
      + '</td></tr>';
  }

  /* ================= Действия ================= */

  window.portalUnitField = function(el){
    const v = el.value === '' ? null : el.value;
    state.form.values[el.getAttribute('data-unit')] = v;
  };

  window.portalUnitAdd = function(){
    state.form = {id: null, values: {}};
    нарисовать();
  };

  window.portalUnitEdit = function(id){
    const r = state.rows.find(x => String(x.unitId) === String(id));
    if (!r) return;
    state.form = {id: r.unitId, values:
      {mkeiCode: r.mkeiCode, symbol: r.symbol, name: r.name, sortOrder: r.sortOrder}};
    нарисовать();
  };

  window.portalUnitCancel = function(){
    state.form = null;
    нарисовать();
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
      const ответ = form.id === null
        ? await api('POST', '/api/units', body)
        : await api('PUT', '/api/units/' + encodeURIComponent(form.id), body);
      применить(ответ);
      state.form = null;
      нарисовать();
      say(T('portal.text.saved'));
    } catch (e){
      say(e.message);
    }
  };

  window.portalUnitToggle = async function(id){
    const r = state.rows.find(x => String(x.unitId) === String(id));
    if (!r) return;
    try {
      применить(await api('POST', '/api/units/' + encodeURIComponent(id) + '/archive',
                          {active: !r.isActive}));
      нарисовать();
    } catch (e){
      say(e.message);
    }
  };

  window.portalUnitDelete = async function(id){
    const r = state.rows.find(x => String(x.unitId) === String(id));
    if (!window.confirm('Удалить «' + (r ? r.name : id) + '»?')) return;
    try {
      применить(await api('DELETE', '/api/units/' + encodeURIComponent(id)));
      нарисовать();
      say(T('portal.text.deleted'));
    } catch (e){
      say(e.message);
    }
  };

  function применить(ответ){
    if (!ответ) return;
    state.rows = ответ.units || [];
    state.canEdit = ответ.canEdit === true;
  }

  /* ================= Установка ================= */

  function install(){
    PORTAL.onLogin(() => {
      создатьВкладку();
      перехватитьПереключение();
      state.form = null;
      state.loaded = false;
      // Права и список — чужие после смены поклажедателя или роли.
      // Сбрасываем сразу, а не при следующем открытии вкладки: иначе
      // на экране остались бы кнопки правки от прошлого входа.
      state.rows = [];
      state.canEdit = false;
      const панель = document.getElementById('ref-sub-units');
      if (панель && панель.classList.contains('active')) загрузить();
      else нарисовать();
    });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
