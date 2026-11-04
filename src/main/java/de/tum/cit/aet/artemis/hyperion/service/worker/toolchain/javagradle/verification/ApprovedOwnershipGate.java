package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

/**
 * Pure (sandbox-free) gates that hold the final candidate to the approved specification's ownership decisions, which the differential build oracle cannot see: it proves
 * the tests distinguish solution from template, not that students still perform the work the approved specification assigned them. The final candidate maps are checked
 * against the immutable approved Design table and Public API before persistence:
 * <ul>
 * <li><b>Ownership.</b> Student-created types exist only in the reference solution, stubbed types ship in the template, and given types ship byte-identical in both.</li>
 * <li><b>Given-type dependencies.</b> Given code never references a student-created type, which does not exist in the template it ships in.</li>
 * <li><b>TODO anchors.</b> Every stubbed seam owner carries a TODO, and neither given types nor graded tests carry one.</li>
 * <li><b>Diagram requirement.</b> Whether the approved design needs a class diagram in the statement.</li>
 * </ul>
 * Static and side-effect-free like {@link ExerciseIntegrityGate}, so the gates are unit-testable without Docker; the lexical machinery lives in {@link JavaSourceInspector}.
 */
public final class ApprovedOwnershipGate {

    private static final Pattern JAVA_IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

    /** A TODO comment: a line comment, a block or doc comment opening, or a continuation line of one. */
    private static final Pattern TODO_COMMENT = Pattern.compile("(?m)(?://|/\\*+|^\\s*\\*)\\s*TODO\\b");

    /** A type declaration header on masked source; its body starts at the next opening brace. */
    private static final Pattern TYPE_DECLARATION = Pattern.compile("(?<![\\w$])(?:class|interface|enum|record)\\s+([A-Za-z_$][\\w$]*)");

    /** A TODO labelled with an internal seam ID, such as {@code TODO S1:} or {@code TODO (S1)}. */
    private static final Pattern TODO_SEAM_LABEL = Pattern.compile("\\bTODO\\s*[-\\[(:]?\\s*S[1-9][0-9]*\\b");

    private static final Set<String> NON_SOURCE_TYPE_FILE_SUFFIXES = Set.of(".class", ".md", ".orig", ".txt");

    private ApprovedOwnershipGate() {
    }

    /**
     * Whether the statement must show a class diagram, decided from the approved design rather than by the specification author, for whom declining is always cheaper: the
     * exercise's own Design-table types extend or implement one another, or at least two of them are student-owned (stubbed or student-created). A single student-owned type
     * without relations among the exercise's types needs no overview.
     *
     * @param approvedSpec the approved specification
     * @return whether the final statement must contain a PlantUML class diagram
     */
    public static boolean diagramRequired(@Nullable String approvedSpec) {
        if (approvedSpec == null || approvedSpec.isBlank()) {
            return false;
        }
        List<StageChecks.DesignRow> rows = StageChecks.designTableRows(approvedSpec);
        long studentOwned = rows.stream().filter(row -> "stubbed".equals(row.status()) || "student-creates".equals(row.status())).map(StageChecks.DesignRow::type).distinct()
                .count();
        if (studentOwned >= 2) {
            return true;
        }
        Set<String> designTypes = rows.stream().map(StageChecks.DesignRow::type).collect(Collectors.toSet());
        ApprovedStructuralContract.ParseResult contract = ApprovedStructuralContract.parse(approvedSpec, designTypes,
                Set.copyOf(StageChecks.specStudentCreatedTypes(approvedSpec)));
        return contract.valid() && contract.contract().relatesAnyOf(designTypes);
    }

