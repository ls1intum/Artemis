#!/usr/bin/env python3
"""Render the 60-second trailers of the Artemis programming exercise module.

The trailers are built from the documentation screenshots, title cards and short motion graphics.
There are two cuts: one for students (focus on fast, interactive feedback) and one for instructors.

Usage:
    python3 supporting_scripts/trailer/render_trailer.py [--cut student|instructor|all] [--out DIR]
        [--font PATH] [--mono PATH]
    python3 supporting_scripts/trailer/render_trailer.py --cut student --preview 6,12.5,30

See README.md in this directory for the requirements.
"""

import argparse
import math
import shutil
import subprocess
import sys
import wave
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

REPO = Path(__file__).resolve().parents[2]
DOCS = str(REPO / "documentation" / "docs")
LOGO_PATH = REPO / "documentation" / "static" / "img" / "artemis-logo-dark-theme.png"
# The documentation pages embed the trailers from these paths.
VIDEO_PATHS = {
    "student": Path(DOCS) / "student" / "exercises" / "assets" / "programming" / "trailer-student.mp4",
    "instructor": Path(DOCS) / "instructor" / "exercises" / "assets" / "programming" / "trailer-instructor.mp4",
}
W, H, FPS, DUR = 1920, 1080, 30, 60.0
OV = 0.6  # transition overlap in seconds
# Defaults are macOS system fonts; --font and --mono replace them. A file with a single face is used for every weight.
FONTS = {"sans": "/System/Library/Fonts/Avenir Next.ttc", "mono": "/System/Library/Fonts/Menlo.ttc"}
BOLD, DEMI, MED, HEAVY = 0, 2, 5, 8

ACCENT = (90, 169, 255)
WHITE = (245, 248, 255)
MUTED = (170, 182, 211)
GREEN = (46, 184, 92)
RED = (226, 76, 76)
ORANGE = (240, 162, 40)
PI = DOCS + "/instructor/exercises/assets/programming/"
PS = DOCS + "/student/"

_fonts = {}


def font(size, idx=MED, family="sans"):
    k = (size, idx, family)
    if k not in _fonts:
        try:
            _fonts[k] = ImageFont.truetype(FONTS[family], size, index=idx)
        except OSError:
            # not a collection with this face index: use the only face of the file
            _fonts[k] = ImageFont.truetype(FONTS[family], size)
    return _fonts[k]


def clamp(x):
    return min(max(x, 0.0), 1.0)


def ease(t):
    t = clamp(t)
    return t * t * (3 - 2 * t)


def ease_out(t):
    return 1 - (1 - clamp(t)) ** 3


def ease_io5(t):  # stronger ease in-out for snappy camera moves
    t = clamp(t)
    return 16 * t**5 if t < 0.5 else 1 - (-2 * t + 2) ** 5 / 2


def back_out(t):
    t = clamp(t)
    c = 1.70158
    return 1 + (c + 1) * (t - 1) ** 3 + c * (t - 1) ** 2


def lerp(a, b, k):
    return a + (b - a) * k


def rgba(c, a):
    return c + (int(255 * clamp(a)),)


# ---------------------------------------------------------------- background
def make_bg():
    y, x = np.mgrid[0:H, 0:W].astype(np.float32)
    top = np.array([10, 15, 30], np.float32)
    bot = np.array([16, 24, 48], np.float32)
    g = (y / H)[..., None]
    img = top * (1 - g) + bot * g
    glow = np.exp(-(((x - W * 0.78) / 700) ** 2 + ((y - H * 0.1) / 500) ** 2))[..., None]
    img += glow * np.array([30, 60, 120], np.float32) * 0.6
    glow2 = np.exp(-(((x - W * 0.1) / 600) ** 2 + ((y - H * 1.0) / 400) ** 2))[..., None]
    img += glow2 * np.array([40, 30, 110], np.float32) * 0.4
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).convert("RGBA")


BG = make_bg()
LOGO = Image.open(LOGO_PATH).convert("RGBA")

# ---------------------------------------------------------------- cards
_cardcache = {}


def card_assets(cw, ch, radius=18):
    k = (cw, ch, radius)
    if k not in _cardcache:
        mask = Image.new("L", (cw, ch), 0)
        ImageDraw.Draw(mask).rounded_rectangle((0, 0, cw - 1, ch - 1), radius, fill=255)
        sh = Image.new("L", (cw + 160, ch + 160), 0)
        ImageDraw.Draw(sh).rounded_rectangle((80, 95, 80 + cw, 95 + ch), radius, fill=170)
        sh = sh.filter(ImageFilter.GaussianBlur(32))
        border = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
        ImageDraw.Draw(border).rounded_rectangle((0, 0, cw - 1, ch - 1), radius, outline=(255, 255, 255, 40), width=2)
        _cardcache[k] = (mask, sh, border)
    return _cardcache[k]


def place_card(layer, content, x, y, alpha=1.0, radius=18):
    """Paste an RGB(A) card with rounded corners and a soft shadow onto layer."""
    cw, ch = content.size
    mask, sh, border = card_assets(cw, ch, radius)
    if alpha <= 0:
        return
    shadow = Image.new("RGBA", sh.size, (0, 0, 0, 0))
    shadow.putalpha(sh if alpha >= 1 else sh.point(lambda v: int(v * alpha)))
    layer.alpha_composite(shadow, (int(x) - 80, int(y) - 80))
    c = content.convert("RGBA")
    c.alpha_composite(border)
    m = mask if alpha >= 1 else mask.point(lambda v: int(v * alpha))
    layer.paste(c, (int(x), int(y)), m)


_img = {}


def load(path):
    if path not in _img:
        _img[path] = Image.open(path).convert("RGB")
    return _img[path]


def camera(keys, t):
    """keys: [(time fraction, cx, cy, wf, easing?)]. Returns (cx, cy, wf)."""
    if t <= keys[0][0]:
        return keys[0][1:4]
    for a, b in zip(keys, keys[1:], strict=False):
        if t <= b[0]:
            fn = b[4] if len(b) > 4 else ease
            k = fn((t - a[0]) / (b[0] - a[0]))
            return tuple(lerp(a[i], b[i], k) for i in range(1, 4))
    return keys[-1][1:4]


