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


# --- Yes/no query-shape rules ---
# Unlike the two count-based checks at the top of this file, each rule below either applies or it
# doesn't -- there is no count to tune. Every finding carries a stable "key" (rule + class + member,
# never a line number, which shifts with every edit) so a run can later be compared against a
# baseline run.
#
#   multiple_collection_fetch  -- one query fetches two or more collections of the SAME parent
#                                 (e.g. c.exerciseLinks and c.lectureUnitLinks): the result rows
#                                 are the cartesian product of both collections. Fetching along a
#                                 chain (results -> feedbacks) is not flagged: its rows are just
#                                 the total number of children.
#   pageable_collection_fetch  -- a paged method (Pageable parameter) whose query fetches a
#                                 collection: Hibernate cannot page in SQL then, so it loads every
#                                 row and pages in memory (warning HHH90003004).
#   eager_to_many              -- a to-many association declared FetchType.EAGER: every load of the
#                                 owner loads the whole collection, wherever it is used.

ANNOTATED_FIELD_PATTERN = re.compile(r'(?:private|protected|public)\s+(?:final\s+)?([\w.]+(?:<[^;=(){}]*>)?)\s+(\w+)\s*(?:=[^;]+)?;')
# anchored to a declaration line, so the word "class" in a Javadoc sentence is never taken for one
ENTITY_CLASS_PATTERN = re.compile(r'^\s*(?:public\s+|abstract\s+|final\s+)*class\s+(\w+)(?:\s*<[^{]*?>)?(?:\s+extends\s+(\w+))?', re.MULTILINE)
TO_MANY_ANNOTATIONS = ('@OneToMany', '@ManyToMany', '@ElementCollection')
TO_ONE_ANNOTATIONS = ('@ManyToOne', '@OneToOne')
REPOSITORY_ENTITY_PATTERN = re.compile(r'\binterface\s+\w+\s+extends\s+[\w.]*Repository\s*<\s*(\w+)\s*,')
QUERY_START_PATTERN = re.compile(r'@Query\s*\(')
ENTITYGRAPH_START_PATTERN = re.compile(r'@EntityGraph\s*\(')
TEXT_BLOCK_OR_STRING = re.compile(r'"""(.*?)"""|"((?:[^"\\\n]|\\.)*)"', re.DOTALL)
FROM_ALIAS_PATTERN = re.compile(r'\b(?:FROM|,)\s+(\w+)\s+(?:AS\s+)?(\w+)', re.IGNORECASE)
JOIN_PATTERN = re.compile(r'\bJOIN\s+(FETCH\s+)?(?:TREAT\s*\(\s*)?(\w+)\.(\w+)(?:\s+AS\s+\w+\s*\))?(?:\s+(?:AS\s+)?(\w+))?', re.IGNORECASE)
JPQL_KEYWORDS = {'join', 'left', 'right', 'inner', 'outer', 'where', 'on', 'order', 'group', 'having', 'fetch', 'and', 'or', 'with', 'union'}


def line_of(content, index):
    return content.count('\n', 0, index) + 1


def matching_paren(content, open_index):
    """Index of the ')' closing the '(' at open_index, skipping string and text-block literals
    (a JPQL query can contain parentheses of its own)."""
    depth, i = 0, open_index
    while i < len(content):
        if content.startswith('"""', i):
            end = content.find('"""', i + 3)
            i = len(content) if end == -1 else end + 3
            continue
        ch = content[i]
        if ch == '"':
            i += 1
            while i < len(content) and content[i] != '"':
                i += 2 if content[i] == '\\' else 1
        elif ch == '(':
            depth += 1
        elif ch == ')':
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return -1


