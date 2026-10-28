# -*- coding: utf-8 -*-
"""Fix common JADX decompilation artifacts in the ported modules:
1. Dropped `Throwable th` accumulator: declare it at the top of each method.
2. Illegal `final` on loop variables (JADX bug): drop `for (final `.
3. Bad cast `(Property<TextView,Float>) View.SCALE_X/Y/ALPHA` -> remove cast.
4. `final boolean z` reassigned -> drop final.
5. AboutActivity `if (r1 != 0)` on a View -> `!= null`.
"""
import os, re

BASE = r"C:/Users/Administrator/Desktop/code/CarAssistant/app/src/main/java/com/carassistant"

PORTED = ["ambient", "voice"]

def _looks_like_method(line):
    s = line.strip()
    if "(" not in s or ")" not in s:
        return False
    if s.startswith("@") or s.startswith("//"):
        return False
    if re.match(r"(if|for|while|switch|catch|try|synchronized|return|throw|do|else|new)\b", s):
        return False
    return True

def _iter_brace_chars(lines):
    """Yield (line_index, char) for braces, skipping string/char literals and comments."""
    in_block = False
    for li, line in enumerate(lines):
        in_string = False
        in_char = False
        in_line = False
        i = 0
        while i < len(line):
            c = line[i]
            nxt = line[i + 1] if i + 1 < len(line) else ""
            if in_block:
                if c == "*" and nxt == "/":
                    in_block = False
                    i += 2
                    continue
                i += 1
                continue
            if in_line:
                i += 1
                continue
            if in_string:
                if c == "\\":
                    i += 2
                    continue
                if c == '"':
                    in_string = False
                i += 1
                continue
            if in_char:
                if c == "\\":
                    i += 2
                    continue
                if c == "'":
                    in_char = False
                i += 1
                continue
            if c == "/" and nxt == "*":
                in_block = True
                i += 2
                continue
            if c == "/" and nxt == "/":
                in_line = True
                i += 2
                continue
            if c == '"':
                in_string = True
                i += 1
                continue
            if c == "'":
                in_char = True
                i += 1
                continue
            if c == "{" or c == "}":
                yield (li, c)
            i += 1

def _find_matching_brace(lines, start_line):
    """Find line index of the matching '}' for the first '{' at/after start_line."""
    depth = 0
    started = False
    for li, ch in _iter_brace_chars(lines):
        if li < start_line:
            continue
        if ch == "{":
            depth += 1
            started = True
        elif ch == "}":
            depth -= 1
            if started and depth == 0:
                return li
    return -1

def fix_th_field(filepath):
    """Fix JADX 'th' accumulator artifact reliably, without method-boundary scanning.
    JADX scatters `Throwable th = null;` (or `Throwable th;`) inside catch/else/finally
    blocks, sometimes duplicating a `catch (Throwable th)` parameter and sometimes
    omitting it entirely. Correct, scope-free fix: declare `Throwable th` ONCE as a
    private field of the class, then delete every scattered in-method declaration
    (they become duplicates of the field). All `th` references then resolve to the field.
    """
    lines = open(filepath, encoding="utf-8").read().split("\n")
    DECL = re.compile(r"^\s*Throwable\s+th\s*(=\s*null)?;\s*$")
    # 1. drop every misplaced in-method Throwable th declaration
    out = [l for l in lines if not DECL.match(l)]
    # 2. ensure a single static field `private static Throwable th;` exists
    #    (idempotent; also converts a prior non-static instance field to static)
    out = [l for l in out if not re.match(r"^\s*private\s+Throwable\s+th\s*;", l)]
    found_static = any(re.match(r"^\s*private\s+static\s+Throwable\s+th\s*;", l) for l in out)
    if not found_static:
        for i, l in enumerate(out):
            if re.search(r"\bclass\s+\w+", l) and "{" in l:
                indent = re.match(r"\s*", l).group(0)
                out.insert(i + 1, indent + "    private static Throwable th;")
                break
    return "\n".join(out)

# 1. th declaration in the 4 files that use it
th_files = [
    os.path.join(BASE, "ambient/AmbientRhythmHelper.java"),
    os.path.join(BASE, "voice/MediaClipPlayer.java"),
    os.path.join(BASE, "voice/WavAudioPlayer.java"),
    os.path.join(BASE, "voice/VehicleVoiceEngine.java"),
]
for fp in th_files:
    if os.path.exists(fp) and os.path.getsize(fp) > 0:
        content = fix_th_field(fp)
        open(fp, "w", encoding="utf-8").write(content)
        print("th fix:", os.path.basename(fp))
    elif os.path.exists(fp):
        print("th fix SKIP (empty):", os.path.basename(fp))

