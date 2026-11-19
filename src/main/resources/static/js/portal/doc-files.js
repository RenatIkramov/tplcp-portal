/* =====================================================================
   Вкладка «Прикреплённые файлы» документа (V145, Ф3)
   =====================================================================

   Файлы, которые клиент прикладывает к поступлению, заказу, перемещению.
   Файл ложится на портал и уходит в WMS: у черновика — вслед за
   документом, у отправленного — сразу. В таблице — состояние отправки,
   «Скачать», у черновика — ✕, у файла с ошибкой — «Отправить ещё раз».

   Проверяет файл сервер (размер, тип, содержимое); здесь — только
   подсказка с пределами и выбор нескольких файлов разом. Отказ
   показывается в самой вкладке.

   Файлы из WMS (печатные формы, фото) — поставка Ф4.

   Сервер: ClientFileController (/api/{вид}/{docId}/files).
   ===================================================================== */

(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  const {esc, dash, say, messageOf} = window.PORTAL;
  const T_ = (k, p) => (typeof T === 'function' ? T(k, p) : k);
  const api = PORTAL.api;

  const RIGHT = {receipts: 'canCreateReceipts', orders: 'canCreateOrders', transfers: 'canCreateTransfers'};
  const BADGE = {waiting: 'warn', pending: 'warn', sent: 'ok', failed: 'err'};

  /**
   * Открытая вкладка: где рисуем, какой документ, что в нём.
   * @type {{host: HTMLElement | null, type: string, docId: string, draft: boolean,
   *         files: any[], maxMb: number, types: string[], error: string, busy: boolean, ask: string | null}}
   */
  const view = {host: null, type: '', docId: '', draft: false, files: [], maxMb: 0, types: [],
                error: '', busy: false, ask: null};

  const base = () => '/api/' + view.type + '/' + encodeURIComponent(view.docId) + '/files';

  const mayAdd = () => !PORTAL.uiUser || PORTAL.uiUser[RIGHT[view.type]] !== false;

  const size = (/** @type {number} */ bytes) => {
    const n = Number(bytes) || 0;
    return n < 1024 ? n + ' B' : n < 1048576 ? (n / 1024).toFixed(0) + ' KB' : (n / 1048576).toFixed(1) + ' MB';
  };

  function take(/** @type {any} */ answer){
    view.files = (answer && answer.files) || [];
    view.maxMb = (answer && answer.maxMb) || 0;
    view.types = (answer && answer.types) || [];
  }

  async function load(){
    try {
      take(await api('GET', base()));
      view.error = '';
    } catch (e){
      view.error = messageOf(e);
    }
    render();
  }

  /* ======================= Разметка ======================= */

  function row(/** @type {any} */ f){
    const state = '<span class="badge ' + (BADGE[f.wmsState] || 'warn') + '"><span class="dot"></span>'
        + esc(T_('files.state.' + f.wmsState)) + '</span>'
        + (f.wmsState === 'failed' && f.lastError ? '<div class="hint">' + esc(f.lastError) + '</div>' : '');
    const actions = '<button type="button" class="btn ghost" data-file-download'
        + PORTAL.call('PORTAL.docFiles.download', f.fileId) + '>' + esc(T_('reports.button.download')) + '</button>'
        + (f.wmsState === 'failed'
            ? ' <button type="button" class="btn ghost" data-file-resend'
              + PORTAL.call('PORTAL.docFiles.resend', f.fileId) + '>' + esc(T_('files.button.resend')) + '</button>'
            : '')
        + (view.draft && f.wmsState === 'waiting' && mayAdd()
            ? ' <button type="button" class="btn ghost" data-file-remove title="' + esc(T_('common.button.delete')) + '"'
              + PORTAL.call('PORTAL.docFiles.askRemove', f.fileId) + '>✕</button>'
            : '');
    const asking = view.ask === f.fileId
        ? '<tr data-file-ask><td colspan="5" style="background:var(--bg);">'
          + esc(T_('files.confirm.remove', {name: f.fileName})) + ' '
          + '<button type="button" class="btn primary" id="docFileRemoveYes"' + PORTAL.call('PORTAL.docFiles.remove', f.fileId)
          + '>' + esc(T_('common.button.delete')) + '</button> '
          + '<button type="button" class="btn ghost"' + PORTAL.call('PORTAL.docFiles.askRemove', null) + '>'
          + esc(T_('common.button.cancel')) + '</button></td></tr>'
        : '';
    return '<tr data-file="' + esc(f.fileId) + '" data-state="' + esc(f.wmsState) + '">'
      + '<td style="white-space:normal;"><strong>' + esc(f.fileName) + '</strong></td>'
      + '<td class="num">' + esc(size(f.sizeBytes)) + '</td>'
      + '<td>' + esc(dash(f.uploadedByName)) + '<div class="hint">' + esc(PORTAL.dateTimeText(f.createdAt)) + '</div></td>'
      + '<td style="white-space:normal;">' + state + '</td>'
      + '<td style="text-align:right;white-space:nowrap;">' + actions + '</td></tr>' + asking;
  }

  function render(){
    const host = view.host;
    if (!host || !document.body.contains(host)) return;
    const accept = view.types.map(t => '.' + t).join(',');
    host.innerHTML = ''
      + (mayAdd()
          ? '<div style="display:flex;gap:12px;align-items:center;flex-wrap:wrap;margin-bottom:10px;">'
            + '<label class="btn primary" style="cursor:pointer;"' + (view.busy ? ' aria-disabled="true"' : '') + '>'
            + esc(T_(view.busy ? 'files.message.uploading' : 'files.button.add'))
            + '<input type="file" id="docFileInput" multiple accept="' + esc(accept) + '" style="display:none;"'
            + (view.busy ? ' disabled' : '') + PORTAL.callOnChange('PORTAL.docFiles.upload', PORTAL.SELF) + '></label>'
            + '<span class="hint">' + esc(T_('files.hint.limits', {max: view.maxMb, types: view.types.join(', ')}))
            + '</span></div>'
          : '')
      + (view.error ? '<div class="form-error" id="docFileError" role="alert" style="margin:0 0 10px;color:#c0392b;'
          + 'background:#fdecea;border:1px solid #f5c6cb;border-radius:6px;padding:8px 10px;">' + esc(view.error) + '</div>' : '')
      + (view.files.length
          ? '<div class="table-card"><table style="width:100%;"><thead><tr>'
            + '<th>' + esc(T_('files.column.name')) + '</th><th class="num">' + esc(T_('files.column.size')) + '</th>'
            + '<th>' + esc(T_('files.column.added')) + '</th><th>' + esc(T_('files.column.wms')) + '</th><th></th>'
            + '</tr></thead><tbody id="docFilesBody">' + view.files.map(row).join('') + '</tbody></table></div>'
          : '<div class="hint" id="docFilesEmpty">' + esc(T_('common.text.no_document_photos')) + '</div>');
  }

  /* ======================= Действия ======================= */

  PORTAL.docFiles = {
    /**
     * Нарисовать файлы документа в host.
     * @param {HTMLElement} host
     * @param {string} type receipts | orders | transfers
     * @param {{docId: string, statusCode: string}} doc
     */
    show(host, type, doc){
      const same = view.type === type && view.docId === doc.docId;
      view.host = host;
      view.type = type;
      view.docId = doc.docId;
      view.draft = doc.statusCode === PORTAL.STATUS.DRAFT;
      if (!same){
        view.files = [];
        view.error = '';
        view.ask = null;
      }
      host.innerHTML = '<div class="hint">' + esc(T_('stock.text.loading_data')) + '</div>';
      load();
    },

    async upload(/** @type {HTMLInputElement} */ input){
      const chosen = input && input.files ? Array.from(input.files) : [];
      if (!chosen.length || view.busy) return;
      const body = new FormData();
      chosen.forEach(f => body.append('files', f, f.name));
      view.busy = true;
      view.error = '';
      render();
      try {
        take(await PORTAL.fetchJson(base(), {method: 'POST', body: body}));
        say(T_('files.message.added', {count: chosen.length}));
      } catch (e){
        view.error = messageOf(e);
      }
      view.busy = false;
      render();
    },

    async download(fileId){
      let resp;
      try {
        resp = await fetch(base() + '/' + encodeURIComponent(fileId));
      } catch (e){ say(messageOf(e)); return; }
      if (!resp.ok){
        view.error = await PORTAL.responseMessage(resp);
        render();
        return;
      }
      const f = view.files.find(x => x.fileId === fileId);
      const url = URL.createObjectURL(await resp.blob());
      const a = document.createElement('a');
      a.href = url;
      a.download = f ? f.fileName : 'file';
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 10000);
    },

    askRemove(fileId){
      view.ask = fileId;
      render();
    },

    async remove(fileId){
      view.ask = null;
      try {
        take(await api('DELETE', base() + '/' + encodeURIComponent(fileId)));
        view.error = '';
      } catch (e){
        view.error = messageOf(e);
      }
      render();
    },

    async resend(fileId){
      try {
        take(await api('POST', base() + '/' + encodeURIComponent(fileId) + '/resend'));
        view.error = '';
      } catch (e){
        view.error = messageOf(e);
      }
      render();
    }
  };

  /** Смена языка: перерисовать открытое (i18n.js зовёт по имени). */
  window.renderDocFiles = function(){
    if (view.host && document.body.contains(view.host)) render();
  };
})();
