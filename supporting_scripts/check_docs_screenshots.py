#!/usr/bin/env python3
"""Keep the documentation screenshots in step with the application.

A screenshot is the part of a page that goes stale fastest and that no text check can see. The screenshots that can be
rendered again from the demo data are listed in documentation/screenshots/manifest.json. This script has three jobs:

  (default)       Validate the manifest. Every entry names an image that exists, is shown by every page that it lists,
                  has a route with a known placeholder, a known user and a viewport, and every `source` path exists.
                  Reports how many images of the documentation are not covered yet (information, not a failure).
  --compare DIR   Compare the images that src/test/playwright/docs-screenshots rendered into DIR with the checked-in
                  ones and list those that differ by more than --threshold (a share of the pixels, default 1 percent).
  --stale         List the manifest images whose `source` files (the components and translations that the screenshot
                  shows) were committed after the image. Needs the git history of the files.

Only the Python standard library is used. PNG files are decoded with zlib: 8-bit grey, grey with alpha, RGB, RGBA and
palette images without interlacing, which covers what browsers and screenshot tools write.

Usage:
    python3 supporting_scripts/check_docs_screenshots.py
    python3 supporting_scripts/check_docs_screenshots.py --compare build/docs-screenshots
    python3 supporting_scripts/check_docs_screenshots.py --stale
    python3 supporting_scripts/check_docs_screenshots.py --self-test
"""

from __future__ import annotations

import argparse
import json
import re
import struct
import subprocess
import sys
import tempfile
import zlib
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
DOCS_ROOT = REPO_ROOT / "documentation" / "docs"
MANIFEST = REPO_ROOT / "documentation" / "screenshots" / "manifest.json"
IMAGE_SUFFIXES = {".png", ".jpg", ".jpeg", ".gif", ".webp"}
# {courseId} is the demo course. {exercise:Title}, {exam:Title} and {lecture:Title} are the id of the item of the demo course with that title.
PLACEHOLDERS = {"courseId": False, "exercise": True, "exam": True, "lecture": True}
PLACEHOLDER = re.compile(r"\{([A-Za-z]+)(?::([^}]*))?\}")
IMPORT_PATH = re.compile(r"""(?:from|require\()\s*['"]([^'"]+\.(?:png|jpe?g|gif|webp))['"]""", re.IGNORECASE)
PNG_SIGNATURE =b"\x89PNG\r\n\x1a\n"
# A channel value may differ by this much before a pixel counts as changed: anti-aliasing differs between machines.
CHANNEL_TOLERANCE = 8
DEFAULT_THRESHOLD = 0.01
# A browser rounds the size of an element to whole pixels, and the images that were cropped by hand are often one pixel off. A size that
# differs by at most this many pixels is compared on the area that both images have.
SIZE_TOLERANCE = 2


# ---------------------------------------------------------------------------------------------------------------------
# Manifest
# ---------------------------------------------------------------------------------------------------------------------


