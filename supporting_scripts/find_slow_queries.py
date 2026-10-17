import argparse
import json
import os
import re

# Configuration
MAX_FETCH_THRESHOLD = 5  # You can adjust this threshold
SEARCH_DIRECTORIES = ["./src/main/java", "./src/main/kotlin"]  # Paths to scan

# Regex Patterns
entitygraph_pattern = re.compile(r'@EntityGraph\s*\(.*?attributePaths\s*=\s*\{([^\}]*)\}', re.DOTALL)
query_pattern = re.compile(r'@Query\s*\(\s*"""(.*?)"""', re.DOTALL | re.MULTILINE)
join_fetch_pattern = re.compile(r'JOIN\s+FETCH\s+\S+', re.IGNORECASE)
jpql_root_entity_pattern = re.compile(r'\bFROM\s+(\w+)', re.IGNORECASE)


def scan_file(file_path, findings):
    with open(file_path, 'r', encoding='utf-8', errors='ignore') as file:
        content = file.read()

        # Check for @EntityGraph
        for match in entitygraph_pattern.finditer(content):
            paths = match.group(1)
            path_count = len(re.findall(r'"[^"]+"', paths))
            if path_count > MAX_FETCH_THRESHOLD:
                print(f"\n[EntityGraph] Potential over-fetch in {file_path} ({path_count} fetches):\n{match.group(0)}")
                findings.append({"type": "wide_entitygraph", "file": file_path, "fetchCount": path_count, "snippet": match.group(0).strip()})

        # Check for @Query
        for match in query_pattern.finditer(content):
            query_text = match.group(1)
            fetch_count = len(join_fetch_pattern.findall(query_text))
            if fetch_count > MAX_FETCH_THRESHOLD:
                print(f"\n[@Query] Potential over-fetch in {file_path} ({fetch_count} JOIN FETCHes):\n{query_text.strip()}")
                root_match = jpql_root_entity_pattern.search(query_text)
                findings.append({
                    "type": "wide_join_fetch",
                    "file": file_path,
                    "fetchCount": fetch_count,
                    "entityClass": root_match.group(1) if root_match else None,
                    "snippet": query_text.strip(),
                })


def scan_directory(directory, callback):
    for root, _, files in os.walk(directory):
        for file in files:
            if file.endswith(".java") or file.endswith(".kt"):
                callback(os.path.join(root, file))


# --- Eager-fetch dependency graph check ---
# @ManyToOne/@OneToOne default to FetchType.EAGER in the JPA spec unless explicitly marked LAZY.
# A single field like that looks harmless, but if the entity it points to has its own eager
# associations, Hibernate joins those in too, transitively -- a handful of individually-reasonable
# field declarations across different entities can combine into a query joining a dozen+ tables
# that nobody explicitly wrote anywhere. This walks that whole graph statically, from source, with
# no live database or app context needed.
#
# Deliberately unfiltered/no threshold: unlike the two checks above (which measure something an
# author directly wrote -- an explicit attributePaths list or JOIN FETCH count), transitive
# reachability across the whole entity graph is a different kind of number with no established
# baseline in this codebase yet. Report every entity's reachable count so a real threshold can be
# calibrated from the actual distribution later, instead of guessing one now.

# Deliberately doesn't try to capture the preceding annotation block as part of this regex:
# annotations that themselves wrap further annotations with their own parens (e.g.
# @JsonSubTypes({ @JsonSubTypes.Type(...), @JsonSubTypes.Type(...) }) has parens nested inside
# its own parens) aren't matchable by a simple non-nesting `\([^)]*\)`, and get parsed wrong badly
# enough to silently break the scan for an entire class, not just the one field near the bad
# annotation. Instead, find bare field declarations and look at a bounded window of raw text
# immediately before each one (see collect_eager_edges) -- much more robust, since it only needs
# to answer "do these substrings appear nearby", not "parse this annotation correctly".
FIELD_DECL_PATTERN = re.compile(r'private\s+(?:final\s+)?([\w.]+)\s+(\w+)\s*(?:=[^;]+)?;')


INHERITANCE_PATTERN = re.compile(r'\bclass\s+(\w+)\s+extends\s+(\w+)')
NON_SINGLE_TABLE_PATTERN = re.compile(r'@Inheritance\s*\(\s*strategy\s*=\s*InheritanceType\.(JOINED|TABLE_PER_CLASS)')


