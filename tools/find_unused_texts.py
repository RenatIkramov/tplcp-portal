#!/usr/bin/env python3
r"""
Ищет ключи текстов, на которые никто не ссылается.

ЗАЧЕМ. Ключи накапливаются: экран переделали, ключ остался. В экране
переводов он выглядит действующей подписью, его переводят на следующий
язык — и перевод уходит в никуда. Инструмент показывает такие ключи,
чтобы их удалить миграцией.

ЧТО СЧИТАЕТСЯ УПОТРЕБЛЕНИЕМ:

  * прямое упоминание ключа в вёрстке, коде или миграции:
    data-i18n="...", T('...'), PORTAL.t('...'), 'col.sku.code' в SQL;
  * ссылка из каталога колонок: table_column.text_key и group_key
    (их читает сервер, в коде этих строк нет);
  * ключ, который собирается из частей во время работы. Такие семейства
    перечислены в DYNAMIC: имя ключа в коде целиком не встречается
    ('col.' + таблица + '.' + колонка), и поиском его не найти.

ОТКУДА БЕРУТСЯ КЛЮЧИ. Из того же файла, что и у gen_i18n_fallback.py:
  --from-json ru.json     словарь {"ключ": "текст", ...}
  --keys-file keys.txt    по ключу в строке
Либо из psql, если он под рукой:
  --psql                  SELECT text_key FROM ui_text_key

Печатает список и ничего не меняет. Удаление — отдельной миграцией,
руками: решение «ключ больше не нужен» инструмент принять не может.
"""
import argparse
import json
import os
import pathlib
import re
import subprocess
import sys

for stream in (sys.stdout, sys.stderr):
    try:
        stream.reconfigure(encoding='utf-8', errors='replace')
    except (AttributeError, ValueError):
        pass

ROOT = pathlib.Path(__file__).resolve().parent.parent

# Где ищем упоминания. Порядок не важен, важно не забыть ни одно место.
SCAN_DIRS = [
    ROOT / 'src' / 'main' / 'resources' / 'static',
    ROOT / 'src' / 'main' / 'java',
    ROOT / 'src' / 'test' / 'java',
]
SCAN_SUFFIXES = {'.html', '.js', '.css', '.java'}

# Миграции НЕ сканируем намеренно. В них ключ заводится — там он есть
# всегда, и по ним любой ключ выглядел бы употреблённым. Ссылки из базы
# берём не текстом миграции, а каталогом колонок (--psql).

# Запасной словарь — производный файл: он СОБИРАЕТСЯ из базы, и ключ,
# который встречается только в нём, никем не используется.
SKIP_FILES = {'i18n-fallback.js'}

# Семейства ключей, которые собираются из частей во время работы.
# Поиском по коду их не найти, поэтому считаем употреблёнными.
DYNAMIC = [
    # columns.js и i18n.js: 'col.' + таблица + '.' + колонка
    re.compile(r'^col\.'),
    # карточка документа: 'col.' уже покрыт, но доп. поля шапки
    # собираются так же — оставлено для наглядности
    re.compile(r'^col\.[a-zA-Z]+\.customField\d+$'),
    # сообщения об отказе справочников: у справочника свой корень
    # ('error.supplier'), повод один и тот же — kind.error('not_found').
    # Состав этих ключей сторожит ServerMessagesTest, здесь они нужны
    # только чтобы не попасть в список неиспользуемых.
    re.compile(r'^error\.(supplier|consignee|order_type|receipt_doc_type)\.'),
]


def say(message):
    try:
        print(message)
    except UnicodeEncodeError:
        print(message.encode('utf-8', 'replace').decode('ascii', 'replace'))


def keys_from_json(path):
    data = json.loads(pathlib.Path(path).read_text(encoding='utf-8'))
    return sorted(data.keys() if isinstance(data, dict) else data)


def keys_from_file(path):
    lines = pathlib.Path(path).read_text(encoding='utf-8').splitlines()
    return sorted(k.strip() for k in lines if k.strip())


