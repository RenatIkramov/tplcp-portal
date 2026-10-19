#!/usr/bin/env python3
r"""
Обновляет запасной словарь подписей - static/js/i18n-fallback.js.

ЗАПУСКАТЬ НЕ ОБЯЗАТЕЛЬНО. Настоящие тексты страница забирает с сервера
синхронным запросом до первой отрисовки, поэтому подписи на экране
приходят из базы. Словарь в файле - страховка на два случая:

  1. портал не ответил на запрос текстов: вместо ключей вида
     sku.column.code человек увидит русские подписи;
  2. ключ есть в коде, а текста в базе ещё нет.

Второго случая быть не должно: ключи добавляются вместе с текстами
в миграции. Поэтому инструмент - гигиена, а не шаг сборки.

Откуда берутся тексты (по порядку предпочтения):

  --url      запрос к работающему порталу (по умолчанию localhost:8080).
             Тот же источник, что и psql - база, - только без psql,
             пароля и PATH.
  --from-json файл с готовым ответом {"ключ": "текст", ...}
  --psql     вызов psql, если портал не запущен, а база под рукой.
             psql ищется сам: --psql-path, переменная PSQL, PATH,
             затем обычные места установки PostgreSQL на Windows.

Подключение берётся из переменных среды libpq, как у любого другого
инструмента PostgreSQL: PGDATABASE, PGUSER, PGHOST, PGPORT. Пароль -
через %APPDATA%\postgresql\pgpass.conf (так правильнее: файл лежит
в профиле и не виден другим процессам) либо через PGPASSWORD.
Пароль инструменту не передаётся и в нём не хранится: psql читает его
сам, а мы лишь наследуем окружение.

Примеры:
    python tools/gen_i18n_fallback.py
    python tools/gen_i18n_fallback.py --url http://localhost:8080
    python tools/gen_i18n_fallback.py --from-json ru.json
    python tools/gen_i18n_fallback.py --psql --db tplcp --user postgres

Имена в коде - латиницей нарочно: консоль Windows обычно не в UTF-8,
и кириллица в именах превращает трейсбек в кашу ровно тогда, когда он
нужен.
"""
import argparse
import json
import pathlib
import re
import os
import shutil
import subprocess
import sys
import urllib.error
import urllib.request

# Консоль Windows и консоль IDE договариваются о кодировке по-разному,
# и по умолчанию русские сообщения приезжают кашей вида "������".
# Переключаем свой вывод на UTF-8: его понимают и PowerShell 7,
# и терминал IDE, и перенаправление в файл.
for stream in (sys.stdout, sys.stderr):
    try:
        stream.reconfigure(encoding='utf-8', errors='replace')
    except (AttributeError, ValueError):
        pass           # очень старый Python или подменённый поток

ROOT = pathlib.Path(__file__).resolve().parent.parent
STATIC = ROOT / 'src' / 'main' / 'resources' / 'static'
FALLBACK = STATIC / 'js' / 'i18n-fallback.js'

SQL = ("SELECT json_object_agg(k.text_key, t.value) "
       "FROM dbo.ui_text_key k "
       "JOIN dbo.ui_text t ON t.text_key = k.text_key "
       "JOIN dbo.dictionary_locale l ON l.locale_id = t.locale_id "
       "WHERE l.code = 'ru' AND k.scope <> 'server'")

# Сообщения сервера в запасном словаре не нужны: их собирает сервер
# (PortalMessages) и присылает уже фразой, экран ключа не видит.
# Фильтруем по корню ключа, а не только запросом: тексты могут прийти
# и от работающего портала (--url), и из файла (--from-json).
#
# Исключение - ключ с таким корнем, который зовёт сама статика. Такие
# есть: core.js подставляет error.status.not_found и error.status.conflict
# на ответы 404 и 409, а i18n.js - info.password.changed. Отфильтровать их
# значит показать человеку строку "error.status.not_found" вместо
# сообщения - ровно в тот момент, когда что-то и так не работает.
# Поэтому фильтр пропускает только то, на что из статики никто
# не ссылается.
SERVER_PREFIXES = ('error.', 'info.')


def say(message: str) -> None:
    """Печать, которая не падает на кириллице в консоли Windows."""
    try:
        print(message)
    except UnicodeEncodeError:
        print(message.encode('utf-8', 'replace').decode('ascii', 'replace'))


