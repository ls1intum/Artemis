"""Verify the Angular entry point served by the deployable WAR."""

import re
import sys
from html.parser import HTMLParser
from zipfile import BadZipFile, ZipFile

STATIC = "WEB-INF/classes/static/"
MAIN_SOURCE = re.compile(r"(?:/|\./)?(main-[a-zA-Z0-9_-]+\.js)(?:[?#].*)?")
# Retain the existing heuristic: the observed optimized bundle averages about
# 700 bytes per line, while the unoptimized bundle averages about 60.
MIN_AVERAGE_LINE_BYTES = 200


class MainScripts(HTMLParser):
    def __init__(self):
        super().__init__()
        self.bundles = []

    def handle_starttag(self, tag, attrs):
        if tag == "script":
            source = dict(attrs).get("src")
            match = MAIN_SOURCE.fullmatch(source or "")
            if match:
                self.bundles.append(match[1])


def verify(war):
    with ZipFile(war) as archive:
        entries = archive.namelist()
        index = STATIC + "index.html"
        if entries.count(index) != 1:
            raise ValueError(f"Expected exactly one {index} in the WAR")

        scripts = MainScripts()
        scripts.feed(archive.read(index).decode("utf-8"))
        scripts.close()
        if len(scripts.bundles) != 1:
            raise ValueError(f"Expected index.html to reference exactly one main-*.js script, found {len(scripts.bundles)}")

        bundle = STATIC + scripts.bundles[0]
        if entries.count(bundle) != 1:
            raise ValueError(f"Expected exactly one entry for the main script referenced by index.html ({bundle})")
        content = archive.read(bundle)

    lines = len(content.splitlines())
    if lines == 0 or len(content) < MIN_AVERAGE_LINE_BYTES * lines:
        raise ValueError(
            f"{scripts.bundles[0]} is not minified ({len(content)} bytes in {lines} lines). "
            "Build the client with 'pnpm run webapp:prod'."
        )


if __name__ == "__main__":
    war = sys.argv[1]
    try:
        verify(war)
    except (BadZipFile, OSError, ValueError) as error:
        print(f"::error::{war}: {error}")
        sys.exit(1)
