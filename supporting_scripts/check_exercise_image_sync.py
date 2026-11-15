#!/usr/bin/env python3
"""Fails when the Java or Kotlin templates pin dependency versions their default image does not hold.

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
        print(f"::error title=The {language} image is out of sync::the {language} templates pin versions that {image} does not hold")
        for problem in problems:
            print(f"  {problem}")
    if failed:
        print(
            "\nEvery build of such an exercise downloads the difference. Update the template project in the image repository "
            "(the same versions as the templates), publish a new image tag and set it in application.yml, "
            "or revert the template change."
        )
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
