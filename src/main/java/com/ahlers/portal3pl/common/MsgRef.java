package com.ahlers.portal3pl.common;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Подстановка, которая сама является текстом портала.
 *
 * <p>Нужна там, где в сообщение подставляется СЛОВО, а не значение.
 * «Товар используется в поступлениях (3), заказах (1)»: числа — данные,
 * а «поступлениях» и «заказах» — такой же текст портала, как сама фраза,
 * и по-английски они должны стать receipts и orders.
 *
 * <p>Передать их обычной строкой нельзя: в английском тексте остались бы
 * русские слова. Поэтому вместо строки передаётся ссылка на ключ, а
 * {@link PortalMessages} разворачивает её на языке получателя — в том
 * числе список таких ссылок, собирая его через запятую.
 */
public record MsgRef(String key, Map<String, Object> params) {

    public static MsgRef of(String key, Object... params) {
        if (params == null || params.length == 0) {
            return new MsgRef(key, Map.of());
        }
        if (params.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "Подстановки задаются парами «имя, значение», получено: " + params.length);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < params.length; i += 2) {
            out.put(String.valueOf(params[i]), params[i + 1]);
        }
        return new MsgRef(key, out);
    }
}
