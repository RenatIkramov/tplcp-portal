/*
  Документ из Excel: кнопки «Скачать шаблон Excel» и «Загрузить из Excel»
  на экранах заказов (V96), поступлений (V100) и перемещений (V101).

  Решения заказчика от 27.10 и 03.11.2026:
    * один файл — один документ, результат — черновик;
    * окна проверки нет. Ошибки — окном со списком (таблица, строка,
      поле, что не так), и ничего не создаётся. Ошибок нет — черновик
      создан и сразу открыт: карточка и есть окно проверки,
      подтверждает человек сам.

  У документов всё общее, кроме адресов, прав и нескольких фраз —
  они в DOCS. Окно ошибок одно на все экраны.

  Кнопки видны тому, кто вправе создавать документ; сервер проверяет это
  право сам (кнопка, скрытая на экране, не закрывает адрес).
*/
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  // общие помощники — core.js (аудит A4)
  const {esc, say, messageOf} = window.PORTAL;
  const tr = (k, p) => (typeof T === 'function' ? T(k, p) : k);
  const byId = id => document.getElementById(id);
  /** Кнопка по идентификатору; null — кнопки на экране нет. */
  const buttonById = id => /** @type {HTMLButtonElement | null} */ (byId(id));
  /** Элемент окна ошибок: его разметку собирает createModal, он обязан быть. */
  const part = id => {
    const el = byId(id);
    if (!el) throw new Error('doc-import: no element #' + id);
    return el;
  };
  /** Текст исключения: бросают не только Error. */

  /**
   * Чем документы отличаются на экране.
   * @type {Record<string, DocImportScreen>}
   */
  const DOCS = {
    orders: {
      importBtn: 'orderImportBtn', templateBtn: 'orderTemplateBtn',
      api: '/api/orders/excel-import', download: 'portalDownloadOrderTemplate',
      right: 'canCreateOrders',
      title: 'orders.title.import_failed', imported: 'orders.message.imported',
      header: 'orders.text.import_table_header'
    },
    receipts: {
      importBtn: 'receiptImportBtn', templateBtn: 'receiptTemplateBtn',
      api: '/api/receipts/excel-import', download: 'portalDownloadReceiptTemplate',
      right: 'canCreateReceipts',
      title: 'receipts.title.import_failed', imported: 'receipts.message.imported',
      header: 'receipts.text.import_table_header'
    },
    transfers: {
      importBtn: 'transferImportBtn', templateBtn: 'transferTemplateBtn',
      api: '/api/transfers/excel-import', download: 'portalDownloadTransferTemplate',
      right: 'canCreateTransfers',
      title: 'transfers.title.import_failed', imported: 'transfers.message.imported',
      header: 'transfers.text.import_table_header'
    }
  };

  /**
   * Последний ответ загрузки — для проверок и для перерисовки окна при
   * смене языка: {type, answer}.
   */
  /** @type {Portal['docImport']} */
  const state = {last: null, type: null};
  PORTAL.docImport = state;

  /** Подпись таблицы файла в окне ошибок. */
  function tableName(type, table){
    if (table === 'header') return tr(DOCS[type].header);
    if (table === 'Goods') return tr('common.text.import_table_goods');
    if (table === 'transport') return tr('common.text.import_table_transport');
    return tr('common.text.import_table_file');
  }

  /* ================= Загрузка ================= */

  function pickFile(type){
    const id = 'docImportFile_' + type;
    let input = /** @type {HTMLInputElement | null} */ (byId(id));
    if (!input){
      const created = document.createElement('input');
      created.type = 'file';
      created.id = id;
      created.accept = '.xlsx';
      created.style.display = 'none';
      document.body.appendChild(created);
      created.onchange = () => {
        const file = created.files && created.files[0];
        // сброс — иначе повторный выбор того же файла не вызовет onchange
        created.value = '';
        if (file) send(type, file);
      };
      input = created;
    }
    input.click();
  }

  /**
   * @param {string} type
   * @param {File} file
   */
  async function send(type, file){
    const doc = DOCS[type];
    const btn = buttonById(doc.importBtn);
    if (btn && btn.dataset.busy) return;
    const label = btn ? btn.innerHTML : '';
    if (btn){
      btn.dataset.busy = '1';
      btn.disabled = true;
      btn.textContent = tr('common.message.importing');
    }
    try {
      const body = new FormData();
      body.append('file', file);
      /** @type {DocImportAnswer} */
      const answer = await PORTAL.fetchJson(doc.api, {method: 'POST', body: body});
      state.last = answer;
      state.type = type;
      if (answer.ok){
        say(tr(doc.imported, {no: answer.documentNo, count: answer.lineCount}));
        // комплекты поступления и перемещения разложены на комплектующие (V153)
        if (answer.expandedKits && answer.expandedKits.length){
          say(T('common.message.kits_expanded', {codes: answer.expandedKits.join(', ')}));
        }
        if (typeof PORTAL.reloadDocs === 'function') PORTAL.reloadDocs(type);
        // Черновик открывает редактор черновиков (draft.js) — тем же путём,
        // что и щелчок по черновику в списке: поля шапки видны сразу,
        // без переоткрытия документа (замечание от 03.11.2026).
        if (PORTAL.draft) await PORTAL.draft.open(type, answer.docId);
      } else {
        showIssues(type, answer.issues || []);
      }
    } catch (e){
      say(messageOf(e));
    } finally {
      if (btn){
        btn.innerHTML = label;
        btn.disabled = false;
        delete btn.dataset.busy;
      }
    }
  }

  /* ================= Окно ошибок ================= */

  function createModal(){
    if (byId('docImportOverlay')) return;
    const modal = document.createElement('div');
    modal.className = 'col-overlay';
    modal.id = 'docImportOverlay';
    modal.style.cssText = 'align-items:center;justify-content:center;';
    modal.onclick = e => { if (e.target === modal) close(); };
    modal.innerHTML = ''
      + '<div class="col-panel doc-import-panel">'
      + '  <h3 id="docImportTitle"></h3>'
      + '  <div class="hint" id="docImportHint"></div>'
      + '  <div class="doc-import-list"><table><thead><tr id="docImportHead"></tr></thead>'
      + '  <tbody id="docImportBody"></tbody></table></div>'
      + '  <div class="col-actions"><button class="btn primary" type="button" id="docImportClose"></button></div>'
      + '</div>';
    document.body.appendChild(modal);
    part('docImportClose').onclick = close;
    document.addEventListener('keydown', e => {
      if (e.key === 'Escape' && part('docImportOverlay').classList.contains('active')) close();
    });
  }

  /**
   * @param {string} type
   * @param {DocImportIssue[]} issues
   */
  function showIssues(type, issues){
    createModal();
    PORTAL.label(byId('docImportTitle'), DOCS[type].title);
    PORTAL.label(byId('docImportHint'), 'common.hint.import_failed');
    PORTAL.label(byId('docImportClose'), 'common.button.close');
    part('docImportHead').innerHTML = ''
      + '<th>' + esc(tr('common.column.import_table')) + '</th>'
      + '<th class="num">' + esc(tr('common.column.import_row')) + '</th>'
      + '<th>' + esc(tr('common.column.import_field')) + '</th>'
      + '<th>' + esc(tr('common.column.import_problem')) + '</th>';
    part('docImportBody').innerHTML = issues.map(i =>
        '<tr><td>' + esc(tableName(type, i.table)) + '</td>'
      + '<td class="num">' + (i.row ? esc(i.row) : '—') + '</td>'
      + '<td>' + esc(i.field || '—') + '</td>'
      + '<td>' + esc(i.message)
      + (i.value ? ' <span class="count">' + esc(i.value) + '</span>' : '') + '</td></tr>').join('');
    part('docImportOverlay').classList.add('active');
  }

  function close(){
    const o = byId('docImportOverlay');
    if (o) o.classList.remove('active');
  }

  /* ================= Установка ================= */

  /** Кнопки — тому, кто вправе создавать документ: так же, как «Создать новый». */
  function applyRight(user){
    Object.values(DOCS).forEach(doc => {
      const may = user ? user[doc.right] !== false : true;
      [doc.importBtn, doc.templateBtn].forEach(id => {
        const b = byId(id);
        if (b) b.style.display = may ? '' : 'none';
      });
    });
  }

  function install(){
    Object.entries(DOCS).forEach(([type, doc]) => {
      const upload = byId(doc.importBtn);
      if (upload && !upload.dataset.wired){
        upload.dataset.wired = '1';
        upload.onclick = () => pickFile(type);
      }
      const template = buttonById(doc.templateBtn);
      if (template && !template.dataset.wired){
        template.dataset.wired = '1';
        template.onclick = () => {
          if (typeof window[doc.download] === 'function') window[doc.download](template);
        };
      }
    });
    // права на кнопки загрузки — вместе с остальными кнопками (auth.js)
    PORTAL.applyImportRights = applyRight;
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