def collect_entity_facts(content, entities, superclass_of):
    """Records, for every @Entity/@MappedSuperclass, each association field as
    {field: (kind, target, eager)} with kind 'many' or 'one', plus the class it extends, so a field
    declared on a superclass is still found from a subclass (see lookup_field)."""
    if '@Entity' not in content and '@MappedSuperclass' not in content:
        return
    class_match = ENTITY_CLASS_PATTERN.search(content)
    if not class_match:
        return
    entity_name = class_match.group(1)
    if class_match.group(2):
        superclass_of[entity_name] = class_match.group(2)
    fields = entities.setdefault(entity_name, {})
    for match in ANNOTATED_FIELD_PATTERN.finditer(content):
        field_type, field_name = match.group(1), match.group(2)
        # same bounded "annotations since the previous statement" window as collect_eager_edges
        preceding = content[max(0, match.start() - 1500):match.start()]
        annotations = preceding[preceding.rfind(';') + 1:]
        if any(a in annotations for a in TO_MANY_ANNOTATIONS):
            # the element type is the last type argument: Set<Result> -> Result, Map<K, V> -> V
            type_arguments = re.findall(r'\w+', field_type[field_type.find('<'):]) if '<' in field_type else []
            target = type_arguments[-1] if type_arguments else None
            fields[field_name] = ('many', target, 'FetchType.EAGER' in annotations, line_of(content, match.start()))
        elif any(a in annotations for a in TO_ONE_ANNOTATIONS):
            fields[field_name] = ('one', field_type.split('.')[-1], 'FetchType.LAZY' not in annotations, line_of(content, match.start()))


def lookup_field(entity, field, entities, superclass_of):
    """The association facts for entity.field, walking up the superclass chain; None if unknown."""
    seen = set()
    while entity and entity not in seen:
        seen.add(entity)
        if field in entities.get(entity, {}):
            return entities[entity][field]
        entity = superclass_of.get(entity)
    return None


def unambiguous_kind(field, entities):
    """Fallback when the owning entity of a fetched field can't be resolved (e.g. an alias the
    simple JPQL parsing below doesn't understand): the field's kind, but only if every entity
    declaring a field of that name agrees on it -- so an unresolvable alias never produces a guess."""
    kinds = {facts[field][0] for facts in entities.values() if field in facts}
    return kinds.pop() if len(kinds) == 1 else None


def collection_fetches_of_query(jpql, entities, superclass_of):
    """[(parent alias, field)] for every JOIN FETCH in the query that fetches a collection."""
    alias_entity = {}
    for entity, alias in FROM_ALIAS_PATTERN.findall(jpql):
        if alias.lower() not in JPQL_KEYWORDS and entity in entities:
            alias_entity[alias] = entity
    joins = JOIN_PATTERN.findall(jpql)
    # resolve join aliases in order; a few passes cover joins that refer to a later-declared alias
    for _ in range(3):
        for _fetch, parent, field, alias in joins:
            if alias and alias.lower() not in JPQL_KEYWORDS and parent in alias_entity and alias not in alias_entity:
                facts = lookup_field(alias_entity[parent], field, entities, superclass_of)
                if facts and facts[1] in entities:
                    alias_entity[alias] = facts[1]
    fetches = []
    for fetch, parent, field, _alias in joins:
        if not fetch:
            continue
        facts = lookup_field(alias_entity[parent], field, entities, superclass_of) if parent in alias_entity else None
        kind = facts[0] if facts else unambiguous_kind(field, entities)
        if kind == 'many':
            fetches.append((parent, field))
    return fetches


def collection_fetches_of_entity_graph(paths, root_entity, entities, superclass_of):
    """[(parent path, field)] for every attributePaths entry that fetches a collection, resolving
    each dotted segment from the repository's own entity type."""
    fetches = []
    for path in paths:
        entity, parent = root_entity, ''
        # every segment is fetched, not just the last: "results.feedbacks" fetches results too
        for segment in path.split('.'):
            facts = lookup_field(entity, segment, entities, superclass_of) if entity else None
            kind = facts[0] if facts else unambiguous_kind(segment, entities)
            if kind == 'many':
                fetches.append((parent or '<root>', segment))
            entity = facts[1] if facts else None
            parent = f"{parent}.{segment}" if parent else segment
    # "a" and "a.b" both name collection a; count each (parent, field) once
    return sorted(set(fetches))


def method_after(content, close_index):
    """Name of the method an annotation ending at close_index belongs to: the first identifier
    followed by '(' once any further annotations in between are skipped."""
    i = close_index + 1
    while True:
        rest = content[i:]
        stripped = rest.lstrip()
        i += len(rest) - len(stripped)
        annotation = re.match(r'@[\w.]+', stripped)
        if not annotation:
            break
        i += annotation.end()
        if content[i:].lstrip().startswith('('):
            i = matching_paren(content, content.index('(', i)) + 1
    declaration = content[i:i + 600].split(';')[0].split('{')[0]
    name = re.search(r'(\w+)\s*\(', declaration)
    return (name.group(1) if name else None), declaration


