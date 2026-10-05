package com.ahlers.portal3pl.documents;

import com.ahlers.portal3pl.config.CurrentUser;
import com.ahlers.portal3pl.config.DepositorScope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    public DraftService(DepositorScope depositorScope, CurrentUser currentUser,
                        DraftRepository drafts, DocumentRepository documents, WmsGateway wms) {
        this.depositorScope = depositorScope;
        this.currentUser = currentUser;
        this.drafts = drafts;
        this.documents = documents;
        this.wms = wms;
    }

    @Transactional
    public Map<String, Object> create(DocumentType type, Map<String, Object> header) {
        depositorScope.apply();
        String userId = currentUser.requireUserId();
        String docId = drafts.createDraft(type, depositorScope.currentDepositorId(), userId);
        if (header != null && !header.isEmpty()) {
            drafts.updateHeader(type, docId, header, type.editableHeaderColumns(), userId, true);
        }
        return card(type, docId);
    }

    @Transactional
    public Map<String, Object> updateHeader(DocumentType type, String docId, Map<String, Object> header) {
        depositorScope.apply();
        String userId = currentUser.requireUserId();
        requireDraft(type, docId);
        drafts.updateHeader(type, docId, header, type.editableHeaderColumns(), userId, true);
        return card(type, docId);
    }

    /**
     * Транспортный раздел живёт по своим правилам (Р5): в черновике не правится,
     * потому что WMS ещё не знает о документе; разрешённые статусы задаются
     * флагом на статусе, а не зашиты в код.
     */
    @Transactional
    public Map<String, Object> updateTransport(DocumentType type, String docId, Map<String, Object> transport) {
        depositorScope.apply();
        String userId = currentUser.requireUserId();

        if (!currentUser.mayEditTransport()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Пользователю не разрешено редактировать транспортный раздел");
        }
        Map<String, Object> state = state(type, docId);
        if (!Boolean.TRUE.equals(state.get("allowsTransportEdit"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "В статусе «" + state.getOrDefault("statusCode", "?")
                            + "» транспортный раздел не редактируется");
        }

        drafts.updateHeader(type, docId, transport, DocumentType.transportColumns(), userId, false);
        // Транспорт в WMS — отдельные сущности, отправляются отдельным запросом (Р5)
        wms.sendTransport(type, docId);
        return card(type, docId);
    }

    @Transactional
    public void delete(DocumentType type, String docId) {
        depositorScope.apply();
        currentUser.requireUserId();
        requireDraft(type, docId);
        if (drafts.deleteDraft(type, docId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Черновик не найден");
        }
    }

    @Transactional
    public Map<String, Object> addLine(DocumentType type, String docId, Map<String, Object> line) {
        depositorScope.apply();
        currentUser.requireUserId();
        requireDraft(type, docId);
        requirePositiveQty(type, line);

        int lineNumber = drafts.nextLineNumber(type, docId);
        drafts.addLine(type, docId, lineNumber, line);
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
        if (drafts.updateLine(type, docId, lineNumber, line) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Строка не найдена");
        }
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

        List<String> problems = validateForConfirm(type, docId);
        if (!problems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Документ не готов к отправке: " + String.join("; ", problems));
        }

        String depositorId = depositorScope.currentDepositorId();
        try {
            wms.sendDocument(type, docId);
            drafts.setStatus(type, docId, "sent_to_wms", true);
            drafts.logSync(type, depositorId, "success", null);
        } catch (RuntimeException e) {
            // Откатывать в черновик нельзя: иначе «не отправлял» неотличимо
            // от «отправлял, не прошло» (Р5).
            drafts.setStatus(type, docId, "send_failed", false);
            drafts.logSync(type, depositorId, "failed", e.getMessage());
        }
        return card(type, docId);
    }

    /**
     * Что мешает подтвердить документ. Пустой список — можно отправлять.
     *
     * Метод вызывают и снаружи (экран спрашивает, активна ли кнопка), и изнутри.
     * Поэтому контекст поклажедателя выставляется здесь же: без него своя
     * же транзакция не увидит документ — RLS отработает честно и вернёт пусто.
     */
    @Transactional(readOnly = true)
    public List<String> validateForConfirm(DocumentType type, String docId) {
        depositorScope.apply();
        Map<String, Object> state = state(type, docId);
        List<String> problems = new ArrayList<>();

        List<String> missing = drafts.missingRequired(type, docId);
        if (!missing.isEmpty()) {
            problems.add("не заполнены поля: " + String.join(", ", missing));
        }
        if (number(state.get("lineCount")) == 0) {
            problems.add("нет ни одной строки");
        }
        if (number(state.get("badQtyCount")) > 0) {
            problems.add("есть строки с нулевым или пустым количеством");
        }
        if (number(state.get("unknownSkuCount")) > 0) {
            problems.add("есть позиции, неизвестные WMS (" + number(state.get("unknownSkuCount")) + ")");
        }
        return problems;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> card(DocumentType type, String docId) {
        depositorScope.apply();
        List<Map<String, Object>> header = documents.header(type, docId);
        if (header.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Документ не найден");
        }
        return Map.of(
                "header", header.get(0),
                "lines", documents.lines(type, docId),
                "lineDetails", documents.lineDetails(type, docId, null),
                "problems", validateForConfirm(type, docId));
    }

    private Map<String, Object> state(DocumentType type, String docId) {
        Map<String, Object> state = drafts.state(type, docId);
        if (state.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Документ не найден");
        }
        return state;
    }

    /** Редактировать можно только собственный черновик портала (Р5). */
    private void requireDraft(DocumentType type, String docId) {
        Map<String, Object> state = state(type, docId);
        boolean draft = "draft".equals(state.get("statusCode")) && "portal".equals(state.get("origin"));
        if (!draft) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Документ уже отправлен в WMS и из портала не редактируется");
        }
    }

    private void requirePositiveQty(DocumentType type, Map<String, Object> line) {
        Object qty = line.get(type.plannedQtyColumn());
        if (qty == null || Double.parseDouble(qty.toString()) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Количество должно быть больше нуля");
        }
    }

    private static long number(Object v) {
        return v == null ? 0 : Long.parseLong(v.toString());
    }
}
