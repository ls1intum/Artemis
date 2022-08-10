#! /usr/bin/env python3

"""
Script to convert Spring configuration YAML files into their environment
variable representation.
"""

import logging
from collections import OrderedDict
from functools import reduce
from optparse import OptionParser
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


def is_simple_value(value: Any) -> bool:
    return isinstance(value, int) or isinstance(value, float) or isinstance(value, str)


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
        elif is_simple_value(value):
            save_value(result, path, str(value))
        elif value is None:
            save_value(result, path, "")
        else:
            logging.warning(f"Unknown YAML element: path={path}, value={value}")

    return result


def parse_yaml(yaml_src: str) -> Dict[str, Any]:
    return yaml.full_load(yaml_src)


def flatten_yaml_file(filename: str) -> Dict[str, str]:
    with open(filename) as f:
        file_content: str = f.read()
        y = parse_yaml(file_content)
        return flatten_yaml(y)


def main(files: List[str], sort: bool = False) -> None:
    result: OrderedDict[str, str] = OrderedDict()

    for file in files:
        result.update(flatten_yaml_file(file))

    keys = list(result.keys())
    if sort:
        keys = sorted(keys)

    for key in keys:
        print(f'{key}="{result[key]}"')


def option_parser() -> OptionParser:
    usage = "%prog [options] yaml-file..."
    description = (
        "Converts a list of Spring configuration YAML files into a combined list of "
        "environment variable mappings."
    )
    parser = OptionParser(usage=usage, description=description)
    parser.add_option(
        "-s",
        "--sort",
        dest="sort",
        action="store_true",
        help="sort config options lexicographically",
        default=False,
    )
    return parser


if __name__ == "__main__":
    (options, args) = option_parser().parse_args()
    main(args, options.sort)
