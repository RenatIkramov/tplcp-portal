package com.ahlers.portal3pl.documents;

import com.ahlers.portal3pl.auth.PortalUser;
import com.ahlers.portal3pl.common.Fail;
import com.ahlers.portal3pl.config.DepositorScope;
import com.ahlers.portal3pl.lists.ListService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Документы: список, карточка, состав, детализация по партиям.
 * Каждый метод открывает транзакцию и выставляет контекст поклажедателя —
 * без этого база не отдаст ни строки.
 */
@Service
public class DocumentService {

    private final DepositorScope depositorScope;
    private final DocumentRepository repository;
    private final DraftService drafts;
    /** Отбор, порядок и страницы — общие для всех списков портала. */
    private final ListService lists;

    public DocumentService(DepositorScope depositorScope, DocumentRepository repository,
                           DraftService drafts, ListService lists) {
        this.depositorScope = depositorScope;
        this.repository = repository;
        this.drafts = drafts;
        this.lists = lists;
    }

    /**
     * Список документов: отбор, порядок и страница.
     *
     * <p>Делается это ровно так же, как в запасах и справочниках,
     * поэтому делается не здесь: {@link ListService}. Повторять тут
     * то же самое значило бы завести копию, которая разойдётся
     * с оригиналом на первой же правке и разойдётся молча.
     */
    public Map<String, Object> list(PortalUser user, DocumentType type,
                                    Map<String, String> params) {
        return lists.page(user, type, params);
    }

    /**
     * Списки для выпадающих списков шапки документа: типы заказов
     * и типы приходных документов.
     *
     * <p>Одним ответом, а не двумя запросами: экран открывает карточку
     * любого типа и ему нужны оба списка — заранее не известно, какой.
     *
     * <p>Контекст поклажедателя не выставляется: это общие справочники
     * портала, в них нет поклажедателя, и выставлять его было бы
     * обещанием изоляции, которой тут нет.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> dictionaries() {
        return Map.of(
                "orderTypes", repository.enumValues("enum_order_type"),
                "receiptDocTypes", repository.enumValues("enum_receipt_doc_type"));
    }

    /** Карточка документа: шапка + состав + детализация в одном ответе. */
    @Transactional(readOnly = true)
    public Map<String, Object> card(DocumentType type, String docId) {
        depositorScope.apply();
        List<Map<String, Object>> header = repository.header(type, docId);
        if (header.isEmpty()) {
            // Чужой документ выглядит так же, как несуществующий — это намеренно:
            // по коду ответа нельзя узнать, есть ли такой документ у другого клиента.
            throw Fail.notFound("error.document.not_found");
        }
        Map<String, Object> head = header.getFirst();
        return Map.of(
                "header", head,
                "lines", repository.lines(type, docId),
                "lineDetails", repository.lineDetails(type, docId, null),
                // Причины, по которым документ нельзя отправить.
                //
                // Их отдавал только ответ на запись (DraftService.card),
                // а эта карточка — чтение, и ею открывается документ
                // из списка. Из-за этого человек видел пустой баннер
                // и активную кнопку «Подтвердить» у черновика, который
                // отправить нельзя: отказ приходил уже после нажатия.
                //
                // Считаем только для черновика: у отправленного документа
                // причин не бывает, а два лишних запроса на каждое
                // открытие карточки — заметно.
                "problems", "draft".equals(head.get("statusCode"))
                        ? drafts.problemTexts(type, docId)
                        : List.of());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> lines(DocumentType type, String docId) {
        depositorScope.apply();
        return repository.lines(type, docId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> lineDetails(DocumentType type, String docId, Integer lineNumber) {
        depositorScope.apply();
        return repository.lineDetails(type, docId, lineNumber);
    }
}