    /**
     * Whether the reference solution declares exactly the approved Java Public API, each type in the exercise package, and would pass the structural oracle written from that
     * contract. The server derives its structural checks from that oracle and looks the types up in the exercise package, so a structural check that fails on such a solution
     * exposes a server seeding defect rather than an authoring error.
     *
     * @param exercisePackage the package the structural checks expect every Design type in
     */
    static boolean solutionMatchesApprovedApi(String approvedSpec, Map<String, String> producedSolutionFiles, @Nullable String exercisePackage) {
        if (approvedSpec == null || !approvedSpec.contains("## Public API")) {
            return false;
        }
        Set<String> designTypes = StageChecks.designTableRows(approvedSpec).stream().map(StageChecks.DesignRow::type).collect(Collectors.toSet());
        ApprovedStructuralContract.ParseResult structuralContract = ApprovedStructuralContract.parse(approvedSpec, designTypes,
                Set.copyOf(StageChecks.specStudentCreatedTypes(approvedSpec)));
        String expectedPackage = Objects.requireNonNullElse(exercisePackage, "");
        // The seeded checks compare what the oracle lists, annotations and modifiers included, so the solution is judged against that oracle rather than signatures alone.
        return structuralContract.valid() && structuralContract.contract().solutionSurfaceReasons(producedSolutionFiles).isEmpty()
                && structuralContract.contract().solutionOracleMismatches(producedSolutionFiles, expectedPackage).isEmpty()
                && designTypes.stream().allMatch(type -> declaredInPackage(producedSolutionFiles, type, expectedPackage));
    }

    /** Whether a source of the package declares the type; a generic Design cell such as {@code Stack<T>} names the type {@code Stack}. */
    private static boolean declaredInPackage(Map<String, String> files, String designType, String expectedPackage) {
        String type = designType.contains("<") ? designType.substring(0, designType.indexOf('<')).strip() : designType.strip();
        return !JAVA_IDENTIFIER.matcher(type).matches() || files.values().stream().filter(Objects::nonNull).anyMatch(
                source -> JavaSourceInspector.sourceDeclaresType(source, type) && expectedPackage.equals(javaPackage(JavaSourceInspector.maskCommentsAndLiterals(source))));
    }

