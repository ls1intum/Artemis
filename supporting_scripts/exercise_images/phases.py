"""Reads the build phases of ``src/main/resources/templates/phases`` without a YAML library.

The phase files are lists of mappings with a ``name``, a ``script`` (plain, quoted or block scalar) and optional
``forceRun`` and ``resultPaths`` entries. The build agent turns them into one bash script (see
``LocalCIBuildConfigurationService``); the offline verification runs the same scripts, so only this subset is needed.
"""

import re
from dataclasses import dataclass
from pathlib import Path

_ITEM_START = re.compile(r"^- name:\s*(?P<name>\S+)\s*$")
_KEY = re.compile(r"^  (?P<key>[A-Za-z]+):(?:\s+(?P<value>.*))?$")
_BLOCK_SCALAR_START = ("|", ">")
# Literal block scalars only. A folded one (">") joins its lines with spaces, a kept one ("|+") keeps trailing line breaks: reading them as
# literal blocks would run a different script than the build agent does, so they are refused instead.
_SUPPORTED_BLOCK_SCALARS = ("|", "|-")


@dataclass(frozen=True)
class Phase:
    name: str
    script: str
    force_run: bool
    result_paths: tuple[str, ...] = ()


def _scalar(text: str) -> str:
    """A plain or quoted YAML scalar without the comment behind it (``'a/*.xml' # JUnit``). A ``#`` inside quotes belongs to the value."""
    text = text.strip()
    if text[:1] not in ("'", '"'):
        return re.sub(r"\s+#.*$", "", text).strip()
    quote = text[0]
    value = []
    index = 1
    while index < len(text):
        character = text[index]
        if quote == "'" and character == "'" and text[index + 1 : index + 2] == "'":
            value.append("'")
            index += 2
        elif quote == '"' and character == "\\" and index + 1 < len(text):
            value.append(text[index + 1])
            index += 2
        elif character == quote:
            rest = text[index + 1 :].strip()
            if rest and not rest.startswith("#"):
                raise ValueError(f"Unexpected text after the quoted value {text}")
            return "".join(value)
        else:
            value.append(character)
            index += 1
    raise ValueError(f"The quoted value {text} is not closed")


def _flow_list(value: str) -> list[str]:
    """The items of an inline list (``['a', "b"]``) with an optional comment behind it."""
    end = value.rfind("]")
    rest = value[end + 1 :].strip()
    if end < 0 or (rest and not rest.startswith("#")):
        raise ValueError(f"Unsupported list {value}")
    items = re.findall(r"""\s*('(?:[^']|'')*'|"(?:[^"\\]|\\.)*"|[^,'"]+)\s*(?:,|$)""", value[1:end])
    return [_scalar(item) for item in items if item.strip()]


def _unquote(value: str) -> str:
    if len(value) >= 2 and value[0] == value[-1] and value[0] in "'\"":
        inner = value[1:-1]
        return inner.replace("''", "'") if value[0] == "'" else inner.replace('\\"', '"')
    return value


def parse_phases(text: str) -> list[Phase]:
    """Parses the content of one phase file."""
    phases: list[Phase] = []
    name: str | None = None
    script = ""
    force_run = False
    result_paths: list[str] = []
    lines = text.splitlines()
    index = 0

    def finish() -> None:
        if name is not None:
            phases.append(Phase(name, script, force_run, tuple(result_paths)))

    while index < len(lines):
        line = lines[index]
        index += 1
        item = _ITEM_START.match(line)
        if item:
            finish()
            name, script, force_run, result_paths = item.group("name"), "", False, []
            continue
        key = _KEY.match(line)
        if not key or name is None:
            continue
        key_name, value = key.group("key"), (key.group("value") or "").strip()
        if key_name == "forceRun":
            force_run = value == "true"
        elif key_name == "resultPaths":
            if value.startswith("["):
                result_paths.extend(_flow_list(value))
            elif value and not value.startswith("#"):
                raise ValueError(f"The result paths of the phase {name} are written as {value}, only a list is supported")
            # A comment or a blank line between the entries does not end the list
            # (YAML allows a tab as well as a space behind the dash)
            while index < len(lines) and (re.match(r"^\s+-[ \t]", lines[index]) or not lines[index].strip() or lines[index].lstrip().startswith("#")):
                if lines[index].strip() and not lines[index].lstrip().startswith("#"):
                    result_paths.append(_scalar(re.sub(r"^-[ \t]+", "", lines[index].strip())))
                index += 1
        elif key_name == "script":
            if value.startswith(_BLOCK_SCALAR_START):
                if value not in _SUPPORTED_BLOCK_SCALARS:
                    raise ValueError(f"The script of the phase {name} uses the block scalar style {value}, only | and |- are supported")
                block: list[str] = []
                while index < len(lines) and (lines[index].startswith("    ") or not lines[index].strip()):
                    block.append(lines[index][4:] if lines[index].strip() else "")
                    index += 1
                script = "\n".join(block).rstrip("\n")
            else:
                script = _unquote(value)
    finish()
    return phases


def load_phases(templates_dir: Path, language: str, file_name: str) -> list[Phase]:
    path = templates_dir / "phases" / language / file_name
    phases = parse_phases(path.read_text(encoding="utf-8"))
    declared = len(re.findall(r"^- name:", path.read_text(encoding="utf-8"), flags=re.MULTILINE))
    if not phases or len(phases) != declared:
        raise ValueError(f"{path} declares {declared} phases but {len(phases)} were read")
    return phases