def render_crop(img, cx, cy, wf, cw, ch):
    iw, ih = img.size
    w = wf * iw
    h = w * ch / cw
    x0, y0, x1, y1 = cx * iw - w / 2, cy * ih - h / 2, cx * iw + w / 2, cy * ih + h / 2
    rect = (x0, y0, x1, y1)
    if x0 < 0 or y0 < 0 or x1 > iw or y1 > ih:
        need_x, need_y = int(max(0, -x0, x1 - iw)) + 2, int(max(0, -y0, y1 - ih)) + 2
        key = (id(img), "padded")
        cached = _img.get(key)
        if cached is None or cached[1] < need_x or cached[2] < need_y:
            # One padded copy per screenshot, with headroom so that a camera move rarely needs a bigger one.
            px, py = max(need_x, iw // 4), max(need_y, ih // 4)
            pad = Image.new("RGB", (iw + 2 * px, ih + 2 * py), "white")
            pad.paste(img, (px, py))
            cached = _img[key] = (pad, px, py)
        img, px, py = cached
        x0, x1, y0, y1 = x0 + px, x1 + px, y0 + py, y1 + py
    return img.resize((cw, ch), Image.LANCZOS, box=(x0, y0, x1, y1), reducing_gap=2.0), rect


def spotlight(card, rect_src, crop_rect, src_size, k, color=ACCENT):
    """Dim everything outside rect_src (fractions of the source image) on the rendered card."""
    if k <= 0:
        return card
    iw, ih = src_size
    x0, y0, x1, y1 = crop_rect
    cw, ch = card.size
    sx, sy = cw / (x1 - x0), ch / (y1 - y0)
    r = (rect_src[0] * iw, rect_src[1] * ih, rect_src[2] * iw, rect_src[3] * ih)
    box = ((r[0] - x0) * sx, (r[1] - y0) * sy, (r[2] - x0) * sx, (r[3] - y0) * sy)
    pad = 10
    box = (box[0] - pad, box[1] - pad, box[2] + pad, box[3] + pad)
    ov = Image.new("RGBA", card.size, (8, 12, 28, int(105 * k)))
    hole = Image.new("L", card.size, 255)
    ImageDraw.Draw(hole).rounded_rectangle(box, 12, fill=0)
    ov.putalpha(Image.composite(ov.getchannel("A"), hole.point(lambda v: 0), hole))
    out = card.convert("RGBA")
    out.alpha_composite(ov)
    d = ImageDraw.Draw(out)
    d.rounded_rectangle(box, 12, outline=rgba(color, k), width=4)
    return out


# ---------------------------------------------------------------- text
def wrap(d, s, f, maxw):
    words, lines, cur = s.split(), [], ""
    for w_ in words:
        nxt = (cur + " " + w_).strip()
        if d.textlength(nxt, font=f) > maxw and cur:
            lines.append(cur)
            cur = w_
        else:
            cur = nxt
    lines.append(cur)
    return lines


def anim_text(d, x, y, s, f, color, lt, delay, style, alpha_mul=1.0, maxw=None, line_h=None, center=False):
    """Draws text with an entrance animation. Returns the y below the block."""
    lines = wrap(d, s, f, maxw) if maxw else [s]
    lh = line_h or int(f.size * 1.22)
    for li, line in enumerate(lines):
        lx = x - d.textlength(line, font=f) / 2 if center else x
        ly = y + li * lh
        if style == "words":
            cx = lx
            for wi, word in enumerate(line.split(" ")):
                k = ease_out((lt - delay - (li * 4 + wi) * 0.06) / 0.45)
                if k > 0:
                    d.text((cx, ly + (1 - k) * 28), word, font=f, fill=rgba(color, k * alpha_mul))
                cx += d.textlength(word + " ", font=f)
        else:
            k = ease_out((lt - delay - li * 0.08) / 0.5)
            if k > 0:
                off = (1 - k) * (-40 if style == "slide" else 0)
                d.text(
                    (lx + off, ly + (1 - k) * (24 if style == "rise" else 0)),
                    line,
                    font=f,
                    fill=rgba(color, k * alpha_mul),
                )
    return y + len(lines) * lh


def caption_fonts():
    """Fonts are loaded on first use, after main() has checked that the font files exist."""
    return font(26, DEMI), font(56, BOLD), font(62, BOLD), font(29, MED)


def caption(layer, sc, lt, layout):
    lt -= 0.3
    f_kick, f_title, f_title_side, f_sub = caption_fonts()
    d = ImageDraw.Draw(layer)
    style = sc.get("title_style", "slide")
    if layout == "top":
        anim_text(d, 180, 64, sc["kicker"], f_kick, ACCENT, lt, 0.05, "slide")
        anim_text(d, 180, 98, sc["title"], f_title, WHITE, lt, 0.15, style)
        anim_text(d, 180, 176, sc["sub"], f_sub, MUTED, lt, 0.35, "slide")
    else:
        d2 = ImageDraw.Draw(layer)
        tl = wrap(d2, sc["title"], f_title_side, 560)
        sl = wrap(d2, sc["sub"], f_sub, 540)
        block = 44 + len(tl) * 76 + 24 + len(sl) * 40
        y = 540 - block / 2
        anim_text(d, 130, y, sc["kicker"], f_kick, ACCENT, lt, 0.05, "rise")
        y = anim_text(d, 130, y + 44, sc["title"], f_title_side, WHITE, lt, 0.15, style, maxw=560, line_h=76)
        anim_text(d, 130, y + 24, sc["sub"], f_sub, MUTED, lt, 0.4, "rise", maxw=540, line_h=40)
        # accent bar
        k = ease_out((lt - 0.1) / 0.6)
        d.rounded_rectangle((96, 540 - block / 2 * k, 101, 540 + block / 2 * k), 3, fill=rgba(ACCENT, k))


def progress_ticks(layer, idx, total):
    d = ImageDraw.Draw(layer)
    for i in range(total):
        x = W - 120 - (total - 1 - i) * 22
        d.rounded_rectangle((x, 60, x + 14, 64), 2, fill=(ACCENT if i == idx else (80, 95, 130)) + (255,))


# ---------------------------------------------------------------- scene kinds
def scene_shot(layer, sc, lt):
    dur = sc["end"] - sc["start"]
    layout = sc.get("layout", "top")
    img = load(sc["img"])
    if layout == "top":
        cw, ch = sc.get("card", (1560, 770))
        x, y = (W - cw) / 2, 262 + (770 - ch) / 2
    else:
        cw, ch = sc.get("card", (1060, 760))
        x, y = 740 + (1060 - cw) / 2, 540 - ch / 2 + 20
    cam = camera(sc["cam"], lt / dur)
    card, rect = render_crop(img, *cam, cw, ch)
    for sp in sc.get("spots", []):
        t0, t1, r = sp[0], sp[1], sp[2]
        k = min(ease((lt - t0) / 0.4), 1 - ease((lt - t1) / 0.4))
        card = spotlight(card, r, rect, img.size, k, sp[3] if len(sp) > 3 else ACCENT)
    k_in = sc.get("card_in", "rise")
    a = ease_out((lt - 0.05) / 0.6)
    if k_in == "rise":
        y += (1 - a) * 60
    elif k_in == "right":
        x += (1 - a) * 160
    place_card(layer, card, x, y, a)
    caption(layer, sc, lt, layout)
    if "tick" in sc:
        progress_ticks(layer, *sc["tick"])


def scene_collage(layer, sc, lt):
    """Several screenshots popping in at given times. items: (img, crop box fractions, scale, x, y, t_in)."""
    for it in sc["items"]:
        img = load(it["img"])
        bx = it.get("box", (0, 0, 1, 1))
        iw, ih = img.size
        box = (bx[0] * iw, bx[1] * ih, bx[2] * iw, bx[3] * ih)
        cw, ch = int((box[2] - box[0]) * it["scale"]), int((box[3] - box[1]) * it["scale"])
        k = back_out((lt - it["t"]) / 0.55)
        a = clamp((lt - it["t"]) / 0.3)
        if a <= 0:
            continue
        card = img.resize((cw, ch), Image.LANCZOS, box=box)
        for sp in it.get("spots", []):
            kk = min(ease((lt - sp[0]) / 0.4), 1 - ease((lt - sp[1]) / 0.4))
            card = spotlight(card, sp[2], box, img.size, kk, sp[3] if len(sp) > 3 else ACCENT)
        s = 0.85 + 0.15 * k
        drift = it.get("drift", (0, 0))
        ox = it["x"] + drift[0] * lt
        oy = it["y"] + drift[1] * lt + (1 - k) * 40
        c2 = card.resize((max(1, int(cw * s)), max(1, int(ch * s))), Image.BILINEAR) if s != 1 else card
        place_card(layer, c2, ox + (cw - c2.size[0]) / 2, oy + (ch - c2.size[1]) / 2, a, radius=14)
    caption(layer, sc, lt, sc.get("layout", "top"))
    if "tick" in sc:
        progress_ticks(layer, *sc["tick"])


# --- motion graphics ------------------------------------------------------
CODE = [
    ("public void ", "kw"),
    ("performSort", "fn"),
    ("(List<Date> input) {", "tx"),
    ("\n", ""),
    ("    for ", "kw"),
    ("(int i = input.size() - 1; i >= 0; i--) {", "tx"),
    ("\n", ""),
    ("        for ", "kw"),
    ("(int j = 0; j < i; j++) {", "tx"),
    ("\n", ""),
    ("            if ", "kw"),
    ("(input.get(j).compareTo(input.get(j + 1)) > 0) {", "tx"),
    ("\n", ""),
    ("                swap", "fn"),
    ("(input, j, j + 1);", "tx"),
    ("\n", ""),
    ("            }", "tx"),
    ("\n", ""),
    ("        }", "tx"),
    ("\n", ""),
    ("    }", "tx"),
    ("\n", ""),
    ("}", "tx"),
]
SYN = {"kw": (170, 13, 145), "fn": (28, 92, 200), "tx": (40, 44, 52)}
TESTS = [
    "testBubbleSort",
    "testMergeSort",
    "testUseBubbleSortForSmallList",
    "testUseMergeSortForBigList",
    "testClass[Context]",
    "testMethods[Policy]",
]


def draw_editor(layer, x, y, w, h, nchars, alpha, filename="BubbleSort.java", cursor=True, lt=0):
    ed = Image.new("RGB", (w, h), (255, 255, 255))
    d = ImageDraw.Draw(ed)
    d.rectangle((0, 0, w, 52), fill=(62, 138, 204))
    d.text((24, 12), "src/de/tum/in/ase/" + filename, font=font(24, DEMI), fill=(255, 255, 255))
    fm = font(25, 0, "mono")
    lx, ly, col = 80, 76, 0
    line = 1
    d.text((24, ly), "1", font=fm, fill=(160, 166, 180))
    shown = 0
    cur_xy = (lx, ly)
    for tok, kind in CODE:
        if shown >= nchars:
            break
        if tok == "\n":
            line += 1
            ly += 40
            col = 0
            d.text((24 if line < 10 else 10, ly), str(line), font=fm, fill=(160, 166, 180))
            shown += 1
            cur_xy = (lx, ly)
            continue
        part = tok[: max(0, nchars - shown)]
        px = lx + d.textlength(" " * col, font=fm)
        d.text((px, ly), part, font=fm, fill=SYN[kind])
        col += len(part)
        shown += len(part)
        cur_xy = (lx + d.textlength(" " * col, font=fm), ly)
    if cursor and int(lt * 2.5) % 2 == 0:
        d.rectangle((cur_xy[0] + 1, cur_xy[1] + 2, cur_xy[0] + 4, cur_xy[1] + 30), fill=(30, 30, 30))
    place_card(layer, ed, x, y, alpha)


def ring(d, cx, cy, r, frac, color, width=18, track=(60, 72, 100)):
    d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=track + (255,), width=width)
    if frac > 0:
        d.arc((cx - r, cy - r, cx + r, cy + r), -90, -90 + 360 * frac, fill=color + (255,), width=width)


