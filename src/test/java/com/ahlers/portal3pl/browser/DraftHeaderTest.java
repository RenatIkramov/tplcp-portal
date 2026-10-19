package com.ahlers.portal3pl.browser;

import com.ahlers.portal3pl.auth.PortalUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Шапка черновика заполняется с экрана и сохраняется — вся, целиком.
 *
 * <p>Проверка появилась после настоящей ошибки: карточка заказа
 * предлагала в поле «Тип заказа» НАИМЕНОВАНИЯ из демо-массива прототипа,
 * а портал отправлял выбранное наименование в колонку ключа
 * {@code order_type_id}. База отвечала ошибкой приведения типа, и
 * черновик заказа не сохранялся вовсе — достаточно было тронуть это поле.
 * То же самое ждало «Тип приходного документа» у поступления.
 *
 * <p>Прежние проверки этого не замечали, потому что заполняли ровно те
 * поля, которые были им нужны. Эта идёт по ВСЕМ полям шапки: список
 * берётся из {@code PORTAL.draftFields}, то есть из самого портала,
 * а не выписывается здесь. Поле, добавленное завтра, попадёт под
 * проверку само.
 *
 * <p>Что утверждается: после сохранения сервер не ответил ошибкой,
 * и каждое поле-ссылка вернулось с экрана тем же значением, которое
 * в него положили. Второе важнее первого: оно и отличает «сохранилось»
 * от «ушло и потерялось».
 */
class DraftHeaderTest extends BrowserTestBase {

    /** Тип документа → как его черновик создать, сохранить и удалить. */
    private static final Map<String, List<String>> ACTIONS = Map.of(
            "receipts", List.of("createNewReceiptDraft", "saveReceiptDraft", "deleteReceiptDraft",
                    "currentReceiptRef"),
            "orders", List.of("createNewOrderDraft", "saveOrderDraft", "deleteOrderDraft",
                    "currentOrderRef"),
            "transfers", List.of("createNewTransferDraft", "saveTransferDraft",
                    "deleteTransferDraft", "currentTransferRef"));

    private String type;

    @Test
    void receiptHeaderSaved() {
        checkHeaderOf("receipts");
    }

    @Test
    void orderHeaderSaved() {
        checkHeaderOf("orders");
    }

    @Test
    void transferHeaderSaved() {
        checkHeaderOf("transfers");
    }

    /**
     * Вкладка «Дополнительная информация» сохраняется.
     *
     * <p>Десять свободных полей не сохранялись и не читались вовсе:
     * человек заполнял вкладку, а она ничего не делала. Поля в базе были
     * с самого начала, а в списке сохраняемых их не было.
     */
    @Test
    void extraInfoSaved() {
        type = "orders";
        PortalUser user = signedInUser();
        localeOf(user, "ru");
        signInUiAsClient();
        createDraft();

        openExtraTab();
        page.evaluate("""
                () => {
                  for (let i = 1; i <= 3; i++){
                    const el = document.getElementById('of_customField' + i);
                    if (el) el.value = 'доп ' + i;
                  }
                }
                """);
        save();
        openExtraTab();

        Object stored = page.evaluate("""
                () => [1, 2, 3].map(i => {
                  const el = document.getElementById('of_customField' + i);
                  return el ? String(el.value || '') : '(поля нет)';
                })
                """);
        assertEquals(List.of("доп 1", "доп 2", "доп 3"), stored,
                "дополнительная информация не сохранилась: " + stored);
        assertNoJsErrors();
    }

    /**
     * Набранное не теряется при переходе на другую вкладку — и не уходит
     * на сервер раньше кнопки.
     *
     * <p>Раньше весь блок шапки имел onchange и сохранял черновик после
     * каждого изменения поля, с плашкой на каждый щелчок. Плашку убрали,
     * сохранение оставили кнопке — и появилась опасность: переключение
     * вкладки перерисовывает карточку из памяти, а не с сервера.
     * Проверяются обе половины: значение на экране осталось, а на сервере
     * его ещё нет.
     */
    @Test
    void unsavedValueSurvivesTabSwitchAndDoesNotReachServer() {
        type = "orders";
        PortalUser user = signedInUser();
        localeOf(user, "ru");
        signInUiAsClient();
        createDraft();

        page.evaluate("() => { document.getElementById('of_comment').value = 'черновая заметка'; }");
        openExtraTab();
        page.evaluate("() => fcSwitchDocTab('orders', 'Общая информация')");

        Object onScreen = page.evaluate(
                "() => String((document.getElementById('of_comment') || {}).value || '')");
        assertEquals("черновая заметка", onScreen,
                "набранное потерялось при переходе на другую вкладку");

        // В базе документа ещё нет вовсе: у карточки нет идентификатора.
        // Это и есть «до кнопки ничего не записано» — проверять нечего
        // по номеру, потому что номера тоже нет.
        Object id = page.evaluate("() => (currentOrderRef || {}).docId || null");
        assertNull(id, "документ появился в базе до нажатия кнопки «Сохранить»");

        // И на всякий случай: заметки нет ни в одном заказе клиента.
        assertEquals("0", singleValue("SELECT count(*)::text FROM dbo.orders"
                + " WHERE comment = 'черновая заметка'"),
                "комментарий ушёл на сервер до нажатия кнопки «Сохранить»");
        assertNoJsErrors();
    }

    private void openExtraTab() {
        page.evaluate("() => fcSwitchDocTab('orders', 'Дополнительная информация')");
        page.waitForSelector("#of_customField1");
    }

