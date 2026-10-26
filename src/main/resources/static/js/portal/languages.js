/*
  Справочник языков — вкладка раздела «Справочники».

  Зачем отдельный экран, если признак в базе был с самого начала:
  `dictionary_locale.is_active` есть с V25, и `v_locales` его учитывает,
  но менять его было можно только запросом к базе. «Какие языки
  показывать» — настройка портала, а не разработки, и место ей
  в портале.

  Два признака, и они разные:
    * «Показывать» — язык есть в списке выбора;
    * «По умолчанию» — на этот язык откатываются тексты, перевода
      для которых нет. Такой язык ровно один (в базе на это стоит
      уникальный индекс) и выключить его нельзя.

  Как и в единицах измерения, право проверяет сервер: таблица общая
  для портала, не под RLS, политикой «кто вправе это менять»
  не выражается.
*/
(function(){
  'use strict';

  const esc = s => String(s === null || s === undefined ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  const T_ = k => (typeof T === 'function' ? T(k) : k);
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

  const state = {rows: [], loaded: false};
  PORTAL.languages = state;

  const isAdmin = () => PORTAL.session && PORTAL.session.roleCode === 'admin';

  /* ================= Разметка вкладки ================= */

  function createTab(){
    if (document.getElementById('admin-sub-languages')) return;
    const host = document.getElementById('view-admin');
    if (!host) return;

    // Кнопку вкладки не создаём: ряд собирает admin-tabs.js по своему
    // списку. Прежде вкладка стояла в «Справочниках», в одном ряду
    // с товарами клиента, — а языки портала ведёт администратор.
    const panel = document.createElement('div');
    panel.className = 'admin-sub';
    panel.id = 'admin-sub-languages';
    panel.innerHTML = ''
      + '<div class="panel-head"><h1 data-i18n="references.title.languages">'
      + PORTAL.t('references.title.languages') + '</h1></div>'
      + '<div class="hint" data-i18n="references.hint.languages" style="margin:-6px 0 12px;">'
      + PORTAL.t('references.hint.languages') + '</div>'
      + '<div class="table-card"><table><thead><tr id="languagesHead"></tr></thead>'
      + '<tbody id="languagesBody"></tbody></table></div>';
    host.appendChild(panel);
  }

  async function load(){
    if (!isAdmin()) return;
    try {
      state.rows = await api('GET', '/api/admin/locales');
      state.loaded = true;
    } catch (e){
      say(e.message);
      return;
    }
    render();
  }

  /* ================= Отрисовка ================= */

  function render(){
    const body = document.getElementById('languagesBody');
    if (!body) return;

    const head = document.getElementById('languagesHead');
    if (head){
      head.innerHTML = '<th>' + PORTAL.t('references.column.code') + '</th>'
        + '<th>' + PORTAL.t('references.column.name') + '</th>'
        + '<th class="num">' + PORTAL.t('references.column.translations') + '</th>'
        + '<th>' + PORTAL.t('references.column.shown') + '</th>'
        + '<th>' + PORTAL.t('references.column.default_language') + '</th>';
    }

    body.innerHTML = state.rows.map(row).join('')
      || '<tr><td colspan="5" style="color:var(--muted);">'
         + PORTAL.t('partners.text.directory_is_empty') + '</td></tr>';

    body.querySelectorAll('[data-lang-shown]').forEach(cb => {
      cb.onchange = () => shown(cb.getAttribute('data-lang-shown'), cb.checked);
    });
    body.querySelectorAll('[data-lang-default]').forEach(r => {
      r.onchange = () => makeDefault(r.getAttribute('data-lang-default'));
    });
  }

  function row(l){
    // Галочка языка по умолчанию отмечена, но выключена: сменить основной
    // язык можно, а снять признак ни с кого — нельзя, иначе откатываться
    // станет некуда.
    const shownBox = '<input type="checkbox" data-lang-shown="' + esc(l.code) + '"'
      + (l.isActive ? ' checked' : '')
      + (l.isDefault ? ' disabled title="'
          + esc(T_('references.hint.default_language_cannot_be_hidden')) + '"' : '')
      + '/>';
    const defaultBox = '<input type="radio" name="portalDefaultLocale"'
      + ' data-lang-default="' + esc(l.code) + '"' + (l.isDefault ? ' checked' : '') + '/>';
    return '<tr><td><strong>' + esc(l.code) + '</strong></td>'
      + '<td>' + esc(l.name) + '</td>'
      + '<td class="num">' + esc(l.textCount) + '</td>'
      + '<td>' + shownBox + '</td>'
      + '<td>' + defaultBox + '</td></tr>';
  }

  /* ================= Действия ================= */

  async function shown(code, value){
    try {
      state.rows = await api('PUT', '/api/admin/locales/' + encodeURIComponent(code),
          {shown: value});
      say(T_('portal.text.saved'));
    } catch (e){
      say(e.message);
    }
    render();
    await refreshPicker();
  }

  async function makeDefault(code){
    try {
      state.rows = await api('PUT', '/api/admin/locales/' + encodeURIComponent(code),
          {isDefault: true});
      say(T_('portal.text.saved'));
    } catch (e){
      say(e.message);
    }
    render();
    await refreshPicker();
  }

  /**
   * Переключатель языка в шапке собран из того же списка, поэтому
   * после правки справочника его надо пересобрать — иначе выключенный
   * язык остался бы в списке до перезагрузки страницы, и человек мог
   * бы его выбрать.
   */
  async function refreshPicker(){
    if (PORTAL.i18n && typeof PORTAL.i18n.rebuildSwitch === 'function'){
      await PORTAL.i18n.rebuildSwitch();
    }
  }

  /* ================= Установка ================= */

  function install(){
    PORTAL.onSession(() => {
      if (!isAdmin()) return;
      createTab();
      state.rows = [];
      state.loaded = false;
      // Список языков короткий и меняется раз в год — читается
      // при открытии вкладки, а не при входе.
      if (PORTAL.adminTabs) PORTAL.adminTabs.onOpen('languages', load);
    });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