def scene_push(layer, sc, lt):
    """Student types code, presses submit, tests run and tick off, score ring fills."""
    total = sum(len(t) if t != "\n" else 1 for t, _ in CODE)
    type_k = clamp((lt - 0.3) / 2.2)
    a = ease_out(lt / 0.5)
    ed_x = lerp(180, 120, ease_io5((lt - 2.9) / 0.6))
    ed_w = int(lerp(1560, 1020, ease_io5((lt - 2.9) / 0.6)))
    draw_editor(layer, ed_x, 262, ed_w, 640, int(total * type_k), a, lt=lt, cursor=lt < 2.9)
    d = ImageDraw.Draw(layer)
    # submit button
    bk = ease_out((lt - 2.2) / 0.3)
    press = 1 - 0.08 * math.sin(math.pi * clamp((lt - 2.55) / 0.25))
    bw, bh = 220 * press, 64 * press
    bx, by = ed_x + ed_w - 250 + (220 - bw) / 2, 940 + (64 - bh) / 2
    if bk > 0:
        d.rounded_rectangle((bx, by, bx + bw, by + bh), 10, fill=rgba((40, 167, 69), bk))
        tw = d.textlength("Submit", font=font(28, DEMI))
        d.text((bx + (bw - tw) / 2, by + 12 * press), "Submit", font=font(28, DEMI), fill=rgba(WHITE, bk))
    # results panel
    pk = ease_out((lt - 3.1) / 0.6)
    if pk > 0:
        px, py, pw, ph = 1180 + (1 - pk) * 120, 262, 620, 700
        panel = Image.new("RGBA", (pw, ph), (0, 0, 0, 0))
        pd = ImageDraw.Draw(panel)
        pd.rounded_rectangle((0, 0, pw - 1, ph - 1), 18, fill=(20, 30, 56, 235), outline=(255, 255, 255, 40), width=2)
        # build steps
        steps = ["Build", "Test", "Result"]
        for i, s in enumerate(steps):
            sk = clamp((lt - 3.4 - i * 0.45) / 0.45)
            sx = 40 + i * 190
            pd.rounded_rectangle((sx, 36, sx + 170, 44), 4, fill=(60, 72, 100, 255))
            if sk > 0:
                pd.rounded_rectangle((sx, 36, sx + 170 * sk, 44), 4, fill=ACCENT + (255,))
            pd.text((sx, 56), s, font=font(24, DEMI), fill=(WHITE if sk >= 1 else MUTED) + (255,))
        # tests
        passed = 0
        for i, name in enumerate(TESTS):
            tk = ease_out((lt - 4.7 - i * 0.22) / 0.35)
            if tk <= 0:
                continue
            ok = i != 3
            passed += ok
            ty = 130 + i * 62
            col = GREEN if ok else RED
            pd.ellipse((40, ty, 76, ty + 36), fill=col + (int(255 * tk),))
            if ok:
                pd.line((49, ty + 19, 56, ty + 26, 68, ty + 11), fill=(255, 255, 255, int(255 * tk)), width=4)
            else:
                pd.line((50, ty + 10, 66, ty + 26), fill=(255, 255, 255, int(255 * tk)), width=4)
                pd.line((66, ty + 10, 50, ty + 26), fill=(255, 255, 255, int(255 * tk)), width=4)
            pd.text((96 + (1 - tk) * 30, ty + 2), name, font=font(26, MED), fill=WHITE + (int(255 * tk),))
        # score ring
        rk = ease_out((lt - 5.0) / 1.6)
        ring(pd, 140, 590, 70, 0.83 * rk, GREEN, 16)
        pct = f"{int(round(83 * rk))}%"
        tw = pd.textlength(pct, font=font(36, BOLD))
        pd.text((140 - tw / 2, 566), pct, font=font(36, BOLD), fill=WHITE + (255,))
        sk = ease_out((lt - 5.6) / 0.5)
        pd.text((250, 546), f"{passed} of {len(TESTS)} passed", font=font(34, BOLD), fill=WHITE + (int(255 * sk),))
        pd.text((250, 594), "testUseMergeSortForBigList failed:", font=font(22, MED), fill=MUTED + (int(255 * sk),))
        pd.text((250, 622), "expected MergeSort for 1000 dates", font=font(22, MED), fill=MUTED + (int(255 * sk),))
        panel.putalpha(panel.getchannel("A").point(lambda v: int(v * pk)))
        layer.alpha_composite(panel, (int(px), int(py)))
    caption(layer, sc, lt, "top")


