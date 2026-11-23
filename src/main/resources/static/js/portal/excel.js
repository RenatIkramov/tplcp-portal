
(function(){
  'use strict';

  const PORTAL = window.PORTAL;
  if (!PORTAL) return;

  // общие помощники — core.js (аудит A4)
  const {say, messageOf} = window.PORTAL;

  /**
   * Скачивание шаблона с откликом на нажатие.
   *
   * <p>Файл приходит целиком, а уже потом браузер показывает загрузку.
   * Пока сервер его собирает, кнопка прежде никак не отзывалась, и
   * человек жал ещё раз — и получал несколько одинаковых файлов. Теперь
   * кнопка сразу говорит «Готовлю шаблон…» и до прихода файла (или
   * сообщения об ошибке) не нажимается: повторные клики пропадают.
   */
  /**
   * @param {BusyButton} btn
   * @param {string} url
   * @param {string} nameKey
   */
  async function downloadTemplate(btn, url, nameKey){
    if (btn && btn.dataset.busy) return;
    const label = btn ? btn.innerHTML : '';
    if (btn){
      btn.dataset.busy = '1';
      btn.disabled = true;
      btn.innerHTML = T('common.button.preparing_template');
    }
    try {
      // нужен cookie сеанса и разбор отказа (403, если загрузка из Excel
      // отключена) — простой переход по ссылке дал бы пустой файл
      const name = await PORTAL.downloadFile(url, T(nameKey) + '.xlsx');
      say(T('common.message.downloading_file', {name: name}));
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

  window.portalDownloadSkuTemplate = btn =>
    downloadTemplate(btn, '/api/sku/excel-template', 'common.text.sku_template_file_name');
  window.portalDownloadPackagingTemplate = btn =>
    downloadTemplate(btn, '/api/packaging/excel-template', 'common.text.packaging_template_file_name');
  // Заказ из Excel (V96): тот же отклик на нажатие, что у справочников
  window.portalDownloadOrderTemplate = btn =>
    downloadTemplate(btn, '/api/orders/excel-template', 'orders.text.order_template_file_name');
  // Поступление из Excel (V100)
  window.portalDownloadReceiptTemplate = btn =>
    downloadTemplate(btn, '/api/receipts/excel-template', 'receipts.text.receipt_template_file_name');
  // Перемещение из Excel (V101)
  window.portalDownloadTransferTemplate = btn =>
    downloadTemplate(btn, '/api/transfers/excel-template', 'transfers.text.transfer_template_file_name');

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
      return {id: 'skuTemplateBtn', run: btn => window.portalDownloadSkuTemplate(btn)};
    }
    if (onclick.indexOf('Шаблон_Упаковка') >= 0){
      return {id: 'packagingTemplateBtn', run: btn => window.portalDownloadPackagingTemplate(btn)};
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
        // заглушка прототипа узнаётся по своему сообщению (data-args, аудит A3)
        const target = templateOf(btn.getAttribute('data-args') || '');
        if (!target) return;
        btn.removeAttribute('data-call');
        btn.removeAttribute('data-args');
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