    /**
     * Enforces the approved specification's student/template ownership decisions against the exact repository maps final verification hands to persistence. A differential build
     * cannot detect this violation: tests pass against a fully stubbed template even when the approved exercise required students to create those types themselves.
     */
    static List<String> approvedSpecificationReasons(String approvedSpec, Map<String, String> producedTemplateFiles, Map<String, String> producedSolutionFiles) {
        if (approvedSpec == null || approvedSpec.isBlank()) {
            return List.of();
        }
        List<StageChecks.DesignRow> designRows = StageChecks.designTableRows(approvedSpec);
        List<String> studentCreatedTypes = StageChecks.specStudentCreatedTypes(approvedSpec);
        List<String> reasons = new ArrayList<>();
        if (approvedSpec.contains("## Public API")) {
            Set<String> designTypes = designRows.stream().map(StageChecks.DesignRow::type).collect(Collectors.toSet());
            ApprovedStructuralContract.ParseResult structuralContract = ApprovedStructuralContract.parse(approvedSpec, designTypes, Set.copyOf(studentCreatedTypes));
            if (structuralContract.valid()) {
                reasons.addAll(structuralContract.contract().solutionSurfaceReasons(producedSolutionFiles));
                Set<String> templateTypes = designRows.stream().filter(row -> "given".equals(row.status()) || "stubbed".equals(row.status())).map(StageChecks.DesignRow::type)
                        .collect(Collectors.toSet());
                reasons.addAll(structuralContract.contract().templateSurfaceReasons(producedTemplateFiles, templateTypes));
            }
            else if (!designTypes.isEmpty()) {
                reasons.add("the approved Java Public API structural contract is invalid: " + structuralContract.errors()
                        + ". This frozen contract cannot be replaced by candidate code; repair or restart from a valid specification.");
            }
        }
        List<String> missingFromSolution = studentCreatedTypes.stream().filter(type -> !repositoryDeclaresType(producedSolutionFiles, type)).toList();
        if (!missingFromSolution.isEmpty()) {
            reasons.add("the approved specification requires students to create these types, but the reference solution does not declare them: " + missingFromSolution
                    + ". Implement every approved student-created type in the solution; changing SPEC.md after approval cannot remove the requirement.");
        }
        List<String> leakedIntoTemplate = studentCreatedTypes.stream().filter(type -> repositoryContainsTypeArtifact(producedTemplateFiles, type)).toList();
        if (!leakedIntoTemplate.isEmpty()) {
            reasons.add("the approved specification requires students to create these types, but the template already declares them: " + leakedIntoTemplate
                    + ". Delete their template declarations and leave any necessary guidance in the problem statement or collaborating given types; changing SPEC.md after "
                    + "approval cannot turn the required design work into prebuilt stubs.");
        }
        List<String> missingStubbedTypes = StageChecks.specStubbedTypes(approvedSpec).stream().filter(type -> !repositoryDeclaresType(producedTemplateFiles, type)).toList();
        if (!missingStubbedTypes.isEmpty()) {
            reasons.add("the approved specification marks these types 'stubbed', but the template does not declare them: " + missingStubbedTypes
                    + ". A stubbed type ships in the template as the real signatures with TODO bodies, so the student has something to complete and the graded tests can name "
                    + "it; restore them instead of deleting the tests that need them.");
        }
        List<String> givenDependencies = givenTypeStudentCreatedDependencies(designRows, studentCreatedTypes, producedTemplateFiles, producedSolutionFiles);
        if (!givenDependencies.isEmpty()) {
            reasons.add("the approved specification marks these types 'given', but their code references types students create: " + givenDependencies
                    + ". A given type ships byte-identical in the template, where a student-created type does not exist, so the template cannot compile, and adding that "
                    + "type or a placeholder for it to the template is rejected. Keep given code limited to given and stubbed types, for example by working through a given "
                    + "or stubbed abstraction the approved API already exposes; do not hide the reference behind reflection or Object. If a given type cannot do its approved "
                    + "job without a student-created type, the ownership is contradictory: that type must be 'stubbed' (its dependent bodies become TODOs) or "
                    + "'student-creates' in the specification.");
        }
        List<String> divergentGivenTypes = designRows.stream().filter(row -> "given".equals(row.status())).map(StageChecks.DesignRow::type)
                .filter(type -> !canonicalGivenFileMatches(type, producedTemplateFiles, producedSolutionFiles)).toList();
        if (!divergentGivenTypes.isEmpty()) {
            List<String> firstDifferences = divergentGivenTypes.stream().map(type -> canonicalGivenFileDifference(type, producedTemplateFiles, producedSolutionFiles)).toList();
            reasons.add("the approved specification marks these as given types, but their canonical Java source is not byte-for-byte identical in the solution and template: "
                    + divergentGivenTypes + ". First differences: " + firstDifferences
                    + ". A given type is supplied rather than student work. The solution is the canonical complete implementation: repair its public documentation first when "
                    + "needed, then copy the whole canonical file to the template. Never delete solution documentation or weaken either copy merely to make the bytes match.");
        }
        return List.copyOf(reasons);
    }

