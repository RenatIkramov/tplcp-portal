package com.ahlers.portal3pl.documents;

import com.ahlers.portal3pl.common.ActorText;
import com.ahlers.portal3pl.common.EnumCodes;
import com.ahlers.portal3pl.common.Fail;
import com.ahlers.portal3pl.common.MsgRef;
import com.ahlers.portal3pl.config.CurrentUser;
import com.ahlers.portal3pl.config.DepositorScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Запись документов. Все правила решения Р5 собраны здесь:
 * что можно менять, когда, кем и что должно быть заполнено перед отправкой.
 */
@Service
public class DraftService {

    private final DepositorScope depositorScope;
    private final CurrentUser currentUser;
    private final DraftRepository drafts;
    private final DocumentRepository documents;
    private final WmsGateway wms;
    private final ActorText texts;
    private final LineQuality quality;
    private final WmsOutbox outbox;
    private final TransactionTemplate tx;

    public DraftService(DepositorScope depositorScope, CurrentUser currentUser,
                        DraftRepository drafts, DocumentRepository documents, WmsGateway wms,
                        ActorText texts, LineQuality quality, WmsOutbox outbox,
                        PlatformTransactionManager txManager) {
        this.depositorScope = depositorScope;
        this.currentUser = currentUser;
        this.drafts = drafts;
        this.documents = documents;
        this.wms = wms;
        this.texts = texts;
        this.quality = quality;
        this.outbox = outbox;
        this.tx = new TransactionTemplate(txManager);
    }

    @Transactional
    public Map<String, Object> create(DocumentType type, Map<String, Object> header) {
        depositorScope.apply();
        String userId = currentUser.requireUserId();
        currentUser.requireMayCreate(type);   // галочка «может создавать…» (аудит S3)
        Map<String, Object> values = header == null ? new java.util.HashMap<>() : new java.util.HashMap<>(header);
        requireErpNumber(type, null, values, true);
        singleWarehouse(type, values);
        String docId = drafts.createDraft(type, depositorScope.currentDepositorId(), userId);
        try {
            drafts.updateHeader(type, docId, values, draftColumns(type), userId, true);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw Fail.conflict("error.document.erp_taken", "erp", values.get("erp_number"), "no", "—");
        }
        return card(type, docId);
    }

    @Transactional
    public Map<String, Object> updateHeader(DocumentType type, String docId, Map<String, Object> header) {
        depositorScope.apply();
        String userId = currentUser.requireUserId();
        requireDraft(type, docId);
        requireErpNumber(type, docId, header, false);
        try {
            drafts.updateHeader(type, docId, header, draftColumns(type), userId, true);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw Fail.conflict("error.document.erp_taken", "erp", header.get("erp_number"), "no", "—");
        }
        return card(type, docId);
    }

    /**
     * Номер документа в ERP клиента — обязательный и уникальный
     * в пределах поклажедателя и типа документа (решение заказчика
     * от 03.11.2026, V98). Без него тот же файл Excel, загруженный
     * дважды, давал два одинаковых заказа.
     *
     * <p>Уникальность держит индекс базы; проверка здесь — чтобы человек
     * получил номер документа, с которым совпал, а не ошибку базы.
     *
     * @param creating при создании номер обязан быть; при правке —
     *                 только если шапка его присылает (пустым его не стереть)
     */
    private void requireErpNumber(DocumentType type, String docId, Map<String, Object> header,
                                  boolean creating) {
        if (header == null) {
            return;
        }
        boolean present = header.containsKey("erp_number");
        if (!creating && !present) {
            return;
        }
        Object raw = header.get("erp_number");
        String erp = raw == null ? "" : String.valueOf(raw).trim();
        if (erp.isEmpty()) {
            throw Fail.badRequest("error.document.erp_required");
        }
        String taken = drafts.documentWithErpNumber(type, depositorScope.currentDepositorId(), erp, docId);
        if (taken != null) {
            throw Fail.conflict("error.document.erp_taken", "erp", erp, "no", taken);
        }
    }

    /**
     * Склад у поклажедателя один — он и подставляется, если человек
     * склад не выбрал (03.11.2026). Выбирать из одного значения незачем,
     * а пустой склад остановил бы документ на «Подтвердить».
     * У перемещения складов два, и с одним складом оно не имеет смысла —
     * его это правило не касается.
     */
    private void singleWarehouse(DocumentType type, Map<String, Object> header) {
        if (type == DocumentType.TRANSFERS) {
            return;
        }
        Object w = header.get("warehouse_id");
        if (w != null && !String.valueOf(w).isBlank()) {
            return;
        }
        String only = drafts.onlyWarehouse();
        if (only != null) {
            header.put("warehouse_id", only);
        }
    }

