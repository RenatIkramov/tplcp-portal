package com.ahlers.portal3pl.documents;

import com.ahlers.portal3pl.common.ActorText;
import com.ahlers.portal3pl.common.EnumCodes;
import com.ahlers.portal3pl.common.Fail;
import com.ahlers.portal3pl.common.MsgRef;
import com.ahlers.portal3pl.config.CurrentUser;
import com.ahlers.portal3pl.config.DepositorScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public DraftService(DepositorScope depositorScope, CurrentUser currentUser,
                        DraftRepository drafts, DocumentRepository documents, WmsGateway wms,
                        ActorText texts, LineQuality quality) {
        this.depositorScope = depositorScope;
        this.currentUser = currentUser;
        this.drafts = drafts;
        this.documents = documents;
        this.wms = wms;
        this.texts = texts;
        this.quality = quality;
    }

    @Transactional
    public Map<String, Object> create(DocumentType type, Map<String, Object> header) {
        depositorScope.apply();
        String userId = currentUser.requireUserId();
        String docId = drafts.createDraft(type, depositorScope.currentDepositorId(), userId);
        if (header != null && !header.isEmpty()) {
            drafts.updateHeader(type, docId, header, draftColumns(type), userId, true);
        }
        return card(type, docId);
    }

    @Transactional
    public Map<String, Object> updateHeader(DocumentType type, String docId, Map<String, Object> header) {
        depositorScope.apply();
        String userId = currentUser.requireUserId();
        requireDraft(type, docId);
        drafts.updateHeader(type, docId, header, draftColumns(type), userId, true);
        return card(type, docId);
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
        drafts.addLine(type, docId, lineNumber, quality.normalized(type, line));
        drafts.refreshLineCount(type, docId);
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
                ? quality.normalized(type, line) : line;
        if (drafts.updateLine(type, docId, lineNumber, values) == 0) {
            throw Fail.notFound("error.line.not_found");
        }
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
            checked.add(quality.normalized(type, line));
        }

        drafts.deleteAllLines(type, docId);
        int lineNumber = 1;
        for (Map<String, Object> line : checked) {
            drafts.addLine(type, docId, lineNumber++, line);
        }
        drafts.refreshLineCount(type, docId);
        return card(type, docId);
    }

    @Transactional
    public Map<String, Object> deleteLines(DocumentType type, String docId, List<Integer> lineNumbers) {
        depositorScope.apply();
        currentUser.requireUserId();
        requireDraft(type, docId);
        drafts.deleteLines(type, docId, lineNumbers);
        drafts.refreshLineCount(type, docId);
        return card(type, docId);
    }

    /**
     * Подтверждение: проверяем, отправляем в WMS, переводим статус.
     * Пока контракта API нет, отправка заглушена (Р5) — меняется ровно одно место.
     */
    @Transactional
    public Map<String, Object> confirm(DocumentType type, String docId) {
        depositorScope.apply();
        currentUser.requireUserId();
        requireDraft(type, docId);

        List<String> problems = problemTexts(type, docId);
        if (!problems.isEmpty()) {
            throw Fail.unprocessable("error.document.not_ready",
                    "problems", String.join("; ", problems));
        }

        String depositorId = depositorScope.currentDepositorId();
        try {
            wms.sendDocument(type, docId);
            drafts.setStatus(type, docId, DocumentStatus.SENT_TO_WMS, true);
            drafts.logSync(type, depositorId, EnumCodes.SyncStatus.SUCCESS, null);
        } catch (RuntimeException e) {
            // Откатывать в черновик нельзя: иначе «не отправлял» неотличимо
            // от «отправлял, не прошло» (Р5).
            drafts.setStatus(type, docId, DocumentStatus.SENDING_FAILED, false);
            drafts.logSync(type, depositorId, EnumCodes.SyncStatus.FAILED, e.getMessage());
            return card(type, docId);
        }
        // Транспорт, заполненный в черновике, уходит вслед за документом (V84).
        // Пустой не отправляется: WMS незачем заводить пустую сущность.
        if (drafts.hasTransport(type, docId)) {
            deliverTransport(type, docId);
        }
        return card(type, docId);
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

    /** Редактировать можно только собственный черновик портала (Р5). */
    private void requireDraft(DocumentType type, String docId) {
        Map<String, Object> state = state(type, docId);
        boolean draft = DocumentStatus.DRAFT.equals(state.get("statusCode")) && EnumCodes.Origin.PORTAL.equals(state.get("origin"));
        if (!draft) {
            throw Fail.conflict("error.document.sent_to_wms");
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
