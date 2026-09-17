#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成 Android 启动图标（各密度 PNG 位图）。

两种设计源，按优先级自动选择：
  1. source/ic_launcher_source.png —— 位图设计源（当前使用）
  2. ic_launcher_foreground.xml     —— 早期的 VectorDrawable 矢量源（兜底，用无头浏览器栅格化）
     该文件目前仅作历史留档，不再是实际使用的图标。

输出：res/mipmap-{m,h,xh,xxh,xxxh}dpi/ 下的 ic_launcher.png 与 ic_launcher_round.png
      （方形直接用设计源；圆形按圆形遮罩裁切，供 roundIcon 用）

用法：python gen_icons.py
"""

import os
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
RES = ROOT / "app" / "src" / "main" / "res"

BITMAP_SOURCE = HERE / "source" / "ic_launcher_source.png"
VECTOR_SOURCE = HERE / "ic_launcher_foreground.xml"

EDGE_CANDIDATES = [
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
]

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"

BACK_COLOR = "#1E54E5"

# 传统启动图标各密度标准尺寸（48dp 基准）
DENSITIES = [
    ("mdpi", 48),
    ("hdpi", 72),
    ("xhdpi", 96),
    ("xxhdpi", 144),
    ("xxxhdpi", 192),
]

ROUND_MASK_SS = 4  # 圆形遮罩超采样倍数，保证边缘平滑


# --------------------------------------------------------------------------
# 位图设计源
# --------------------------------------------------------------------------

def generate_from_bitmap(src: Path) -> None:
    src_img = Image.open(src).convert("RGBA")
    print("设计源：%s  %s" % (src.relative_to(ROOT).as_posix(), src_img.size))

    bbox = src_img.getbbox()
    print("非透明内容范围：%s（画布 %s）" % (str(bbox), str(src_img.size)))
    if bbox and (bbox[0] > 2 or bbox[1] > 2
                 or bbox[2] < src_img.width - 2 or bbox[3] < src_img.height - 2):
        print("  ! 提示：设计源四周存在透明留白，图标在桌面上会显得偏小")

    written = []
    for density, size in DENSITIES:
        if size > src_img.width:
            print("  ! 警告：目标 %dpx 大于设计源 %dpx，该密度会被放大而发虚"
                  % (size, src_img.width))

        target_dir = RES / ("mipmap-%s" % density)
        target_dir.mkdir(parents=True, exist_ok=True)

        square_path = target_dir / "ic_launcher.png"
        src_img.resize((size, size), Image.LANCZOS).save(square_path, "PNG", optimize=True)
        written.append((square_path, size))

        round_path = target_dir / "ic_launcher_round.png"
        make_round(src_img, size).save(round_path, "PNG", optimize=True)
        written.append((round_path, size))

    write_previews(src_img)
    report(written)


def make_round(src_img: Image.Image, size: int) -> Image.Image:
    """把方形设计源按圆形遮罩裁切，供圆形图标槽位使用。"""
    base = src_img.resize((size, size), Image.LANCZOS)

    big = size * ROUND_MASK_SS
    mask = Image.new("L", (big, big), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, big - 1, big - 1), fill=255)
    mask = mask.resize((size, size), Image.LANCZOS)

    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(base, (0, 0), mask)
    return out


def write_previews(src_img: Image.Image) -> None:
    out_dir = HERE / "build"
    out_dir.mkdir(parents=True, exist_ok=True)
    # 预览保持原生分辨率，不放大，避免看起来比实际清晰
    size = src_img.width
    src_img.save(out_dir / "preview_square.png", "PNG")
    make_round(src_img, size).save(out_dir / "preview_round.png", "PNG")


# --------------------------------------------------------------------------
# 矢量源兜底（历史方案：VectorDrawable -> SVG -> 无头浏览器栅格化）
# --------------------------------------------------------------------------

def find_browser() -> str:
    for p in EDGE_CANDIDATES:
        if os.path.exists(p):
            return p
    sys.exit("找不到 Edge / Chrome，无法渲染矢量源")


def _float(node, name, default=0.0):
    v = node.get(ANDROID_NS + name)
    return float(v) if v is not None else default


def group_transform(g) -> str:
    px, py = _float(g, "pivotX"), _float(g, "pivotY")
    sx = _float(g, "scaleX", 1.0)
    sy = _float(g, "scaleY", 1.0)
    rot = _float(g, "rotation")
    tx, ty = _float(g, "translateX"), _float(g, "translateY")

    parts = ["translate(%g,%g)" % (tx + px, ty + py)]
    if rot:
        parts.append("rotate(%g)" % rot)
    parts.append("scale(%g,%g)" % (sx, sy))
    parts.append("translate(%g,%g)" % (-px, -py))
    return " ".join(parts)


def vector_to_svg(node, out: list) -> None:
    for child in node:
        if child.tag == "path":
            d = (child.get(ANDROID_NS + "pathData") or "").strip()
            if not d:
                continue
            attrs = ['fill="%s"' % child.get(ANDROID_NS + "fillColor", "#000000")]
            alpha = child.get(ANDROID_NS + "fillAlpha")
            if alpha is not None:
                attrs.append('fill-opacity="%s"' % alpha)
            out.append("<path %s d=\"%s\"/>" % (" ".join(attrs), d))
        elif child.tag == "group":
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


def generate_from_vector() -> None:
    browser = find_browser()
    print("设计源：%s" % VECTOR_SOURCE.relative_to(ROOT).as_posix())
    print("渲染器：%s" % browser)

    lines: list = []
    vector_to_svg(ET.parse(VECTOR_SOURCE).getroot(), lines)
    svg_inner = "".join(lines)

    workdir = HERE / "build"
    if workdir.exists():
        shutil.rmtree(workdir)
    workdir.mkdir(parents=True)
    profile = workdir / "profile"
    profile.mkdir()

    # 矢量源按 108dp 自适应画布换算像素
    adaptive = [("mdpi", 108), ("hdpi", 162), ("xhdpi", 216),
                ("xxhdpi", 324), ("xxxhdpi", 432)]

    written = []
    for variant, filename in (("square", "ic_launcher.png"), ("round", "ic_launcher_round.png")):
        for density, size in adaptive:
            html_path = workdir / ("%s_%s.html" % (variant, density))
            html_path.write_text(build_html(svg_inner, size, variant), encoding="utf-8")

            target_dir = RES / ("mipmap-%s" % density)
            target_dir.mkdir(parents=True, exist_ok=True)
            png = target_dir / filename

            if png.exists():
                png.unlink()
            subprocess.run([
                browser, "--headless=new", "--disable-gpu", "--hide-scrollbars",
                "--no-first-run", "--no-default-browser-check",
                "--force-device-scale-factor=1", "--default-background-color=00000000",
                "--user-data-dir=%s" % profile,
                "--window-size=%d,%d" % (size, size),
                "--screenshot=%s" % png.as_posix(),
                html_path.as_uri(),
            ], capture_output=True, text=True, timeout=120)
            if not png.exists():
                sys.exit("渲染失败 size=%d" % size)
            written.append((png, size))

    report(written)


# --------------------------------------------------------------------------

def report(written) -> None:
    print("\n输出校验：")
    ok = True
    for path, expect in written:
        with Image.open(path) as im:
            good = im.size == (expect, expect)
            ok = ok and good
            print("  %s%-52s %s" % ("OK " if good else "!! ",
                                    path.relative_to(ROOT).as_posix(), im.size))
    print("\n预览图：%s" % (HERE / "build" / "preview_square.png").relative_to(ROOT).as_posix())
    if not ok:
        sys.exit("存在尺寸不符的输出")


def main() -> None:
    if BITMAP_SOURCE.exists():
        generate_from_bitmap(BITMAP_SOURCE)
    elif VECTOR_SOURCE.exists():
        generate_from_vector()
    else:
        sys.exit("找不到设计源，需要 %s 或 %s"
                 % (BITMAP_SOURCE.as_posix(), VECTOR_SOURCE.as_posix()))


if __name__ == "__main__":
    main()