    /**
     * Что можно записать в шапку черновика.
     *
     * <p>С V84 в черновике заполняется и транспорт — тем же сохранением,
     * что шапка: WMS о документе ещё не знает, хранит его портал. Право
     * «может редактировать транспорт» действует и здесь: без него
     * транспортные поля просто не принимаются — экран их такому
     * пользователю и не открывает.
     */
    private java.util.Set<String> draftColumns(DocumentType type) {
        java.util.Set<String> columns = new java.util.LinkedHashSet<>(type.editableHeaderColumns());
        if (currentUser.mayEditTransport()) {
            columns.addAll(DocumentType.transportColumns());
        }
        // Доп. поля, закрытые поклажедателю, не принимаются: на экране их
        // нет, а запрос в обход экрана не должен их записать (03.11.2026).
        columns.removeAll(drafts.closedCustomFields(type));
        return columns;
    }

    /**
     * Транспорт после отправки документа (Р5): разрешённые статусы задаются
     * флагом на статусе, а не зашиты в код. В черновике транспорт пишется
     * вместе с шапкой ({@link #draftColumns}) и уходит в WMS при
     * подтверждении.
     */
    @Transactional
    public Map<String, Object> updateTransport(DocumentType type, String docId, Map<String, Object> transport) {
        depositorScope.apply();
        String userId = currentUser.requireUserId();

        if (!currentUser.mayEditTransport()) {
            throw Fail.forbidden("error.transport.not_allowed");
        }
        Map<String, Object> state = state(type, docId);
        if (!Boolean.TRUE.equals(state.get("allowsTransportEdit"))) {
            throw Fail.conflict("error.transport.status_locked",
                    "status", state.getOrDefault("statusCode", "?"));
        }

        drafts.updateHeader(type, docId, transport, DocumentType.transportColumns(), userId, false);
        // Транспорт в WMS — отдельные сущности, отправляются отдельным запросом (Р5).
        // Неудача не откатывает сохранённое: данные портала остаются,
        // а состояние «не передан» видно на вкладке и повторяется кнопкой.
        deliverTransport(type, docId);
        return card(type, docId);
    }

    /** Повторить отправку транспорта, который не дошёл до WMS. */
    @Transactional
    public Map<String, Object> resendTransport(DocumentType type, String docId) {
        depositorScope.apply();
        currentUser.requireUserId();
        if (!currentUser.mayEditTransport()) {
            throw Fail.forbidden("error.transport.not_allowed");
        }
        Map<String, Object> state = state(type, docId);
        if (!EnumCodes.Transport.NOT_SENT.equals(state.get("transportState"))) {
            throw Fail.conflict("error.transport.nothing_to_resend");
        }
        deliverTransport(type, docId);
        return card(type, docId);
    }

    /**
     * Передать транспорт в WMS и записать, чем кончилось.
     *
     * <p>Документ при неудаче остаётся отправленным: откатывать заказ
     * в черновик из-за водителя нельзя (Р5). Как именно транспорт
     * доберётся до WMS — вторым запросом или вместе с документом через
     * шину, — решает {@link WmsGateway}; вопрос записан в «Решение Р5»
     * как вопрос к контракту.
     */
    private void deliverTransport(DocumentType type, String docId) {
        String depositorId = depositorScope.currentDepositorId();
        try {
            wms.sendTransport(type, docId);
            drafts.setTransportState(type, docId, EnumCodes.Transport.SENT, null);
        } catch (RuntimeException e) {
            drafts.setTransportState(type, docId, EnumCodes.Transport.NOT_SENT, e.getMessage());
            drafts.logSync(type, depositorId, EnumCodes.SyncStatus.FAILED, "transport: " + e.getMessage());
        }
    }

    /**
     * Удалить черновик вместе с его строками.
     *
     * <p>Строки удаляются первыми, и это не предосторожность: внешний ключ
     * {@code fk_*_details_doc_id} объявлен без каскада, поэтому удаление
     * шапки у документа, в котором есть хоть одна строка, падало ошибкой
     * целостности. То есть кнопка «Удалить черновик» работала только
     * на пустом документе — а пустых в работе не бывает.
     *
     * <p>Каскад на уровне базы здесь намеренно не ставится: удалять
     * документ целиком вправе только этот метод, он один проверяет, что
     * документ — наш черновик (решение Р5). Каскад разрешил бы удаление
     * строк любому, кто удалит шапку в обход проверки.
     */
    @Transactional
    public void delete(DocumentType type, String docId) {
        depositorScope.apply();
        currentUser.requireUserId();
        requireDraft(type, docId);
        drafts.deleteAllLines(type, docId);
        if (drafts.deleteDraft(type, docId) == 0) {
            throw Fail.notFound("error.draft.not_found");
        }
    }

