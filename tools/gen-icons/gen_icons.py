#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
把 Android VectorDrawable 前景图标栅格化成各密度的 PNG 位图图标。

原理：VectorDrawable 的 <path android:pathData> 与 SVG 的 <path d> 语法完全兼容，
      <group> 的 pivot/scale/rotate/translate 可以换算成等价的 SVG transform。
      换算成 SVG 后用无头浏览器按目标分辨率渲染并截图，得到矢量级清晰的位图。

用法：python gen_icons.py
"""

import os
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]

# 矢量源文件在本目录留一份作为图标的设计源；若不存在则回退到 res 里找
VECTOR_CANDIDATES = [
    HERE / "ic_launcher_foreground.xml",
    ROOT / "app" / "src" / "main" / "res" / "drawable" / "ic_launcher_foreground.xml",
]
RES = ROOT / "app" / "src" / "main" / "res"

EDGE_CANDIDATES = [
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
]

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"

BACK_COLOR = "#1E54E5"

# 自适应图标画布为 108dp，按密度换算成像素
DENSITIES = [
    ("mdpi", 108),
    ("hdpi", 162),
    ("xhdpi", 216),
    ("xxhdpi", 324),
    ("xxxhdpi", 432),
]

VARIANTS = [
    ("square", "ic_launcher.png"),
    ("round", "ic_launcher_round.png"),
]

PREVIEW_SIZE = 512


def find_browser() -> str:
    for p in EDGE_CANDIDATES:
        if os.path.exists(p):
            return p
    sys.exit("找不到 Edge / Chrome，无法渲染")


def _float(node, name, default=0.0):
    v = node.get(ANDROID_NS + name)
    return float(v) if v is not None else default


def group_transform(g) -> str:
    """换算 Android <group> 变换为等价的 SVG transform。

    Android 的 postTranslate/postScale 语义是左乘（与 SVG 一致），
    变换链为 T(translate+pivot) · R(rotation) · S(scale) · T(-pivot)，
    即 pivot 点保持不动。
    """
    px = _float(g, "pivotX")
    py = _float(g, "pivotY")
    sx = _float(g, "scaleX", 1.0)
    sy = _float(g, "scaleY", 1.0)
    rot = _float(g, "rotation")
    tx = _float(g, "translateX")
    ty = _float(g, "translateY")

    parts = ["translate(%g,%g)" % (tx + px, ty + py)]
    if rot:
        parts.append("rotate(%g)" % rot)
    parts.append("scale(%g,%g)" % (sx, sy))
    parts.append("translate(%g,%g)" % (-px, -py))
    return " ".join(parts)


def vector_to_svg(node, out: list) -> None:
    for child in node:
        tag = child.tag
        if tag == "path":
            d = (child.get(ANDROID_NS + "pathData") or "").strip()
            if not d:
                continue
            fill = child.get(ANDROID_NS + "fillColor", "#000000")
            attrs = ['fill="%s"' % fill]
            alpha = child.get(ANDROID_NS + "fillAlpha")
            if alpha is not None:
                attrs.append('fill-opacity="%s"' % alpha)
            stroke = child.get(ANDROID_NS + "strokeColor")
            if stroke is not None:
                attrs.append('stroke="%s"' % stroke)
                attrs.append('stroke-width="%s"' % child.get(ANDROID_NS + "strokeWidth", "1"))
            out.append("<path %s d=\"%s\"/>" % (" ".join(attrs), d))
        elif tag == "group":
            out.append('<g transform="%s">' % group_transform(child))
            vector_to_svg(child, out)
            out.append("</g>")


def build_html(svg_inner: str, size: int, variant: str) -> str:
    if variant == "round":
        bg = '<circle cx="54" cy="54" r="54" fill="%s"/>' % BACK_COLOR
    else:
        bg = '<rect x="0" y="0" width="108" height="108" fill="%s"/>' % BACK_COLOR
    return (
        "<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
        "<style>html,body{margin:0;padding:0;background:transparent;overflow:hidden}</style>"
        "</head><body>"
        '<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 108 108">'
        "%s%s</svg></body></html>"
    ) % (size, size, bg, svg_inner)


def render(browser: str, html_path: Path, png_path: Path, size: int, profile: Path) -> None:
    if png_path.exists():
        png_path.unlink()
    cmd = [
        browser,
        "--headless=new",
        "--disable-gpu",
        "--hide-scrollbars",
        "--no-first-run",
        "--no-default-browser-check",
        "--force-device-scale-factor=1",
        "--default-background-color=00000000",
        "--user-data-dir=%s" % profile,
        "--window-size=%d,%d" % (size, size),
        "--screenshot=%s" % png_path.as_posix(),
        html_path.as_uri(),
    ]
    proc = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
    if not png_path.exists():
        sys.exit(
            "渲染失败 size=%d\nstdout=%s\nstderr=%s"
            % (size, proc.stdout[-2000:], proc.stderr[-2000:])
        )


def main() -> None:
    vector = next((p for p in VECTOR_CANDIDATES if p.exists()), None)
    if vector is None:
        sys.exit("找不到前景矢量，尝试过：%s" % ", ".join(str(p) for p in VECTOR_CANDIDATES))
    print("矢量源：%s" % vector.relative_to(ROOT).as_posix())

    browser = find_browser()
    print("渲染器：%s" % browser)

    root = ET.parse(vector).getroot()
    svg_inner_lines: list = []
    vector_to_svg(root, svg_inner_lines)
    svg_inner = "".join(svg_inner_lines)
    print("已转换 %d 个 SVG 元素" % sum(1 for l in svg_inner_lines if l.startswith("<path")))

    workdir = HERE / "build"
    if workdir.exists():
        shutil.rmtree(workdir)
    workdir.mkdir(parents=True)
    profile = workdir / "profile"
    profile.mkdir()

    written = []
    for variant, filename in VARIANTS:
        for density, size in DENSITIES:
            html_path = workdir / ("%s_%s.html" % (variant, density))
            html_path.write_text(build_html(svg_inner, size, variant), encoding="utf-8")

            target_dir = RES / ("mipmap-%s" % density)
            target_dir.mkdir(parents=True, exist_ok=True)
            target_png = target_dir / filename

            render(browser, html_path, target_png, size, profile)
            written.append((target_png, size))

    # 预览图，方便肉眼确认
    for variant, _ in VARIANTS:
        html_path = workdir / ("%s_preview.html" % variant)
        html_path.write_text(build_html(svg_inner, PREVIEW_SIZE, variant), encoding="utf-8")
        preview = workdir / ("preview_%s.png" % variant)
        render(browser, html_path, preview, PREVIEW_SIZE, profile)

    try:
        from PIL import Image

        print("\n输出尺寸校验：")
        for path, expect in written:
            with Image.open(path) as im:
                flag = "OK " if im.size == (expect, expect) else "!! "
                print("  %s%-52s %s" % (flag, path.relative_to(ROOT).as_posix(), im.size))
    except ImportError:
        for path, _ in written:
            print("  写入 %s" % path.relative_to(ROOT).as_posix())

    print("\n预览图：%s" % (workdir / "preview_square.png").relative_to(ROOT).as_posix())


if __name__ == "__main__":
    main()
