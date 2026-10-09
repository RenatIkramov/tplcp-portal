#!/usr/bin/env python3
"""
Собирает статику обратно в один файл.

Два применения:

1. **Проверка распила.** Сразу после разрезания собранный файл обязан
   совпасть с прежним index.html побайтово. Совпали байты — значит
   в наборе файлов ровно тот же текст в том же порядке, и браузер
   выполнит его так же. Это проверка, которая либо проходит, либо
   указывает точное место расхождения, в отличие от «обошёл экраны,
   вроде работает».

2. **Один файл для показа.** Портал без сервера не работает, но
   собранный файл удобно открыть, чтобы посмотреть вёрстку, или
   отдать кому-то целиком.

Запуск:
    python3 one_file.py <папка static> <куда записать>
    python3 one_file.py <папка static> <куда записать> --cmp <с чем сверить>
"""
import pathlib
import re
import sys


def assemble(root: pathlib.Path) -> str:
    html = (root / 'index.html').read_text(encoding='utf-8')

    def script(m: re.Match) -> str:
        attrs, path = m.group(1), m.group(2)
        attrs = attrs.replace(f' src="{path}"', '')
        return f'<script{attrs}>' + (root / path).read_text(encoding='utf-8') + '</script>'

    def style(m: re.Match) -> str:
        return '<style>' + (root / m.group(1)).read_text(encoding='utf-8') + '</style>'

    html = re.sub(r'<script([^>]*src="([^"]+)"[^>]*)></script>', script, html)
    html = re.sub(r'<link rel="stylesheet" href="([^"]+)">', style, html)
    return html


def main() -> int:
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    root, out = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
    html = assemble(root)
    out.write_text(html, encoding='utf-8')
    print(f'собрано: {out} ({len(html.encode("utf-8"))} байт)')

    if '--cmp' in sys.argv:
        other = pathlib.Path(sys.argv[sys.argv.index('--cmp') + 1])
        same = other.read_bytes() == out.read_bytes()
        print('совпадает с', other, ':', 'ДА, байт в байт' if same else 'НЕТ')
        return 0 if same else 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