    @Transactional
    public Map<String, Object> addLine(DocumentType type, String docId, Map<String, Object> line) {
        depositorScope.apply();
        currentUser.requireUserId();
        requireDraft(type, docId);
        requirePositiveQty(type, line);

        int lineNumber = drafts.nextLineNumber(type, docId);
        drafts.addLine(type, docId, lineNumber, quality.normalized(line));
        drafts.refreshLineCount(type, docId);
        drafts.touch(type, docId);
        return card(type, docId);
    }

    @Transactional
    public Map<String, Object> updateLine(DocumentType type, String docId, int lineNumber,
                                          Map<String, Object> line) {
        depositorScope.apply();
        currentUser.requireUserId();
        requireDraft(type, docId);
        if (line.containsKey(type.plannedQtyColumn())) {
            requirePositiveQty(type, line);
        }
        Map<String, Object> values = line.containsKey("quality")
                ? quality.normalized(line) : line;
        if (drafts.updateLine(type, docId, lineNumber, values) == 0) {
            throw Fail.notFound("error.line.not_found");
        }
        drafts.touch(type, docId);
        return card(type, docId);
    }

    /**
     * Заменяет состав документа целиком.
     * <p>
     * Экран редактирования черновика ведёт состав у себя: там работают
     * объединение дублей, пересчёт по упаковке, подстановка цены. Присылать
     * результат целиком надёжнее, чем угадывать по одной строке, что именно
     * изменилось, и порядок строк при этом сохраняется ровно таким,
     * каким его видит пользователь.
     */
    @Transactional
    public Map<String, Object> replaceLines(DocumentType type, String docId, List<Map<String, Object>> lines) {
        depositorScope.apply();
        currentUser.requireUserId();
        requireDraft(type, docId);

        List<Map<String, Object>> checked = new ArrayList<>();
        for (Map<String, Object> line : lines) {
            requirePositiveQty(type, line);
            checked.add(quality.normalized(line));
        }

        drafts.deleteAllLines(type, docId);
        int lineNumber = 1;
        for (Map<String, Object> line : checked) {
            drafts.addLine(type, docId, lineNumber++, line);
        }
        drafts.refreshLineCount(type, docId);
        drafts.touch(type, docId);
        return card(type, docId);
    }

    @Transactional
    public Map<String, Object> deleteLines(DocumentType type, String docId, List<Integer> lineNumbers) {
        depositorScope.apply();
        currentUser.requireUserId();
        requireDraft(type, docId);
        drafts.deleteLines(type, docId, lineNumbers);
        drafts.refreshLineCount(type, docId);
        drafts.touch(type, docId);
        return card(type, docId);
    }

    /**
     * Подтверждение (Р5, очередь — аудит A1, V127).
     *
     * <p>Без {@code @Transactional} намеренно: транзакций две, и WMS
     * вызывается между ними, когда соединение с базой уже свободно.
     * <ol>
     *   <li>Транзакция: документ заблокирован и сверен с версией экрана,
     *       проверен, получает статус «Отправляется», задание — в очередь.
     *       Второе одновременное «Подтвердить» ждёт блокировку и получает
     *       отказ: документ уже не черновик.</li>
     *   <li>Первая попытка отправки — сразу ({@link WmsOutbox#deliver}).
     *       WMS ответил — «Отправлен в WMS»; нет — «Отправляется», дальше
     *       повторяет расписание.</li>
     * </ol>
     */
    public Map<String, Object> confirm(DocumentType type, String docId) {
        Long job = tx.execute(s -> {
            depositorScope.apply();
            currentUser.requireUserId();
            requireDraft(type, docId);
            List<String> problems = problemTexts(type, docId);
            if (!problems.isEmpty()) {
                throw Fail.unprocessable("error.document.not_ready",
                        "problems", String.join("; ", problems));
            }
            return enqueue(type, docId);
        });
        outbox.deliver(job);
        return tx.execute(s -> card(type, docId));
    }

    /**
     * «Отправить ещё раз» у документа с ошибкой отправки (V127): то же,
     * что подтверждение, только без проверок черновика — состав уже ушёл
     * в очередь однажды и с тех пор не менялся.
     */
    public Map<String, Object> resend(DocumentType type, String docId) {
        Long job = tx.execute(s -> {
            depositorScope.apply();
            currentUser.requireUserId();
            if (drafts.lockVersion(type, docId) == null) {
                throw Fail.notFound("error.document.not_found");
            }
            Map<String, Object> state = state(type, docId);
            if (!DocumentStatus.SENDING_FAILED.equals(state.get("statusCode"))
                    || !EnumCodes.Origin.PORTAL.equals(state.get("origin"))) {
                throw Fail.conflict("error.document.not_failed");
            }
            return enqueue(type, docId);
        });
        outbox.deliver(job);
        return tx.execute(s -> card(type, docId));
    }

