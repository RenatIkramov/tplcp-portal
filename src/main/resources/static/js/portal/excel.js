
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

  /**
   * Скачивание шаблона с откликом на нажатие.
   *
   * <p>Файл приходит целиком, а уже потом браузер показывает загрузку.
   * Пока сервер его собирает, кнопка прежде никак не отзывалась, и
   * человек жал ещё раз — и получал несколько одинаковых файлов. Теперь
   * кнопка сразу говорит «Готовлю шаблон…» и до прихода файла (или
   * сообщения об ошибке) не нажимается: повторные клики пропадают.
   */
  async function downloadTemplate(btn, url, nameKey){
    if (btn && btn.dataset.busy) return;
    const label = btn ? btn.innerHTML : null;
    if (btn){
      btn.dataset.busy = '1';
      btn.disabled = true;
      btn.innerHTML = T('common.button.preparing_template');
    }
    try {
      const name = await download(url, T(nameKey) + '.xlsx');
      say(T('common.message.downloading_file', {name: name}));
    } catch (e){
      say(e.message);
    } finally {
      if (btn){
        btn.innerHTML = label;
        btn.disabled = false;
        delete btn.dataset.busy;
      }
    }
  }

  window.portalDownloadSkuTemplate = btn =>
    downloadTemplate(btn, '/api/sku/excel-template', 'common.text.sku_template_file_name');
  window.portalDownloadPackagingTemplate = btn =>
    downloadTemplate(btn, '/api/packaging/excel-template', 'common.text.packaging_template_file_name');

  /**
   * Кнопки «Скачать шаблон Excel» в товарах и упаковках.
   *
   * Своих идентификаторов у них нет: ищем по тексту-заглушке, которую
   * прототип поставил в onclick. Кнопка упаковок до V81 так и оставалась
   * заглушкой — нажатие показывало «Скачивается: Шаблон_Упаковка.xlsx»,
   * и ничего не скачивалось.
   *
   * Какую загрузку обслуживает кнопка-заглушка — по тексту в её onclick.
   * Строки здесь — образцы поиска, а не надписи: на экран они не идут.
   */
  function templateOf(onclick){
    if (onclick.indexOf('Шаблон_Номенклатура') >= 0){
      return {id: 'skuTemplateBtn', run: btn => portalDownloadSkuTemplate(btn)};
    }
    if (onclick.indexOf('Шаблон_Упаковка') >= 0){
      return {id: 'packagingTemplateBtn', run: btn => portalDownloadPackagingTemplate(btn)};
    }
    return null;
  }

  /**
   * Подключение — сразу при загрузке страницы, а не по входу.
   *
   * <p>Прежде кнопки подключались из `onLogin`, а подписчики `onLogin`
   * запускаются только после выбора поклажедателя. Администратор,
   * которому доступны несколько клиентов, до выбора видел заглушку
   * прототипа: «Скачивается: Шаблон_Упаковка.xlsx» — и ничего.
   * Кнопки лежат в разметке страницы и от входа не зависят; без
   * выбранного клиента сервер сам ответит, что его надо выбрать.
   */
  function install(){
    ['ref-sub-sku', 'ref-sub-packaging'].forEach(hostId => {
      const host = document.getElementById(hostId);
      if (!host) return;
      host.querySelectorAll('button').forEach(btn => {
        const target = templateOf(btn.getAttribute('onclick') || '');
        if (!target) return;
        btn.removeAttribute('onclick');
        btn.id = btn.id || target.id;
        btn.onclick = () => target.run(btn);
      });
    });
  }

  if (document.readyState === 'loading'){
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
