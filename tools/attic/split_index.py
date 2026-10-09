#!/usr/bin/env python3
"""
Распил index.html на обычные исходники. Запускается ОДИН раз.

Что делает: вырезает содержимое каждого <style> и <script> в отдельный
файл, а на его место в разметке ставит ссылку. Порядок ссылок — тот же,
в котором блоки стояли в файле, поэтому браузер выполняет их в том же
порядке и в той же общей глобальной области.

Почему обычные скрипты, а не ES-модули: код прототипа держит состояние
в лексических глобальных переменных (const/let на верхнем уровне:
tableColumnState, colPickerDraft и прочие). Обычные скрипты делят одну
глобальную лексическую область, модули — нет, и 635 обращений вместе
с 310 onclick= в разметке сломались бы разом.

Проверка: tools/one_file.py собирает всё обратно; результат обязан
совпасть с исходным index.html побайтово.
"""
import pathlib
import re
import sys

SRC = pathlib.Path('/home/claude/tplcp-portal/src/main/resources/static/index.html')
OUT = pathlib.Path('/home/claude/split/static')

# Имена по порядку следования блоков в файле. Порядок задан сборкой
# (patch_demo.py): запасной словарь текстов, логотип входа, код
# прототипа, затем наши блоки в порядке склейки.
NAMES = [
    'css/portal.css',               # <style> прототипа
    'js/i18n-fallback.js',          # T() и запасной русский словарь
    'js/login-logo.js',             # перекраска логотипа на экране входа
    'js/prototype.js',              # собственный код демо
    'js/portal/core.js',            # PORTAL: запросы, ошибки, перезагрузка
    'js/portal/docs.js',            # поступления и заказы
    'js/portal/catalog.js',         # списки товаров, тарифов, счетов
    'js/portal/card.js',            # карточки документов
    'js/portal/sku.js',             # список товаров
    'js/portal/draft.js',           # черновики документов
    'js/portal/auth.js',            # вход и смена пароля
    'js/portal/admin.js',           # пользователи
    'js/portal/access.js',          # доступы менеджеров
    'js/portal/sku-edit.js',        # карточка товара
    'js/portal/partners.js',        # поставщики и грузополучатели
    'js/portal/portal-dir.js',      # справочники уровня портала
    'js/portal/columns.js',         # настройка колонок
    'js/portal/excel.js',           # шаблон Excel
    'js/portal/import.js',          # окно проверки загрузки
    'js/portal/units.js',           # единицы измерения
    'js/portal/group-filter.js',    # фильтры по группе и типу товара
    'js/portal/import-log.js',      # журнал загрузок
    'js/portal/dep-visibility.js',  # видимость полей поклажедателя
    'js/portal/billing.js',         # биллинг
    'js/portal/i18n.js',            # язык пользователя и тексты интерфейса
]

BLOCK = re.compile(r'<(style|script)(?![^>]*src=)([^>]*)>([\s\S]*?)</\1>')


def main() -> int:
    html = SRC.read_text(encoding='utf-8')
    blocks = list(BLOCK.finditer(html))
    if len(blocks) != len(NAMES):
        print(f'блоков в файле {len(blocks)}, имён задано {len(NAMES)} — '
              'список имён разошёлся с файлом, резать нельзя', file=sys.stderr)
        return 1

    parts, pos = [], 0
    for name, m in zip(NAMES, blocks):
        tag, attrs, body = m.group(1), m.group(2), m.group(3)
        expect = 'style' if name.endswith('.css') else 'script'
        if tag != expect:
            print(f'блок {name}: ожидался <{expect}>, а стоит <{tag}>', file=sys.stderr)
            return 1

        target = OUT / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(body, encoding='utf-8')

        parts.append(html[pos:m.start()])
        parts.append(f'<link rel="stylesheet" href="{name}">' if tag == 'style'
                     else f'<script{attrs} src="{name}"></script>')
        pos = m.end()
    parts.append(html[pos:])

    (OUT / 'index.html').write_text(''.join(parts), encoding='utf-8')
    print(f'вырезано файлов: {len(blocks)}')
    print(f'index.html: было строк {html.count(chr(10)) + 1}, '
          f'стало {"".join(parts).count(chr(10)) + 1}')
    for name in NAMES:
        p = OUT / name
        print(f'  {name:34s} {p.read_text(encoding="utf-8").count(chr(10)):6d} строк')
    return 0


if __name__ == '__main__':
    sys.exit(main())