    private void checkHeaderOf(String docType) {
        type = docType;
        PortalUser user = signedInUser();
        localeOf(user, "ru");
        signInUiAsClient();

        createDraft();
        fillHeader();

        // Снимок делается ПОСЛЕ заполнения и паузы, а не во время:
        // выбор грузополучателя перечитывает адреса доставки запросом
        // и сам подставляет основной адрес. Снимок «что было на экране
        // перед сохранением» — честнее, чем «что мы туда положили»:
        // проверяется именно сохранение, а не наша расстановка значений.
        page.waitForTimeout(800);
        Object before = readHeader();
        assertFalse(((Map<?, ?>) before).isEmpty(),
                "ни одного поля шапки не нашлось на экране — проверять нечего");

        save();

        // Сервер ответил ошибкой — значит шапка не сохранилась, и дальше
        // сравнивать значения уже незачем: сообщение будет понятнее.
        assertNoJsErrors();

        Object lost = page.evaluate("""
                (before) => {
                  const out = [];
                  Object.keys(before).forEach(id => {
                    const el = document.getElementById(id);
                    const now = el ? String(el.value || '') : '(поля нет)';
                    if (now !== String(before[id])) out.push(id + ': было «' + before[id]
                        + '», стало «' + now + '»');
                  });
                  return out;
                }
                """, before);
        assertEquals(List.of(), lost, "поля шапки не сохранились: " + lost);

        // И отдельно: в поле-ссылке должен лежать ключ, а не человеческий
        // текст. Ровно это и было сломано — в списке стояли наименования.
        Object humanText = page.evaluate("""
                (docType) => {
                  const fields = PORTAL.draftFields[docType] || {};
                  return Object.keys(fields).filter(id => {
                    if (!/_id$/.test(fields[id])) return false;
                    const el = document.getElementById(id);
                    const v = el ? String(el.value || '') : '';
                    if (v === '') return false;
                    // Ключ — это число, uuid или код склада вроде WH-ALA.
                    // Наименование («Обычный», «Склад Алматы») под это
                    // не подходит, и ровно оно сюда и попадало.
                    const looksLikeKey = /^[0-9]+$/.test(v)
                        || /^[0-9a-fA-F-]{36}$/.test(v) || /^[A-Z0-9_-]+$/.test(v);
                    return !looksLikeKey;
                  });
                }
                """, docType);
        assertEquals(List.of(), humanText,
                "в поле-ссылке лежит человеческий текст вместо ключа: " + humanText);
    }

    /* ---------------- помощники ---------------- */

    /**
     * Открывает карточку нового документа. В базе его ещё НЕТ: запись
     * появляется по кнопке «Сохранить», её делает {@link #save()}.
     */
    private void createDraft() {
        page.evaluate("() => " + action(0) + "()");
        page.waitForFunction("() => !!" + action(3));
    }

    /**
     * Заполняет каждое поле шапки, какое можно заполнить с экрана,
     * и возвращает «идентификатор поля → что в него положили».
     *
     * <p>В выпадающем списке берётся ПОСЛЕДНЕЕ непустое значение, а не
     * первое: первым часто стоит «не выбрано», и проверка, выбравшая его,
     * ничего бы не проверила.
     */
    private void fillHeader() {
        page.evaluate("""
                (docType) => {
                  const fields = PORTAL.draftFields[docType] || {};
                  Object.keys(fields).forEach(id => {
                    const el = document.getElementById(id);
                    if (!el || el.disabled || el.readOnly) return;
                    if (el.tagName === 'SELECT'){
                      const real = Array.from(el.options).filter(o => o.value !== '');
                      if (!real.length) return;
                      el.value = real[real.length - 1].value;
                    } else if (el.type === 'date'){
                      el.value = '2026-12-01';
                    } else if (el.type === 'number'){
                      el.value = '7';
                    } else {
                      el.value = 'проверка шапки';
                    }
                    el.dispatchEvent(new Event('change', {bubbles: true}));
                  });
                }
                """, type);
    }

    /** Что сейчас стоит в полях шапки: «идентификатор поля → значение». */
    private Object readHeader() {
        return page.evaluate("""
                (docType) => {
                  const fields = PORTAL.draftFields[docType] || {};
                  const now = {};
                  Object.keys(fields).forEach(id => {
                    const el = document.getElementById(id);
                    if (!el || el.disabled || el.readOnly) return;
                    now[id] = String(el.value || '');
                  });
                  return now;
                }
                """, type);
    }

    /** Нажать «Сохранить» и дождаться, пока карточка перечитается. */
    private void save() {
        page.evaluate("() => " + action(1) + "()");
        // Сохранение — три запроса подряд (создание или правка шапки,
        // состав, чтение карточки). Ждём идентификатор: он появляется,
        // когда база создала запись, то есть сохранение состоялось.
        page.waitForFunction("() => !!(" + action(3) + " && " + action(3) + ".docId)");
        page.waitForTimeout(500);
    }

    private String action(int index) {
        return ACTIONS.get(type).get(index);
    }

    private PortalUser signedInUser() {
        ensurePassword(USER, USER_PASSWORD);
        return authService.authenticate(USER, USER_PASSWORD);
    }

    /** Черновик за собой убираем: проверка создаёт настоящий документ. */
    @AfterEach
    void removeDraft() {
        if (type == null || page == null) {
            return;
        }
        try {
            // Для несохранённого документа это просто закрывает карточку:
            // удалять в базе нечего.
            page.evaluate("() => " + action(2) + "()");
            page.waitForFunction("() => !" + action(3));
        } catch (RuntimeException e) {
            System.out.println("не удалось удалить черновик " + type + ": " + e.getMessage());
        }
    }
}
