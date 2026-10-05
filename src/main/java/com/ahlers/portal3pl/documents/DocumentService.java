package com.ahlers.portal3pl.documents;

import com.ahlers.portal3pl.config.DepositorScope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
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

    public DocumentService(DepositorScope depositorScope, DocumentRepository repository) {
        this.depositorScope = depositorScope;
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(DocumentType type, String search, String status,
                                          String warehouseId, LocalDate dateFrom, LocalDate dateTo) {
        depositorScope.apply();
        return repository.list(type, search, status, warehouseId, dateFrom, dateTo);
    }

    /** Карточка документа: шапка + состав + детализация в одном ответе. */
    @Transactional(readOnly = true)
    public Map<String, Object> card(DocumentType type, String docId) {
        depositorScope.apply();
        List<Map<String, Object>> header = repository.header(type, docId);
        if (header.isEmpty()) {
            // Чужой документ выглядит так же, как несуществующий — это намеренно:
            // по коду ответа нельзя узнать, есть ли такой документ у другого клиента.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Документ не найден");
        }
        return Map.of(
                "header", header.get(0),
                "lines", repository.lines(type, docId),
                "lineDetails", repository.lineDetails(type, docId, null));
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