def load_manifest(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def page_text(docs_root: Path, page: str) -> str | None:
    path = docs_root / page
    return path.read_text(encoding="utf-8") if path.is_file() else None


def page_imports(docs_root: Path, page: str, text: str, image: str) -> bool:
    """Whether the page imports exactly this image, as in `import shot from './assets/shot.png'`, and not a file of the same name elsewhere or a mention in prose."""
    target = (docs_root / image).resolve()
    return any((docs_root / page).parent.joinpath(reference).resolve() == target for reference in IMPORT_PATH.findall(text))


def validate(manifest: dict, repo_root: Path, docs_root: Path) -> list[str]:
    """Returns the problems of a manifest, empty when it is sound."""
    problems: list[str] = []
    users = manifest.get("users")
    if not isinstance(users, dict) or not users:
        problems.append("`users` must map a role name to a login")
        users = {}
    entries = manifest.get("screenshots")
    if not isinstance(entries, list) or not entries:
        return problems + ["`screenshots` must be a non-empty list"]
    ids: set[str] = set()
    images: set[str] = set()
    for position, entry in enumerate(entries):
        label = entry.get("id", f"#{position}") if isinstance(entry, dict) else f"#{position}"
        if not isinstance(entry, dict):
            problems.append(f"entry {label}: must be an object")
            continue
        for key in ("id", "image", "pages", "user", "route"):
            if key not in entry:
                problems.append(f"{label}: missing `{key}`")
        if "id" in entry:
            if not isinstance(entry["id"], str) or not re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*", entry["id"]):
                problems.append(f"{label}: `id` must be a kebab-case string")
            elif entry["id"] in ids:
                problems.append(f"{label}: duplicate id")
            else:
                ids.add(entry["id"])
        image = entry.get("image")
        if "image" in entry and not isinstance(image, str):
            problems.append(f"{label}: `image` must be a path below documentation/docs")
        if isinstance(image, str):
            if image in images:
                problems.append(f"{label}: `{image}` is described twice")
            images.add(image)
            if Path(image).is_absolute() or ".." in Path(image).parts:
                problems.append(f"{label}: `{image}` must be a path below documentation/docs, without `..`")
            elif Path(image).suffix.lower() not in IMAGE_SUFFIXES:
                problems.append(f"{label}: `{image}` is not an image file")
            elif not (docs_root / image).is_file():
                problems.append(f"{label}: image `{image}` does not exist below documentation/docs")
        pages = entry.get("pages")
        if "pages" in entry and (not isinstance(pages, list) or not pages):
            problems.append(f"{label}: `pages` must list at least one page")
        elif isinstance(pages, list) and isinstance(image, str):
            for page in pages:
                text = page_text(docs_root, page)
                if text is None:
                    problems.append(f"{label}: page `{page}` does not exist")
                elif not page_imports(docs_root, page, text, image):
                    problems.append(f"{label}: page `{page}` does not import `{image}`")
        if "user" in entry and entry["user"] not in users:
            problems.append(f"{label}: unknown user `{entry['user']}`")
        route = entry.get("route")
        if "route" in entry and not isinstance(route, str):
            problems.append(f"{label}: `route` must be a string")
        if isinstance(route, str):
            outside_placeholders = PLACEHOLDER.sub("", route)
            if not route.startswith("/") or re.search(r"\s", outside_placeholders):
                problems.append(f"{label}: `route` must start with / and contain no spaces outside a placeholder")
            if "{" in outside_placeholders or "}" in outside_placeholders:
                problems.append(f"{label}: `route` has a brace that is not part of a placeholder such as {{courseId}}")
            for name, argument in PLACEHOLDER.findall(route):
                if name not in PLACEHOLDERS:
                    problems.append(f"{label}: unknown placeholder {{{name}}} in the route")
                elif PLACEHOLDERS[name] and not argument:
                    problems.append(f"{label}: placeholder {{{name}}} needs the title of the item, as in {{{name}:Title}}")
                elif not PLACEHOLDERS[name] and argument:
                    problems.append(f"{label}: placeholder {{{name}}} takes no title")
        for key in ("viewport",):
            viewport = entry.get(key)
            if viewport is not None and not (isinstance(viewport, dict) and all(isinstance(viewport.get(side), int) and viewport[side] > 0 for side in ("width", "height"))):
                problems.append(f"{label}: `{key}` needs a positive integer width and height")
        scale = entry.get("deviceScaleFactor")
        if scale is not None and (not isinstance(scale, (int, float)) or scale <= 0):
            problems.append(f"{label}: `deviceScaleFactor` must be positive")
        clip = entry.get("clip")
        if clip is not None and not (isinstance(clip, dict) and set(clip) == {"x", "y", "width", "height"} and all(isinstance(v, int) and v >= 0 for v in clip.values()) and clip["width"] > 0 and clip["height"] > 0):
            problems.append(f"{label}: `clip` needs the integers x, y, width and height")
        if clip is not None and entry.get("selector"):
            problems.append(f"{label}: use `selector` or `clip`, not both")
        limit = entry.get("threshold")
        if limit is not None and not (isinstance(limit, (int, float)) and 0 < limit <= 1):
            problems.append(f"{label}: `threshold` must be a share of the pixels between 0 and 1")
        if limit is not None and not str(entry.get("note", "")).strip():
            problems.append(f"{label}: a `threshold` of its own needs a `note` that says why the image varies")
        for key in ("selector", "waitFor"):
            if key in entry and not (isinstance(entry[key], str) and entry[key].strip()):
                problems.append(f"{label}: `{key}` must be a CSS selector")
        padding = entry.get("padding")
        if padding is not None and (not isinstance(padding, int) or isinstance(padding, bool) or padding <= 0 or not entry.get("selector")):
            problems.append(f"{label}: `padding` needs a positive integer and a `selector`")
        steps = entry.get("steps", [])
        if not isinstance(steps, list):
            problems.append(f"{label}: `steps` must be a list")
            steps = []
        for number, step in enumerate(steps, 1):
            problems.extend(f"{label}: step {number} {problem}" for problem in step_problems(step))
            upload = step.get("upload") if isinstance(step, dict) else None
            if isinstance(upload, dict) and isinstance(upload.get("file"), str):
                upload_path = Path(upload["file"])
                if upload_path.is_absolute() or ".." in upload_path.parts:
                    problems.append(f"{label}: step {number} uploads `{upload['file']}`, which must be a path below the repository root, without `..`")
                elif not (repo_root / upload_path).is_file():
                    problems.append(f"{label}: step {number} uploads `{upload['file']}`, which does not exist")
        for key in ("source", "mask", "maskSelector", "hide"):
            values = entry.get(key, [])
            if not isinstance(values, list) or not all(isinstance(value, str) for value in values):
                problems.append(f"{label}: `{key}` must be a list of strings")
        for source in entry.get("source", []) if isinstance(entry.get("source"), list) else []:
            if isinstance(source, str) and not (repo_root / source).exists():
                problems.append(f"{label}: source `{source}` does not exist")
    return problems


STEP_FIELDS = {"fill": ("selector", "value"), "select": ("selector", "label"), "click": None, "clickIfPresent": None, "check": None, "upload": ("selector", "file"), "waitFor": None}


def step_problems(step: object) -> list[str]:
    """The problems of one step of an entry: exactly one action, with the strings that it needs."""
    if not isinstance(step, dict):
        return ["must be an object"]
    actions = [key for key in step if key in STEP_FIELDS]
    if len(actions) != 1 or len(step) != 1:
        return [f"needs exactly one of {', '.join(STEP_FIELDS)}"]
    action = actions[0]
    value = step[action]
    fields = STEP_FIELDS[action]
    if fields is None:
        return [] if isinstance(value, str) and value.strip() else [f"`{action}` must be a selector"]
    if not isinstance(value, dict) or set(value) != set(fields) or not all(isinstance(value[field], str) and value[field].strip() for field in fields):
        return [f"`{action}` needs the strings {' and '.join(fields)}"]
    return []


def uncovered_images(manifest: dict, docs_root: Path) -> tuple[int, int]:
    """Returns (images in the documentation, images the manifest covers)."""
    present = {path.relative_to(docs_root).as_posix() for path in docs_root.rglob("*") if path.is_file() and path.suffix.lower() in IMAGE_SUFFIXES}
    covered = {entry["image"] for entry in manifest.get("screenshots", []) if isinstance(entry, dict) and "image" in entry}
    return len(present), len(present & covered)


# ---------------------------------------------------------------------------------------------------------------------
# PNG comparison
# ---------------------------------------------------------------------------------------------------------------------

CHANNELS = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}


