/*
  Страница «Контакты» из справочников.

  Вёрстку не меняем: в прототипе это уже «два столбца, блок на запись»
  (`about-grid` + `about-card`), и согласованный вид трогать незачем.
  Меняется источник: вместо вписанных в разметку реквизитов, складов
  и менеджеров — данные справочников, которые администратор ведёт
  и так.

  Почему перерисовываем всю страницу, а не подменяем значения
  по местам: блоков заранее неизвестное число. Складов может быть
  один, а может пять; менеджеров столько, сколько их в портале.
*/
(function(){
  'use strict';

  const esc = s => String(s === null || s === undefined ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  const T_ = k => (typeof T === 'function' ? T(k) : k);
  const dash = v => (v === null || v === undefined || v === '' ? '—' : v);

  const state = {loaded: false, data: null};
  PORTAL.contacts = state;

  /* ================= Загрузка ================= */

  async function load(){
    try {
      state.data = await PORTAL.fetchJson('/api/contacts');
      state.loaded = true;
    } catch (e){
      if (typeof notify === 'function') notify(e.message);
      return;
    }
    render();
  }

  /* ================= Отрисовка ================= */

  function render(){
    const view = document.getElementById('view-contacts');
    if (!view || !state.data) return;

    // Заголовок и кнопка «Назад» в разметке остаются, меняем всё после них.
    const keep = view.querySelector('.back-btn');
    const head = view.querySelector('.panel-head');
    view.innerHTML = '';
    if (keep) view.appendChild(keep);
    if (head) view.appendChild(head);

    view.insertAdjacentHTML('beforeend',
        operators() + warehouses() + managers());
  }

  function title(key){
    return '<div class="section-title">' + PORTAL.t(key) + '</div>';
  }

  /** Пара «подпись — значение» в сетке реквизитов. */
  function kv(labelKey, value){
    return '<div class="kv"><label>' + PORTAL.t(labelKey) + '</label>'
      + '<div>' + value + '</div></div>';
  }

  /**
   * Почта ссылкой. Адрес экранируется и в href, и в тексте: он приходит
   * из справочника, то есть его ввёл человек, и кавычка в нём разорвала
   * бы атрибут.
   */
  function mail(address){
    return address ? '<a href="mailto:' + esc(address) + '">' + esc(address) + '</a>' : '—';
  }

  function operators(){
    const rows = state.data.operators || [];
    if (!rows.length){
      return title('common.text.company_details')
        + '<div class="hint">' + PORTAL.t('common.text.no_contacts_yet') + '</div>';
    }
    // Организаций может быть несколько: клиент видит свою, менеджер —
    // все, с которыми работает оператор.
    return title('common.text.company_details')
      + rows.map(o =>
          '<div class="detail-head"><div class="kv-grid"'
          + ' style="grid-template-columns:repeat(auto-fit,minmax(220px,1fr));width:100%;">'
          + kv('common.text.full_legal_name', '<strong>' + esc(dash(o.name)) + '</strong>')
          + kv('references.column.bin', esc(dash(o.bin)))
          + kv('common.text.legal_address', esc(dash(o.legalAddress)))
          + kv('common.text.bank', esc(dash(o.bankName)))
          + kv('common.text.iban', esc(dash(o.bankAccount)))
          + kv('partners.label.phone', esc(dash(o.phone)))
          + kv('common.text.general_email', mail(o.email))
          + '</div></div>').join('');
  }

  function warehouses(){
    const rows = state.data.warehouses || [];
    if (!rows.length) return '';
    return title('references.button.warehouses')
      + '<div class="about-grid">'
      + rows.map(w => card(esc(w.name), [
          esc(dash(w.address)),
          line('portal.label.warehouse_area', w.areaM2),
          line('portal.label.work_hours', w.workHours),
          line('common.text.responsible_person', w.responsible),
          w.phone ? esc(w.phone) : '',
          w.email ? mail(w.email) : ''
        ])).join('')
      + '</div>';
  }

  function managers(){
    const rows = state.data.managers || [];
    if (!rows.length) return '';
    return title('common.text.account_managers')
      + '<div class="about-grid">'
      + rows.map(m => card(
          esc(dash(m.fullName))
            // Менеджер, который ведёт этого клиента, помечен. Какие ещё
            // клиенты у него есть, не показываем: по такому списку один
            // клиент узнал бы о других.
            + (m.isYours ? ' <span class="count">'
                + esc(T_('common.text.your_manager')) + '</span>' : ''),
          [
            esc(dash(m.jobTitle)),
            m.phone ? esc(m.phone) : '',
            m.email ? mail(m.email) : ''
          ])).join('')
      + '</div>';
  }

  /** Строка «подпись: значение», пустое значение строку не создаёт. */
  function line(labelKey, value){
    if (value === null || value === undefined || value === '') return '';
    return PORTAL.t(labelKey) + ': ' + esc(value);
  }

  function card(heading, lines){
    const body = lines.filter(Boolean);
    return '<div class="about-card"><div class="about-card-body">'
      + '<h3>' + heading + '</h3>'
      + body.map((t, i) => '<p' + (i ? ' style="margin-top:6px;"' : '') + '>'
          + t + '</p>').join('')
      + '</div></div>';
  }

  /* ================= Установка ================= */

  function install(){
    const original = window.openContacts;
    window.openContacts = function(){
      if (typeof original === 'function') original.apply(this, arguments);
      // Читаем при каждом открытии: справочники правятся в соседней
      // вкладке, и показывать вчерашний адрес склада незачем.
      load();
    };

    // Смена языка перерисовывает подписи сама — они с data-i18n, —
    // но блоки собраны из данных, поэтому после смены языка
    // перерисовываем страницу целиком, если она открыта.
    PORTAL.onSession(() => { state.loaded = false; state.data = null; });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