def from_portal(url: str) -> dict:
    """Тексты от работающего портала. Тот же источник - база."""
    address = url.rstrip('/') + '/api/i18n/texts/ru'
    try:
        with urllib.request.urlopen(address, timeout=10) as resp:
            return json.loads(resp.read().decode('utf-8'))
    except urllib.error.URLError as e:
        sys.exit(f'портал не ответил по адресу {address}: {e}\n'
                 'Запустите приложение, либо возьмите тексты иначе:\n'
                 '  --from-json <файл>   готовый ответ\n'
                 '  --psql               через psql, если он в PATH')


# Куда обычно ставится PostgreSQL на Windows. Нужно затем, чтобы
# инструмент работал, не требуя правки PATH: установщик PostgreSQL
# по умолчанию PATH не меняет, и psql не находится.
WINDOWS_DIRS = [
    r'C:\Program Files\PostgreSQL',
    r'C:\Program Files (x86)\PostgreSQL',
]


def find_psql(explicit: str = None) -> str:
    """Путь к psql: явный, из PSQL, из PATH, из обычных мест установки."""
    for candidate in (explicit, os.environ.get('PSQL')):
        if candidate:
            if pathlib.Path(candidate).is_file():
                return candidate
            sys.exit(f'psql не найден по указанному пути: {candidate}')

    found = shutil.which('psql')
    if found:
        return found

    # Версии перебираем от новой к старой: на машине их бывает несколько
    for base in WINDOWS_DIRS:
        root = pathlib.Path(base)
        if not root.is_dir():
            continue
        versions = sorted((d for d in root.iterdir() if d.is_dir()),
                          key=lambda d: d.name, reverse=True)
        for v in versions:
            exe = v / 'bin' / 'psql.exe'
            if exe.is_file():
                return str(exe)

    sys.exit('psql не найден: ни в PATH, ни в обычных местах установки.\n'
             'Варианты:\n'
             '  - запустите портал и вызовите инструмент без --psql\n'
             '  - укажите путь: --psql-path "C:\\Program Files\\PostgreSQL\\16\\bin\\psql.exe"\n'
             '  - или задайте переменную среды PSQL с этим путём')


def from_psql(args) -> dict:
    exe = find_psql(args.psql_path)
    say(f'psql: {exe}')

    # Подключение - переменными среды libpq, как у остальных
    # инструментов PostgreSQL. Явные ключи только там, где человек
    # задал их сам: иначе они перебили бы PGDATABASE и PGUSER.
    cmd = [exe, '-At', '-w', '-c', SQL]
    if args.db:
        cmd[1:1] = ['-d', args.db]
    if args.user:
        cmd[1:1] = ['-U', args.user]

    try:
        done = subprocess.run(cmd, capture_output=True, text=True, check=True)
    except subprocess.CalledProcessError as e:
        error = e.stderr.strip()
        hint = ''
        if 'no password supplied' in error or 'аутентификац' in error.lower() \
                or 'authentication failed' in error.lower():
            hint = ('\nПароль psql берёт сам: положите его в '
                    '%APPDATA%\\postgresql\\pgpass.conf строкой\n'
                    '  localhost:5432:*:postgres:ВАШ_ПАРОЛЬ\n'
                    'либо задайте переменную среды PGPASSWORD.')
        sys.exit(f'psql вернул ошибку:\n{error}{hint}')

    out = done.stdout.strip()
    if not out:
        sys.exit('база вернула пустой ответ - проверьте, та ли это база '
                 '(PGDATABASE / --db) и накатаны ли миграции')
    return json.loads(out)


def read_object(text: str, name: str):
    """Вырезает объект `const <name> = {...};` и возвращает его с границами."""
    try:
        start = text.index(f'const {name} = {{')
    except ValueError:
        sys.exit(f'в {FALLBACK.name} нет словаря {name} - файл не тот')
    brace = text.index('{', start)
    depth, i, in_string, escaped = 0, brace, False, False
    while i < len(text):
        c = text[i]
        if in_string:
            if escaped:
                escaped = False
            elif c == '\\':
                escaped = True
            elif c == '"':
                in_string = False
        elif c == '"':
            in_string = True
        elif c == '{':
            depth += 1
        elif c == '}':
            depth -= 1
            if depth == 0:
                return json.loads(text[brace:i + 1]), brace, i + 1
        i += 1
    sys.exit(f'не удалось разобрать словарь {name} в {FALLBACK.name}')