def collect_eager_edges(file_path, content, eager_edges, subclasses_of, non_single_table):
    if '@Entity' not in content:
        return
    class_match = re.search(r'\bclass\s+(\w+)', content)
    if not class_match:
        return
    entity_name = class_match.group(1)

    for match in FIELD_DECL_PATTERN.finditer(content):
        field_type, field_name = match.group(1), match.group(2)
        # Annotation window = everything between the previous statement's closing `;` and this
        # field -- bounded to 1500 chars back as a safety cap for the very first field in a class
        # (where the "previous ;" might be far away or nonexistent). A semicolon is a safe
        # boundary marker here because annotation argument values in this codebase's entities
        # don't contain embedded `;` themselves. 1500 comfortably covers even a long
        # @JsonIgnoreProperties value list plus a few comment lines (measured up to ~730 chars on
        # a real example in this codebase).
        window_start = max(0, match.start() - 1500)
        preceding = content[window_start:match.start()]
        prev_semicolon = preceding.rfind(';')
        annotations = preceding[prev_semicolon + 1:] if prev_semicolon != -1 else preceding

        is_to_one = '@ManyToOne' in annotations or '@OneToOne' in annotations
        if is_to_one and 'FetchType.LAZY' not in annotations:
            target_entity = field_type.split('.')[-1]
            eager_edges.setdefault(entity_name, []).append((field_name, target_entity))

    # Entity inheritance: JPA's default (and only) strategy that doesn't need an explicit
    # @Inheritance annotation is SINGLE_TABLE -- every subclass's columns live in the same
    # physical table as the parent, so a query against the parent type must be ready to join in
    # ANY subclass's eager associations too (Hibernate doesn't know which subclass a given row
    # actually is until the discriminator column is read). JOINED/TABLE_PER_CLASS don't have this
    # behavior -- each subclass's columns are genuinely separate, so they opt out explicitly.
    inheritance_match = INHERITANCE_PATTERN.search(content)
    if inheritance_match:
        child, parent = inheritance_match.group(1), inheritance_match.group(2)
        subclasses_of.setdefault(parent, []).append(child)
    if NON_SINGLE_TABLE_PATTERN.search(content):
        non_single_table.add(entity_name)


def merged_eager_edges(entity, eager_edges, subclasses_of, non_single_table, seen=None):
    """Own eager edges for `entity`, plus -- unless `entity` explicitly opts out of single-table
    inheritance -- every (transitive) subclass's eager edges too, since Hibernate may need to join
    those in for any query against the base type. Subclass-sourced edges are tagged with the
    subclass name so a reader can tell an inherited edge from a directly-declared one."""
    if seen is None:
        seen = set()
    if entity in seen:
        return []
    seen.add(entity)

    edges = list(eager_edges.get(entity, []))
    if entity not in non_single_table:
        for child in subclasses_of.get(entity, []):
            for field_name, target in merged_eager_edges(child, eager_edges, subclasses_of, non_single_table, seen):
                tagged_field = field_name if " (via " in field_name else f"{field_name} (via {child})"
                edges.append((tagged_field, target))
    return edges


def compute_reachable_entities(start_entity, eager_edges, subclasses_of, non_single_table):
    """Breadth-first walk over eager-only edges (including inherited single-table subclass edges,
    see merged_eager_edges). Returns (count, paths): the number of distinct entities transitively
    reachable, and for each one, the chain of field names that leads to it (e.g. "exam (via
    Channel) -> course") -- the count alone doesn't tell a reader WHY an entity is flagged, the
    path does."""
    visited = {start_entity}
    paths = []
    queue = [(start_entity, [])]
    while queue:
        current, chain = queue.pop(0)
        for field_name, target in merged_eager_edges(current, eager_edges, subclasses_of, non_single_table):
            if target in visited:
                continue
            visited.add(target)
            new_chain = chain + [field_name]
            paths.append(" -> ".join(new_chain))
            queue.append((target, new_chain))
    return len(visited) - 1, paths


def analyze_eager_fetch_graph(eager_edges, subclasses_of, non_single_table, findings):
    for entity_name in eager_edges:
        reachable_count, paths = compute_reachable_entities(entity_name, eager_edges, subclasses_of, non_single_table)
        if reachable_count > 0:
            findings.append({"type": "wide_eager_fetch", "entityClass": entity_name, "reachableEntityCount": reachable_count, "reachablePath": paths})


def read(file_path):
    with open(file_path, 'r', encoding='utf-8', errors='ignore') as file:
        return file.read()


def main():
    parser = argparse.ArgumentParser(description="Static query-quality scanner")
    parser.add_argument("--json", metavar="PATH", help="Also write all findings as structured JSON to PATH")
    args = parser.parse_args()

    findings = []
    eager_edges = {}
    subclasses_of = {}
    non_single_table = set()

    for directory in SEARCH_DIRECTORIES:
        scan_directory(directory, lambda file_path: scan_file(file_path, findings))
        scan_directory(directory, lambda file_path: collect_eager_edges(file_path, read(file_path), eager_edges, subclasses_of, non_single_table))

    analyze_eager_fetch_graph(eager_edges, subclasses_of, non_single_table, findings)
    eager_fetch_findings = [f for f in findings if f["type"] == "wide_eager_fetch"]
    if eager_fetch_findings:
        worst = max(eager_fetch_findings, key=lambda f: f["reachableEntityCount"])
        print(f"\n[EagerFetchGraph] Analyzed {len(eager_edges)} entities with eager to-one associations; "
              f"worst case: {worst['entityClass']} transitively reaches {worst['reachableEntityCount']} other entities")

    print("\nScan complete.")

    if args.json:
        with open(args.json, 'w', encoding='utf-8') as out:
            json.dump(findings, out, indent=2)
        print(f"Wrote {len(findings)} findings to {args.json}")


if __name__ == "__main__":
    main()
