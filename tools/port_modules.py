# -*- coding: utf-8 -*-
"""
Port 旗光律动 (qiguang) and 红旗智声 (carvoice) resources/assets/Java into CarAssistant.
Strategy:
- Resources referenced at runtime via Res.xxx(name)/getIdentifier must keep exact names.
- Only rename to avoid collisions:
  * app_name            -> skipped (CarAssistant owns it)
  * 8 shared string names -> qg_/cv_ prefix
  * 3 shared layouts (activity_main/activity_about/item_about_member) -> qg_/cv_ prefix
  * avatar_* drawables  -> only one set kept (identical between apps); carvoice's skipped
- Java: rename package com.hzsoft.{qiguang,carvoice} -> com.carassistant.{ambient,voice};
  com.hzsoft.X.R -> com.carassistant.R; apply same string/layout literal prefixes in code.
- Layout XML content: apply same @string/@layout prefixes so compile-time refs resolve.
"""
import os, re, shutil

SRC = r"C:/Users/Administrator/Downloads/apk_analysis"
DST = r"C:/Users/Administrator/Desktop/code/CarAssistant/app/src/main"
RES = os.path.join(DST, "res")
JAVA = os.path.join(DST, "java")
ASSETS = os.path.join(DST, "assets")

SHARED = ["a11y_desc", "about_body", "about_disclaimer_tip", "about_section_info",
          "about_section_team", "about_title", "about_version_fmt", "master_label"]
LAYOUTS = ["activity_main", "activity_about", "item_about_member"]

# Apply @string/<shared> and @layout/<renamed> prefixes inside copied XML, and remap
# the launcher icon (CarAssistant ships it as a mipmap, not a drawable).
def prefix_xml_content(text, prefix):
    for s in SHARED:
        text = text.replace('@string/%s"' % s, '@string/%s%s"' % (prefix, s))
    for l in LAYOUTS:
        text = text.replace('@layout/%s"' % l, '@layout/%s%s"' % (prefix, l))
    text = text.replace("@drawable/ic_launcher", "@mipmap/ic_launcher")
    return text


def makedirs(p):
    os.makedirs(p, exist_ok=True)

def copy2(src, dst):
    makedirs(os.path.dirname(dst))
    shutil.copy2(src, dst)

def inner_resources(xml):
    m = re.search(r"<resources>(.*)</resources>", xml, re.S)
    return m.group(1).strip() if m else ""

# ---------------- RESOURCES ----------------
# qiguang drawable (skip ic_launcher)
qg_draw = os.path.join(SRC, "qiguang/resources/res/drawable")
for f in os.listdir(qg_draw):
    if f == "ic_launcher.png":
        continue
    copy2(os.path.join(qg_draw, f), os.path.join(RES, "drawable", f))

# qiguang layouts (rename 3)
qg_layout_renames = {f + ".xml": "qg_" + f + ".xml" for f in LAYOUTS}
for f in os.listdir(os.path.join(SRC, "qiguang/resources/res/layout")):
    target = qg_layout_renames.get(f, f)
    copy2(os.path.join(SRC, "qiguang/resources/res/layout", f), os.path.join(RES, "layout", target))

# qiguang xml (prefix shared strings; repoint settingsActivity to new package)
_qg_acc = os.path.join(RES, "xml", "qg_accessibility.xml")
copy2(os.path.join(SRC, "qiguang/resources/res/xml/qiguang_accessibility.xml"), _qg_acc)
_qg_txt = prefix_xml_content(open(_qg_acc, encoding="utf-8").read(), "qg_")
_qg_txt = _qg_txt.replace("com.hzsoft.qiguang.MainActivity", "com.carassistant.ambient.MainActivity")
open(_qg_acc, "w", encoding="utf-8").write(_qg_txt)

# carvoice xml (prefix shared strings; repoint settingsActivity to new package)
_cv_acc = os.path.join(RES, "xml", "carvoice_accessibility.xml")
copy2(os.path.join(SRC, "hongqizhisheng/resources/res/xml/carvoice_accessibility.xml"), _cv_acc)
_cv_txt = prefix_xml_content(open(_cv_acc, encoding="utf-8").read(), "cv_")
_cv_txt = _cv_txt.replace("com.hzsoft.carvoice.MainActivity", "com.carassistant.voice.MainActivity")
open(_cv_acc, "w", encoding="utf-8").write(_cv_txt)

# carvoice drawable (skip avatar_* (identical to qiguang) and ic_launcher)
cv_draw = os.path.join(SRC, "hongqizhisheng/resources/res/drawable")
for f in os.listdir(cv_draw):
    if f.startswith("avatar_") or f == "ic_launcher.png":
        continue
    copy2(os.path.join(cv_draw, f), os.path.join(RES, "drawable", f))

# carvoice layouts (rename 3 shared; keep unique)
cv_layout_renames = {f + ".xml": "cv_" + f + ".xml" for f in LAYOUTS}
for f in os.listdir(os.path.join(SRC, "hongqizhisheng/resources/res/layout")):
    target = cv_layout_renames.get(f, f)
    copy2(os.path.join(SRC, "hongqizhisheng/resources/res/layout", f), os.path.join(RES, "layout", target))
cv_ll = os.path.join(SRC, "hongqizhisheng/resources/res/layout-land")
if os.path.isdir(cv_ll):
    for f in os.listdir(cv_ll):
        target = cv_layout_renames.get(f, f)
        copy2(os.path.join(cv_ll, f), os.path.join(RES, "layout-land", target))

