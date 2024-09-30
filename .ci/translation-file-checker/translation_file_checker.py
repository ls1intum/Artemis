#! /usr/bin/env python3

import argparse
import json
import sys
from collections import Counter
from dataclasses import dataclass
from functools import reduce
from pathlib import Path
from typing import Any, Self


@dataclass
class Config:
    are_files: bool
    german_translations: Path
    english_translations: Path


@dataclass
class Diff:
    all_german_keys: list[str]
    only_german: set[str]
    all_english_keys: list[str]
    only_english: set[str]

    def duplicates(self) -> tuple[list[str], list[str]]:
        def _duplicates(items: list[str]) -> list[str]:
            return [k for k, count in Counter(items).items() if count > 1]

        return _duplicates(self.all_german_keys), _duplicates(self.all_english_keys)

    def __add__(self, other: Self) -> Self:
        return Diff(
            all_german_keys=self.all_german_keys + other.all_german_keys,
            only_german=self.only_german.union(other.only_german),
            all_english_keys=self.all_english_keys + other.all_english_keys,
            only_english=self.only_english.union(other.only_english),
        )

    def __str__(self) -> str:
        german = list(self.only_german)
        german.sort()
        only_german = "\n".join(german)

        english = list(self.only_english)
        english.sort()
        only_english = "\n".join(english)

        return (
            f"Missing English translation keys:\n{only_german}\n\n"
            f"Missing German translation keys:\n{only_english}"
        )

    def __len__(self) -> int:
        return len(self.only_english) + len(self.only_german)


def main(argv: list[str] | None = None) -> int:
    if argv is None:
        argv = sys.argv[1:]

    config = _config_from_args(argv)

    if config.are_files:
        diff = _compare_files(config.german_translations, config.english_translations)
    else:
        diff = _compare_directories(
            config.german_translations, config.english_translations
        )

    if len(diff) > 0:
        print(f"\n{diff}")

    duplicates_german, duplicates_english = diff.duplicates()
    duplicates_exist = len(duplicates_german) > 0 or len(duplicates_english) > 0

    if duplicates_exist:
        _log_duplicate_keys(duplicates_german, duplicates_english)

    if len(diff) == 0 and not duplicates_exist:
        return 0
    else:
        return 1


def _config_from_args(argv: list[str]) -> Config:
    arg_parser = _build_arg_parser()
    args = arg_parser.parse_args(argv)

    german_translations: Path = args.german_files
    english_translations: Path = args.english_files

    if german_translations.is_file() and english_translations.is_file():
        are_files = True
    elif german_translations.is_dir() and english_translations.is_dir():
        are_files = False
    else:
        err = "The paths to the translations must either both be files or both be directories."
        raise ValueError(err)

    return Config(
        are_files=are_files,
        german_translations=german_translations,
        english_translations=english_translations,
    )


def _build_arg_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()

    parser.add_argument(
        "--german-files",
        type=Path,
        required=True,
        help="Directory containing the German translation files.",
    )
    parser.add_argument(
        "--english-files",
        type=Path,
        required=True,
        help="Directory containing the English translation files.",
    )

    return parser


def _compare_directories(german_dir: Path, english_dir: Path) -> Diff:
    def json_files(directory: Path) -> set[Path]:
        return {
            file
            for file in directory.iterdir()
            if file.is_file() and file.suffix == ".json"
        }

    german_files = json_files(german_dir)
    english_files = json_files(english_dir)

    # We assume here that the German and English translation keys are defined in
    # identically named files. If they are not, missing files will be reported and
    # the reduction of Diffs below will report missing keys.
    # This way we continue to enforce the same structure in both German and English
    # translations, which eases maintenance.
    file_pairs = _find_file_pairs(german_files, english_files)

    return reduce(
        lambda d1, d2: d1 + d2,
        (_compare_files(german, english) for german, english in file_pairs),
    )


def _find_file_pairs(
    german_files: set[Path], english_files: set[Path]
) -> list[tuple[Path, Path]]:
    pairs: dict[str, tuple[Path | None, Path | None]] = {}

    for german_file in german_files:
        pairs[german_file.name] = (german_file, None)

    for english_file in english_files:
        pair = pairs.get(english_file.name, (None, None))
        pairs[english_file.name] = (pair[0], english_file)

    for german, english in pairs.values():
        if german is None:
            print("Missing German translation file: " + english.name, file=sys.stderr)
        elif english is None:
            print("Missing English translation file: " + german.name, file=sys.stderr)

    return list(v for v in pairs.values() if v[0] is not None and v[1] is not None)


def _compare_files(german: Path, english: Path) -> Diff:
    german_keys = _flat_json_keys(german)
    english_keys = _flat_json_keys(english)

    return Diff(
        all_german_keys=list(german_keys),
        only_german=german_keys - english_keys,
        all_english_keys=list(english_keys),
        only_english=english_keys - german_keys,
    )


def _flat_json_keys(json_file: Path) -> set[str]:
    """
    Flattens a JSON file into a flat set of keys.

    E.g.

    .. code-block:: json

        {
            "a": {
                "v": 10,
                "b": {
                    "c": null,
                    "d": 1.0
                }
            }
        }

    will result in

    .. code-block::

        "a.v", "a.b.c", "a.b.d"
    """
    with json_file.open(mode="r") as f:
        data = json.load(f)

    result = set()

    def flatten(prefix: str, item: dict[str, Any] | list[Any] | str | float) -> None:
        if isinstance(item, dict):
            for k, v in item.items():
                flatten(f"{prefix}.{k}", v)
        elif isinstance(item, list):
            for idx, _ in enumerate(item):
                result.add(f"{prefix}.[{idx}]")
        else:
            result.add(prefix)

    flatten("", data)

    return result


def _log_duplicate_keys(
    duplicates_german: list[str], duplicates_english: list[str]
) -> None:
    if len(duplicates_german) > 0 or len(duplicates_english) > 0:
        print("\nThere are duplicated translation keys!")

    if len(duplicates_german) > 0:
        duplicates_german.sort()
        print("German:\n" + "\n".join(duplicates_german))

    if len(duplicates_english) > 0:
        duplicates_english.sort()
        print("English:\n" + "\n".join(duplicates_english))


if __name__ == "__main__":
    raise SystemExit(main())