def png_size(data: bytes) -> tuple[int, int]:
    if not data.startswith(PNG_SIGNATURE):
        raise ValueError("not a PNG file")
    if len(data) < 24:
        raise ValueError("truncated PNG file")
    return struct.unpack(">II", data[16:24])


def decode_png(data: bytes) -> tuple[int, int, int, list[bytes]]:
    """Returns (width, height, channels, rows) of an 8-bit, non-interlaced PNG. Palette images are expanded to RGB."""
    width, height = png_size(data)
    position = 8
    idat = b""
    palette = b""
    depth = color = interlace = None
    while position < len(data):
        length, kind = struct.unpack(">I4s", data[position : position + 8])
        body = data[position + 8 : position + 8 + length]
        if kind == b"IHDR":
            depth, color, _, _, interlace = struct.unpack(">5B", body[8:13])
        elif kind == b"PLTE":
            palette = body
        elif kind == b"IDAT":
            idat += body
        position += 12 + length
    if depth != 8 or interlace != 0 or color not in CHANNELS:
        raise ValueError(f"unsupported PNG (bit depth {depth}, colour type {color}, interlace {interlace})")
    channels = CHANNELS[color]
    stride = width * channels
    raw = zlib.decompress(idat)
    rows: list[bytes] = []
    previous = bytearray(stride)
    offset = 0
    for _ in range(height):
        method = raw[offset]
        line = bytearray(raw[offset + 1 : offset + 1 + stride])
        offset += 1 + stride
        if method == 1:
            for i in range(channels, stride):
                line[i] = (line[i] + line[i - channels]) & 255
        elif method == 2:
            for i in range(stride):
                line[i] = (line[i] + previous[i]) & 255
        elif method == 3:
            for i in range(stride):
                left = line[i - channels] if i >= channels else 0
                line[i] = (line[i] + ((left + previous[i]) >> 1)) & 255
        elif method == 4:
            for i in range(stride):
                left = line[i - channels] if i >= channels else 0
                up = previous[i]
                corner = previous[i - channels] if i >= channels else 0
                estimate = left + up - corner
                distance_left, distance_up, distance_corner = abs(estimate - left), abs(estimate - up), abs(estimate - corner)
                if distance_left <= distance_up and distance_left <= distance_corner:
                    predictor = left
                elif distance_up <= distance_corner:
                    predictor = up
                else:
                    predictor = corner
                line[i] = (line[i] + predictor) & 255
        elif method != 0:
            raise ValueError(f"unknown PNG filter {method}")
        previous = line
        rows.append(bytes(line))
    if color == 3:
        expanded = []
        for row in rows:
            out = bytearray()
            for index in row:
                out += palette[index * 3 : index * 3 + 3]
            expanded.append(bytes(out))
        return width, height, 3, expanded
    return width, height, channels, rows


