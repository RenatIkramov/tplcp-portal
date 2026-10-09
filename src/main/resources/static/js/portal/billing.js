
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  const T_ = k => (typeof T === 'function' ? T(k) : k);

  const ВКЛАДКИ = [
    {id: 'reports',  ключ: 'billing.tab.reports'},
    {id: 'services', ключ: 'billing.tab.services_and_rates'},
    {id: 'invoices', ключ: 'billing.tab.invoices'}
  ];
  let активная = 'reports';

  /**
   * Раскладывает блоки экрана по вкладкам.
   *
   * Опознаём блоки по tbody, который наполняет прототип: у самих
   * section-title и table-card идентификаторов нет, а переименовать их
   * значило бы править разметку в двух местах.
   */
  function разложить(){
    const view = document.getElementById('view-billing');
    if (!view || view.querySelector('#billingTabs')) return;

    const узел = id => document.getElementById(id);
    const дети = [...view.children];
    const принадлежность = {};

    // Блок начинается с заголовка (section-title или panel-head) и идёт
    // до следующего заголовка. Какой это блок, определяет tbody внутри.
    let текущий = null;
    const группы = [];
    дети.forEach(el => {
      const заголовок = el.classList.contains('section-title')
        || (el.classList.contains('panel-head') && el.querySelector('.section-title'));
      if (заголовок || !текущий){
        текущий = {узлы: [], таблица: null};
        группы.push(текущий);
      }
      текущий.узлы.push(el);
      ['billingServicesBody', 'billingReportsBody', 'billingInvoicesBody'].forEach(id => {
        if (el.querySelector && el.querySelector('#' + id)) текущий.таблица = id;
      });
    });

    принадлежность.billingReportsBody = 'reports';
    принадлежность.billingServicesBody = 'services';
    принадлежность.billingInvoicesBody = 'invoices';

    const заголовокЭкрана = view.querySelector('.panel-head');
    const полосы = {};
    ВКЛАДКИ.forEach(в => {
      const d = document.createElement('div');
      d.className = 'admin-sub';
      d.id = 'billing-sub-' + в.id;
      полосы[в.id] = d;
    });

    группы.forEach(г => {
      // первый блок — это шапка экрана с кнопкой экспорта, она не таблица
      if (г.узлы[0] === заголовокЭкрана) return;
      const куда = принадлежность[г.таблица];
      if (!куда) return;
      г.узлы.forEach(el => полосы[куда].appendChild(el));
    });

    const полоса = document.createElement('div');
    полоса.className = 'admin-subtabs';
    полоса.id = 'billingTabs';
    view.appendChild(полоса);
    ВКЛАДКИ.forEach(в => view.appendChild(полосы[в.id]));

    // Кнопка «Экспортировать в Excel» выгружает тарифы и услуги —
    // на других вкладках она сбивала бы с толку, поэтому переезжает
    // вместе с ними.
    const экспорт = заголовокЭкрана
      && заголовокЭкрана.querySelector('.actions');
    if (экспорт) полосы.services.insertBefore(экспорт, полосы.services.firstChild);

    отрисовать();
  }

  function отрисовать(){
    const полоса = document.getElementById('billingTabs');
    if (!полоса) return;
    полоса.innerHTML = ВКЛАДКИ.map(в =>
      '<button class="admin-subtab-btn' + (в.id === активная ? ' active' : '') + '"'
      + ' type="button" data-billing-tab="' + в.id + '">'
      + T_(в.ключ) + '</button>').join('');
    полоса.querySelectorAll('[data-billing-tab]').forEach(b => {
      b.onclick = () => { активная = b.getAttribute('data-billing-tab'); отрисовать(); };
    });
    ВКЛАДКИ.forEach(в => {
      const el = document.getElementById('billing-sub-' + в.id);
      if (el) el.classList.toggle('active', в.id === активная);
    });
  }

  window.showBillingSub = function(id){
    if (ВКЛАДКИ.some(в => в.id === id)) активная = id;
    отрисовать();
  };

  function install(){
    разложить();
    // подписи вкладок приходят вместе с переводами
    PORTAL.onLogin(() => { разложить(); отрисовать(); });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
