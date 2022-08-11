#! /usr/bin/env python3

"""
Script to convert Spring configuration YAML files into their environment
variable representation.
"""

import logging
from argparse import ArgumentParser
from collections import OrderedDict
from functools import reduce
from typing import Any, Dict, List, Optional, Tuple

import yaml


def to_spring_env_property(config_option: str) -> str:
    """
    Converts YAML keys to environment variable names.

    The keys of configuration values are converted by removing underscores and
    dashes, and making all letters uppercase.

    :param config_option: The key of the config option that should be converted.
    :return: The key as environment variable name.
    """
    replacements = [("_", ""), ("-", ""), (".", "_")]

    def replace(option: str, replacement: Tuple[str, str]) -> str:
        replace_from, replace_to = replacement
        return option.replace(replace_from, replace_to)

    return reduce(replace, replacements, config_option).upper()


def path_to_spring_env_property(path: List[str]) -> str:
    """
    Flattens the path of a configuration option key into a single environment
    variable name.

    In YAML those paths are either denoted by ``some.configuration.key`` or by
    indentation::

        some:
            configuration:
                key: ...

    :param path: The complete path of a configuration key.
    :return: The key as environment variable name.
    """
    return "_".join(map(to_spring_env_property, path))


def save_value(result: Dict[str, str], option_name: List[str], value: str) -> None:
    """
    Adds the given config entry to the result.

    :param result: The dictionary holding the key-value-pairs of environment variables.
    :param option_name: The key of the config entry as YAML path.
    :param value: The value of the config entry.
    """
    result[path_to_spring_env_property(option_name)] = value


def flatten_list(
    yml: List[Any], current_path: Optional[List[str]] = None
) -> Dict[str, str]:
    """
    Converts a YAML list of configuration options into their environment
    variable representation.

    :param yml: The YAML list of configuration values.
    :param current_path: The path within the configuration that points to the list.
    :return: All list items recursively resolved as environment variable
             key-value-pairs.
    """

    def is_str_list(items: List[Any]) -> bool:
        return all(map(lambda i: isinstance(i, str), items))

    if current_path is None:
        current_path = []

    result: OrderedDict[str, str] = OrderedDict()

    if is_str_list(yml):
        # String lists can be given in the yaml as
        # key: [a, b]
        # or
        # key:
        # - a
        # - b
        # Both are converted to `"KEY": "a, b"`
        save_value(result, current_path, ", ".join(yml))
    else:
        # The list contains complex objects, e.g.
        # key:
        # - name: a
        #   value: b
        # - ...
        # In this case the index of the complex object in the list turns into a part of
        # the key:
        # { "KEY_0_NAME": "a", "KEY_0_VALUE": "b", "KEY_1_...": ... }
        for idx, item in enumerate(yml):
            path = [*current_path, str(idx)]
            assert isinstance(item, dict)
            result.update(flatten_yaml(item, path))

    return result


def flatten_yaml(
    yml: Dict[str, Any], current_path: Optional[List[str]] = None
) -> Dict[str, str]:
    """
    Flattens a YAML file into key-value pairs by recursively walking over all elements.

    :param yml: The YAML data that should be converted.
    :param current_path: The path of keys within the YAML leading up to the root of
                         ``yml``.
    :return: A map of key-value-pairs obtained from the YAML. The keys are in the format
             that is used by Spring to read the values from environment variables.
    """
    if current_path is None:
        current_path = []

    result: OrderedDict[str, str] = OrderedDict()

    for k in yml.keys():
        path = [*current_path, k]
        value = yml[k]

        if isinstance(value, dict):
            result.update(flatten_yaml(value, path))
        elif isinstance(value, list):
            result.update(flatten_list(value, path))
        elif isinstance(value, bool):
            save_value(result, path, str(value).lower())
        elif isinstance(value, (float, int, str)):
            save_value(result, path, str(value))
        elif value is None:
            save_value(result, path, "")
        else:
            logging.warning("Unknown YAML element: path=%s, value=%s", path, value)

    return result


def parse_yaml(yaml_src: str) -> Dict[str, Any]:
    """
    Parses some YAML structure.

    :param yaml_src: The YAML source.
    :return: The parsed YAML.
    """
    return yaml.full_load(yaml_src)


def flatten_yaml_file(filename: str) -> Dict[str, str]:
    """
    Converts a single YAML file into the environment variable key-value-mappings.

    :param filename: The file that should be converted.
    :return: The mapping of environment variables to their values.
    """
    with open(filename, encoding="UTF-8") as file:
        file_content: str = file.read()
        parsed_yaml = parse_yaml(file_content)
        return flatten_yaml(parsed_yaml)


def main(files: List[str], sort: bool = False) -> None:
    """
    Main entry point to the script.

    :param files: The list of YAML files that should be converted.
    :param sort: True, if the config keys should be sorted lexicographically.
    """
    result: OrderedDict[str, str] = OrderedDict()

    for file in files:
        result.update(flatten_yaml_file(file))

    keys = list(result.keys())
    if sort:
        keys = sorted(keys)

    for key in keys:
        print(f'{key}="{result[key]}"')


def argument_parser() -> ArgumentParser:
    """
    Builds the argument parser.

    :return: The argument parser.
    """
    description = (
        "Converts a list of Spring configuration YAML files into a combined list of "
        "environment variable mappings."
    )
    parser = ArgumentParser(description=description)
    parser.add_argument(
        "-s",
        "--sort",
        dest="sort",
        action="store_true",
        help="sort config options lexicographically",
        default=False,
    )
    parser.add_argument(
        "yaml_files", nargs="*", help="YAML files that should be converted"
    )
    return parser


if __name__ == "__main__":
    args = argument_parser().parse_args()
    main(args.yaml_files, args.sort)
