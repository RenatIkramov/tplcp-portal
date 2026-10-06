package com.ahlers.portal3pl.documents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Единственное место, через которое портал обращается к ArtWMS.
 * <p>
 * Контракт API пока не согласован, поэтому здесь заглушка: вызовы ничего
 * не делают и считаются успешными. Когда контракт появится, меняется только
 * этот класс — вся логика статусов, проверок и экранов остаётся как есть.
 * <p>
 * Отправка документа и отправка транспорта — разные вызовы: транспорт в WMS
 * хранится отдельными сущностями и привязывается к уже созданному документу
 * (решение Р5).
 */
@Component
public class WmsGateway {

    private static final Logger log = LoggerFactory.getLogger(WmsGateway.class);

    /** Передать документ в WMS. Исключение означает неуспех отправки. */
    public void sendDocument(DocumentType type, String docId) {
        // TODO этап 8: POST в ArtWMS, разбор ответа, сохранение doc_number.
        // Пока только запись в журнал — по ней видно, что портал дошёл до
        // отправки и с какими данными, а документ переводится в статус
        // «Отправлен в WMS» так же, как будет с настоящей интеграцией.
        log.info("Заглушка WMS: документ {} ({}) считается отправленным", docId, type.path());
    }

    /** Передать или обновить транспортную информацию по документу. */
    public void sendTransport(DocumentType type, String docId) {
        // TODO этап 8: отдельный запрос к сущностям транспорта ArtWMS
        log.info("Заглушка WMS: транспорт по документу {} ({}) считается переданным",
                docId, type.path());
    }
}