def to_rgb(rows: list[bytes], channels: int) -> list[bytes]:
    """Rows of grey, grey with alpha, RGB or RGBA pixels as rows of RGB pixels. Transparency is ignored."""
    if channels == 3:
        return rows
    converted = []
    for row in rows:
        out = bytearray()
        for start in range(0, len(row), channels):
            if channels >= 3:
                out += row[start : start + 3]
            else:
                out += bytes((row[start],)) * 3
        converted.append(bytes(out))
    return converted


def changed_share(old: bytes, new: bytes, size_tolerance: int = SIZE_TOLERANCE) -> float | str:
    """The share of pixels that differ, or a text when the images cannot be compared pixel by pixel.

    Sizes that differ by at most `size_tolerance` pixels in each direction are compared on the overlap, and the pixels outside it count as changed."""
    if old == new:
        return 0.0
    try:
        old_size, new_size = png_size(old), png_size(new)
    except (ValueError, struct.error) as error:
        return f"not comparable ({error})"
    if abs(old_size[0] - new_size[0]) > size_tolerance or abs(old_size[1] - new_size[1]) > size_tolerance:
        return f"size {new_size[0]}x{new_size[1]} instead of {old_size[0]}x{old_size[1]}"
    try:
        old_width, old_height, old_channels, old_rows = decode_png(old)
        new_width, new_height, new_channels, new_rows = decode_png(new)
    except (ValueError, struct.error, zlib.error) as error:
        return f"not comparable ({error})"
    # A screenshot tool writes RGB, an image editor RGBA or grey: compare the colours only.
    old_rows, new_rows = to_rgb(old_rows, old_channels), to_rgb(new_rows, new_channels)
    width, height = min(old_width, new_width), min(old_height, new_height)
    changed = 0
    for old_row, new_row in zip(old_rows[:height], new_rows[:height]):
        if old_row[: width * 3] == new_row[: width * 3]:
            continue
        for start in range(0, width * 3, 3):
            for channel in range(3):
                if abs(old_row[start + channel] - new_row[start + channel]) > CHANNEL_TOLERANCE:
                    changed += 1
                    break
    union = max(old_width, new_width) * max(old_height, new_height)
    return (changed + union - width * height) / union


