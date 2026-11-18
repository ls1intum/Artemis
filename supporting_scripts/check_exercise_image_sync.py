#!/usr/bin/env python3
"""Fails when the exercise templates pin dependency versions their default image does not hold.

Covers Java, Kotlin, Rust, JavaScript, TypeScript, Swift, Ruby, Dart and C#.

Compares the build files of ``src/main/resources/templates`` with the template project of the image configured in
``application.yml``, read from the image repository at the image's tag. It needs no Docker and downloads no dependency, so
it runs on every pull request that touches the templates or the image. ``verify_exercise_images_offline.py`` builds
the exercises in the image and runs nightly.
"""

import sys

from exercise_images.sync_check import check


def main() -> int:
    failed = False
    for language, (image, problems) in check().items():
        if not problems:
            print(f"OK: the {language} templates match {image}")
            continue
        failed = True
        for problem in problems:
            detail = problem.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
            print(f"::error title=The {language} image could not be verified::{detail}")
    if failed:
        print(
            "\nFix any reported fetch errors. For version differences, update the template project in the image repository "
            "(the same versions as the templates), publish a new image tag and set it in application.yml, "
            "or revert the template change."
        )
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