# 2. drop `for (final ` across all ported java
for pkg in PORTED:
    d = os.path.join(BASE, pkg)
    for f in os.listdir(d):
        if not f.endswith(".java"):
            continue
        fp = os.path.join(d, f)
        txt = open(fp, encoding="utf-8").read()
        txt2 = txt.replace("for (final ", "for (")
        if txt2 != txt:
            open(fp, "w", encoding="utf-8").write(txt2)
            print("final-loop fix:", pkg + "/" + f)

# 3. bad Property cast on View.SCALE_X/Y/ALPHA (ambient MainActivity only)
mp = os.path.join(BASE, "ambient/MainActivity.java")
if os.path.exists(mp):
    txt = open(mp, encoding="utf-8").read()
    for sym in ["View.SCALE_X", "View.SCALE_Y", "View.ALPHA"]:
        txt = txt.replace("(Property<TextView, Float>) " + sym, sym)
    open(mp, "w", encoding="utf-8").write(txt)
    print("property-cast fix: ambient/MainActivity.java")

# 4. final boolean z (ClipPickerActivity)
cp = os.path.join(BASE, "voice/ClipPickerActivity.java")
if os.path.exists(cp):
    txt = open(cp, encoding="utf-8").read()
    txt = txt.replace("final boolean z = false;", "boolean z = false;")
    open(cp, "w", encoding="utf-8").write(txt)
    print("final-z fix: voice/ClipPickerActivity.java")

# 5. AboutActivity r1 != 0 -> != null
ap = os.path.join(BASE, "ambient/AboutActivity.java")
if os.path.exists(ap):
    txt = open(ap, encoding="utf-8").read()
    txt = txt.replace("if (r1 != 0) {", "if (r1 != null) {")
    open(ap, "w", encoding="utf-8").write(txt)
    print("r1-null fix: ambient/AboutActivity.java")

# 6. JADX `??` type-inference placeholders in ambient AboutActivity
#    (r1 = LinearLayout; r3/r4/r16/r17 = LayoutInflater; all assigned from a LayoutInflater)
qap = os.path.join(BASE, "ambient/AboutActivity.java")
if os.path.exists(qap) and os.path.getsize(qap) > 0:
    q = open(qap, encoding="utf-8").read()
    repl = {
        "        ?? r16;\n": "        LayoutInflater r16 = null;\n",
        "        ?? r17;\n": "        LayoutInflater r17 = null;\n",
        "        ?? r1 = (LinearLayout)": "        LinearLayout r1 = (LinearLayout)",
        "            ?? r3 = layoutInflaterFrom;": "            LayoutInflater r3 = layoutInflaterFrom;",
        "                    ?? r4 = r3;": "                    LayoutInflater r4 = r3;",
    }
    for k, v in repl.items():
        q = q.replace(k, v)
    open(qap, "w", encoding="utf-8").write(q)
    print("qmark fix: ambient/AboutActivity.java")

# 7. AmbientRhythmHelper.isMusicPlaying(): JADX left a `try { return ...; } catch (...) {}`
#    with no trailing return -> add `return false;` before the method's closing brace.
arh = os.path.join(BASE, "ambient/AmbientRhythmHelper.java")
if os.path.exists(arh) and os.path.getsize(arh) > 0:
    a = open(arh, encoding="utf-8").read()
    a = a.replace("        } catch (Throwable th4) {\n        }\n    }\n",
                 "        } catch (Throwable th4) {\n        }\n        return false;\n    }\n")
    open(arh, "w", encoding="utf-8").write(a)
    print("return-false fix: ambient/AmbientRhythmHelper.java")

# 8. ambient MainActivity.rebuildPaletteChips(): the for-loop index `i` is captured by the
#    onLongClick inner class (must be effectively final). Capture it into `iLoop`.
am = os.path.join(BASE, "ambient/MainActivity.java")
if os.path.exists(am) and os.path.getsize(am) > 0:
    m = open(am, encoding="utf-8").read()
    m = m.replace("        for (int i = 0; i < customPalette.length; i++) {\n            final int i2 = customPalette[i];\n",
                  "        for (int i = 0; i < customPalette.length; i++) {\n            final int i2 = customPalette[i];\n            final int iLoop = i;\n")
    m = m.replace("if (i3 != i) {", "if (i3 != iLoop) {")
    open(am, "w", encoding="utf-8").write(m)
    print("illegal-capture fix: ambient/MainActivity.java")

print("done")