def scene_attempts(layer, sc, lt):
    d = ImageDraw.Draw(layer)
    vals = [(25, RED), (50, ORANGE), (83, (120, 200, 80)), (100, GREEN)]
    base, top = 940, 360
    for i, (v, col) in enumerate(vals):
        k = ease_out((lt - 0.4 - i * 0.55) / 0.7)
        if k <= 0:
            continue
        x = 330 + i * 330
        hgt = (base - top) * v / 100 * k
        d.rounded_rectangle((x, base - hgt, x + 200, base), 14, fill=rgba(col, 0.92))
        lbl = f"{int(round(v * k))}%"
        tw = d.textlength(lbl, font=font(46, HEAVY))
        d.text((x + 100 - tw / 2, base - hgt - 64), lbl, font=font(46, HEAVY), fill=rgba(WHITE, k))
        ax = f"Push {i + 1}"
        tw = d.textlength(ax, font=font(28, DEMI))
        d.text((x + 100 - tw / 2, base + 18), ax, font=font(28, DEMI), fill=rgba(MUTED, k))
    d.line((280, base, 1640, base), fill=(90, 105, 140, 255), width=2)
    caption(layer, sc, lt, "top")


TERM = [
    ("$ ", "git clone https://artemis.example.org/git/SORTING/sorting-ga12abc.git"),
    ("", "Cloning into 'sorting-ga12abc'... done."),
    ("$ ", 'git commit -am "Use MergeSort for big lists"'),
    ("", "[main 3f9c2e1] Use MergeSort for big lists"),
    ("$ ", "git push"),
    ("", "To https://artemis.example.org/git/SORTING/sorting-ga12abc.git"),
    ("", "   8a41d07..3f9c2e1  main -> main"),
]


def scene_terminal(layer, sc, lt):
    tw_, th_ = 1300, 560
    term = Image.new("RGB", (tw_, th_), (24, 26, 33))
    d = ImageDraw.Draw(term)
    d.rectangle((0, 0, tw_, 46), fill=(40, 43, 54))
    for i, c in enumerate([(255, 95, 86), (255, 189, 46), (39, 201, 63)]):
        d.ellipse((20 + i * 30, 14, 38 + i * 30, 32), fill=c)
    d.text((tw_ / 2 - 60, 10), "Terminal", font=font(22, DEMI), fill=(170, 175, 190))
    fm = font(24, 0, "mono")
    t = 0.3
    y = 76
    for prompt, txt in TERM:
        if prompt:
            n = int(clamp((lt - t) / 0.9) * len(txt))
            if lt < t:
                break
            d.text((30, y), prompt, font=fm, fill=(120, 220, 140))
            d.text((30 + d.textlength(prompt, font=fm), y), txt[:n], font=fm, fill=(230, 232, 240))
            t += 1.05
        else:
            if lt < t:
                break
            d.text((30, y), txt, font=fm, fill=(150, 156, 172))
            t += 0.2
        y += 46
    a = ease_out(lt / 0.5)
    place_card(layer, term, 180 + (1 - a) * -80, 300, a, radius=14)
    # toast with result
    k = back_out((lt - 4.0) / 0.5)
    if lt > 4.0:
        toast = Image.new("RGB", (560, 150), (255, 255, 255))
        td = ImageDraw.Draw(toast)
        ring(td, 75, 75, 46, 1.0 * clamp((lt - 4.1) / 0.6), GREEN, 10, track=(225, 230, 238))
        td.line((58, 77, 70, 89, 92, 63), fill=GREEN, width=7)
        td.text((145, 30), "100%, 6 of 6 passed", font=font(34, BOLD), fill=(30, 36, 48))
        td.text((145, 82), "Build result · just now", font=font(24, MED), fill=(110, 118, 135))
        place_card(layer, toast, 1180 + (1 - k) * 200, 720, clamp((lt - 4.0) / 0.25), radius=14)
    caption(layer, sc, lt, "top")


def centered(d, y, s, f, color, alpha):
    w = d.textlength(s, font=f)
    d.text(((W - w) / 2, y), s, font=f, fill=rgba(color, alpha))