def compare(manifest: dict, rendered: Path, docs_root: Path, threshold: float, show_all: bool = False, only: set[str] | None = None) -> tuple[list[str], int]:
    """Returns the Markdown lines about the images that differ and the number of images that were compared.

    With `show_all` every compared image gets a line with its similarity, which is what a review of the thresholds needs.
    With `only` the entries of other ids are skipped, as the renderer skipped them too."""
    lines: list[str] = []
    compared = 0
    for entry in manifest["screenshots"]:
        if only is not None and entry["id"] not in only:
            continue
        new_path = rendered / entry["image"]
        old_path = docs_root / entry["image"]
        if not new_path.is_file():
            lines.append(f"- `{entry['image']}`: was not rendered")
            continue
        compared += 1
        result = changed_share(old_path.read_bytes(), new_path.read_bytes())
        pages = ", ".join(f"`{page}`" for page in entry["pages"])
        limit = max(threshold, entry.get("threshold", 0))
        if isinstance(result, str):
            lines.append(f"- `{entry['image']}` ({pages}): {result}")
        elif result > limit:
            lines.append(f"- `{entry['image']}` ({pages}): similarity {1 - result:.1%}, {result:.1%} of the pixels changed")
        elif show_all:
            lines.append(f"- `{entry['image']}` ({pages}): similarity {1 - result:.1%}, within the threshold of {limit:.1%}")
    return lines, compared


# ---------------------------------------------------------------------------------------------------------------------
# Age
# ---------------------------------------------------------------------------------------------------------------------


def last_commit(path: str) -> int | None:
    result = subprocess.run(["git", "log", "-1", "--format=%ct", "--", path], cwd=REPO_ROOT, capture_output=True, text=True, check=False)
    if result.returncode != 0:
        # Without a usable history every image would count as current, which is a wrong answer and not a clean result.
        raise SystemExit(f"git log failed for {path} (does the checkout have its history?): {result.stderr.strip()}")
    text = result.stdout.strip()
    return int(text) if text else None


def stale(manifest: dict) -> list[str]:
    lines: list[str] = []
    for entry in manifest["screenshots"]:
        image_time = last_commit(f"documentation/docs/{entry['image']}")
        if image_time is None:
            continue
        newer = [source for source in entry.get("source", []) if (changed := last_commit(source)) is not None and changed > image_time]
        if newer:
            lines.append(f"- `{entry['image']}`: changed after the image: {', '.join(f'`{source}`' for source in newer)}")
    return lines


# ---------------------------------------------------------------------------------------------------------------------
# Self-test
# ---------------------------------------------------------------------------------------------------------------------


def make_png(width: int, height: int, pixel, color: int = 2, method: int = 0) -> bytes:
    """Writes a PNG whose rows all use the given filter, so the decoder is exercised on each of them."""
    channels = CHANNELS[color]
    rows = [bytes(value for x in range(width) for value in pixel(x, y)) for y in range(height)]
    raw = b""
    previous = bytes(width * channels)
    for row in rows:
        out = bytearray()
        for i, value in enumerate(row):
            left = row[i - channels] if i >= channels else 0
            up = previous[i]
            corner = previous[i - channels] if i >= channels else 0
            if method == 0:
                predictor = 0
            elif method == 1:
                predictor = left
            elif method == 2:
                predictor = up
            elif method == 3:
                predictor = (left + up) >> 1
            else:
                estimate = left + up - corner
                distances = (abs(estimate - left), abs(estimate - up), abs(estimate - corner))
                predictor = (left, up, corner)[distances.index(min(distances))]
            out.append((value - predictor) & 255)
        raw += bytes([method]) + bytes(out)
        previous = row

    def chunk(kind: bytes, body: bytes) -> bytes:
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body))

    header = struct.pack(">IIBBBBB", width, height, 8, color, 0, 0, 0)
    return PNG_SIGNATURE + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")


