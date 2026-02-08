"""
dto_coverage.py — Heuristic DTO coverage scanner for Spring (Java) REST endpoints.

- Classifies endpoints as DTO / Entity / Mixed / Neutral based on return types and @RequestBody payloads:

  - dto:     DTOs are present and Entities are not present across request/response (no DTO/Entity mixing).
  - entity:  Entities are present, and DTOs are not present across request/response (no DTO/Entity mixing).
  - mixed:   DTOs and Entities are mixed across request/response (e.g., request DTO + response Entity, or vice versa,
            or both kinds appear in the request body).
  - neutral: Neither request body nor return type transfers DTOs or Entities (i.e., no structured models detected).

- Coverage metric:
    Coverage = (DTO + Neutral) / All endpoints

- Per-module and overall coverage; outputs JSON/CSV/Markdown.
- Defaults tuned for Artemis (base package example: de.tum.cit.aet.artemis).
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import sys
from pathlib import Path
from typing import Dict, List, Tuple, Optional

RE_PACKAGE = re.compile(r'^\s*package\s+([a-zA-Z0-9_.]+)\s*;', re.MULTILINE)
RE_IMPORT = re.compile(r'^\s*import\s+([a-zA-Z0-9_.*]+)\s*;', re.MULTILINE)

MAPPING_ANNOS = ['GetMapping', 'PostMapping', 'PutMapping', 'PatchMapping', 'DeleteMapping', 'RequestMapping']
RE_METHOD_ANNOT = re.compile(r'@(' + '|'.join(MAPPING_ANNOS) + r')\b[^)]*\)', re.MULTILINE)

RE_METHOD_HEAD = re.compile(
    r'(public|protected|private)\s+'
    r'(?P<ret>[a-zA-Z0-9_<>, ?\[\].]+)\s+'
    r'(?P<name>[a-zA-Z0-9_]+)\s*\(',
    re.DOTALL
)

RE_PARAM = re.compile(
    r'(?P<annos>(@\w+(?:\([^)]*\))?\s*)*)'
    r'(?P<type>[a-zA-Z0-9_<>,\.\[\]]+)'
    r'(?:\s+\.{3})?'
    r'\s+(?P<name>[a-zA-Z0-9_]+)'
)

RE_GENERIC_INNER = re.compile(r'<([^<>]+)>')

JAVA_FRAMEWORK = set("""void boolean byte short int long float double char
String Object ResponseEntity Optional List Set Map Collection Page Slice Stream Mono Flux""".split())

JAVA_LANG = {
    "String", "Object", "Integer", "Long", "Short", "Byte", "Boolean",
    "Character", "Double", "Float", "Void", "Class", "Enum", "Record"
}


def split_generic_parts(t: str) -> List[str]:
    parts, cleaned = [], t.replace('[]', '')
    queue = [cleaned]
    while queue:
        cur = queue.pop(0)
        m = RE_GENERIC_INNER.search(cur)
        if m:
            before, inner, after = cur[:m.start()], m.group(1), cur[m.end():]
            if before.strip():
                parts.append(before.strip())
            if inner.strip():
                queue.append(inner.strip())
            if after.strip():
                queue.append(after.strip())
        else:
            if cur.strip():
                parts.append(cur.strip())
    flat: List[str] = []
    for p in parts:
        for tok in re.split(r'[, ?<>]', p):
            tok = tok.strip()
            if tok:
                flat.append(tok)
    return flat


def collect_import_map(text: str) -> Dict[str, str]:
    mapping: Dict[str, str] = {}
    for imp in RE_IMPORT.findall(text):
        if imp.endswith('.*'):
            continue
        mapping[imp.split('.')[-1]] = imp
    return mapping


def resolve_full_names(simple: List[str], imports: Dict[str, str], current_pkg: Optional[str]) -> List[str]:
    out: List[str] = []
    for name in simple:
        if name in JAVA_FRAMEWORK or name in JAVA_LANG:
            out.append(name)
            continue

        # Already fully-qualified
        if '.' in name:
            out.append(name)
            continue

        # Imported
        if name in imports:
            out.append(imports[name])
            continue

        # Same package fallback
        if current_pkg:
            out.append(current_pkg + "." + name)
        else:
            out.append(name)

    return out


def classify_type(fq: str, dto_hints: List[str], entity_hints: List[str]) -> Optional[str]:
    if not fq or fq in JAVA_FRAMEWORK:
        return None
    low = fq.lower()
    if any(h in low for h in dto_hints) or low.endswith('dto'):
        return 'dto'
    if any(h in low for h in entity_hints):
        return 'entity'
    return None


def classify_endpoint(ret_types_fq: List[str],
                      body_param_types_fq: List[str],
                      dto_hints: List[str],
                      entity_hints: List[str]) -> str:
    """
    Classification rules:

    - neutral:
        Neither request body nor return type contains DTOs or Entities.
    - dto:
        DTOs are present and Entities are not present across request/response.
    - entity:
        Entities are present and DTOs are not present across request/response.
    - mixed:
        DTOs and Entities are both present across request/response (any direction), or both appear in request body.

    Notes:
    - Only @RequestBody parameters are considered for the request side.
    - Generic wrappers like ResponseEntity/List/Optional are ignored during type detection.
    """

    body_saw_dto = False
    body_saw_entity = False
    for fq in body_param_types_fq:
        cat = classify_type(fq, dto_hints, entity_hints)
        if cat == 'dto':
            body_saw_dto = True
        elif cat == 'entity':
            body_saw_entity = True

    ret_saw_dto = False
    ret_saw_entity = False
    for fq in ret_types_fq:
        cat = classify_type(fq, dto_hints, entity_hints)
        if cat == 'dto':
            ret_saw_dto = True
        elif cat == 'entity':
            ret_saw_entity = True

    saw_dto = body_saw_dto or ret_saw_dto
    saw_entity = body_saw_entity or ret_saw_entity

    if not saw_dto and not saw_entity:
        return 'neutral'
    if saw_dto and saw_entity:
        return 'mixed'
    if saw_entity:
        return 'entity'
    return 'dto'


def iter_endpoints(text: str) -> List[Tuple[str, str, str, str]]:
    """
    Returns tuples: (mapping_anno, method_sig, ret_type, params)
    mapping_anno: e.g. 'PostMapping', 'PutMapping', ...
    """
    eps: List[Tuple[str, str, str, str]] = []

    for m in RE_METHOD_ANNOT.finditer(text):
        mapping_anno = m.group(1)  # GetMapping/PostMapping/...

        search_from = m.end()

        # Find the next method head after this annotation
        mh = RE_METHOD_HEAD.search(text, pos=search_from)
        if not mh:
            continue

        # If another mapping annotation appears before this method head, skip to avoid mismatch
        next_map = RE_METHOD_ANNOT.search(text, pos=search_from)
        if next_map and next_map.start() < mh.start():
            continue

        ret_type = mh.group('ret').strip()

        # Extract params by balanced parentheses starting at '(' right after the method head
        open_paren_idx = mh.end() - 1
        if open_paren_idx < 0 or open_paren_idx >= len(text) or text[open_paren_idx] != '(':
            continue

        try:
            params, close_paren_idx = extract_balanced(text, open_paren_idx)
        except ValueError:
            continue

        sig_start = mh.start()
        sig_end = min(close_paren_idx + 1, len(text))
        method_sig = text[sig_start:sig_end]

        eps.append((mapping_anno, method_sig, ret_type, params.strip()))

    return eps


def extract_param_types(params_str: str) -> List[Tuple[str, str]]:
    out: List[Tuple[str, str]] = []
    for pm in RE_PARAM.finditer(params_str):
        annos = (pm.group('annos') or '').strip()
        typ = pm.group('type').strip()
        out.append((annos, typ))
    return out


def is_controller_path(path: str, hints: List[str]) -> bool:
    if not hints:  # empty hints => scan everything
        return True
    p = path.replace('\\', '/')
    return any(h in p for h in hints)


def find_module_name(java_path: Path, root: Path, strategy: str, base_package: Optional[str]) -> str:
    s = java_path.as_posix()
    if strategy in ('auto', 'folder'):
        m = re.search(r'/(.+?)/src/main/java/', s)
        if m:
            return m.group(1)
    if strategy == 'package' and base_package:
        try:
            text = java_path.read_text(encoding='utf-8', errors='ignore')
            pm = RE_PACKAGE.search(text)
            if pm:
                pkg = pm.group(1)
                if pkg.startswith(base_package + '.'):
                    rest = pkg[len(base_package) + 1:]
                    return rest.split('.', 1)[0]
        except Exception:
            pass
    rel = java_path.relative_to(root).parts
    return rel[0] if rel else 'unknown'


def get_current_package(text: str) -> Optional[str]:
    pm = RE_PACKAGE.search(text)
    return pm.group(1) if pm else None


def extract_balanced(s: str, open_idx: int, open_ch: str = '(', close_ch: str = ')') -> Tuple[str, int]:
    """
    Extract content inside balanced parentheses starting at s[open_idx] == '('.
    Returns (content_without_outer_parens, index_of_closing_paren).
    Skips strings and // /* */ comments to avoid false matches.
    """
    if open_idx < 0 or open_idx >= len(s) or s[open_idx] != open_ch:
        raise ValueError(f"extract_balanced: expected '{open_ch}' at index {open_idx}")

    i = open_idx + 1
    depth = 1
    out: List[str] = []

    in_str = False
    str_ch = ''
    in_line_comment = False
    in_block_comment = False

    while i < len(s):
        ch = s[i]
        nxt = s[i + 1] if i + 1 < len(s) else ''

        # handle comments
        if in_line_comment:
            if ch == '\n':
                in_line_comment = False
            out.append(ch)
            i += 1
            continue

        if in_block_comment:
            if ch == '*' and nxt == '/':
                in_block_comment = False
                out.append(ch)
                out.append(nxt)
                i += 2
                continue
            out.append(ch)
            i += 1
            continue

        # start comments (only if not in string)
        if not in_str and ch == '/' and nxt == '/':
            in_line_comment = True
            out.append(ch)
            out.append(nxt)
            i += 2
            continue
        if not in_str and ch == '/' and nxt == '*':
            in_block_comment = True
            out.append(ch)
            out.append(nxt)
            i += 2
            continue

        # handle strings
        if in_str:
            out.append(ch)
            if ch == '\\' and i + 1 < len(s):  # escape
                out.append(s[i + 1])
                i += 2
                continue
            if ch == str_ch:
                in_str = False
            i += 1
            continue
        else:
            if ch in ('"', "'"):
                in_str = True
                str_ch = ch
                out.append(ch)
                i += 1
                continue

        # balance
        if ch == open_ch:
            depth += 1
        elif ch == close_ch:
            depth -= 1
            if depth == 0:
                return ''.join(out), i

        out.append(ch)
        i += 1

    raise ValueError("Unbalanced parentheses while extracting params")


def main():
    ap = argparse.ArgumentParser(description="Heuristic DTO coverage scanner for Spring REST endpoints.")
    ap.add_argument('--root', required=True, help='Path to project root')
    ap.add_argument('--out', default='dto_coverage_out', help='Output directory')
    ap.add_argument('--base-package', help='Base package (e.g., de.tum.cit.aet.artemis)')
    ap.add_argument('--module-strategy', default='auto', choices=['auto', 'folder', 'package'])
    ap.add_argument(
        '--controller-path-hints',
        default='web/rest,web/api,controller,src/main/java',
        help='Comma-separated substrings to identify REST controllers; empty scans all *.java'
    )
    ap.add_argument('--dto-package-hints', default='service.dto,dto', help='Comma-separated substrings for DTO packages')
    ap.add_argument('--entity-package-hints', default='domain,entity', help='Comma-separated substrings for Entity packages')
    args = ap.parse_args()

    root = Path(args.root).resolve()
    out_dir = Path(args.out).resolve()
    out_dir.mkdir(parents=True, exist_ok=True)

    controller_hints = [h.strip().replace('\\', '/').replace('//', '/')
                        for h in args.controller_path_hints.split(',') if h.strip()]
    dto_hints = [h.strip().lower() for h in args.dto_package_hints.split(',') if h.strip()]
    entity_hints = [h.strip().lower() for h in args.entity_package_hints.split(',') if h.strip()]

    java_files: List[Path] = [p for p in root.rglob('*.java') if is_controller_path(p.as_posix(), controller_hints)]
    results: List[Dict[str, str]] = []
    per_module: Dict[str, Dict[str, int]] = {}

    for jfile in java_files:
        try:
            text = jfile.read_text(encoding='utf-8', errors='ignore')
        except Exception:
            continue

        current_pkg = get_current_package(text)
        imports = collect_import_map(text)
        endpoints = iter_endpoints(text)
        if not endpoints:
            continue

        module = find_module_name(jfile, root, args.module_strategy, args.base_package)

        for mapping_anno, _sig, ret_type, params_str in endpoints:
            ret_full = resolve_full_names(split_generic_parts(ret_type), imports, current_pkg)

            body_params_types: List[str] = []
            for annos, ptype in extract_param_types(params_str):
                if '@RequestBody' not in annos:
                    continue  # only body payloads contribute to classification
                p_full_all = resolve_full_names(split_generic_parts(ptype), imports, current_pkg)
                rep = next(
                    (t for t in reversed(p_full_all) if t not in JAVA_FRAMEWORK),
                    p_full_all[-1] if p_full_all else ''
                )
                if rep:
                    body_params_types.append(rep)

            classification = classify_endpoint(ret_full, body_params_types, dto_hints, entity_hints)

            http_method = mapping_anno.lower().replace('mapping', '').upper()
            if http_method == 'REQUEST':
                http_method = 'REQUEST'

            results.append({
                'file': str(jfile.relative_to(root).as_posix()),
                'module': module,
                'http_method': http_method,
                'return_type_raw': ret_type,
                'request_body_types': ";".join(body_params_types),
                'classification': classification,
            })

            per_module.setdefault(module, {'dto': 0, 'mixed': 0, 'entity': 0, 'neutral': 0, 'unknown': 0})
            per_module[module][classification] = per_module[module].get(classification, 0) + 1

    totals = {'dto': 0, 'mixed': 0, 'entity': 0, 'neutral': 0, 'unknown': 0}
    for _mod, counts in per_module.items():
        for k in totals.keys():
            totals[k] += counts.get(k, 0)

    total_endpoints = sum(totals.values())
    coverage = (totals['dto'] + totals['neutral']) / total_endpoints * 100 if total_endpoints else 0.0

    summary = {
        'root': str(root),
        'total_endpoints': total_endpoints,
        'totals': totals,
        'coverage_percent': round(coverage, 2),
        'dto_only_percent': round((totals['dto'] / total_endpoints * 100) if total_endpoints else 0.0, 2),
        'entity_share_percent': round((totals['entity'] / total_endpoints * 100) if total_endpoints else 0.0, 2),
        'mixed_share_percent': round((totals['mixed'] / total_endpoints * 100) if total_endpoints else 0.0, 2),
        'neutral_share_percent': round((totals['neutral'] / total_endpoints * 100) if total_endpoints else 0.0, 2),
        'unknown_share_percent': round((totals['unknown'] / total_endpoints * 100) if total_endpoints else 0.0, 2),
        'per_module': per_module,
        'config': {
            'controller_hints': controller_hints,
            'dto_hints': dto_hints,
            'entity_hints': entity_hints,
            'module_strategy': args.module_strategy,
            'base_package': args.base_package,
        }
    }

    # write files
    json_path = out_dir / 'dto_coverage.json'
    csv_path = out_dir / 'dto_coverage.csv'
    md_path = out_dir / 'dto_coverage_summary.md'

    json_path.write_text(json.dumps(summary, indent=2), encoding='utf-8')

    with csv_path.open('w', newline='', encoding='utf-8') as f:
        w = csv.DictWriter(
            f,
            fieldnames=['file', 'module', 'http_method', 'return_type_raw', 'request_body_types', 'classification']
        )
        w.writeheader()
        for row in results:
            w.writerow(row)

    lines: List[str] = []
    lines.append("# DTO Coverage Report\n")
    lines.append(f"- Project root: `{root}`")
    lines.append(f"- Total endpoints: **{total_endpoints}**")
    lines.append(f"- Coverage (DTO + Neutral): **{summary['coverage_percent']}%**")
    lines.append(f"- DTO-only endpoints: **{summary['dto_only_percent']}%**")
    lines.append(f"- Entity share: **{summary['entity_share_percent']}%**")
    lines.append(f"- Mixed share: **{summary['mixed_share_percent']}%**")
    lines.append(f"- Neutral share: **{summary['neutral_share_percent']}%**")
    lines.append(f"- Unknown share: **{summary['unknown_share_percent']}%**\n")

    lines.append("## Per Module\n")
    lines.append("| Module | Endpoints | DTO | Mixed | Entity | Neutral | Unknown | Coverage* |")
    lines.append("|---|---:|---:|---:|---:|---:|---:|---:|")
    for mod, counts in sorted(per_module.items()):
        tot = sum(counts.values())
        cov = (counts.get('dto', 0) + counts.get('neutral', 0)) / tot * 100 if tot else 0.0
        lines.append(
            f"| {mod} | {tot} | {counts.get('dto', 0)} | {counts.get('mixed', 0)} | {counts.get('entity', 0)} | "
            f"{counts.get('neutral', 0)} | {counts.get('unknown', 0)} | {cov:.2f}% |"
        )
    lines.append("\n_*Coverage = (DTO + Neutral) / All_\n")
    md_path.write_text("\n".join(lines), encoding='utf-8')

    print(f"Wrote:\n- {json_path}\n- {csv_path}\n- {md_path}")


if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception as e:
        print(f'ERROR: {e}', file=sys.stderr)
        sys.exit(2)