def scene_kinetic(layer, sc, lt):
    """Big words punching in one after another, then a title."""
    d = ImageDraw.Draw(layer)
    words = sc["words"]
    step = sc.get("step", 0.55)
    for i, (wd, col) in enumerate(words):
        t0 = 0.2 + i * step
        k = back_out((lt - t0) / 0.4)
        a = clamp((lt - t0) / 0.2)
        if a <= 0:
            continue
        f = font(int(120 * (0.7 + 0.3 * k)), HEAVY)
        dim = 1 - 0.55 * clamp((lt - t0 - step) / 0.3) if i < len(words) - 1 and sc.get("dim", True) else 1
        tw = d.textlength(wd, font=f)
        total_w = sum(d.textlength(w_ + "  ", font=font(120, HEAVY)) for w_, _ in words)
        x0 = (W - total_w) / 2 + sum(d.textlength(w_ + "  ", font=font(120, HEAVY)) for w_, _ in words[:i])
        bw = d.textlength(wd, font=font(120, HEAVY))
        d.text((x0 + (bw - tw) / 2, sc.get("y", 380) + (120 - f.size) / 2), wd, font=f, fill=rgba(col, a * dim))
    if "title" in sc:
        k = ease_out((lt - sc["title_t"]) / 0.7)
        centered(d, sc.get("y", 380) + 170 + (1 - k) * 20, sc["title"], font(44, DEMI), ACCENT, k)
    if "sub" in sc and sc["sub"]:
        k = ease_out((lt - sc["title_t"] - 0.3) / 0.7)
        centered(d, sc.get("y", 380) + 240, sc["sub"], font(32, MED), MUTED, k)