def sibling_groups(fetches):
    by_parent = {}
    for parent, field in fetches:
        by_parent.setdefault(parent, []).append(field)
    return {parent: fields for parent, fields in by_parent.items() if len(fields) >= 2}


def is_paged(declaration):
    return re.search(r'\bPageable\b', declaration) is not None


def check_query_shapes(file_path, content, entities, superclass_of, findings):
    class_name = os.path.splitext(os.path.basename(file_path))[0]
    root_match = REPOSITORY_ENTITY_PATTERN.search(content)
    root_entity = root_match.group(1) if root_match else None

    for start_pattern, kind in ((QUERY_START_PATTERN, 'query'), (ENTITYGRAPH_START_PATTERN, 'entitygraph')):
        for match in start_pattern.finditer(content):
            open_index = match.end() - 1
            close_index = matching_paren(content, open_index)
            if close_index == -1:
                continue
            annotation = content[open_index + 1:close_index]
            if kind == 'query':
                literal = TEXT_BLOCK_OR_STRING.search(annotation)
                if not literal or 'nativeQuery = true' in annotation.replace('nativeQuery=true', 'nativeQuery = true'):
                    continue
                snippet = (literal.group(1) or literal.group(2) or '').strip()
                fetches = collection_fetches_of_query(snippet, entities, superclass_of)
            else:
                paths_match = re.search(r'attributePaths\s*=\s*\{([^}]*)\}|attributePaths\s*=\s*"([^"]+)"', annotation)
                if not paths_match:
                    continue
                paths = re.findall(r'"([^"]+)"', paths_match.group(1)) if paths_match.group(1) is not None else [paths_match.group(2)]
                snippet = f"@EntityGraph(attributePaths = {{{', '.join(paths)}}})"
                fetches = collection_fetches_of_entity_graph(paths, root_entity, entities, superclass_of)
            if not fetches:
                continue

            method, declaration = method_after(content, close_index)
            member = f"{class_name}.{method}" if method else f"{class_name}:{line_of(content, match.start())}"
            line = line_of(content, match.start())
            for parent, fields in sibling_groups(fetches).items():
                detail = f"fetches {len(fields)} collections of {parent}: {', '.join(fields)}"
                print(f"\n[MultipleCollectionFetch] {member} ({file_path}:{line}): {detail}")
                findings.append({"type": "multiple_collection_fetch", "key": f"multiple_collection_fetch:{member}:{parent}", "file": file_path, "line": line,
                                 "member": member, "entityClass": root_entity, "detail": detail, "snippet": snippet})
            if is_paged(declaration):
                fields = ', '.join(field for _, field in fetches)
                detail = f"paged method fetches collection(s) {fields}; Hibernate pages in memory"
                print(f"\n[PageableCollectionFetch] {member} ({file_path}:{line}): {detail}")
                findings.append({"type": "pageable_collection_fetch", "key": f"pageable_collection_fetch:{member}", "file": file_path, "line": line,
                                 "member": member, "entityClass": root_entity, "detail": detail, "snippet": snippet})


# --- repository_call_in_loop: one query per item instead of one query for all items ---
# Flags a call on one of the class's own repository fields that sits inside a loop body: a for /
# for-each / while / do loop, or a lambda (or method reference) that runs once per element --
# Iterable/Map.forEach, removeIf, a stream operation, or a Collectors.toMap/groupingBy function.
# This is the source-code side of the dynamic N+1 shape.
#
# Precision measures, each against a concrete false-positive source:
#   * comments and string literals are blanked out first (same length, so indices still line up):
#     a commented-out call is no finding, and a '{' inside a string does not derail brace matching;
#   * the receiver must be a field of the class whose declared type is a repository, not just any
#     identifier ending in "Repository";
#   * map/filter/flatMap/... count only inside a stream pipeline, so Optional.map(...) -- not a loop,
#     and everywhere in this codebase as findById(id).map(...) -- is not flagged;
#   * nested loops report a call once, keyed by (enclosing method, repository method).
# Known blind spots: a repository reached indirectly (a service method called in the loop, or an
# Optional<...Api> module wrapper) is not followed -- the dynamic check covers those at runtime.