    /** Статус «Отправляется» и задание в очередь — в транзакции вызывающего. */
    private long enqueue(DocumentType type, String docId) {
        drafts.setStatus(type, docId, DocumentStatus.SENDING, false);
        // Транспорт, заполненный в черновике, уходит вслед за документом (V84).
        // Пустой не отправляется: WMS незачем заводить пустую сущность.
        return outbox.enqueue(type, docId, depositorScope.currentDepositorId(),
                drafts.hasTransport(type, docId));
    }

    /**
     * Что мешает подтвердить документ. Пустой список — можно отправлять.
     *
     * <p>Метод вызывают и снаружи (экран спрашивает, активна ли кнопка),
     * и изнутри. Поэтому контекст поклажедателя выставляется здесь же:
     * без него своя же транзакция не увидит документ — RLS отработает
     * честно и вернёт пусто.
     *
     * <p>Возвращаются ключи с подстановками, а не готовые фразы: причина —
     * это ответ «чего не хватает», и он один для всех языков. Во фразу его
     * превращает {@link #problemTexts}, когда известно, кому отвечаем.
     */
    @Transactional(readOnly = true)
    public List<MsgRef> validateForConfirm(DocumentType type, String docId) {
        depositorScope.apply();
        Map<String, Object> state = state(type, docId);
        List<MsgRef> problems = new ArrayList<>();

        List<String> missing = drafts.missingRequired(type, docId,
                currentUser.requireUserId(), texts.locale());
        if (!missing.isEmpty()) {
            problems.add(MsgRef.of("error.ready.fields_missing",
                    "fields", String.join(", ", missing)));
        }
        if (number(state.get("lineCount")) == 0) {
            problems.add(MsgRef.of("error.ready.no_lines"));
        }
        if (number(state.get("badQtyCount")) > 0) {
            problems.add(MsgRef.of("error.ready.bad_quantity"));
        }
        if (number(state.get("unknownSkuCount")) > 0) {
            problems.add(MsgRef.of("error.ready.unknown_sku",
                    "count", number(state.get("unknownSkuCount"))));
        }
        // Перемещение «со склада на тот же склад» смысла не имеет. Это
        // причина, а не отказ при вводе: человек заполняет шапку в любом
        // порядке, и запрещать сохранение на полпути нельзя — он только
        // что выбрал получателя и ещё не дошёл до отправителя.
        if (Boolean.TRUE.equals(state.get("sameWarehouse"))) {
            problems.add(MsgRef.of("error.ready.same_warehouse"));
        }
        return problems;
    }

    /** Те же причины фразами на языке того, кто смотрит документ. */
    @Transactional(readOnly = true)
    public List<String> problemTexts(DocumentType type, String docId) {
        List<String> out = new ArrayList<>();
        for (MsgRef problem : validateForConfirm(type, docId)) {
            out.add(texts.text(problem.key(), problem.params()));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> card(DocumentType type, String docId) {
        depositorScope.apply();
        List<Map<String, Object>> header = documents.header(type, docId);
        if (header.isEmpty()) {
            throw Fail.notFound("error.document.not_found");
        }
        return Map.of(
                "version", documents.version(type, docId),
                "header", header.getFirst(),
                "lines", documents.lines(type, docId),
                "lineDetails", documents.lineDetails(type, docId, null),
                "problems", problemTexts(type, docId));
    }

    private Map<String, Object> state(DocumentType type, String docId) {
        Map<String, Object> state = drafts.state(type, docId);
        if (state.isEmpty()) {
            throw Fail.notFound("error.document.not_found");
        }
        return state;
    }

    /**
     * Редактировать можно только собственный черновик портала (Р5), и
     * только тот, который экран видел (A9): строка шапки блокируется до
     * конца транзакции, версия сверяется с присланной экраном
     * ({@link EditVersion}). Второе «Подтвердить» по той же причине ждёт
     * первое и получает отказ, а не отправляет документ ещё раз.
     */
    private void requireDraft(DocumentType type, String docId) {
        String current = drafts.lockVersion(type, docId);
        if (current == null) {
            throw Fail.notFound("error.document.not_found");
        }
        Map<String, Object> state = state(type, docId);
        boolean draft = DocumentStatus.DRAFT.equals(state.get("statusCode")) && EnumCodes.Origin.PORTAL.equals(state.get("origin"));
        if (!draft) {
            throw Fail.conflict("error.document.sent_to_wms");
        }
        String expected = EditVersion.expected();
        if (expected != null && !expected.equals(current)) {
            throw Fail.conflict("error.document.changed_by_other");
        }
    }

    private void requirePositiveQty(DocumentType type, Map<String, Object> line) {
        Object qty = line.get(type.plannedQtyColumn());
        if (qty == null || Double.parseDouble(qty.toString()) <= 0) {
            throw Fail.badRequest("error.quantity.must_be_positive");
        }
    }

    private static long number(Object v) {
        return v == null ? 0 : Long.parseLong(v.toString());
    }
}