def keys_from_psql(psql, database):
    sql = 'SELECT text_key FROM dbo.ui_text_key'
    out = subprocess.run([psql, '-tA', '-c', sql] + (['-d', database] if database else []),
                         capture_output=True, text=True, encoding='utf-8')
    if out.returncode != 0:
        raise SystemExit('psql: ' + (out.stderr or '').strip())
    return sorted(k.strip() for k in out.stdout.splitlines() if k.strip())


def catalogue_keys(psql, database):
    """Ключи, на которые ссылается каталог колонок."""
    sql = ('SELECT text_key FROM dbo.table_column '
           'UNION SELECT group_key FROM dbo.table_column WHERE group_key IS NOT NULL')
    out = subprocess.run([psql, '-tA', '-c', sql] + (['-d', database] if database else []),
                         capture_output=True, text=True, encoding='utf-8')
    if out.returncode != 0:
        return set()
    return {k.strip() for k in out.stdout.splitlines() if k.strip()}


def read_sources():
    """Всё содержимое сканируемых файлов одной строкой на файл."""
    out = []
    for folder in SCAN_DIRS:
        if not folder.exists():
            continue
        for path in folder.rglob('*'):
            if not path.is_file() or path.suffix not in SCAN_SUFFIXES:
                continue
            if path.name in SKIP_FILES:
                continue
            try:
                out.append((path, path.read_text(encoding='utf-8')))
            except (UnicodeDecodeError, OSError):
                continue
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--from-json', help='словарь текстов {"ключ": "текст"}')
    parser.add_argument('--keys-file', help='файл со списком ключей')
    parser.add_argument('--catalogue-file',
                        help='ключи, на которые ссылается каталог колонок '
                             '(text_key и group_key); в коде их нет')
    parser.add_argument('--psql', nargs='?', const='psql', help='взять ключи через psql')
    parser.add_argument('--db', help='имя базы для psql')
    parser.add_argument('--show-used', action='store_true', help='печатать и употреблённые')
    args = parser.parse_args()

    if args.from_json:
        keys = keys_from_json(args.from_json)
        catalogue = set(keys_from_file(args.catalogue_file)) if args.catalogue_file else set()
    elif args.keys_file:
        keys = keys_from_file(args.keys_file)
        catalogue = set(keys_from_file(args.catalogue_file)) if args.catalogue_file else set()
    elif args.psql:
        keys = keys_from_psql(args.psql, args.db)
        catalogue = catalogue_keys(args.psql, args.db)
    else:
        raise SystemExit('Укажите --from-json, --keys-file или --psql')

    sources = read_sources()
    say('Ключей: %d, файлов просмотрено: %d' % (len(keys), len(sources)))

    # Один проход по файлам вместо «ключ × файл»: ключей больше тысячи,
    # а файлов сотни — иначе это минуты вместо секунды.
    seen = set()
    # Ключ — минимум два сегмента через точку: about.url, sku.text.barcode.
    pattern = re.compile(r'[a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z0-9_]+)+')
    for path, text in sources:
        seen.update(pattern.findall(text))

    unused = []
    for key in keys:
        if key in seen:
            continue
        if key in catalogue:
            continue
        if any(rx.match(key) for rx in DYNAMIC):
            continue
        unused.append(key)

    if not unused:
        say('Ключей без употребления не нашлось.')
        return

    say('')
    say('НЕ ИСПОЛЬЗУЮТСЯ: %d' % len(unused))
    by_scope = {}
    for key in unused:
        by_scope.setdefault(key.split('.')[0], []).append(key)
    for scope in sorted(by_scope):
        say('')
        say('  %s (%d)' % (scope, len(by_scope[scope])))
        for key in by_scope[scope]:
            say('    ' + key)

    say('')
    say('Проверьте список глазами: ключ мог собираться из частей в коде,')
    say('который инструмент не разобрал. Удаление — миграцией.')


if __name__ == '__main__':
    main()