# any "Type name;" / "Type name =" declaration; filtered down to repository types afterwards (a
# pattern spelling out the "Repository" suffix backtracks through every capitalized word, ~100x slower)
TYPED_DECLARATION_PATTERN = re.compile(r'\b([A-Z]\w*)\s+([a-z]\w*)\s*[;=]')
LOOP_KEYWORD_PATTERN = re.compile(r'\b(for|while)\s*\(')
DO_LOOP_PATTERN = re.compile(r'\bdo\s*\{')
LAMBDA_ARROW_PATTERN = re.compile(r'->')
METHOD_REFERENCE_PATTERN = re.compile(r'\b(?:this\.)?(\w+)::(\w+)')
METHOD_DECLARATION_PATTERN = re.compile(
    r'(?:(?:public|protected|private|static|final|synchronized|default|abstract)\s+)*(?:<[^>]+>\s+)?[\w.]+(?:<[^;{}()]*>)?(?:\[\])*\s+(\w+)\s*\([^;{}]*\)\s*(?:throws\s+[\w.,\s]+)?\{')
# operations whose function argument runs once per element, wherever they are called
PER_ELEMENT_ALWAYS = {'forEach', 'forEachRemaining', 'removeIf', 'replaceAll', 'toMap', 'groupingBy', 'partitioningBy', 'mapping', 'flatMapping', 'filtering'}
# per element only on a stream: the same names exist on Optional, where they run at most once
PER_ELEMENT_ON_STREAM = {'map', 'flatMap', 'filter', 'peek', 'anyMatch', 'allMatch', 'noneMatch', 'mapToInt', 'mapToLong', 'mapToDouble', 'mapToObj', 'takeWhile',
                         'dropWhile', 'sorted', 'min', 'max', 'reduce', 'collect'}
STREAM_SOURCE_PATTERN = re.compile(r'\.(?:stream|parallelStream|chars|lines)\s*\(|\b(?:Stream|IntStream|LongStream|DoubleStream|StreamSupport)\.')
STREAM_TERMINAL_OPERATIONS = {'max', 'min', 'findFirst', 'findAny', 'reduce', 'collect', 'toList', 'toArray', 'count', 'anyMatch', 'allMatch', 'noneMatch', 'average',
                              'sum'}
NON_QUERYING_REPOSITORY_METHODS ={'getReferenceById', 'getReference'}  # return a proxy without a query
# a Spring Data repository interface; entity classes like AuxiliaryRepository or JGit's Repository share the suffix
REPOSITORY_INTERFACE_PATTERN = re.compile(r'\binterface\s+(\w+)(?:<[^>{]*>)?\s+extends\s+[^{]*?\b\w*Repository\s*<')


# one alternation, so whichever construct starts first wins: a "//" inside a string stays a string
COMMENT_OR_LITERAL_PATTERN = re.compile(r'//[^\n]*|/\*.*?\*/|""".*?"""|"(?:[^"\\\n]|\\.)*"|' + r"'(?:[^'\\\n]|\\.)*'", re.DOTALL)


def blank_comments_and_strings(content):
    """content with comments and the inside of string, text-block and char literals replaced by
    spaces (newlines kept), so every index still points at the same place in the original."""

    def blank(match):
        text = match.group(0)
        if text.startswith('//') or text.startswith('/*'):
            return re.sub(r'[^\n]', ' ', text)
        quote = 3 if text.startswith('"""') else 1
        return text[:quote] + re.sub(r'[^\n]', ' ', text[quote:-quote]) + text[-quote:]

    return COMMENT_OR_LITERAL_PATTERN.sub(blank, content)


def matching_close(code, open_index, open_char, close_char):
    """Index of the bracket closing the one at open_index; code must already be blanked."""
    depth = 0
    for i in range(open_index, len(code)):
        if code[i] == open_char:
            depth += 1
        elif code[i] == close_char:
            depth -= 1
            if depth == 0:
                return i
    return len(code)