    /**
     * Finds given types whose source names a student-created type outside comments and literals. Both repositories are read: the solution copy shows the dependency before
     * the template copy exists, and the template copy is the one that fails to compile. Signature-level dependencies are already rejected at the specification gate; this
     * catches the bodies, imports, and private members the specification does not list.
     *
     * @return {@code Given->StudentCreated} pairs in Design-table order
     */
    static List<String> givenTypeStudentCreatedDependencies(List<StageChecks.DesignRow> designRows, List<String> studentCreatedTypes, Map<String, String> templateFiles,
            Map<String, String> solutionFiles) {
        if (studentCreatedTypes.isEmpty()) {
            return List.of();
        }
        // A student-created type is resolved against its declaration in the reference solution; without one, no simple name can be shown to mean it.
        Map<String, String> createdTypePackages = new LinkedHashMap<>();
        studentCreatedTypes.forEach(created -> solutionFiles.values().stream().filter(Objects::nonNull).filter(source -> JavaSourceInspector.sourceDeclaresType(source, created))
                .findFirst().ifPresent(source -> createdTypePackages.put(created, javaPackage(JavaSourceInspector.maskCommentsAndLiterals(source)))));
        List<String> dependencies = new ArrayList<>();
        designRows.stream().filter(row -> "given".equals(row.status())).map(StageChecks.DesignRow::type).filter(type -> JAVA_IDENTIFIER.matcher(type).matches()).forEach(given -> {
            String suffix = "/" + given + ".java";
            List<String> sources = java.util.stream.Stream
                    .concat(canonicalJavaFiles(templateFiles, given, suffix).stream(), canonicalJavaFiles(solutionFiles, given, suffix).stream())
                    .map(entry -> JavaSourceInspector.maskCommentsAndLiterals(entry.getValue())).toList();
            createdTypePackages.forEach((created, createdPackage) -> {
                if (!created.equals(given) && sources.stream().anyMatch(source -> referencesType(source, created, createdPackage))) {
                    dependencies.add(given + "->" + created);
                }
            });
        });
        return List.copyOf(dependencies);
    }

    private static final Pattern PACKAGE_DECLARATION = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");

    private static final Pattern IMPORT_DECLARATION = Pattern.compile("(?m)^\\s*import\\s+(static\\s+)?([\\w.]+?)(\\s*\\.\\s*\\*)?\\s*;");

    private static String javaPackage(String maskedSource) {
        Matcher declaration = PACKAGE_DECLARATION.matcher(maskedSource);
        return declaration.find() ? declaration.group(1) : "";
    }

    /**
     * Whether a source refers to the given top-level type: by its fully qualified name, or by a simple-name use that resolves to it. A simple name must be visible (same package,
     * or imported by name or on demand), not shadowed by a single-type import of another type with that name, and used unqualified, so {@code Map.Entry} never means a
     * student's {@code Entry}.
     */
    private static boolean referencesType(String maskedSource, String simpleName, String typePackage) {
        if (!typePackage.isEmpty()) {
            List<String> qualifiedNameParts = java.util.Arrays.stream((typePackage + "." + simpleName).split("\\.")).map(Pattern::quote).toList();
            if (Pattern.compile("(?<![\\w$.])" + String.join("\\s*\\.\\s*", qualifiedNameParts) + "(?![\\w$])").matcher(maskedSource).find()) {
                return true;
            }
        }
        boolean visible = typePackage.equals(javaPackage(maskedSource));
        Matcher imports = IMPORT_DECLARATION.matcher(maskedSource);
        while (imports.find()) {
            if (imports.group(1) != null) {
                continue;
            }
            String imported = imports.group(2);
            if (imports.group(3) != null) {
                visible |= imported.equals(typePackage);
            }
            else if (imported.equals(simpleName) || imported.endsWith("." + simpleName)) {
                if (!imported.equals(typePackage.isEmpty() ? simpleName : typePackage + "." + simpleName)) {
                    return false;
                }
                visible = true;
            }
        }
        return visible && Pattern.compile("(?<![\\w$.])" + Pattern.quote(simpleName) + "(?![\\w$])").matcher(maskedSource).find();
    }

    private static boolean canonicalGivenFileMatches(String type, Map<String, String> templateFiles, Map<String, String> solutionFiles) {
        if (!JAVA_IDENTIFIER.matcher(type).matches()) {
            return true;
        }
        String suffix = "/" + type + ".java";
        List<Map.Entry<String, String>> templateMatches = canonicalJavaFiles(templateFiles, type, suffix);
        List<Map.Entry<String, String>> solutionMatches = canonicalJavaFiles(solutionFiles, type, suffix);
        return templateMatches.size() == 1 && solutionMatches.size() == 1 && templateMatches.getFirst().getKey().equals(solutionMatches.getFirst().getKey())
                && templateMatches.getFirst().getValue().equals(solutionMatches.getFirst().getValue());
    }