def self_test() -> None:
    def gradient(x, y):
        return (x * 20 % 256, y * 30 % 256, (x + y) * 10 % 256)

    for method in range(5):
        data = make_png(9, 7, gradient, method=method)
        width, height, channels, rows = decode_png(data)
        assert (width, height, channels) == (9, 7, 3), method
        assert rows[3][:3] == bytes(gradient(0, 3)), method
        assert rows[6][-3:] == bytes(gradient(8, 6)), method
    rgba = make_png(4, 4, lambda x, y: (x, y, 0, 255), color=6, method=4)
    assert decode_png(rgba)[2] == 4

    same = make_png(20, 10, gradient)
    assert changed_share(same, make_png(20, 10, gradient, method=2)) == 0.0, "the filter must not change the pixels"
    one_pixel = make_png(20, 10, lambda x, y: (255, 0, 0) if (x, y) == (3, 3) else gradient(x, y))
    assert abs(changed_share(same, one_pixel) - 1 / 200) < 1e-9
    nearly = make_png(20, 10, lambda x, y: tuple(min(255, value + 3) for value in gradient(x, y)))
    assert changed_share(same, nearly) == 0.0, "differences inside the tolerance are anti-aliasing"
    assert changed_share(same, make_png(20, 10, lambda x, y: (*gradient(x, y), 255), color=6)) == 0.0, "RGBA and RGB images are compared by their colours"
    assert changed_share(make_png(4, 4, lambda x, y: (200,), color=0), make_png(4, 4, lambda x, y: (200, 200, 200))) == 0.0, "grey and RGB images are compared by their colours"
    assert isinstance(changed_share(same, make_png(30, 10, gradient)), str), "a very different size is reported as text"
    one_wider = changed_share(same, make_png(21, 10, gradient))
    assert isinstance(one_wider, float) and abs(one_wider - 10 / 210) < 1e-9, "a size that is one pixel off is compared on the overlap"

    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        docs = root / "docs"
        (docs / "area" / "assets").mkdir(parents=True)
        (docs / "area" / "assets" / "shot.png").write_bytes(same)
        (docs / "area" / "page.mdx").write_text("import shot from './assets/shot.png';\n", encoding="utf-8")
        (docs / "area" / "assets" / "other.png").write_bytes(same)
        (root / "src").mkdir()
        entry = {"id": "shot", "image": "area/assets/shot.png", "pages": ["area/page.mdx"], "user": "student", "route": "/courses/{courseId}/x", "source": ["src"]}
        good = {"users": {"student": "demo_student"}, "screenshots": [entry]}
        assert validate(good, root, docs) == [], validate(good, root, docs)
        assert uncovered_images(good, docs) == (2, 1)

        def broken(**changes) -> list[str]:
            return validate({"users": good["users"], "screenshots": [{**entry, **changes}]}, root, docs)

        assert any("does not exist below" in problem for problem in broken(image="area/assets/missing.png"))
        assert any("does not import" in problem for problem in broken(image="area/assets/other.png")), "a page that does not import the image fails"
        (docs / "area" / "prose.mdx").write_text("The picture shot.png is described here, but not shown.\n", encoding="utf-8")
        assert any("does not import" in problem for problem in broken(pages=["area/prose.mdx"])), "a mention of the file name in prose is not an import"
        (docs / "elsewhere" / "assets").mkdir(parents=True)
        (docs / "elsewhere" / "assets" / "shot.png").write_bytes(same)
        (docs / "elsewhere" / "page.mdx").write_text("import shot from './assets/shot.png';\n", encoding="utf-8")
        assert any("does not import" in problem for problem in broken(pages=["elsewhere/page.mdx"])), "a file of the same name in another folder is another image"
        assert any("without `..`" in problem for problem in broken(image="../docs/area/assets/shot.png"))
        assert any("without `..`" in problem for problem in broken(image=str(docs / "area" / "assets" / "shot.png")))
        assert any("kebab-case string" in problem for problem in broken(id=["shot"])), "an id that is not hashable is a problem, not a traceback"
        assert any("`route` must be a string" in problem for problem in broken(route=None))
        assert any("`route` must be a string" in problem for problem in broken(route=12))
        assert any("brace" in problem for problem in broken(route="/courses/{course_id}/x"))
        assert any("page `nope.mdx` does not exist" in problem for problem in broken(pages=["nope.mdx"]))
        assert any("unknown user" in problem for problem in broken(user="admin"))
        assert any("unknown placeholder" in problem for problem in broken(route="/courses/{exerciseId}"))
        assert any("needs the title" in problem for problem in broken(route="/courses/{courseId}/exercises/{exercise}"))
        assert any("takes no title" in problem for problem in broken(route="/courses/{courseId:x}"))
        assert broken(route="/courses/{courseId}/exercises/{exercise:Essay: The Value}") == []
        assert any("`clip`" in problem for problem in broken(clip={"x": 0, "y": 0}))
        assert any("not both" in problem for problem in broken(clip={"x": 0, "y": 0, "width": 5, "height": 5}, selector="main"))
        assert broken(selector="main", maskSelector=[".time"]) == []
        assert any("between 0 and 1" in problem for problem in broken(threshold=2, note="x"))
        assert any("needs a `note`" in problem for problem in broken(threshold=0.05))
        assert broken(threshold=0.05, note="a graph with a random layout") == []
        assert any("must start with /" in problem for problem in broken(route="courses/1"))
        assert any("source `gone` does not exist" in problem for problem in broken(source=["gone"]))
        assert broken(steps=[{"fill": {"selector": "#title", "value": "Demo"}}, {"click": "text=Save"}, {"select": {"selector": "select", "label": "WS26/27"}}, {"waitFor": "main"}]) == []
        assert any("step 1 needs exactly one" in problem for problem in broken(steps=[{"click": "a", "waitFor": "b"}])), "a step does two things"
        assert any("step 1 needs exactly one" in problem for problem in broken(steps=[{"hover": "a"}])), "an unknown action is a problem"
        assert any("step 2 `fill` needs" in problem for problem in broken(steps=[{"click": "a"}, {"fill": {"selector": "a"}}])), "a fill without a value"
        assert any("`steps` must be a list" in problem for problem in broken(steps="click"))
        assert any("uploads `gone.png`" in problem for problem in broken(steps=[{"upload": {"selector": "input", "file": "gone.png"}}]))
        assert any("uploads `src`" in problem for problem in broken(steps=[{"upload": {"selector": "input", "file": "src"}}])), "a directory is not a file"
        assert any("without `..`" in problem for problem in broken(steps=[{"upload": {"selector": "input", "file": "../gone.png"}}])), "an upload stays below the repository"
        assert broken(selector="main", padding=12) == []
        assert any("`padding`" in problem for problem in broken(padding=12)), "padding without a selector"
        assert any("`padding`" in problem for problem in broken(selector="main", padding=0))
        assert any("viewport" in problem for problem in broken(viewport={"width": 0, "height": 5}))
        assert any("kebab-case" in problem for problem in broken(id="Shot_1"))
        assert any("described twice" in problem or "duplicate id" in problem for problem in validate({"users": good["users"], "screenshots": [entry, entry]}, root, docs))

        rendered = root / "rendered"
        (rendered / "area" / "assets").mkdir(parents=True)
        (rendered / "area" / "assets" / "shot.png").write_bytes(one_pixel)
        lines, compared = compare(good, rendered, docs, 0.001)
        assert compared == 1 and len(lines) == 1 and "0.5%" in lines[0], lines
        assert compare(good, rendered, docs, 0.01)[0] == []
        other = {**entry, "id": "other", "image": "area/assets/other.png"}
        both = {"users": good["users"], "screenshots": [entry, other]}
        assert "was not rendered" in compare(both, rendered, docs, 0.01)[0][0], "without --only an entry that was not rendered is reported"
        assert compare(both, rendered, docs, 0.01, only={"shot"}) == ([], 1), "with --only the entries of other ids are skipped"
        (rendered / "area" / "assets" / "other.png").write_bytes(same[:20])
        truncated = compare(both, rendered, docs, 0.01, only={"other"})[0]
        assert len(truncated) == 1 and "not comparable" in truncated[0], "a PNG that is cut off is reported, not raised"
    with tempfile.TemporaryDirectory() as directory:
        original_root = globals()["REPO_ROOT"]
        globals()["REPO_ROOT"] = Path(directory)
        try:
            last_commit("anything")
            raise AssertionError("--stale outside a git checkout must fail instead of reporting nothing")
        except SystemExit as failure:
            assert "git log failed" in str(failure)
        finally:
            globals()["REPO_ROOT"] = original_root
    ids = {"screenshots": [{"id": "one"}, {"id": "two"}]}
    assert select_ids(ids, "one") == {"one"} and select_ids(ids, " one, two ") == {"one", "two"}
    for bad_selection in ("one,tow", "tow", "", " , "):
        try:
            select_ids(ids, bad_selection)
        except SystemExit as failure:
            assert "--only" in str(failure), bad_selection
            continue
        raise AssertionError(f"--only {bad_selection!r} must be rejected")
    # A new entry has no image yet: the selection of its id is fine, and only the validation of the manifest reports the missing image.
    new_entry = {"id": "brand-new", "image": "area/assets/not-rendered-yet.png", "pages": ["area/page.mdx"], "user": "student", "route": "/courses/{courseId}/x", "source": []}
    with tempfile.TemporaryDirectory() as directory:
        manifest_file = Path(directory) / "manifest.json"
        manifest_file.write_text(json.dumps({"users": {"student": "demo_student"}, "screenshots": [new_entry]}), encoding="utf-8")
        original_manifest, original_argv = globals()["MANIFEST"], sys.argv
        globals()["MANIFEST"], sys.argv = manifest_file, ["check_docs_screenshots.py", "--only", "brand-new"]
        try:
            assert main() == 0, "the preflight of --only <new id> has to pass before the first image exists"
            sys.argv = ["check_docs_screenshots.py", "--only", "brand-new,brand-nwe"]
            try:
                main()
                raise AssertionError("a misspelled id has to stop the preflight")
            except SystemExit as failure:
                assert "brand-nwe" in str(failure)
        finally:
            globals()["MANIFEST"], sys.argv = original_manifest, original_argv
    for bad in ("nan","inf", "-0.1", "1.5", "x"):
        try:
            share(bad)
        except argparse.ArgumentTypeError:
            continue
        raise AssertionError(f"--threshold {bad} must be rejected")
    assert share("0.05") == 0.05
    print("check_docs_screenshots self-test passed")