def statement_body_end(code, start):
    """End of a brace-less loop body: the first ';' at bracket depth 0."""
    depth = 0
    for i in range(start, len(code)):
        ch = code[i]
        if ch in '({[':
            depth += 1
        elif ch in ')}]':
            depth -= 1
        elif ch == ';' and depth <= 0:
            return i + 1
    return len(code)


def enclosing_call(code, index):
    """(method name, index of its '(') of the call whose argument list contains index, or (None, -1)."""
    depth = 0
    for i in range(index - 1, -1, -1):
        ch = code[i]
        if ch in ')]':
            depth += 1
        elif ch in '([':
            if depth == 0:
                if ch == '[':
                    return None, -1
                j = i - 1
                while j >= 0 and code[j].isspace():
                    j -= 1
                name_end = j + 1
                while j >= 0 and (code[j].isalnum() or code[j] == '_'):
                    j -= 1
                return (code[j + 1:name_end] or None), i
            depth -= 1
        elif ch in '{};' and depth == 0:
            return None, -1
    return None, -1


def statement_start(code, index):
    """Start of the statement containing index: just after the nearest ';', '{' or '}' outside brackets."""
    depth = 0
    for i in range(index - 1, -1, -1):
        ch = code[i]
        if ch in ')]':
            depth += 1
        elif ch in '([':
            depth -= 1
        elif ch in ';{}' and depth <= 0:
            return i + 1
    return 0


def chain_calls(segment):
    """Names of the method calls chained at the top level of segment (inside arguments ignored)."""
    names, depth = [], 0
    for match in re.finditer(r'\.\s*(\w+)\s*\(|[()]', segment):
        token = match.group(0)
        if token == '(':
            depth += 1
        elif token == ')':
            depth = max(0, depth - 1)  # the segment may start inside the source call, e.g. right after ".stream("
        else:
            if depth == 0:
                names.append(match.group(1))
            depth += 1  # the call's own '(' was consumed by this match
    return names


def runs_per_element(code, call_name, call_open):
    if call_name in PER_ELEMENT_ALWAYS:
        return True
    if call_name in PER_ELEMENT_ON_STREAM:
        statement = code[statement_start(code, call_open):call_open]
        sources = list(STREAM_SOURCE_PATTERN.finditer(statement))
        if not sources:
            return False
        # a terminal operation ends the stream: in stream().max(...).flatMap(...) the flatMap is Optional.flatMap
        between = statement[sources[-1].end():].rsplit(call_name, 1)[0]
        return not any(name in STREAM_TERMINAL_OPERATIONS for name in chain_calls(between))
    return False


def lambda_body_span(code, arrow_index, call_open):
    """(start, end) of the lambda body after the arrow: a {...} block, or an expression ending at
    the first ',' or ')' that closes it inside the enclosing call."""
    start = arrow_index + 2
    while start < len(code) and code[start].isspace():
        start += 1
    if start < len(code) and code[start] == '{':
        return start, matching_close(code, start, '{', '}')
    depth = 0
    for i in range(start, len(code)):
        ch = code[i]
        if ch in '({[':
            depth += 1
        elif ch in ')}]':
            if depth == 0:
                return start, i
            depth -= 1
        elif ch in ',;' and depth == 0:
            return start, i
    return start, len(code)


def loop_bodies(code):
    """[(start, end, kind)] for every loop body and per-element lambda in the (blanked) code."""
    bodies = []
    for match in LOOP_KEYWORD_PATTERN.finditer(code):
        header_close = matching_close(code, match.end() - 1, '(', ')')
        after = header_close + 1
        while after < len(code) and code[after].isspace():
            after += 1
        if code.startswith('{', after):
            bodies.append((after, matching_close(code, after, '{', '}'), match.group(1)))
        elif match.group(1) == 'for' or not code.startswith(';', after):  # "} while (...);" ends a do loop
            bodies.append((after, statement_body_end(code, after), match.group(1)))
    for match in DO_LOOP_PATTERN.finditer(code):
        brace = match.end() - 1
        bodies.append((brace, matching_close(code, brace, '{', '}'), 'do'))
    for match in LAMBDA_ARROW_PATTERN.finditer(code):
        call_name, call_open = enclosing_call(code, match.start())
        if call_name and runs_per_element(code, call_name, call_open):
            start, end = lambda_body_span(code, match.start(), call_open)
            bodies.append((start, end, call_name))
    return bodies