    private static String canonicalGivenFileDifference(String type, Map<String, String> templateFiles, Map<String, String> solutionFiles) {
        String suffix = "/" + type + ".java";
        List<Map.Entry<String, String>> templateMatches = canonicalJavaFiles(templateFiles, type, suffix);
        List<Map.Entry<String, String>> solutionMatches = canonicalJavaFiles(solutionFiles, type, suffix);
        if (templateMatches.size() != 1 || solutionMatches.size() != 1) {
            return type + " (expected one canonical file in each repository; found solution=" + solutionMatches.size() + ", template=" + templateMatches.size() + ")";
        }
        Map.Entry<String, String> solution = solutionMatches.getFirst();
        Map.Entry<String, String> template = templateMatches.getFirst();
        if (!solution.getKey().equals(template.getKey())) {
            return type + " (path: solution " + solution.getKey() + ", template " + template.getKey() + ")";
        }
        List<String> solutionLines = solution.getValue().lines().toList();
        List<String> templateLines = template.getValue().lines().toList();
        int shared = Math.min(solutionLines.size(), templateLines.size());
        for (int index = 0; index < shared; index++) {
            if (!solutionLines.get(index).equals(templateLines.get(index))) {
                return type + " (line " + (index + 1) + ": solution `" + boundedLine(solutionLines.get(index)) + "`, template `" + boundedLine(templateLines.get(index)) + "`)";
            }
        }
        return type + " (line " + (shared + 1) + ": solution has " + solutionLines.size() + " lines, template has " + templateLines.size() + ")";
    }

    private static String boundedLine(String line) {
        String stripped = line.strip();
        return stripped.length() <= 120 ? stripped : stripped.substring(0, 117) + "...";
    }

    private static List<Map.Entry<String, String>> canonicalJavaFiles(Map<String, String> files, String type, String suffix) {
        if (files == null) {
            return List.of();
        }
        return files.entrySet().stream().filter(entry -> {
            String path = entry.getKey().replace('\\', '/');
            return (path.equals(type + ".java") || path.endsWith(suffix)) && JavaSourceInspector.sourceDeclaresType(entry.getValue(), type);
        }).toList();
    }