# ---------------------------------------------------------------------------------------------------------------------


def select_ids(manifest: dict, text: str) -> set[str]:
    """The manifest ids that `--only` asks for. A misspelled id and an empty selection stop the run, because the run would otherwise render or compare less than was asked."""
    requested = [identifier.strip() for identifier in text.split(",") if identifier.strip()]
    if not requested:
        raise SystemExit("--only needs at least one manifest id")
    known = {entry["id"] for entry in manifest["screenshots"]}
    unknown = [identifier for identifier in requested if identifier not in known]
    if unknown:
        raise SystemExit(f"--only names ids that the manifest does not have: {', '.join(unknown)}")
    return set(requested)


def share(text: str) -> float:
    """An argparse type for a share of the pixels: a finite number from 0 to 1."""
    try:
        value = float(text)
    except ValueError:
        raise argparse.ArgumentTypeError(f"{text!r} is not a number") from None
    if not 0 <= value <= 1:
        raise argparse.ArgumentTypeError(f"{text!r} is not a share between 0 and 1")
    return value


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--compare", metavar="DIR", help="compare the images rendered into DIR with the checked-in ones")
    parser.add_argument("--threshold", type=share, default=DEFAULT_THRESHOLD, help="share of changed pixels that counts as a difference (default 0.01)")
    parser.add_argument("--only", metavar="IDS", help="with --compare: compare only these manifest ids, comma separated, as the renderer rendered only them. A misspelled id is an error. Without --compare or --stale, only the ids are checked (the preflight of render-docs-screenshots.sh).")
    parser.add_argument("--all", action="store_true", help="with --compare: also list the images that are within the threshold, with their similarity")
    parser.add_argument("--stale", action="store_true", help="list images whose source files changed after the image")
    parser.add_argument("--self-test", action="store_true", help="run the built-in tests")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return 0
    manifest = load_manifest(MANIFEST)
    only = select_ids(manifest, args.only) if args.only is not None else None
    if only is not None and not args.compare and not args.stale:
        # The preflight of the render script: only the selection is checked. The images of a new entry do not exist before the first rendering,
        # so the manifest itself is validated afterwards, by a run without --only.
        print(f"{len(only)} manifest id(s) selected")
        return 0
    if args.compare:
        lines, compared = compare(manifest, Path(args.compare), DOCS_ROOT, args.threshold, args.all, only)
        differing = [line for line in lines if "within the threshold" not in line]
        print(f"{len(differing)} of {compared} compared screenshot(s) differ (threshold {args.threshold:.1%} of the pixels)")
        print("\n".join(lines))
        return 0
    if args.stale:
        lines = stale(manifest)
        print(f"{len(lines)} screenshot(s) may be outdated")
        print("\n".join(lines))
        return 0
    problems = validate(manifest, REPO_ROOT, DOCS_ROOT)
    total, covered = uncovered_images(manifest, DOCS_ROOT)
    print(f"{covered} of {total} documentation images are described in the screenshot manifest")
    if problems:
        print("\nThe screenshot manifest has problems:", file=sys.stderr)
        for problem in problems:
            print(f"  {problem}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
