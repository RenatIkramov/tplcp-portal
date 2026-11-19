package com.ahlers.portal3pl.documents;

/**
 * Коды статусов документов (V87): один общий список для поступлений,
 * заказов и перемещений.
 *
 * <p>Код — то, чем портал обменивается с WMS и что пишет в фильтры;
 * имя статуса живёт в базе и переводится там же ({@code enum_name}).
 * Здесь только те коды, от которых зависит поведение портала, —
 * остальные статусы ставит WMS, и портал их лишь показывает.
 */
public final class DocumentStatus {

    /** Черновик: документ принадлежит порталу, его можно править. */
    public static final String DRAFT = "01";
    /** Передан в WMS, склад ещё не подтвердил получение. */
    public static final String SENT_TO_WMS = "02";
    /** Передать не удалось за 15 минут — у документа кнопка «Отправить ещё раз» (V127). */
    public static final String SENDING_FAILED = "03";
    /** Задание в очереди: WMS ещё не ответил, портал повторяет сам (V127). */
    public static final String SENDING = "04";

    private DocumentStatus() {
    }
}