    /**
     * Verifies the template's TODO anchors against the approved specification. The Testing Strategy, not the comment text, links each seam to its owner type, so a TODO carries
     * only the task wording: a seam ID would be a label students see but the statement never defines. Every stubbed seam owner carries a TODO in its declaring source. A TODO
     * in a given type marks work in code that ships complete, and one in the graded tests is an unfinished grader, so both are rejected; other student-facing files, such as
     * student test skeletons or helpers outside the Design table, may carry TODOs.
     */
    static List<String> templateTodoSeamReasons(String specification, Map<String, String> templateFiles, Map<String, String> testsFiles) {
        List<StageChecks.TestingStrategyRow> rows = StageChecks.testingStrategyRows(specification == null ? "" : specification);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<String, String> statusByType = StageChecks.designTableRows(specification).stream().filter(row -> row.status() != null)
                .collect(Collectors.toMap(StageChecks.DesignRow::type, StageChecks.DesignRow::status, (first, ignored) -> first, LinkedHashMap::new));
        Map<String, String> javaFiles = templateFiles == null ? Map.of()
                : templateFiles.entrySet().stream().filter(entry -> JavaSourceInspector.isJavaSource(entry.getKey()) && entry.getValue() != null)
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, ignored) -> first, LinkedHashMap::new));
        List<String> reasons = new ArrayList<>();
        List<String> labelledFiles = javaFiles.entrySet().stream().filter(entry -> TODO_SEAM_LABEL.matcher(entry.getValue()).find()).map(Map.Entry::getKey).sorted().toList();
        if (!labelledFiles.isEmpty()) {
            reasons.add("the template labels TODOs with internal seam IDs in " + labelledFiles + ". Students never see these IDs defined, so write each as '// TODO: <task "
                    + "wording>' without the ID; the approved Testing Strategy already links every seam to its owner.");
        }
        Set<String> stubbedTypes = typesWithStatus(statusByType, "stubbed");
        Set<String> givenTypes = typesWithStatus(statusByType, "given");
        Set<String> designTypes = new LinkedHashSet<>(stubbedTypes);
        designTypes.addAll(givenTypes);
        // Each TODO comment belongs to the innermost given or stubbed declaration around it; one outside every such declaration belongs to the whole file.
        Map<String, List<Set<String>>> todoOwnersByFile = new LinkedHashMap<>();
        javaFiles.forEach((path, source) -> {
            List<Set<String>> owners = todoOwners(source, designTypes);
            if (!owners.isEmpty()) {
                todoOwnersByFile.put(path, owners);
            }
        });
        for (StageChecks.TestingStrategyRow row : rows) {
            if (!"stubbed".equals(statusByType.get(row.ownerType()))) {
                continue; // a student-created owner is absent; a malformed owner/status is rejected by the specification gate
            }
            // Only a TODO inside the owner's own body anchors it; one outside every declaration could stand for any type of the file.
            boolean anchored = todoOwnersByFile.values().stream().flatMap(List::stream).anyMatch(owners -> owners.contains(row.ownerType()));
            if (!anchored) {
                reasons.add("the stubbed owner " + row.ownerType() + " of seam " + row.seamId() + " has no '// TODO: ...' inside its declaration in its declaring source. Put "
                        + "the TODO at the unfinished student work inside the owner's body. If its student-owned members cannot be declared without an omitted student-created type, keep a "
                        + "compile-safe empty owner class and put the TODO once in the class body where those members will be added; do not restore the absent type or change "
                        + "SPEC.md.");
            }
        }
        List<String> givenFiles = todoOwnersByFile.entrySet().stream().filter(entry -> entry.getValue().stream().anyMatch(owners -> {
            Set<String> fileTypes = owners.isEmpty()
                    ? designTypes.stream().filter(type -> JavaSourceInspector.sourceDeclaresType(javaFiles.get(entry.getKey()), type)).collect(Collectors.toSet())
                    : owners;
            return fileTypes.stream().anyMatch(givenTypes::contains) && fileTypes.stream().noneMatch(stubbedTypes::contains);
        })).map(Map.Entry::getKey).sorted().toList();
        if (!givenFiles.isEmpty()) {
            reasons.add("the template has TODOs in given types: " + givenFiles + ". Given types ship complete, so a TODO there points students at work that does not exist; "
                    + "remove it, or move it to the stubbed owner the work belongs to. A student-created type's work is anchored by its statement task and tests instead.");
        }
        List<String> testTodoFiles = testsFiles == null ? List.of()
                : testsFiles.entrySet().stream().filter(entry -> JavaSourceInspector.isJavaSource(entry.getKey()) && entry.getValue() != null)
                        .filter(entry -> TODO_COMMENT.matcher(commentsOnly(entry.getValue())).find()).map(Map.Entry::getKey).sorted().toList();
        if (!testTodoFiles.isEmpty()) {
            reasons.add(
                    "the graded tests contain TODOs: " + testTodoFiles + ". The tests are finished grading code, not student work; complete or remove what each TODO " + "marks.");
        }
        return List.copyOf(reasons);
    }

    /**
     * The owners of each TODO comment in a source: the innermost given or stubbed type whose declaration body contains it, as a singleton, or an empty set when the TODO lies
     * outside every such declaration. A TODO-like text inside a string literal is not a comment and is not counted.
     */
    private static List<Set<String>> todoOwners(String source, Set<String> designTypes) {
        List<Integer> todoPositions = TODO_COMMENT.matcher(commentsOnly(source)).results().map(java.util.regex.MatchResult::start).toList();
        if (todoPositions.isEmpty()) {
            return List.of();
        }
        String masked = JavaSourceInspector.maskCommentsAndLiterals(source);
        List<DeclarationBody> bodies = new ArrayList<>();
        Matcher declaration = TYPE_DECLARATION.matcher(masked);
        while (declaration.find()) {
            int open = masked.indexOf('{', declaration.end());
            if (designTypes.contains(declaration.group(1)) && open >= 0) {
                int close = matchingBrace(masked, open);
                bodies.add(new DeclarationBody(declaration.group(1), open, close < 0 ? masked.length() : close));
            }
        }
        return todoPositions.stream().map(position -> bodies.stream().filter(body -> body.open() < position && position < body.close())
                .max(java.util.Comparator.comparingInt(DeclarationBody::open)).map(body -> Set.of(body.type())).orElse(Set.of())).toList();
    }

    private record DeclarationBody(String type, int open, int close) {
    }

    /** The source with everything but its comments blanked, keeping positions and line breaks, so a TODO inside a string literal is not taken for a comment. */
    private static String commentsOnly(String source) {
        String code = JavaSourceInspector.stripJavaComments(source);
        if (code.length() != source.length()) {
            return source;
        }
        StringBuilder comments = new StringBuilder(source.length());
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            comments.append(current == '\n' || current == '\r' || current != code.charAt(index) ? current : ' ');
        }
        return comments.toString();
    }

    private static int matchingBrace(String masked, int open) {
        int depth = 0;
        for (int index = open; index < masked.length(); index++) {
            char current = masked.charAt(index);
            if (current == '{') {
                depth++;
            }
            else if (current == '}' && --depth == 0) {
                return index;
            }
        }
        return -1;
    }

    private static Set<String> typesWithStatus(Map<String, String> statusByType, String status) {
        return statusByType.entrySet().stream().filter(entry -> status.equals(entry.getValue())).map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    private static boolean repositoryDeclaresType(Map<String, String> files, String type) {
        if (files == null || files.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, String> file : files.entrySet()) {
            String lowerPath = file.getKey().toLowerCase(Locale.ROOT);
            if (lowerPath.startsWith("target/") || lowerPath.startsWith("build/") || NON_SOURCE_TYPE_FILE_SUFFIXES.stream().anyMatch(lowerPath::endsWith)) {
                continue;
            }
            if (JavaSourceInspector.sourceDeclaresType(file.getValue(), type)) {
                return true;
            }
        }
        return false;
    }

    /** A student-owned type must leave behind neither a declaration nor a source file named after it. */
    private static boolean repositoryContainsTypeArtifact(Map<String, String> files, String type) {
        if (files == null || files.isEmpty()) {
            return false;
        }
        return files.entrySet().stream().anyMatch(file -> {
            String normalizedPath = file.getKey().replace('\\', '/');
            String lowerPath = normalizedPath.toLowerCase(Locale.ROOT);
            if (lowerPath.startsWith("target/") || lowerPath.startsWith("build/") || NON_SOURCE_TYPE_FILE_SUFFIXES.stream().anyMatch(lowerPath::endsWith)) {
                return false;
            }
            return pathOrContentRepresentsType(normalizedPath, file.getValue(), type);
        });
    }

    /** Shared by the prospective-write guard and final repository verification, so a write rejected in-loop is also rejected at acceptance. */
    static boolean pathOrContentRepresentsType(String path, String content, String type) {
        String normalizedPath = path.replace('\\', '/');
        String fileName = normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1);
        return fileName.startsWith(type + ".") || JavaSourceInspector.sourceDeclaresType(content, type);
    }
}
