"""把 flow-bsl.json、flow.json、flow-sr.json 写进 index.html 里对应的 JSON 块。

用 edit.html（或直接改文件）改完 JSON 后运行：python docs/render-scale/inline-flow.py
只替换 <script type="application/json" id="flow-…"> 和 </script> 之间的内容，页面其他部分不动。
"""
import json
import pathlib
import re
import sys

HERE = pathlib.Path(__file__).resolve().parent
PAGE = HERE / "index.html"
BLOCKS = {
    "flow-bsl": "flow-bsl.json", "flow-kappa": "flow.json", "flow-sr": "flow-sr.json",
    "flow-bsl-en": "flow-bsl-en.json", "flow-kappa-en": "flow-en.json", "flow-sr-en": "flow-sr-en.json",
}


def main():
    # 控制台编码不一定能显示中文（比如 cp932），显示不了的字符转义，不要让输出报错。
    for stream in (sys.stdout, sys.stderr):
        stream.reconfigure(errors="backslashreplace")
    page = PAGE.read_text(encoding="utf-8")
    for block, name in BLOCKS.items():
        text = (HERE / name).read_text(encoding="utf-8").rstrip("\n")
        json.loads(text)  # 不是合法 JSON 就在这里报错，不写坏页面
        if "</script" in text.lower():
            sys.exit(name + " 里有 \"</script\"，内联后会截断页面")
        pattern = re.compile(r'(<script type="application/json" id="' + block + r'">\n).*?(\n</script>)', re.S)
        page, n = pattern.subn(lambda m: m.group(1) + text + m.group(2), page)
        if n != 1:
            sys.exit("index.html 里找不到 id=\"" + block + "\" 的 JSON 块")
    PAGE.write_text(page, encoding="utf-8", newline="\n")
    print("已把 " + "、".join(BLOCKS.values()) + " 写进 index.html")


if __name__ == "__main__":
    main()