# colors module file
qg_colors = open(os.path.join(SRC, "qiguang/resources/res/values/colors.xml"), encoding="utf-8").read()
cv_colors = open(os.path.join(SRC, "hongqizhisheng/resources/res/values/colors.xml"), encoding="utf-8").read()
with open(os.path.join(RES, "values", "colors_modules.xml"), "w", encoding="utf-8") as fp:
    fp.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n')
    fp.write("    <!-- 旗光律动 -->\n" + inner_resources(qg_colors) + "\n")
    fp.write("    <!-- 红旗智声 -->\n" + inner_resources(cv_colors) + "\n")
    fp.write("</resources>\n")

# styles module file
qg_styles = open(os.path.join(SRC, "qiguang/resources/res/values/styles.xml"), encoding="utf-8").read()
cv_styles = open(os.path.join(SRC, "hongqizhisheng/resources/res/values/styles.xml"), encoding="utf-8").read()
with open(os.path.join(RES, "values", "styles_modules.xml"), "w", encoding="utf-8") as fp:
    fp.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n')
    fp.write(inner_resources(qg_styles) + "\n")
    fp.write(inner_resources(cv_styles) + "\n")
    fp.write("</resources>\n")

# strings module file
def process_strings(path, prefix):
    txt = open(path, encoding="utf-8").read()
    out = []
    for line in txt.splitlines():
        s = line.strip()
        if not s or s.startswith("<?xml") or s.startswith("<!--") or s.endswith("-->"):
            continue
        m = re.search(r'<string name="([^"]+)">(.*)</string>', line)
        if m:
            name, body = m.group(1), m.group(2)
            if name == "app_name":
                continue
            if name in SHARED:
                name = prefix + name
            out.append('    <string name="%s">%s</string>' % (name, body))
    return out

qg_str = process_strings(os.path.join(SRC, "qiguang/resources/res/values/strings.xml"), "qg_")
cv_str = process_strings(os.path.join(SRC, "hongqizhisheng/resources/res/values/strings.xml"), "cv_")
with open(os.path.join(RES, "values", "strings_modules.xml"), "w", encoding="utf-8") as fp:
    fp.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n')
    fp.write("    <!-- ===== 旗光律动 ===== -->\n")
    fp.write("\n".join(qg_str) + "\n")
    fp.write("\n    <!-- ===== 红旗智声 ===== -->\n")
    fp.write("\n".join(cv_str) + "\n")
    fp.write("</resources>\n")

# raw WAVs
cv_raw = os.path.join(SRC, "hongqizhisheng/resources/res/raw")
if os.path.isdir(cv_raw):
    for f in os.listdir(cv_raw):
        copy2(os.path.join(cv_raw, f), os.path.join(RES, "raw", f))

# assets voicepacks (catalog.json + files/)
cv_assets = os.path.join(SRC, "hongqizhisheng/resources/assets/voicepacks")
shutil.copytree(cv_assets, os.path.join(ASSETS, "voicepacks"), dirs_exist_ok=True)

# ---------------- RESOURCE XML CONTENT PREFIXING ----------------
# (prefix_xml_content is defined near the top, before resource copies.)

for sub in ["layout", "layout-land"]:
    d = os.path.join(RES, sub)
    if not os.path.isdir(d):
        continue
    mapping = qg_layout_renames if sub == "layout" else cv_layout_renames
    prefix = "qg_" if sub == "layout" else "cv_"
    # Determine prefix per file: qiguang files start with qg_, carvoice with cv_
    for f in os.listdir(d):
        fp = os.path.join(d, f)
        if not f.endswith(".xml"):
            continue
        is_qg = f.startswith("qg_")
        p = "qg_" if is_qg else "cv_"
        txt = open(fp, encoding="utf-8").read()
        new = prefix_xml_content(txt, p)
        if new != txt:
            open(fp, "w", encoding="utf-8").write(new)

# ---------------- JAVA ----------------
def port_java(src_dir, dst_pkg_dir, old_pkg, new_pkg, prefix):
    count = 0
    for root, _, files in os.walk(src_dir):
        for f in files:
            if not f.endswith(".java"):
                continue
            if f == "R.java":
                continue
            sp = os.path.join(root, f)
            code = open(sp, encoding="utf-8", errors="replace").read()
            # R import/refs -> app R
            code = code.replace(old_pkg + ".R", "com.carassistant.R")
            # package + class refs + component strings
            code = code.replace(old_pkg, new_pkg)
            # prefix shared string literals
            for s in SHARED:
                code = code.replace('"%s"' % s, '"%s%s"' % (prefix, s))
            # prefix layout literals
            for l in LAYOUTS:
                code = code.replace('"%s"' % l, '"%s%s"' % (prefix, l))
            dp = os.path.join(dst_pkg_dir, f)
            makedirs(os.path.dirname(dp))
            open(dp, "w", encoding="utf-8").write(code)
            count += 1
    return count

n1 = port_java(os.path.join(SRC, "qiguang/sources"),
               os.path.join(JAVA, "com/carassistant/ambient"),
               "com.hzsoft.qiguang", "com.carassistant.ambient", "qg_")
n2 = port_java(os.path.join(SRC, "hongqizhisheng/sources"),
               os.path.join(JAVA, "com/carassistant/voice"),
               "com.hzsoft.carvoice", "com.carassistant.voice", "cv_")

print("Port complete.")
print("  qiguang java files:", n1)
print("  carvoice java files:", n2)
print("  avatar drawables kept (qiguang only): skipped carvoice duplicates")
