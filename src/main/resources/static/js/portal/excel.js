
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  const say = m => { if (typeof notify === 'function') notify(m); };

  /**
   * Скачивание файла с сервера.
   *
   * Простой переход по ссылке не подходит: нужен тот же cookie сеанса
   * и разбор отказа (403, если загрузка из Excel отключена) — иначе
   * вместо объяснения человек получил бы пустой файл или страницу
   * с ошибкой вместо таблицы.
   */
  async function download(url, fallbackName){
    const resp = await fetch(url, {headers: {'Accept': '*/*'}});
    if (!resp.ok) throw new Error(await PORTAL.responseMessage(resp));

    // имя файла сервер присылает в заголовке, в том числе по-русски
    let name = fallbackName;
    const cd = resp.headers.get('Content-Disposition') || '';
    const utf8 = cd.match(/filename\*\s*=\s*UTF-8''([^;]+)/i);
    const plain = cd.match(/filename\s*=\s*"?([^";]+)"?/i);
    if (utf8) name = decodeURIComponent(utf8[1]);
    else if (plain) name = plain[1];

    const blob = await resp.blob();
    const href = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = href;
    a.download = name;
    document.body.appendChild(a);
    a.click();
    a.remove();
    // ссылку на blob надо отпустить, иначе файл висит в памяти страницы
    setTimeout(() => URL.revokeObjectURL(href), 2000);
    return name;
  }

  window.portalDownloadSkuTemplate = async function(){
    try {
      const name = await download('/api/sku/excel-template',
          T('common.text.sku_template_file_name') + '.xlsx');
      say(T('common.message.downloading_file', {name: name}));
    } catch (e){
      say(e.message);
    }
  };

  function install(){
    PORTAL.onLogin(() => {
      // кнопка в справочнике товаров: ищем по тексту-заглушке, которую
      // прототип поставил в onclick, — своего идентификатора у неё нет
      const host = document.getElementById('ref-sub-sku');
      if (!host) return;
      host.querySelectorAll('button').forEach(btn => {
        const onclick = btn.getAttribute('onclick') || '';
        if (onclick.indexOf('Шаблон_Номенклатура') >= 0){
          btn.removeAttribute('onclick');
          btn.id = btn.id || 'skuTemplateBtn';
          btn.onclick = () => portalDownloadSkuTemplate();
        }
      });
    });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