def scene_intro(layer, sc, lt):
    d = ImageDraw.Draw(layer)
    a = ease_out((lt - 0.1) / 0.8)
    sz = int(150 + 20 * ease_out(lt / 4))
    lg = LOGO.resize((sz, sz), Image.LANCZOS)
    lg.putalpha(lg.getchannel("A").point(lambda v: int(v * a)))
    layer.alpha_composite(lg, ((W - sz) // 2, 230 - (sz - 150) // 2))
    k1 = ease_out((lt - 0.5) / 0.7)
    centered(d, 430, sc["kicker"], font(30, DEMI), ACCENT, k1)
    f = font(104, HEAVY)
    tw = d.textlength(sc["title"], font=f)
    anim_text(d, (W - tw) / 2, 475, sc["title"], f, WHITE, lt, 0.7, "words")
    anim_text(d, W / 2, 625, sc["sub"], font(38, MED), MUTED, lt, 1.5, "rise", center=True)


# ProgrammingLanguage.ENABLED_LANGUAGES on the server: the languages an instructor can select for an exercise.
LANGS = [
    "Java",
    "Python",
    "C",
    "C++",
    "C#",
    "Kotlin",
    "Swift",
    "Rust",
    "Go",
    "TypeScript",
    "JavaScript",
    "Haskell",
    "OCaml",
    "Ruby",
    "Dart",
    "R",
    "MATLAB",
    "Bash",
    "VHDL",
    "Assembler",
]


def scene_langs(layer, sc, lt):
    d = ImageDraw.Draw(layer)
    centered(d, 150, sc["title"], font(80, HEAVY), WHITE, ease_out(lt / 0.6))
    centered(d, 260, sc["title2"], font(80, HEAVY), ACCENT, ease_out((lt - 0.3) / 0.6))
    cols, cw, rh = 5, 300, 120
    rows = math.ceil(len(LANGS) / cols)
    f = font(38, DEMI)
    x0 = (W - cols * cw) / 2
    # boxes appear from the centre of the grid outwards
    order = sorted(
        range(len(LANGS)), key=lambda i: (i % cols - (cols - 1) / 2) ** 2 + (i // cols - (rows - 1) / 2) ** 2
    )
    for rank, i in enumerate(order):
        name = LANGS[i]
        r, c = divmod(i, cols)
        ai = back_out((lt - 0.4 - rank * 0.045) / 0.45)
        al = clamp((lt - 0.4 - rank * 0.045) / 0.2)
        if al <= 0:
            continue
        cxm, cym = x0 + c * cw + cw / 2, 430 + r * rh + 44
        bw, bh = (cw - 24) * (0.6 + 0.4 * ai), 88 * (0.6 + 0.4 * ai)
        d.rounded_rectangle(
            (cxm - bw / 2, cym - bh / 2, cxm + bw / 2, cym + bh / 2),
            16,
            fill=(255, 255, 255, int(16 * al)),
            outline=(120, 160, 230, int(90 * al)),
            width=2,
        )
        tw = d.textlength(name, font=f)
        d.text((cxm - tw / 2, cym - 24), name, font=f, fill=rgba(WHITE, al))


def scene_outro(layer, sc, lt):
    d = ImageDraw.Draw(layer)
    a0 = ease_out(lt / 0.8)
    lg = LOGO.resize((150, 150), Image.LANCZOS)
    lg.putalpha(lg.getchannel("A").point(lambda v: int(v * a0)))
    layer.alpha_composite(lg, ((W - 150) // 2, 230))
    anim_text(d, W / 2, 410, sc["title"], font(88, HEAVY), WHITE, lt, 0.3, "rise", center=True)
    anim_text(d, W / 2, 540, sc["sub"], font(38, MED), MUTED, lt, 0.7, "rise", center=True)
    anim_text(
        d,
        W / 2,
        660,
        "Open source  ·  github.com/ls1intum/Artemis",
        font(32, DEMI),
        ACCENT,
        lt,
        1.2,
        "rise",
        center=True,
    )


KINDS = {
    "shot": scene_shot,
    "collage": scene_collage,
    "push": scene_push,
    "attempts": scene_attempts,
    "terminal": scene_terminal,
    "kinetic": scene_kinetic,
    "intro": scene_intro,
    "langs": scene_langs,
    "outro": scene_outro,
}


# ---------------------------------------------------------------- transitions
def apply_transition(layer, style, p, entering):
    """p in [0,1]: 0 = fully hidden, 1 = fully visible (for both directions)."""
    if p >= 1:
        return layer
    e = ease(p)
    if style == "fade":
        dx, dy, s = 0, 0, 1
    elif style == "slide":  # enter from right, exit to left
        dx, dy, s = (1 - e) * (260 if entering else -260), 0, 1
    elif style == "up":
        dx, dy, s = 0, (1 - e) * (140 if entering else -140), 1
    elif style == "zoom":  # zoom-through: enter from small, exit growing
        dx, dy, s = 0, 0, (lerp(0.9, 1, e) if entering else lerp(1.12, 1, e))
    elif style == "wipe":
        q = ease(p if entering else 1 - p)
        edge = q * (W + 300) - 300
        ramp = np.clip((np.arange(W, dtype=np.float32) - edge) / 300.0, 0, 1)
        m = (1 - ramp) if entering else ramp * (1 - q) ** 1.5
        a = np.asarray(layer.getchannel("A"), np.float32) * m[None, :]
        layer.putalpha(Image.fromarray(a.astype(np.uint8)))
        return layer
    else:
        dx, dy, s = 0, 0, 1
    if s != 1:
        nw, nh = int(W * s), int(H * s)
        scaled = layer.resize((nw, nh), Image.BILINEAR)
        out = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        out.alpha_composite(scaled, ((W - nw) // 2, (H - nh) // 2)) if s < 1 else out.alpha_composite(
            scaled.crop(((nw - W) // 2, (nh - H) // 2, (nw - W) // 2 + W, (nh - H) // 2 + H))
        )
        layer = out
    if dx or dy:
        out = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        out.alpha_composite(layer, (int(dx), int(dy))) if dx >= 0 and dy >= 0 else out.paste(
            layer, (int(dx), int(dy)), layer
        )
        layer = out
    f = e if entering else e**2.5
    a = layer.getchannel("A").point(lambda v: int(v * f))
    layer.putalpha(a)
    return layer


def frame_at(t, scenes):
    frame = BG.copy()
    for i, sc in enumerate(scenes):
        last = i == len(scenes) - 1
        end_vis = sc["end"] + (0 if last else OV)
        if not (sc["start"] <= t < end_vis):
            continue
        lt = t - sc["start"]
        layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        KINDS[sc["kind"]](layer, sc, lt)
        if i > 0 and lt < OV:
            layer = apply_transition(layer, sc.get("tin", "fade"), lt / OV, True)
        if t >= sc["end"] and not last:
            layer = apply_transition(layer, scenes[i + 1].get("tin", "fade"), 1 - (t - sc["end"]) / OV, False)
        if last and t > DUR - 0.8:
            layer.putalpha(layer.getchannel("A").point(lambda v: int(v * clamp((DUR - t) / 0.8))))
        frame.alpha_composite(layer)
    return frame.convert("RGB")


# ---------------------------------------------------------------- storyboards
def student():
    n = 6
    return [
        dict(
            kind="kinetic",
            start=0,
            end=4.5,
            words=[("Write.", WHITE), ("Push.", WHITE), ("Learn.", ACCENT)],
            step=0.6,
            title="Programming exercises in Artemis",
            title_t=2.2,
            sub="Instant, interactive feedback on every line you write.",
            dim=False,
        ),
        dict(
            kind="shot",
            start=4.5,
            end=9.5,
            tin="zoom",
            tick=(0, n),
            img=PS + "exercises/assets/programming/OnlineEditor.png",
            kicker="START CODING",
            title="Open the editor. Start coding.",
            sub="No setup: files, tasks and build output in one browser tab.",
            title_style="words",
            # the window from the panel headers to the last task keeps every row whole while panning
            cam=[
                (0, 0.355, 0.435, 0.71),
                (0.15, 0.355, 0.435, 0.71),
                (0.85, 0.645, 0.435, 0.71, ease_io5),
                (1, 0.645, 0.435, 0.71),
            ],
            spots=[(3.3, 4.75, (0.675, 0.40, 0.965, 0.70))],
        ),
        dict(
            kind="push",
            start=9.5,
            end=17.5,
            tin="slide",
            kicker="INSTANT FEEDBACK",
            title="Push. Get feedback right away.",
            sub="Every submission is built and tested automatically.",
            title_style="words",
        ),
        dict(
            kind="shot",
            start=17.5,
            end=22,
            tin="wipe",
            tick=(1, n),
            img=PI + "online-editor-feedback.png",
            card=(1560, 377),
            kicker="FEEDBACK IN CONTEXT",
            title="Feedback right where it matters.",
            sub="Comments appear next to the exact line of your code.",
            cam=[(0, 0.5, 0.5, 1.03), (0.3, 0.5, 0.5, 1.03), (1, 0.325, 0.42, 0.65, ease_io5)],
        ),
        dict(
            kind="attempts",
            start=22,
            end=26.5,
            tin="up",
            kicker="ITERATE",
            title="Improve with every push.",
            sub="Fix, push, check again — until every test is green.",
            title_style="slide",
        ),
        dict(
            kind="collage",
            start=26.5,
            end=31.5,
            tin="zoom",
            tick=(2, n),
            layout="side",
            kicker="AI FEEDBACK",
            title="Ask for AI feedback.",
            sub="Get hints on what to improve before the deadline — without the solution.",
            items=[
                dict(
                    img=PS + "exercises/assets/ai-feedback/automatic-feedback-request-score.png",
                    scale=2.6,
                    x=860,
                    y=380,
                    t=0.3,
                    spots=[(1.2, 3.0, (0.05, 0.47, 0.75, 0.97))],
                ),
                dict(
                    img=PS + "exercises/assets/ai-feedback/automatic-feedback-request-alert.png",
                    box=(0.045, 0.055, 0.985, 0.28),
                    scale=1.33,
                    x=760,
                    y=640,
                    t=2.2,
                ),
            ],
        ),
        dict(
            kind="shot",
            start=31.5,
            end=36.5,
            tin="slide",
            tick=(3, n),
            layout="side",
            card=(880, 700),
            img=PS + "learning-content/assets/iris/iris-conversation.png",
            kicker="AI TUTOR",
            title="Stuck? Ask Iris.",
            sub="Your virtual tutor guides you to the answer instead of giving it away.",
            title_style="words",
            card_in="right",
            # ends above the paragraph that refers to a diagram from another exercise type
            cam=[(0, 0.5, 0.285, 1.0), (0.2, 0.5, 0.285, 1.0), (1, 0.5, 0.316, 0.92)],
        ),
        dict(
            kind="terminal",
            start=36.5,
            end=42,
            tin="wipe",
            kicker="YOUR TOOLS",
            title="Prefer your own IDE? Just push.",
            sub="Clone with Git, work locally, get the same feedback.",
            title_style="slide",
        ),
        dict(
            kind="collage",
            start=42,
            end=46.5,
            tin="up",
            tick=(4, n),
            layout="side",
            kicker="PRACTICE MODE",
            title="Missed the deadline? Keep practising.",
            sub="Practice runs never touch your grade.",
            items=[
                dict(
                    img=PS + "exercises/assets/practice-mode/practice-mode-popover.png",
                    box=(0.0, 0.0, 0.985, 0.185),
                    scale=1.4,
                    x=760,
                    y=360,
                    t=0.25,
                    spots=[(0.9, 2.1, (0.785, 0.035, 0.975, 0.175))],
                ),
                dict(
                    img=PS + "exercises/assets/practice-mode/practice-mode-popover.png",
                    box=(0.093, 0.205, 0.975, 0.925),
                    scale=1.55,
                    x=760,
                    y=470,
                    t=1.3,
                    drift=(-6, 0),
                    spots=[(2.4, 4.3, (0.835, 0.675, 0.975, 0.85))],
                ),
            ],
        ),
        dict(
            kind="shot",
            start=46.5,
            end=51.5,
            tin="zoom",
            tick=(5, n),
            img=PS + "assets/exams/exam_programming_exercises.png",
            kicker="EXAMS",
            title="The same editor in your exams.",
            sub="Nothing new to learn when it counts.",
            title_style="words",
            cam=[(0, 0.8225, 0.305, 0.4), (0.25, 0.8225, 0.305, 0.4), (1, 0.5, 0.46, 1.15, ease_io5)],
        ),
        dict(
            kind="langs",
            start=51.5,
            end=56,
            tin="fade",
            title=f"{len(LANGS)} programming languages.",
            title2="One place to learn them.",
        ),
        dict(
            kind="outro",
            start=56,
            end=60,
            tin="zoom",
            title="Artemis Programming Exercises",
            sub="Learn programming by doing — with feedback in seconds.",
        ),
    ]


def instructor():
    n = 9
    return [
        dict(
            kind="intro",
            start=0,
            end=4,
            kicker="ARTEMIS FOR INSTRUCTORS",
            title="Programming Exercises",
            sub="From the first idea to the final grade.",
        ),
        dict(
            kind="shot",
            start=4,
            end=9,
            tin="zoom",
            tick=(0, n),
            layout="side",
            card=(1060, 700),
            card_in="right",
            img=PI + "create-form-language.png",
            kicker="01 · CREATE",
            title="Set up an exercise in minutes.",
            sub="Pick a language and a build system — Artemis creates the repositories.",
            title_style="words",
            cam=[(0, 0.27, 0.31, 0.54), (1, 0.31, 0.43, 0.64)],
            spots=[(0.9, 2.5, (0.0, 0.115, 0.998, 0.215)), (2.6, 4.5, (0.0, 0.235, 0.26, 0.31))],
        ),
        dict(
            kind="shot",
            start=9,
            end=14,
            tin="slide",
            tick=(1, n),
            img=PI + "hyperion-generate.png",
            kicker="02 · WRITE",
            title="Draft the problem statement with AI.",
            sub="Describe your idea. Artemis Intelligence writes the first version.",
            cam=[(0, 0.5, 0.5, 1.06), (1, 0.5, 0.47, 1.0)],
            spots=[(0.7, 2.1, (0.005, 0.33, 0.975, 0.5)), (2.1, 4.5, (0.005, 0.56, 0.215, 0.62))],
        ),
        dict(
            kind="shot",
            start=14,
            end=19,
            tin="wipe",
            tick=(2, n),
            img=PI + "hyperion-diff-view.png",
            kicker="03 · REFINE",
            title="Stay in control of every change.",
            sub="Accept or discard AI suggestions side by side.",
            cam=[
                (0, 0.26, 0.475, 0.5),
                (0.3, 0.26, 0.475, 0.5),
                (0.7, 0.74, 0.475, 0.5, ease_io5),
                (0.8, 0.74, 0.475, 0.5),
                (1, 0.5, 0.5, 1.02, ease_io5),
            ],
            spots=[(3.9, 4.6, (0.865, 0.16, 0.955, 0.22), GREEN)],
        ),
        dict(
            kind="shot",
            start=19,
            end=24,
            tin="up",
            tick=(3, n),
            img=PI + "collaborative-editing-code-editor.png",
            kicker="04 · COLLABORATE",
            title="Build it together, live.",
            title_style="words",
            sub="Co-edit template, solution and tests in real time.",
            cam=[(0, 0.5, 0.5, 1.04), (0.35, 0.5, 0.5, 1.04), (1, 0.5, 0.5, 1.0)],
            spots=[(1.2, 2.9, (0.24, 0.60, 0.47, 0.67)), (2.9, 4.6, (0.75, 0.55, 0.95, 0.67))],
        ),
        dict(
            kind="shot",
            start=24,
            end=29,
            tin="zoom",
            tick=(4, n),
            img=PI + "solution-template-result.png",
            card=(1500, 553),
            kicker="05 · VERIFY",
            title="Know it works before students start.",
            sub="Artemis builds template and solution: one fails, one passes all tests.",
            # template half, pan to the solution half, then the whole page
            cam=[
                (0, 0.25, 0.425, 0.52),
                (0.4, 0.25, 0.425, 0.52),
                (0.52, 0.755, 0.425, 0.52, ease_io5),
                (0.76, 0.755, 0.425, 0.52),
                (1, 0.5, 0.5, 1.03, ease_io5),
            ],
            spots=[(0.7, 1.9, (0.015, 0.455, 0.32, 0.53), RED), (2.7, 3.7, (0.52, 0.455, 0.85, 0.53), GREEN)],
        ),
        dict(
            kind="shot",
            start=29,
            end=34,
            tin="slide",
            tick=(5, n),
            layout="side",
            card=(1060, 760),
            card_in="right",
            img=PI + "configure-grading-test-cases.png",
            kicker="06 · GRADE",
            title="Grade with your own tests.",
            sub="Weight test cases, award bonus points, re-evaluate in one click.",
            title_style="words",
            cam=[(0, 0.5, 0.5, 1.02), (0.35, 0.5, 0.5, 1.02), (1, 0.815, 0.535, 0.37, ease_io5)],
            spots=[(0.8, 1.9, (0.78, 0.18, 0.985, 0.225)), (3.7, 4.6, (0.64, 0.555, 0.975, 0.708))],
        ),
        dict(
            kind="shot",
            start=34,
            end=39,
            tin="wipe",
            tick=(6, n),
            layout="side",
            card=(1060, 760),
            img=PI + "configure-grading-code-analysis.png",
            kicker="07 · QUALITY",
            title="Reward clean code.",
            sub="Static code analysis turns quality issues into feedback.",
            card_in="right",
            cam=[(0, 0.29, 0.40, 0.58), (1, 0.29, 0.645, 0.58)],
        ),
        dict(
            kind="shot",
            start=39,
            end=44,
            tin="up",
            tick=(7, n),
            img=PI + "feedback-analysis-overview.png",
            kicker="08 · INSIGHT",
            title="See where students struggle.",
            title_style="words",
            sub="Recurring feedback, grouped across all submissions.",
            cam=[(0, 0.5, 0.31, 1.0), (0.3, 0.5, 0.31, 1.0), (0.8, 0.3, 0.186, 0.6, ease_io5), (1, 0.3, 0.186, 0.6)],
            spots=[(3.2, 4.6, (0.0, 0.04, 0.58, 0.108))],
        ),
        dict(
            kind="shot",
            start=44,
            end=49,
            tin="zoom",
            tick=(8, n),
            img=PI + "online-editor-assessment.png",
            kicker="09 · ASSESS",
            title="Add the human touch.",
            sub="Comment on any line with manual feedback.",
            cam=[(0, 0.45, 0.455, 0.56), (0.4, 0.45, 0.455, 0.56), (1, 0.5, 0.5, 1.03, ease_io5)],
            spots=[(0.7, 2.1, (0.205, 0.49, 0.73, 0.73), GREEN), (3.6, 4.6, (0.0, 0.94, 0.085, 0.995))],
        ),
        dict(
            kind="kinetic",
            start=49,
            end=52.5,
            tin="fade",
            words=[("Create.", WHITE), ("Grade.", WHITE), ("Analyse.", ACCENT)],
            step=0.45,
            title="One module, built for courses of any size.",
            title_t=1.7,
            sub="",
        ),
        dict(
            kind="langs",
            start=52.5,
            end=56,
            tin="zoom",
            title=f"{len(LANGS)} programming languages.",
            title2="One workflow.",
        ),
        dict(
            kind="outro",
            start=56,
            end=60,
            tin="up",
            title="Artemis Programming Exercises",
            sub="Interactive learning with individual feedback — at scale.",
        ),
    ]


# ---------------------------------------------------------------- audio
def make_audio(path, cuts, bpm, sr=48000):
    n = int(DUR * sr)
    tt = np.arange(n) / sr
    out = np.zeros(n, np.float64)

    def hz(m):
        return 440.0 * 2 ** ((m - 69) / 12)

    prog = [[57, 60, 64], [53, 57, 60], [48, 55, 60], [55, 59, 62]]
    bass = [45, 41, 48, 43]
    bar = 60 / bpm * 4
    seg = bar * 2
    k = 0
    while k * seg < DUR:
        s0, s1 = k * seg, (k + 1) * seg + 1.0
        i0, i1 = int(s0 * sr), min(n, int(s1 * sr))
        lt = tt[i0:i1] - s0
        env = np.minimum(1, lt / 0.8) * np.clip((s1 - s0 - lt) / 1.2, 0, 1)
        ch = prog[k % 4]
        sig = np.zeros_like(lt)
        for m in ch + [ch[0] + 12]:
            for det in (-0.12, 0.12):
                f = hz(m + det)
                sig += np.sin(2 * np.pi * f * lt) + 0.25 * np.sin(4 * np.pi * f * lt)
        sig += 1.5 * np.sin(2 * np.pi * hz(bass[k % 4]) * lt)
        out[i0:i1] += 0.035 * sig * env
        k += 1
    step = 60 / bpm / 2
    t0, j = 2.0, 0
    while t0 < DUR - 2.5:
        ch = prog[int(t0 / seg) % 4]
        notes = ch + [ch[0] + 12, ch[1] + 12, ch[2]]
        m = notes[j % len(notes)] + 12
        i0 = int(t0 * sr)
        lt = np.arange(min(int(0.6 * sr), n - i0)) / sr
        f = hz(m)
        pl = (np.sin(2 * np.pi * f * lt) + 0.3 * np.sin(6 * np.pi * f * lt)) * np.exp(-lt * 7)
        out[i0 : i0 + len(lt)] += (0.045 + 0.04 * min(1, t0 / 50)) * pl
        t0 += step
        j += 1
    # soft hi-hat on off-beats after the intro for more drive
    rng = np.random.default_rng(3)
    t0 = 4.0 + step
    while t0 < DUR - 4:
        i0 = int(t0 * sr)
        length = int(0.05 * sr)
        hh = np.diff(rng.standard_normal(length + 1)) * np.exp(-np.arange(length) / sr * 80)
        out[i0 : i0 + length] += 0.012 * hh
        t0 += step * 2
    for s in cuts:
        i0 = int(s * sr)
        lt = np.arange(int(0.5 * sr)) / sr
        f = 50 + 90 * np.exp(-lt * 30)
        out[i0 : i0 + len(lt)] += 0.35 * np.sin(2 * np.pi * np.cumsum(f) / sr) * np.exp(-lt * 9)
        # whoosh just before the cut
        length = int(0.45 * sr)
        a0 = max(0, i0 - length)
        noise = np.convolve(rng.standard_normal(i0 - a0), np.ones(12) / 12, "same")
        out[a0:i0] += 0.06 * noise * np.linspace(0, 1, i0 - a0) ** 2
    out *= np.clip(tt / 1.0, 0, 1) * np.clip((DUR - tt) / 2.5, 0, 1)
    st = np.stack([out, np.roll(out, int(0.012 * sr))], 1)
    st = st / max(1e-9, np.abs(st).max()) * 0.85
    with wave.open(path, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes((st * 32767).astype(np.int16).tobytes())


def render(cut, out, preview):
    scenes = student() if cut == "student" else instructor()
    if preview:
        out = out or REPO / "build" / "trailers"
        out.mkdir(parents=True, exist_ok=True)
        for ts in preview:
            path = out / f"preview-{cut}-{ts:05.2f}.png"
            frame_at(ts, scenes).resize((960, 540)).save(path)
            print(path)
        return
    mp4 = out / f"artemis-programming-exercises-{cut}.mp4" if out else VIDEO_PATHS[cut]
    mp4.parent.mkdir(parents=True, exist_ok=True)
    wav = mp4.with_suffix(".wav")
    make_audio(str(wav), [sc["start"] for sc in scenes[1:]], 104 if cut == "student" else 96)
    # CRF 30 with the animation tuning keeps the screenshot text sharp at about 5 MB per trailer.
    # The flat backgrounds and slow camera moves compress well, so a higher quality barely shows.
    encoder = "-c:v libx264 -preset veryslow -tune animation -crf 30 -pix_fmt yuv420p -c:a aac -b:a 96k"
    ff = subprocess.Popen(
        ["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}"]
        + ["-r", str(FPS), "-i", "-", "-i", str(wav), *encoder.split(), "-shortest", "-movflags", "+faststart"]
        + [str(mp4)],
        stdin=subprocess.PIPE,
    )
    total = int(DUR * FPS)
    for i in range(total):
        ff.stdin.write(frame_at(i / FPS, scenes).tobytes())
        if i % 300 == 0:
            print(f"{cut}: frame {i}/{total}", flush=True)
    ff.stdin.close()
    if ff.wait() != 0:
        sys.exit(f"ffmpeg failed for the {cut} trailer")
    wav.unlink()
    print(f"{cut}: {mp4} ({mp4.stat().st_size // 1000} kB)")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--cut", choices=["student", "instructor", "all"], default="all")
    parser.add_argument(
        "--out", type=Path, help="output directory; by default the videos replace the ones the documentation embeds"
    )
    parser.add_argument("--preview", help="comma-separated timestamps in seconds; writes stills instead of a video")
    parser.add_argument("--font", default=FONTS["sans"], help="font file or collection for the text")
    parser.add_argument("--mono", default=FONTS["mono"], help="monospaced font file for code and the terminal")
    args = parser.parse_args()
    FONTS.update(sans=args.font, mono=args.mono)
    for required in FONTS.values():
        if not Path(required).exists():
            sys.exit(f"Font {required} not found. Pass another font file with --font or --mono.")
    if not args.preview and shutil.which("ffmpeg") is None:
        sys.exit("ffmpeg is required to encode the trailers.")
    preview = [float(t) for t in args.preview.split(",")] if args.preview else None
    for cut in ["student", "instructor"] if args.cut == "all" else [args.cut]:
        render(cut, args.out, preview)


if __name__ == "__main__":
    main()