def method_spans(code):
    spans = []
    for match in METHOD_DECLARATION_PATTERN.finditer(code):
        if match.group(1) in ('if', 'for', 'while', 'switch', 'catch', 'synchronized', 'return', 'new'):
            continue
        brace = match.end() - 1
        spans.append((brace, matching_close(code, brace, '{', '}'), match.group(1)))
    return spans


def enclosing_method(spans, index):
    inside = [s for s in spans if s[0] <= index <= s[1]]
    return min(inside, key=lambda s: s[1] - s[0])[2] if inside else None


def check_repository_calls_in_loops(file_path, content, repository_types, findings):
    code = blank_comments_and_strings(content)
    repository_fields = {name: type_ for type_, name in TYPED_DECLARATION_PATTERN.findall(code) if type_ in repository_types}
    if not repository_fields:
        return
    class_name = os.path.splitext(os.path.basename(file_path))[0]
    call_pattern = re.compile(r'\b(?:this\.)?(' + '|'.join(map(re.escape, repository_fields)) + r')\s*\.\s*(\w+)\s*\(')
    spans = method_spans(code)
    reported = set()

    def report(index, field, repository_method, loop_kind):
        if repository_method in NON_QUERYING_REPOSITORY_METHODS:
            return
        method = enclosing_method(spans, index)
        member = f"{class_name}.{method}" if method else class_name
        repository_call = f"{repository_fields[field]}.{repository_method}"
        key = f"repository_call_in_loop:{member}:{repository_call}"
        if key in reported:
            return
        reported.add(key)
        line = line_of(content, index)
        detail = f"{repository_call} called once per iteration ({loop_kind})"
        print(f"\n[RepositoryCallInLoop] {member} ({file_path}:{line}): {detail}")
        findings.append({"type": "repository_call_in_loop", "key": key, "file": file_path, "line": line, "member": member, "repositoryMethod": repository_call,
                         "detail": detail, "snippet": content[index:content.find('\n', index)].strip()})

    for start, end, kind in sorted(loop_bodies(code)):
        for call in call_pattern.finditer(code, start, end):
            report(call.start(), call.group(1), call.group(2), kind)
    # method references to a repository passed to a per-element operation: .map(participationRepository::findById)
    for ref in METHOD_REFERENCE_PATTERN.finditer(code):
        if ref.group(1) in repository_fields:
            call_name, call_open = enclosing_call(code, ref.start())
            if call_name and runs_per_element(code, call_name, call_open):
                report(ref.start(), ref.group(1), ref.group(2), call_name)


def check_eager_to_many(entities, entity_files, findings):
    for entity, fields in sorted(entities.items()):
        for field, (kind, target, eager, line) in sorted(fields.items()):
            if kind == 'many' and eager:
                member = f"{entity}.{field}"
                detail = f"to-many association to {target} is FetchType.EAGER"
                print(f"\n[EagerToMany] {member} ({entity_files.get(entity)}:{line}): {detail}")
                findings.append({"type": "eager_to_many", "key": f"eager_to_many:{member}", "file": entity_files.get(entity), "line": line,
                                 "member": member, "entityClass": entity, "detail": detail})


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

    # Yes/no query-shape rules: one pass to learn every entity's associations, one to apply the rules
    entities, superclass_of, entity_files, sources = {}, {}, {}, {}

    def learn(file_path):
        content = sources[file_path] = read(file_path)
        known = set(entities)
        collect_entity_facts(content, entities, superclass_of)
        for entity in set(entities) - known:
            entity_files[entity] = file_path

    for directory in SEARCH_DIRECTORIES:
        scan_directory(directory, learn)
    repository_types = {name for content in sources.values() for name in REPOSITORY_INTERFACE_PATTERN.findall(content)}
    for file_path, content in sources.items():
        check_query_shapes(file_path, content, entities, superclass_of, findings)
        check_repository_calls_in_loops(file_path, content, repository_types, findings)
    check_eager_to_many(entities, entity_files, findings)

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
