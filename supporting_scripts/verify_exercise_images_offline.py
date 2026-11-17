#!/usr/bin/env python3
"""Builds the exercise templates of Java, Kotlin, Rust, JavaScript, TypeScript and Swift in their default images without network access.

Assembles the test and assignment repositories like the server does, runs the build phases of
``src/main/resources/templates/phases`` in the image configured in ``application.yml`` and fails when a build needs
anything the image does not hold. It needs Docker and pulls the images, so it runs nightly, not on every pull request;
``check_exercise_image_sync.py`` is the cheap check for pull requests.

    python3 supporting_scripts/verify_exercise_images_offline.py
    python3 supporting_scripts/verify_exercise_images_offline.py --language kotlin --platform linux/amd64
"""

import argparse
import sys
import tempfile
from pathlib import Path

from exercise_images.assembly import VARIANTS
from exercise_images.images import TEMPLATES_DIR, configured_images
from exercise_images.offline import verify


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--variant", action="append", help="build only this variant (repeatable), see --list")
    parser.add_argument("--language", action="append", choices=sorted({variant.language for variant in VARIANTS}), help="build only the variants of this language (repeatable)")
    parser.add_argument("--list", action="store_true", help="list the variants and exit")
    parser.add_argument("--image", action="append", default=[], metavar="LANGUAGE=IMAGE", help="use this image instead of the configured default")
    parser.add_argument("--platform", help="docker platform, e.g. linux/amd64 for an image that is not published for arm64")
    parser.add_argument("--work-dir", type=Path, help="keep the repositories and logs here (default: a temporary directory)")
    args = parser.parse_args()

    if args.list:
        print("\n".join(variant.name for variant in VARIANTS))
        return 0

    languages = {variant.language for variant in VARIANTS}
    overrides: dict[str, str] = {}
    for item in args.image:
        language, separator, image = item.partition("=")
        image = image.strip()
        if not separator or not image or language not in languages:
            parser.error(f"--image expects LANGUAGE=IMAGE with LANGUAGE one of {sorted(languages)}: {item}")
        overrides[language] = image
    defaults = {language: images["default"] for language, images in configured_images().items() if "default" in images}
    selected = [variant for variant in VARIANTS if (not args.variant or variant.name in args.variant) and (not args.language or variant.language in args.language)]
    unknown = set(args.variant or []) - {variant.name for variant in VARIANTS}
    if unknown:
        parser.error(f"unknown variant: {', '.join(sorted(unknown))}")
    if not selected:
        parser.error("no variants match the selected language and variant filters")

    work_dir = args.work_dir or Path(tempfile.mkdtemp(prefix="exercise-images-"))
    work_dir.mkdir(parents=True, exist_ok=True)
    failed = 0
    for variant in selected:
        image = overrides.get(variant.language, defaults[variant.language])
        print(f"::group::{variant.name} in {image}", flush=True)
        result = verify(TEMPLATES_DIR, variant, image, work_dir, args.platform)
        print(f"{'PASSED' if result.passed else 'FAILED'}: {variant.name} ({result.log})", flush=True)
        if result.attempts > 1:
            print(f"::warning title=Known compiler failure::{variant.name} was built twice, the first build failed with a known intermittent compiler error", flush=True)
        if not result.passed:
            failed += 1
            for line in result.missing[:20] or ["see the log for the failing phase"]:
                print(f"  {line}", flush=True)
            print("::endgroup::", flush=True)
            print(f"::error title=Exercise image is stale::{variant.name} does not build offline in {image}", flush=True)
        else:
            print("::endgroup::", flush=True)

    print(f"{len(selected) - failed} of {len(selected)} variants build offline; logs in {work_dir}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