def keys_in_static() -> set:
    """Ключи, на которые ссылается статика."""
    found = set()
    pattern = re.compile(r"""['"]([a-z][a-z0-9_]*\.[a-z0-9_]+\.[a-z0-9_]+)['"]""")
    for f in list(STATIC.rglob('*.js')) + [STATIC / 'index.html']:
        # Сам запасной словарь пропускаем: в нём перечислены ВСЕ ключи,
        # и по нему любой ключ выглядел бы используемым - в том числе
        # тот, который из кода давно убрали.
        if f.name == FALLBACK.name:
            continue
        found |= set(pattern.findall(f.read_text(encoding='utf-8')))
    html = (STATIC / 'index.html').read_text(encoding='utf-8')
    found |= set(re.findall(r'data-i18n(?:-placeholder)?="([^"]+)"', html))
    return found


def main() -> int:
    p = argparse.ArgumentParser(description='Обновляет static/js/i18n-fallback.js')
    p.add_argument('--url', default='http://localhost:8080',
                   help='адрес работающего портала (по умолчанию localhost:8080)')
    p.add_argument('--from-json', help='файл с ответом {"ключ": "текст"}')
    p.add_argument('--psql', action='store_true', help='взять тексты через psql')
    p.add_argument('--psql-path', help='путь к psql.exe, если он не в PATH')
    # Пусто по умолчанию нарочно: тогда работают переменные среды
    # PGDATABASE и PGUSER, как у любого инструмента PostgreSQL.
    p.add_argument('--db', default=os.environ.get('PGDATABASE', ''),
                   help='база (по умолчанию из PGDATABASE)')
    p.add_argument('--user', default=os.environ.get('PGUSER', ''),
                   help='пользователь (по умолчанию из PGUSER)')
    p.add_argument('--prune', action='store_true',
                   help='убрать из словаря ключи, которых нет в базе '
                        '(после удаления ненужных текстов)')
    p.add_argument('--dry-run', action='store_true',
                   help='только показать, что изменилось бы')
    args = p.parse_args()

    if not FALLBACK.exists():
        sys.exit(f'нет файла {FALLBACK}')

    if args.from_json:
        texts = json.loads(pathlib.Path(args.from_json).read_text(encoding='utf-8'))
    elif args.psql:
        texts = from_psql(args)
    else:
        texts = from_portal(args.url)
    in_static = keys_in_static()
    texts = {k: v for k, v in texts.items()
             if not k.startswith(SERVER_PREFIXES) or k in in_static}
    say(f'текстов получено: {len(texts)}')

    content = FALLBACK.read_text(encoding='utf-8')
    ru, start, end = read_object(content, 'RU')
    was = len(ru)

    changed = []
    for key in list(ru):
        if key in texts and texts[key] != ru[key]:
            ru[key] = texts[key]
            changed.append(key)

    added = []
    for key in sorted(keys_in_static()):
        if key not in ru and key in texts:
            ru[key] = texts[key]
            added.append(key)

    missing = [k for k in ru if k not in texts]

    say(f'ключей в словаре: {len(ru)} (было {was})')
    say(f'обновлено текстов: {len(changed)} - добавлено ключей: {len(added)}')
    for key in changed[:10]:
        say(f'  обновлён  {key}')
    for key in added[:10]:
        say(f'  добавлен  {key}')
    if len(changed) + len(added) > 20:
        say(f'  ... и ещё {len(changed) + len(added) - 20}')
    removed = []
    if missing and args.prune:
        # Чистка после удаления ненужных текстов: ключа нет ни в базе,
        # ни в коде - значит и в запасном словаре ему делать нечего.
        # Ключи, оставшиеся в коде, не трогаем: подпись пропала бы
        # совсем, а это хуже устаревшего текста.
        in_code = keys_in_static()
        removed = [k for k in missing if k not in in_code]
        for key in removed:
            ru.pop(key, None)
        say(f'убрано ключей: {len(removed)}')
        missing = [k for k in missing if k in in_code]
    if missing:
        # Не удаляем: ключ мог остаться в коде, а текст - исчезнуть
        # из базы по ошибке. Молча терять подпись хуже, чем сказать.
        say(f'ВНИМАНИЕ: в базе нет русских текстов для {len(missing)} ключей '
            f'словаря, например: {", ".join(missing[:5])}')
        if not args.prune:
            say('  --prune уберёт те из них, которых нет и в коде')

    if not changed and not added and not removed:
        say('словарь уже совпадает с базой, файл не менялся')
        return 0
    if args.dry_run:
        say('--dry-run: файл не записан')
        return 0

    FALLBACK.write_text(content[:start] + json.dumps(ru, ensure_ascii=False)
                        + content[end:], encoding='utf-8')
    say(f'записано: {FALLBACK}')
    say('Проверьте страницу: подписи приходят с сервера, запасной словарь '
        'виден только когда сервер не ответил.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
