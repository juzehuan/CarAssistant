# -*- coding: utf-8 -*-
"""生成 R8/ProGuard 混淆字典 app/dictionary.txt。

字典要求：
- 每行一个标识符，字符集限 [A-Za-z0-9_]
- 不能是 Java 关键字或字面量（true/false/null）
- 不以数字开头（避免部分工具解析异常）
- # 开头为注释，空行忽略

这里刻意使用视觉上极易混淆的字符（o/O/0、i/I/l/1、s/S/5、z/Z/2 等），
让反编译出来的类名/方法名难以阅读与区分。
"""
import itertools
import random
import io

random.seed(20261023)

KEYWORDS = set("""abstract assert boolean break byte case catch char class const
continue default do double else enum extends final finally float for goto if
implements import instanceof int interface long native new package private
protected public return short static strictfp super switch synchronized this
throw throws transient try void volatile while true false null""".split())

# 视觉高度相似的字符组
HEAD = "oO0iIl1sS5zZ2b6g9qQcCduvVwWxXyY"
TAIL = "oO0iIl1sS5zZ2b6g9qQ"

names = set()
# 2~4 位组合，长度递增，保证够用（R8 需要的标识符数量远小于此）
for length in (2, 3, 4):
    tries = 0
    while len([n for n in names if len(n) == length]) < 400 and tries < 20000:
        tries += 1
        first = random.choice(HEAD)
        if first.isdigit():
            continue
        rest = "".join(random.choice(TAIL) for _ in range(length - 1))
        n = first + rest
        if n.lower() in KEYWORDS or n in KEYWORDS:
            continue
        names.add(n)

ordered = sorted(names, key=lambda s: (len(s), s))
lines = [
    "# R8/ProGuard obfuscation dictionary",
    "# 由 tools/gen-obfuscation-dictionary.py 生成，请勿手工编辑。",
    "# 使用视觉易混淆字符，增加反编译阅读难度。",
    "# 共 %d 个标识符。" % len(ordered),
    "",
]
lines.extend(ordered)

out = r"C:\Users\Administrator\Desktop\code\CarAssistant\app\dictionary.txt"
with io.open(out, "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(lines) + "\n")

print("已生成:", out)
print("标识符数量:", len(ordered))
print("示例:", ordered[:8])
bad = [n for n in ordered if not n.replace("_", "a").isalnum() or n[0].isdigit()
       or n.lower() in KEYWORDS]
print("非法条目:", len(bad))
