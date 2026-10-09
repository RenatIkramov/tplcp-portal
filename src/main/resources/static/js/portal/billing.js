
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  const T_ = k => (typeof T === 'function' ? T(k) : k);

  const TABS = [
    {id: 'reports',  key: 'billing.tab.reports'},
    {id: 'services', key: 'billing.tab.services_and_rates'},
    {id: 'invoices', key: 'billing.tab.invoices'}
  ];
  let active = 'reports';

  /**
   * Раскладывает блоки экрана по вкладкам.
   *
   * Опознаём блоки по tbody, который наполняет прототип: у самих
   * section-title и table-card идентификаторов нет, а переименовать их
   * значило бы править разметку в двух местах.
   */
  function spread(){
    const view = document.getElementById('view-billing');
    if (!view || view.querySelector('#billingTabs')) return;

    const kids = [...view.children];
    const ownerOf = {};

    // Блок начинается с заголовка (section-title или panel-head) и идёт
    // до следующего заголовка. Какой это блок, определяет tbody внутри.
    let current = null;
    const groups = [];
    kids.forEach(el => {
      const title = el.classList.contains('section-title')
        || (el.classList.contains('panel-head') && el.querySelector('.section-title'));
      if (title || !current){
        current = {nodes: [], table: null};
        groups.push(current);
      }
      current.nodes.push(el);
      ['billingServicesBody', 'billingReportsBody', 'billingInvoicesBody'].forEach(id => {
        if (el.querySelector && el.querySelector('#' + id)) current.table = id;
      });
    });

    ownerOf.billingReportsBody = 'reports';
    ownerOf.billingServicesBody = 'services';
    ownerOf.billingInvoicesBody = 'invoices';

    const screenTitle = view.querySelector('.panel-head');
    const bars = {};
    TABS.forEach(tab => {
      const d = document.createElement('div');
      d.className = 'admin-sub';
      d.id = 'billing-sub-' + tab.id;
      bars[tab.id] = d;
    });

    groups.forEach(grp => {
      // первый блок — это шапка экрана с кнопкой экспорта, она не таблица
      if (grp.nodes[0] === screenTitle) return;
      const target = ownerOf[grp.table];
      if (!target) return;
      grp.nodes.forEach(el => bars[target].appendChild(el));
    });

    const bar = document.createElement('div');
    bar.className = 'admin-subtabs';
    bar.id = 'billingTabs';
    view.appendChild(bar);
    TABS.forEach(tab => view.appendChild(bars[tab.id]));

    // Кнопка «Экспортировать в Excel» выгружает тарифы и услуги —
    // на других вкладках она сбивала бы с толку, поэтому переезжает
    // вместе с ними.
    const exportBtn = screenTitle
      && screenTitle.querySelector('.actions');
    if (exportBtn) bars.services.insertBefore(exportBtn, bars.services.firstChild);

    draw();
  }

  function draw(){
    const bar = document.getElementById('billingTabs');
    if (!bar) return;
    bar.innerHTML = TABS.map(tab =>
      '<button class="admin-subtab-btn' + (tab.id === active ? ' active' : '') + '"'
      + ' type="button" data-billing-tab="' + tab.id + '">'
      + T_(tab.key) + '</button>').join('');
    bar.querySelectorAll('[data-billing-tab]').forEach(b => {
      b.onclick = () => { active = b.getAttribute('data-billing-tab'); draw(); };
    });
    TABS.forEach(tab => {
      const el = document.getElementById('billing-sub-' + tab.id);
      if (el) el.classList.toggle('active', tab.id === active);
    });
  }

  window.showBillingSub = function(id){
    if (TABS.some(tab => tab.id === id)) active = id;
    draw();
  };

  function install(){
    spread();
    // подписи вкладок приходят вместе с переводами
    PORTAL.onLogin(() => { spread(); draw(); });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
