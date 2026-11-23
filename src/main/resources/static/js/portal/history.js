/*
  «История» — кто, когда и что менял (журнал действий, V116–V118).

  Одна таблица на все карточки: документ, товар, контрагент, поклажедатель,
  склад, организация оператора, пользователь. Прежде жила в doc-card.js;
  вынесена, когда история понадобилась остальным карточкам (V118).
  Подписи частей записи («строка 3», «доступ к складам», «поле «…»») и
  вид значений — общие и для «Журнала действий» (audit-log.js).

  Слова в значениях (статусы, склады, да/нет) уже на языке читающего —
  их делает сервер; здесь только даты по-человечески.
*/
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  // общие помощники — core.js (аудит A4)

  const {esc} = window.PORTAL;

  /** Дата и отметка времени — по-человечески; пусто — «—»; остальное как пришло. */
  function value(v){
    if (v === null || v === undefined || v === '') return '—';
    const s = String(v);
    if (/^\d{4}-\d{2}-\d{2}$/.test(s)) return window.fromISODate(s);
    if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(s)) return PORTAL.dateTimeText(s);
    return s;
  }

  /** Подпись колонки настроек клиента: col.<таблица>.<колонка>, если такая есть. */
  function columnLabel(ref){
    const key = 'col.' + ref;
    const text = T(key);
    return text !== key ? text : ref;
  }

  /**
   * Какая часть записи менялась: строка документа, место доставки,
   * права и доступы пользователя, настройки клиента, язык перевода,
   * параметр и отчёт клиента, сопоставление с WMS.
   * @param {string | null | undefined} part
   */
  function partText(part){
    if (!part) return '';
    const i = part.indexOf(':');
    const kind = i < 0 ? part : part.slice(0, i);
    const rest = i < 0 ? '' : part.slice(i + 1);
    switch (kind){
      case 'line': return T('audit.part.line', {n: rest});
      case 'delivery_point': return T('audit.part.delivery_point', {v: rest});
      case 'column': return T('audit.part.column', {v: columnLabel(rest)});
      case 'label': return T('audit.part.label', {v: columnLabel(rest)});
      case 'locale': return T('audit.part.locale', {v: rest});
      case 'settings': case 'depositors': case 'warehouses': case 'sections':
        return T('audit.part.' + kind);
      // каталог отчётов и WMS (V148): «param:<ключ>[:locale:<код>]», «report:<код>», «wms[:<код WMS>]»
      case 'param': {
        const at = rest.indexOf(':locale:');
        return at < 0 ? T('audit.part.param', {v: rest})
          : T('audit.part.param', {v: rest.slice(0, at)}) + ' · ' + T('audit.part.locale', {v: rest.slice(at + 8)});
      }
      case 'report': return T('audit.part.report', {v: rest});
      // состав комплекта (V151): «kit:<код комплектующей>»
      case 'kit': return T('audit.part.kit', {v: rest});
      case 'wms': return rest ? T('audit.part.wms_code', {v: rest}) : T('audit.part.wms');
      default: return part;
    }
  }

  /**
   * Заголовок события: «Документ создан», «Изменена строка 3»,
   * «Создание · доступ к складам»…
   * @param {{action: string, part: string | null}} ev
   * @param {string} createdKey подпись создания самой записи
   */
  function title(ev, createdKey){
    const line = /^line:(\d+)$/.exec(ev.part || '');
    if (line){
      const key = ev.action === 'create' ? 'audit.action.line_added'
          : ev.action === 'delete' ? 'audit.action.line_deleted' : 'audit.action.line_changed';
      return T(key, {n: line[1]});
    }
    const part = partText(ev.part);
    if (ev.action === 'create' && !part) return T(createdKey);
    if (ev.action === 'status') return T('audit.action.status');
    const what = ev.action === 'create' ? T('audit.action.create')
        : ev.action === 'delete' ? T('audit.action.delete') : T('audit.action.changed');
    return part ? what + ' · ' + part : what;
  }

  /**
   * Таблица истории: события новыми сверху, под каждым — изменённые поля.
   * @param {Array<{eventId: number, at: string, actorName: string, action: string, part: string | null,
   *   field: string | null, fieldKey: string | null, oldText: string | null, newText: string | null}>} rows
   * @param {string} [createdKey]
   */
  function html(rows, createdKey){
    const created = createdKey || 'audit.action.record_created';
    if (!rows.length) return '<div class="hint">' + esc(T('audit.text.no_history')) + '</div>';
    /** @type {Array<{head: any, fields: any[]}>} */
    const events = [];
    rows.forEach(r => {
      const last = events[events.length - 1];
      if (last && last.head.eventId === r.eventId){
        if (r.field) last.fields.push(r);
      } else {
        events.push({head: r, fields: r.field ? [r] : []});
      }
    });
    const th = key => '<th>' + esc(T(key)) + '</th>';
    return '<div class="table-card"><table class="history-table"><thead><tr>'
        + th('audit.column.when') + th('audit.column.who') + th('audit.column.field')
        + th('audit.column.was') + th('audit.column.became') + '</tr></thead><tbody>'
        + events.map(ev => {
          const h = ev.head;
          return '<tr class="history-event"><td style="white-space:nowrap;">'
              + esc(PORTAL.dateTimeText(h.at)) + '</td><td>' + esc(h.actorName) + '</td>'
              + '<td colspan="3"><strong>' + esc(title(h, created)) + '</strong></td></tr>'
              + ev.fields.map(f => '<tr><td></td><td></td><td>' + esc(T(f.fieldKey || ''))
                  + '</td><td>' + esc(value(f.oldText)) + '</td><td>'
                  + esc(value(f.newText)) + '</td></tr>').join('');
        }).join('') + '</tbody></table></div>';
  }

  /**
   * Прочитать и нарисовать историю в host. Читается при каждом показе:
   * история пополняется, пока карточка открыта.
   * @param {HTMLElement} host
   * @param {string} url
   * @param {{createdKey?: string, stillWanted?: () => boolean}} [opts]
   *   stillWanted — ответ, пришедший после ухода с вкладки или карточки,
   *   не рисуется
   */
  async function show(host, url, opts){
    const o = opts || {};
    host.innerHTML = '<div class="hint">' + esc(T('stock.text.loading_data')) + '</div>';
    let rows;
    try {
      rows = await PORTAL.fetchJson(url);
    } catch (e){
      if (!o.stillWanted || o.stillWanted()) {
        host.innerHTML = '<div class="hint">' + esc(e instanceof Error ? e.message : String(e)) + '</div>';
      }
      return;
    }
    if (o.stillWanted && !o.stillWanted()) return;
    host.innerHTML = html(rows || [], o.createdKey);
  }

  /** Адрес истории карточки справочника или пользователя (V118). */
  const url = (objectType, id) => '/api/history/' + objectType + '/' + encodeURIComponent(id);

  PORTAL.history = {value, partText, title, html, show, url};
})();
