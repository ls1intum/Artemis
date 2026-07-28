package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import java.util.ArrayList;
import java.util.HashSet;
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

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.GenerationWorkspaceService;

/**
 * Pure (sandbox-free) correctness gates {@link DifferentialVerificationService} applies on top of the differential build oracle, catching broken-exercise classes the build cannot
 * see:
 * <ul>
 * <li><b>Harness tampering.</b> The seeded tests-repo build/harness/manifest files are graded verbatim in production, so a sandbox build can pass while production fails. The
 * snapshot taken at seed time must survive generation unchanged, modulo only the CI checkout-placeholder substitution the pipeline itself applies.</li>
 * <li><b>Solution leak.</b> The template ships to students, so a reference implementation copied into a non-graded template path hands them the answer while the build still
 * passes. Shared interfaces/headers and git config legitimately identical in both repositories are not flagged; a graded-path copy makes the template pass and is left to the
 * differential oracle.</li>
 * <li><b>Specification contract loss.</b> The differential proves the tests distinguish solution from template, not that students still perform the work the approved
 * specification assigned them, so the final candidate maps are checked against the immutable approved ownership decisions before persistence.</li>
 * </ul>
 * Static and side-effect-free, so the gates are unit-testable without Docker and the residue strip can be reused by {@link GenerationWorkspaceService} on read-back. Everything
 * here states a policy; the lexical machinery those policies need lives in {@link JavaSourceInspector}.
 */
public final class ExerciseIntegrityGate {

    private static final Pattern TODO_SEAM = Pattern.compile("\\bTODO\\s+(S[1-9][0-9]*)\\s*:");

    /**
     * The sibling repositories CI lays out next to each other, never legitimate top-level source folders. A file whose first path component is one of these duplicates the CI
     * layout inside a single repository and is orphan residue: stripped on read-back, never counted as a harness or source file.
     */
    private static final Set<String> CI_CHECKOUT_DIRECTORY_NAMES = Set.of("assignment", "solution", "template", "tests");

    /**
     * Exact basenames of build/harness/manifest files in the tests repository, graded verbatim in production so the agent must not change them. Matched case-insensitively.
     */
    private static final Set<String> HARNESS_FILE_NAMES = Set.of("pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "gradle.properties",
            "package.json", "package-lock.json", "pnpm-lock.yaml", "yarn.lock", "tsconfig.json", "jest.config.js", "jest.config.ts", "cargo.toml", "cargo.lock", "cabal.project",
            "stack.yaml", "stack.yaml.lock", "dune", "dune-project", "rakefile", "gemfile", "gemfile.lock", "pubspec.yaml", "pubspec.lock", "go.mod", "go.sum", "package.swift",
            "cmakelists.txt", "tests.py", "run.sh", "build.sh", "makefile", "description", "namespace", "assignment_path.rb", "test_helper.rb", ".clang-format");

    /** Filename suffixes that always denote a build/harness/manifest file regardless of basename. Matched case-insensitively. */
    private static final List<String> HARNESS_FILE_SUFFIXES = List.of(".cabal", ".csproj", ".fsproj", ".vbproj", ".sln");

    /** Randomness constructs whose only purpose is to make one run differ from the next; a seeded generator ({@code new Random(42)}) is deliberately absent. */
    private static final List<String> NONDETERMINISM_SOURCES = List.of("Collections.shuffle", "Math.random()", "new Random()", "ThreadLocalRandom", "UUID.randomUUID()");

    private static final Set<String> NON_SOURCE_TYPE_FILE_SUFFIXES = Set.of(".class", ".md", ".orig", ".txt");

    private ExerciseIntegrityGate() {
    }

    private static String firstComponent(String path) {
        int slash = path.indexOf('/');
        return slash < 0 ? path : path.substring(0, slash);
    }

    private static String basename(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    static boolean isResidueOutsideCanonicalRoot(String path) {
        return CI_CHECKOUT_DIRECTORY_NAMES.contains(firstComponent(path).toLowerCase(Locale.ROOT));
    }

    /**
     * Strips orphan residue from a TEMPLATE or SOLUTION file map, preserving canonical-root files and their order.
     *
     * @param files the produced files keyed by repository-relative path
     * @return the same map without residue files
     */
    public static Map<String, String> stripResidueOutsideCanonicalRoots(Map<String, String> files) {
        Map<String, String> cleaned = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : files.entrySet()) {
            if (!isResidueOutsideCanonicalRoot(entry.getKey())) {
                cleaned.put(entry.getKey(), entry.getValue());
            }
        }
        return cleaned;
    }

    /**
     * Whether a path names a build/harness/manifest file, graded verbatim in production and therefore immutable to the agent.
     *
     * @param path the repository-relative path
     * @return {@code true} if the file is part of the immutable harness
     */
    public static boolean isHarnessFile(String path) {
        String base = basename(path).toLowerCase(Locale.ROOT);
        if (HARNESS_FILE_NAMES.contains(base)) {
            return true;
        }
        for (String suffix : HARNESS_FILE_SUFFIXES) {
            if (base.endsWith(suffix)) {
                return true;
            }
        }
        // Only root-level YAML is a build descriptor; a nested *.yml is test data.
        return (base.endsWith(".yml") || base.endsWith(".yaml")) && !path.contains("/");
    }

    /** Applies only the checkout-placeholder substitutions the build pipeline itself performs. */
    static String normalizeLayoutLine(String line) {
        return line.replace("${studentWorkingDirectory}", "/assignment/src").replace("${studentParentWorkingDirectoryName}", "assignment")
                .replace("${solutionWorkingDirectory}", "assignment").replace("${testWorkingDirectory}", ".");
    }

    private static List<String> normalizedLines(String content) {
        List<String> lines = new ArrayList<>();
        for (String line : content.replace("\r\n", "\n").split("\n", -1)) {
            lines.add(normalizeLayoutLine(line));
        }
        return lines;
    }

    /**
     * Detects harness tampering (see class javadoc): seeded harness files must stay byte-equivalent after line-ending and checkout-placeholder normalization. An empty snapshot
     * disables the gate for harness-free languages, unless {@code requireNonEmptySnapshot} marks it as a failed capture that must fail closed.
     */
    static List<String> harnessTamperingReasons(Map<String, String> seedTestsFiles, Map<String, String> producedTestsFiles, boolean requireNonEmptySnapshot) {
        return harnessTamperingReasons("tests", seedTestsFiles, producedTestsFiles, requireNonEmptySnapshot);
    }

    static List<String> harnessTamperingReasons(String repository, Map<String, String> seedFiles, Map<String, String> producedFiles, boolean requireNonEmptySnapshot) {
        if (seedFiles == null || seedFiles.isEmpty()) {
            if (requireNonEmptySnapshot) {
                return List.of("the seeded test-harness snapshot is empty, so harness immutability could not be verified. This language always ships a build harness "
                        + "(e.g. pom.xml/build.gradle), so an empty snapshot means the tests repository was not captured — a failed read-back, not a harness-free exercise. "
                        + "Fail closed rather than accept on that doubt; this is usually transient, so retry the generation.");
            }
        }
        List<String> reasons = new ArrayList<>();
        for (Map.Entry<String, String> seed : seedFiles == null ? Map.<String, String>of().entrySet() : seedFiles.entrySet()) {
            String path = seed.getKey();
            if (!isHarnessFile(path)) {
                continue;
            }
            String produced = producedFiles == null ? null : producedFiles.get(path);
            if (produced == null) {
                reasons.add("you deleted the seeded harness file " + repository + "/" + path + "; restore it unchanged.");
                continue;
            }
            List<String> seedNormalized = normalizedLines(seed.getValue());
            List<String> producedNormalized = normalizedLines(produced);
            if (!seedNormalized.equals(producedNormalized)) {
                reasons.add("you modified the seeded harness file " + repository + "/" + path
                        + "; the harness is graded verbatim in production, so changing dependencies, plugins, scripts, lockfiles, or build layout can make verified results differ "
                        + "from real grading — restore " + repository + "/" + path + " to the seed.");
            }
        }
        if (producedFiles != null) {
            for (String path : producedFiles.keySet()) {
                if (isHarnessFile(path) && (seedFiles == null || !seedFiles.containsKey(path))) {
                    reasons.add("you added the harness file " + repository + "/" + path + "; build manifests and scripts are fixed by the seeded exercise scaffold.");
                }
            }
        }
        return reasons;
    }

    /**
     * Rejects an adaptation that retains none of the exercise's existing graded test names; partial test changes remain allowed. Fails open on an empty baseline (generate, or a
     * never-graded exercise). Post-loop only: the baseline comes from the pre-adapt persisted state the agent loop does not have mid-session.
     */
    static List<String> adaptWipedGradedTestsReasons(Set<String> baselineGradedTestNames, List<String> producedSolutionTestNames) {
        if (baselineGradedTestNames == null || baselineGradedTestNames.isEmpty()) {
            return List.of();
        }
        Set<String> baseline = new HashSet<>();
        for (String name : baselineGradedTestNames) {
            if (name != null) {
                String normalized = ProblemStatementBindingChecker.normalizeTestName(name);
                if (!normalized.isEmpty()) {
                    baseline.add(normalized);
                }
            }
        }
        if (baseline.isEmpty()) {
            return List.of();
        }
        Set<String> produced = new HashSet<>();
        if (producedSolutionTestNames != null) {
            for (String name : producedSolutionTestNames) {
                if (name != null) {
                    produced.add(ProblemStatementBindingChecker.normalizeTestName(name));
                }
            }
        }
        for (String baselineName : baseline) {
            if (produced.contains(baselineName)) {
                return List.of();
            }
        }
        return List.of("this adapt retained NONE of the exercise's " + baseline.size() + " previously-graded test(s) (e.g. " + sampleNames(baseline)
                + "), so the graded coverage was wiped and rebuilt from scratch — that is a from-scratch regeneration masquerading as an adapt, not a refinement of the existing "
                + "exercise. Keep and adjust the existing graded tests (retain at least the ones still relevant) instead of deleting them all and authoring a brand-new suite.");
    }

    /**
     * Enforces the approved specification's student/template ownership decisions against the exact repository maps final verification hands to persistence. A differential build
     * cannot detect this violation: tests pass against a fully stubbed template even when the approved exercise required students to create those types themselves.
     */
    static List<String> approvedSpecificationReasons(String approvedSpec, Map<String, String> producedTemplateFiles, Map<String, String> producedSolutionFiles) {
        if (approvedSpec == null || approvedSpec.isBlank()) {
            return List.of();
        }
        List<StageCheckService.DesignRow> designRows = StageCheckService.designTableRows(approvedSpec);
        List<String> studentCreatedTypes = StageCheckService.specStudentCreatedTypes(approvedSpec);
        List<String> reasons = new ArrayList<>();
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
        // An all-student-creates design defeats the differential itself: the empty template "compiles" (no sources) and "fails every test" (none run), so the degenerate candidate
        // satisfies the very checks meant to reject it. Duplicated from the stage gate because repair attempts do not re-run the staged gates but do reach acceptance.
        // Requires at least one parsed row: with none there is no evidence of a design at all, and an unparseable or absent '## Design' table would otherwise be rejected as if it
        // had marked every type 'student-creates' — a claim the specification never made. Doubt on read-back leaves this contract inert, as it does for a blank specification.
        if (!designRows.isEmpty() && designRows.stream().noneMatch(row -> "given".equals(row.status()) || "stubbed".equals(row.status()))) {
            reasons.add("the approved specification marks every type 'student-creates', so the template ships no starting scaffold and students would clone an empty project. "
                    + "Supply at least one type as 'given' or 'stubbed' so the exercise has a teaching scaffold the differential can actually discriminate.");
        }
        List<String> missingStubbedTypes = StageCheckService.specStubbedTypes(approvedSpec).stream().filter(type -> !repositoryDeclaresType(producedTemplateFiles, type)).toList();
        if (!missingStubbedTypes.isEmpty()) {
            reasons.add("the approved specification marks these types 'stubbed', but the template does not declare them: " + missingStubbedTypes
                    + ". A stubbed type ships in the template as the real signatures with TODO bodies, so the student has something to complete and the graded tests can name "
                    + "it; restore them instead of deleting the tests that need them.");
        }
        List<String> divergentGivenTypes = designRows.stream().filter(row -> "given".equals(row.status())).map(StageCheckService.DesignRow::type)
                .filter(type -> !canonicalGivenFileMatches(type, producedTemplateFiles, producedSolutionFiles)).toList();
        if (!divergentGivenTypes.isEmpty()) {
            reasons.add("the approved specification marks these as given types, but their canonical Java source is not byte-for-byte identical in the solution and template: "
                    + divergentGivenTypes
                    + ". A given type is supplied rather than student work. Keep exactly the same canonical <Type>.java file in both repositories; repair both copies together "
                    + "instead of weakening the starter merely to make a test fail.");
        }
        return List.copyOf(reasons);
    }

    private static boolean canonicalGivenFileMatches(String type, Map<String, String> templateFiles, Map<String, String> solutionFiles) {
        if (!type.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
            return true;
        }
        String suffix = "/" + type + ".java";
        List<Map.Entry<String, String>> templateMatches = canonicalJavaFiles(templateFiles, type, suffix);
        List<Map.Entry<String, String>> solutionMatches = canonicalJavaFiles(solutionFiles, type, suffix);
        return templateMatches.size() == 1 && solutionMatches.size() == 1 && templateMatches.getFirst().getKey().equals(solutionMatches.getFirst().getKey())
                && templateMatches.getFirst().getValue().equals(solutionMatches.getFirst().getValue());
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

    static List<String> approvedTestPlanReasons(String approvedSpec, String testPlanJson, List<String> verifiedTestNames) {
        return approvedTestPlanReasons(approvedSpec, testPlanJson, verifiedTestNames, true, Set.of());
    }

    static List<String> approvedTestPlanReasons(String approvedSpec, String testPlanJson, List<String> verifiedTestNames, boolean hasDueDate) {
        return approvedTestPlanReasons(approvedSpec, testPlanJson, verifiedTestNames, hasDueDate, Set.of());
    }

    /**
     * Ensures plan traceability, approved grading emphasis, formative visibility, and due-date-compatible hidden coverage survive into the final candidate. Structural tests are
     * identified from the server-seeded authority rather than a forgeable name pattern.
     */
    static List<String> approvedTestPlanReasons(String approvedSpec, String testPlanJson, List<String> verifiedTestNames, boolean hasDueDate,
            Set<String> seededStructuralTestNames) {
        if (testPlanJson == null || testPlanJson.isBlank()) {
            if (approvedSpec == null || approvedSpec.isBlank()) {
                return List.of();
            }
            return List.of("the approved specification has no valid test-plan.json in the final candidate. Write the grading plan with the exact verified test names, weights, "
                    + "and visibility decisions before submitting.");
        }
        GeneratedTestPlan plan;
        try {
            plan = GeneratedTestPlan.parse(testPlanJson);
        }
        catch (IllegalArgumentException exception) {
            return List.of("the final test-plan.json is invalid: " + exception.getMessage());
        }
        Set<String> knownNames = verifiedTestNames == null ? Set.of() : Set.copyOf(verifiedTestNames);
        List<String> unknownNames = plan.tests().stream().map(GeneratedTestPlan.Entry::name).filter(name -> !knownNames.contains(name)).toList();
        if (!unknownNames.isEmpty()) {
            return List.of("the final test-plan.json names tests the verifier did not run: " + unknownNames + ". Use only exact verified test names: " + knownNames + ".");
        }
        List<String> plannedBuildGates = plan.tests().stream().map(GeneratedTestPlan.Entry::name).filter(BuildGateTestNames::isBuildGate).sorted().toList();
        if (!plannedBuildGates.isEmpty()) {
            return List.of("the final test-plan.json includes build-gate test(s) " + plannedBuildGates
                    + ". Build gates are zero-weight infrastructure checks and cannot satisfy an approved learning seam, visible evidence, or hidden coverage.");
        }
        Set<String> structuralNames = seededStructuralTestNames == null ? Set.of() : Set.copyOf(seededStructuralTestNames);
        List<String> plannedStructuralTests = plan.tests().stream().map(GeneratedTestPlan.Entry::name).filter(structuralNames::contains).sorted().toList();
        if (!plannedStructuralTests.isEmpty()) {
            return List.of("the final test-plan.json includes server-seeded structural test(s) " + plannedStructuralTests
                    + ". Remove them from the agent-authored plan; Artemis keeps structural checks ALWAYS visible and zero-weight, and they cannot stand in for a behavioral "
                    + "witness.");
        }
        List<String> unplannedNames = knownNames.stream().filter(name -> !BuildGateTestNames.isBuildGate(name)).filter(name -> !structuralNames.contains(name))
                .filter(name -> plan.tests().stream().noneMatch(entry -> entry.name().equals(name))).sorted().toList();
        if (!unplannedNames.isEmpty()) {
            return List.of("the final test-plan.json omits verified gradable test(s) " + unplannedNames
                    + ". Map every agent-authored behavioral test to one approved seam so no test bypasses the approved weight, visibility, or statement traceability. "
                    + "Server-seeded structural checks are managed separately and must not be added to the plan.");
        }
        List<StageCheckService.TestingStrategyRow> declaredRows = StageCheckService.testingStrategyRows(approvedSpec);
        List<String> declaredSeams = declaredRows.stream().map(StageCheckService.TestingStrategyRow::seamId).filter(id -> id.matches("S[1-9][0-9]*")).distinct().toList();
        // Seam traceability is enforced exactly where the specification declares seams; a candidate without them (an adaptation of a hand-authored exercise) is not made to invent
        // metadata that has nowhere to be persisted.
        if (!declaredSeams.isEmpty()) {
            List<String> entriesWithoutSeams = plan.tests().stream().filter(entry -> entry.seam().isBlank()).map(GeneratedTestPlan.Entry::name).toList();
            if (!entriesWithoutSeams.isEmpty()) {
                return List.of("the final test-plan.json has no seam for test(s) " + entriesWithoutSeams + ". Map every generated test to one approved Testing Strategy ID: "
                        + declaredSeams + ".");
            }
            List<String> undeclaredSeams = plan.tests().stream().map(GeneratedTestPlan.Entry::seam).filter(seam -> !declaredSeams.contains(seam)).distinct().toList();
            if (!undeclaredSeams.isEmpty()) {
                return List.of("the final test-plan.json uses seam(s) the approved Testing Strategy never declared: " + undeclaredSeams + ". Use only " + declaredSeams + ".");
            }
            Map<String, Double> weightBySeam = declaredRows.stream().filter(row -> row.weightTier().matches("[123]"))
                    .collect(Collectors.toMap(StageCheckService.TestingStrategyRow::seamId, row -> Double.parseDouble(row.weightTier()), (first, ignored) -> first));
            List<String> wrongWeights = plan.tests().stream().filter(entry -> weightBySeam.containsKey(entry.seam()))
                    .filter(entry -> Double.compare(entry.seamWeightTier(), weightBySeam.get(entry.seam())) != 0)
                    .map(entry -> entry.name() + "=" + entry.seamWeightTier() + " (seam " + entry.seam() + " requires " + weightBySeam.get(entry.seam()).intValue() + ")").toList();
            if (!wrongWeights.isEmpty()) {
                return List.of("the final test-plan.json weights do not match the approved Testing Strategy: " + wrongWeights
                        + ". Carry each seam's approved 1/2/3 weight into every mapped test.");
            }
        }
        Set<String> hiddenPlanSeams = plan.hiddenEntries().stream().map(GeneratedTestPlan.Entry::seam).collect(Collectors.toSet());
        Set<String> hiddenVariantSeams = StageCheckService.hiddenVariantSeamIds(approvedSpec);
        if (!plan.hiddenEntries().isEmpty() && !hasDueDate) {
            return List.of("the final test-plan.json marks tests AFTER_DUE_DATE, but this exercise has no due date. Such tests would remain hidden indefinitely; use ALWAYS "
                    + "visibility and declare no hidden variant in the specification.");
        }
        List<String> unexpectedHiddenSeams = hiddenPlanSeams.stream().filter(seam -> !hiddenVariantSeams.contains(seam)).sorted().toList();
        if (!unexpectedHiddenSeams.isEmpty()) {
            return List.of("the approved Testing Strategy says no hidden variant for seam(s) " + unexpectedHiddenSeams
                    + ", but the final test-plan.json hides tests mapped to them. Keep those tests ALWAYS visible.");
        }
        List<String> missingHiddenSeams = hiddenVariantSeams.stream().filter(seam -> !hiddenPlanSeams.contains(seam)).sorted().toList();
        if (!missingHiddenSeams.isEmpty()) {
            return List.of("the approved Testing Strategy requires AFTER_DUE_DATE variants for seam(s) " + missingHiddenSeams
                    + ", but the final test-plan.json has no hidden test mapped to them. Add a fresh witness for every listed seam; one unrelated hidden test cannot satisfy "
                    + "several seam-specific decisions.");
        }
        Set<String> visiblePlanSeams = plan.visibleEntries().stream().map(GeneratedTestPlan.Entry::seam).collect(Collectors.toSet());
        List<String> seamsWithoutVisibleTests = declaredSeams.stream().filter(seam -> !visiblePlanSeams.contains(seam)).toList();
        if (!seamsWithoutVisibleTests.isEmpty()) {
            return List.of("the approved Testing Strategy seam(s) " + seamsWithoutVisibleTests
                    + " have no ALWAYS-visible test. Every student task needs formative visible evidence; hidden coverage is additional.");
        }
        return List.of();
    }

    /** Ensures repairs did not split one student-work seam into test-shaped statement tasks or mix unrelated seams into one checkbox. */
    static List<String> statementTraceabilityReasons(String testPlanJson, String problemStatement) {
        if (testPlanJson == null || testPlanJson.isBlank() || problemStatement == null || problemStatement.isBlank()) {
            return List.of();
        }
        try {
            GeneratedTestPlan plan = GeneratedTestPlan.parse(testPlanJson);
            return plan.tests().stream().noneMatch(entry -> !entry.seam().isBlank()) ? List.of() : ProblemStatementBindingChecker.seamTaskGroupingReasons(problemStatement, plan);
        }
        catch (IllegalArgumentException exception) {
            return List.of("the final statement cannot be traced to the grading plan because test-plan.json is invalid: " + exception.getMessage());
        }
    }

    /** Every Artemis task needs an actual instruction before the next task or section; a checkbox-only statement is not usable teaching material. */
    static List<String> statementTaskInstructionReasons(String problemStatement) {
        if (problemStatement == null || problemStatement.isBlank()) {
            return List.of();
        }
        List<String> bareTasks = ProblemStatementBindingChecker.tasksWithoutInstruction(problemStatement);
        return bareTasks.isEmpty() ? List.of()
                : List.of("the final statement has task binding(s) with no student-facing instruction before the next task or heading: " + bareTasks
                        + ". Follow each task with concise prose naming the work to perform; the binding alone is grading metadata, not an exercise instruction.");
    }

    /**
     * Verifies the specification's seam-to-owner links against the template: a stubbed owner carries its seam TODO in the source declaring it, while an omitted student-created
     * owner has no truthful template location and so must not have a breadcrumb fabricated in an unrelated collaborator.
     */
    static List<String> templateTodoSeamReasons(String specification, Map<String, String> templateFiles) {
        List<StageCheckService.TestingStrategyRow> rows = StageCheckService.testingStrategyRows(specification == null ? "" : specification);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<String, String> statusByType = StageCheckService.designTableRows(specification).stream().filter(row -> row.status() != null)
                .collect(Collectors.toMap(StageCheckService.DesignRow::type, StageCheckService.DesignRow::status, (first, ignored) -> first, LinkedHashMap::new));
        Map<String, Set<String>> seamFiles = new LinkedHashMap<>();
        Map<String, String> javaFiles = templateFiles == null ? Map.of()
                : templateFiles.entrySet().stream().filter(entry -> JavaSourceInspector.isJavaSource(entry.getKey()) && entry.getValue() != null)
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, ignored) -> first, LinkedHashMap::new));
        javaFiles.forEach((path, content) -> TODO_SEAM.matcher(content).results().map(match -> match.group(1))
                .forEach(seam -> seamFiles.computeIfAbsent(seam, ignored -> new LinkedHashSet<>()).add(path)));

        Set<String> declared = rows.stream().map(StageCheckService.TestingStrategyRow::seamId).collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> reasons = new ArrayList<>();
        List<String> unknown = seamFiles.keySet().stream().filter(id -> !declared.contains(id)).toList();
        if (!unknown.isEmpty()) {
            reasons.add("the template uses TODO seam IDs the approved Testing Strategy does not declare: " + unknown + ". Replace these stale or invented IDs with one of "
                    + declared + ".");
        }
        for (StageCheckService.TestingStrategyRow row : rows) {
            Set<String> actualFiles = seamFiles.getOrDefault(row.seamId(), Set.of());
            String status = statusByType.get(row.ownerType());
            if ("student-creates".equals(status) && !actualFiles.isEmpty()) {
                reasons.add("the template uses " + row.seamId() + " TODO breadcrumb(s) in " + actualFiles + " even though its owner " + row.ownerType()
                        + " is student-created and absent. Remove the misleading breadcrumb; the statement task and reflective tests guide this seam.");
                continue;
            }
            if (!"stubbed".equals(status)) {
                continue; // malformed owner/status is rejected by the specification gate
            }
            Set<String> ownerFiles = javaFiles.entrySet().stream().filter(entry -> JavaSourceInspector.sourceDeclaresType(entry.getValue(), row.ownerType())).map(Map.Entry::getKey)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Set<String> correctlyPlaced = actualFiles.stream().filter(ownerFiles::contains).collect(Collectors.toCollection(LinkedHashSet::new));
            if (correctlyPlaced.isEmpty()) {
                reasons.add("the stubbed owner " + row.ownerType() + " has no '// TODO " + row.seamId() + ": ...' breadcrumb in its declaring source. Put the marker at the "
                        + "unfinished student work inside that source. If its student-owned members cannot be declared without an omitted student-created type, keep a compile-safe "
                        + "empty owner class and put this owner's own seam TODO once in the class body where those members will be added; do not restore the absent type or change SPEC.md.");
            }
            Set<String> misplaced = actualFiles.stream().filter(path -> !ownerFiles.contains(path)).collect(Collectors.toCollection(LinkedHashSet::new));
            if (!misplaced.isEmpty()) {
                reasons.add("the template places TODO " + row.seamId() + " outside its approved owner " + row.ownerType() + ": " + misplaced
                        + ". Move or relabel those breadcrumbs so the seam ID points at the work it actually grades.");
            }
        }
        return List.copyOf(reasons);
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

    /** Sorted and capped, so a large offending set never floods the rejection text and the message stays deterministic. */
    private static String sampleNames(Set<String> names) {
        return names.stream().sorted().limit(5).collect(Collectors.joining(", "));
    }

    static List<String> javaAresConventionReasons(Map<String, String> producedTestsFiles) {
        return javaAresConventionReasons(Map.of(), producedTestsFiles, false);
    }

    /**
     * Java exercises are graded through the Ares test sandbox, and a plain JUnit suite can pass the differential oracle while bypassing its public-test and timeout conventions.
     * When {@code preserveUnchangedLegacyTests} is set, only sources this run touched are held to the conventions; the build-harness protections always apply to the whole
     * produced repository.
     */
    static List<String> javaAresConventionReasons(Map<String, String> seedTestsFiles, Map<String, String> producedTestsFiles, boolean preserveUnchangedLegacyTests) {
        if (producedTestsFiles == null || producedTestsFiles.isEmpty()) {
            return List.of();
        }
        Map<String, String> seed = safeFiles(seedTestsFiles);
        List<Map.Entry<String, String>> javaTests = producedTestsFiles.entrySet().stream().filter(entry -> isJavaTestSourcePath(entry.getKey()))
                .filter(entry -> !preserveUnchangedLegacyTests || !Objects.equals(seed.get(entry.getKey()), entry.getValue())).toList();

        List<String> reasons = new ArrayList<>();
        List<String> generatedBuildOutput = producedTestsFiles.keySet().stream().filter(path -> path.startsWith("target/") || path.startsWith("build/")).toList();
        if (!generatedBuildOutput.isEmpty()) {
            reasons.add("Java tests repository must not contain generated build output such as target/ or build/ files; remove "
                    + sampleNames(new LinkedHashSet<>(generatedBuildOutput)) + ".");
        }
        String pom = producedTestsFiles.get("pom.xml");
        String gradle = firstNonNull(producedTestsFiles.get("build.gradle"), producedTestsFiles.get("build.gradle.kts"));
        if (pom != null) {
            String pomWithoutComments = JavaSourceInspector.stripXmlComments(pom);
            if (!JavaSourceInspector.hasMavenDependency(pomWithoutComments, "de.tum.in.ase", "artemis-java-test-sandbox")) {
                reasons.add(
                        "Java Maven tests must keep the Artemis Ares dependency in tests/pom.xml (de.tum.in.ase:artemis-java-test-sandbox); do not replace it with plain JUnit.");
            }
            if (!JavaSourceInspector.hasMavenPlugin(pomWithoutComments, "org.apache.maven.plugins", "maven-enforcer-plugin")
                    || !JavaSourceInspector.hasXmlElementText(pomWithoutComments, "file", "de/tum/in/test/api/")
                    || !JavaSourceInspector.hasXmlElementText(pomWithoutComments, "file", "org/junit/")) {
                reasons.add("Java Maven tests must keep the seeded Maven enforcer plugin in tests/pom.xml so student code cannot shadow trusted packages.");
            }
        }
        else if (gradle != null) {
            String gradleWithoutComments = JavaSourceInspector.stripJavaComments(gradle);
            if (!Pattern.compile("(?m)^\\s*(?:testImplementation|implementation)\\s+['\"]de\\.tum\\.in\\.ase:artemis-java-test-sandbox:").matcher(gradleWithoutComments).find()) {
                reasons.add(
                        "Java Gradle tests must keep the Artemis Ares dependency in tests/build.gradle (de.tum.in.ase:artemis-java-test-sandbox); do not replace it with plain JUnit.");
            }
            if (!Pattern.compile("(?m)^\\s*def\\s+forbiddenPackageFolders\\s*=").matcher(gradleWithoutComments).find() || !gradleWithoutComments.contains("de/tum/in/test/api/")
                    || !gradleWithoutComments.contains("org/junit/")) {
                reasons.add("Java Gradle tests must keep the seeded forbidden-package checks in tests/build.gradle so student code cannot shadow trusted packages.");
            }
        }
        else {
            reasons.add("Java tests must keep the seeded Maven or Gradle harness file containing the Artemis Ares dependency and trusted-package protections.");
        }

        List<String> missingClassAnnotations = new ArrayList<>();
        List<String> missingTimeouts = new ArrayList<>();
        for (Map.Entry<String, String> javaTest : javaTests) {
            String path = javaTest.getKey();
            String content = javaTest.getValue();
            var annotationSummary = JavaSourceInspector.javaTestAnnotationSummary(content);
            if (annotationSummary.hasTestMethods() && annotationSummary.classWithMissingAresAnnotations()) {
                missingClassAnnotations.add(path);
            }
            if (annotationSummary.testMethodWithoutStrictTimeout()) {
                missingTimeouts.add(path);
            }
        }
        if (!missingClassAnnotations.isEmpty()) {
            reasons.add("Java test classes must use the trusted Ares annotations @Public (de.tum.in.test.api.jupiter.Public), @WhitelistPath(\"target\") "
                    + "(de.tum.in.test.api.WhitelistPath), and @BlacklistPath(\"target/test-classes\") (de.tum.in.test.api.BlacklistPath); missing or shadowed in "
                    + sampleNames(new LinkedHashSet<>(missingClassAnnotations))
                    + ". Copy these exact imports from the seeded reference tests; only @Public lives in the .jupiter package.");
        }
        if (!missingTimeouts.isEmpty()) {
            reasons.add("Every Java @Test method must carry the trusted de.tum.in.test.api.StrictTimeout, set to a bounded number of seconds between "
                    + JavaSourceInspector.MIN_STRICT_TIMEOUT_SECONDS + " and " + JavaSourceInspector.MAX_STRICT_TIMEOUT_SECONDS
                    + " inclusive (e.g. @StrictTimeout(1)), so an infinite loop cannot hang grading and a generous but still-bounded structural check is not falsely rejected; "
                    + "missing, shadowed, or out of that range in " + sampleNames(new LinkedHashSet<>(missingTimeouts)) + ".");
        }
        return reasons;
    }

    /**
     * Confines Java generation to exercise-package-scoped source artifacts. Files untouched since the seed stay valid, so an adaptation keeps its legacy tree; a newly added or
     * modified file may not impersonate a dependency, alter repository infrastructure, or escape the exercise package.
     */
    static List<String> javaGeneratedSourceLayoutReasons(String packageName, Map<String, String> seedTestsFiles, Map<String, String> seedTemplateFiles,
            Map<String, String> seedSolutionFiles, Map<String, String> producedTestsFiles, Map<String, String> producedTemplateFiles, Map<String, String> producedSolutionFiles) {
        boolean repositoriesChanged = !safeFiles(seedTestsFiles).equals(safeFiles(producedTestsFiles)) || !safeFiles(seedTemplateFiles).equals(safeFiles(producedTemplateFiles))
                || !safeFiles(seedSolutionFiles).equals(safeFiles(producedSolutionFiles));
        if (!repositoriesChanged) {
            return List.of();
        }
        if (packageName == null || packageName.isBlank()) {
            return List.of("Java exercise generation requires a package name before generated source files can be verified.");
        }
        String packagePath = packageName.replace('.', '/');
        Set<String> invalidChanges = new LinkedHashSet<>();
        collectInvalidGeneratedChanges(invalidChanges, "tests/", seedTestsFiles, producedTestsFiles,
                List.of("test/" + packagePath + "/", "behavior/test/" + packagePath + "/", "structural/test/" + packagePath + "/"),
                List.of("test/", "behavior/test/", "structural/test/"), true);
        collectInvalidGeneratedChanges(invalidChanges, "template/", seedTemplateFiles, producedTemplateFiles, List.of("src/" + packagePath + "/"), List.of("src/"), false);
        collectInvalidGeneratedChanges(invalidChanges, "solution/", seedSolutionFiles, producedSolutionFiles, List.of("src/" + packagePath + "/"), List.of("src/"), false);
        if (invalidChanges.isEmpty()) {
            return List.of();
        }
        String testPackageRoot = "tests/test/" + packagePath + "/";
        String behaviorTestPackageRoot = "tests/behavior/test/" + packagePath + "/";
        String structuralTestPackageRoot = "tests/structural/test/" + packagePath + "/";
        return List.of("Generated Java files must stay inside the exercise package's canonical source roots. Use solution/src/" + packagePath + "/, template/src/" + packagePath
                + "/, and " + testPackageRoot + " (or " + behaviorTestPackageRoot + " / " + structuralTestPackageRoot + "), not tests/src/test/java/. Move, remove, or restore "
                + sampleNames(invalidChanges) + ".");
    }

    private static void collectInvalidGeneratedChanges(Set<String> target, String repository, Map<String, String> seedFiles, Map<String, String> producedFiles,
            List<String> allowedPrefixes, List<String> sourceRoots, boolean allowStructuralOracle) {
        Map<String, String> safeSeed = safeFiles(seedFiles);
        Map<String, String> safeProduced = safeFiles(producedFiles);
        Set<String> paths = new LinkedHashSet<>(safeSeed.keySet());
        paths.addAll(safeProduced.keySet());
        paths.stream().filter(path -> !Objects.equals(safeSeed.get(path), safeProduced.get(path))).filter(path -> {
            boolean inSourceRoot = allowedPrefixes.stream().anyMatch(path::startsWith);
            boolean allowedFile = path.endsWith(".java") || allowStructuralOracle && path.endsWith("/test.json");
            boolean packageMatchesPath = !path.endsWith(".java") || !safeProduced.containsKey(path)
                    || JavaSourceInspector.declaresPackageMatchingPath(path, safeProduced.get(path), sourceRoots);
            return !inSourceRoot || !allowedFile || !packageMatchesPath;
        }).map(repository::concat).forEach(target::add);
    }

    private static Map<String, String> safeFiles(Map<String, String> files) {
        return files == null ? Map.of() : files;
    }

    private static boolean isJavaTestSourcePath(String path) {
        return path.endsWith(".java") && (path.startsWith("test/") || path.startsWith("structural/test/") || path.startsWith("behavior/test/"));
    }

    private static String firstNonNull(String first, String second) {
        return first != null ? first : second;
    }

    /** Minimum normalized length for a body to count as a meaningful source file, so an empty .gitkeep or a one-line marker is never called a leak. */
    private static final int MIN_LEAK_BODY_LENGTH = 40;

    /**
     * Rejects unseeded randomness in a graded test: the same submission then scores differently on re-run, and neither student nor instructor can tell a regression from a dice
     * roll. The differential oracle is structurally blind to it, building each assignment once, where a probabilistic pass looks exactly like a real one.
     * <p>
     * Time and identity sources ({@code Instant.now()}, {@code LocalDate.now()}) are deliberately not matched: constructing a value object with the current timestamp is common in
     * a test that never asserts on it, so matching them would reject correct suites.
     */
    static List<String> nondeterministicGradedTestReasons(Map<String, String> producedTestsFiles) {
        if (producedTestsFiles == null || producedTestsFiles.isEmpty()) {
            return List.of();
        }
        List<String> reasons = new ArrayList<>();
        for (Map.Entry<String, String> file : producedTestsFiles.entrySet()) {
            if (isHarnessFile(file.getKey()) || file.getValue() == null) {
                continue;
            }
            List<String> found = NONDETERMINISM_SOURCES.stream().filter(source -> file.getValue().contains(source)).toList();
            if (!found.isEmpty()) {
                reasons.add("the graded test file '" + file.getKey() + "' draws on unseeded randomness (" + String.join(", ", found)
                        + "), so the same student submission can score differently on re-run. Replace it with fixed data: to prove the implementation does not depend on input "
                        + "order, pass a list that is already deliberately out of order; if a test genuinely needs a random generator, seed it (new Random(42)).");
            }
        }
        return List.copyOf(reasons);
    }

    /**
     * Detects a solution leak the differential oracle cannot see (see class javadoc): a solution implementation copied into an extra template file at a non-graded path. Files
     * identical at the same path are shared interfaces/headers/config rather than answers, and a copy at the same graded path makes the template pass and is the oracle's to
     * reject. Both inputs are residue-stripped; fails open when either side is empty.
     */
    static List<String> solutionLeakReasons(Map<String, String> templateFiles, Map<String, String> solutionFiles) {
        if (templateFiles == null || templateFiles.isEmpty() || solutionFiles == null || solutionFiles.isEmpty()) {
            return List.of();
        }
        Set<String> implementationBodies = new HashSet<>();
        for (Map.Entry<String, String> entry : solutionFiles.entrySet()) {
            if (isLeakIgnoredFile(entry.getKey())) {
                continue;
            }
            String body = JavaSourceInspector.normalizeBody(entry.getValue());
            if (body.length() < MIN_LEAK_BODY_LENGTH) {
                continue;
            }
            if (body.equals(JavaSourceInspector.normalizeBody(templateFiles.get(entry.getKey())))) {
                continue;
            }
            implementationBodies.add(body);
        }
        List<String> leakedPaths = new ArrayList<>();
        for (Map.Entry<String, String> entry : templateFiles.entrySet()) {
            String path = entry.getKey();
            if (isLeakIgnoredFile(path)) {
                continue;
            }
            String body = JavaSourceInspector.normalizeBody(entry.getValue());
            if (body.length() < MIN_LEAK_BODY_LENGTH || !implementationBodies.contains(body)) {
                continue;
            }
            // Left to the oracle's "template must fail" gate rather than double-reported here.
            if (body.equals(JavaSourceInspector.normalizeBody(solutionFiles.get(path)))) {
                continue;
            }
            if (!leakedPaths.contains(path)) {
                leakedPaths.add(path);
            }
        }
        if (leakedPaths.isEmpty()) {
            return List.of();
        }
        return List.of("the template leaks the reference solution: these template files copy a reference-solution implementation to a non-graded path: " + leakedPaths
                + ". The template ships to students, so it must contain only unimplemented placeholders, never a copy of the solution. Replace these with placeholder bodies.");
    }

    /** Grading-context introspection a produced assignment source must never contain: a stub that senses its caller can fake "fails on the template" per test. */
    private static final Pattern GRADING_CONTEXT_SNIFFING = Pattern.compile("Thread\\s*\\.\\s*currentThread\\s*\\(\\s*\\)\\s*\\.\\s*getStackTrace|StackWalker");

    /** Implementation-technique mandates: control flow or an API whose use the tests cannot see. Kept narrow so observable mandates ("must delegate to ...") never match. */
    private static final Pattern TECHNIQUE_MANDATE = Pattern.compile(
            // The nouns split by polarity, because that is where the ambiguity lives. Forbidding a technique is unambiguous ("must not use loops"), so bare nouns match there.
            // Requiring one is not: "must use the pipeline stages", "the loopback address", "the previous iteration's estimate" are all ordinary domain phrases, so the
            // positive form matches only construct-bearing names — recursion, the Stream API, a looping construct, a lambda expression.
            // The standalone prohibition ("Loops are not allowed") needs a third discriminator, because a domain rule can be lexically identical: "Recursion is not allowed in
            // the grammar of the input language" is about the input, not the student's code. The mandate ends the clause or scopes itself to the implementation; the domain
            // rule continues into what it constrains. The hyphen lookbehind separately keeps "Self-loops are not allowed" out.
            "(?:must|should|shall)\\s+(?:be\\s+)?(?:implemented\\s+)?\\**recursive(?:ly)?\\**" + "|implement\\w*\\s+[^.|\\n]{0,60}?\\brecursively\\b"
                    + "|must\\s+(?:[\\w*]+\\s+){0,3}?(?:use|using|implement)\\s+(?:[\\w*]+\\s+){0,3}\\**"
                    + "(?:recursion|stream\\s+api|looping\\s+constructs?|loop\\s+constructs?|stream\\**\\s+\\**pipelines?|lambda\\s+expressions?)\\**\\b"
                    + "|(?:must\\s+not|may\\s+not|cannot|can't|do\\s+not|don't|never)\\s+(?:[\\w*]+\\s+){0,3}?(?:use|using|contain)\\s+(?:[\\w*]+\\s+){0,3}\\**"
                    + "(?<![-\\w])(?:recursion|loops?|iteration|lambdas?|pipelines?|stream\\s+api|looping\\s+constructs?|loop\\s+constructs?)\\**\\b"
                    + "|must\\s+avoid\\s+(?:[\\w*]+\\s+){0,2}(?<![-\\w])(?:recursion|loops?|iteration)\\b" + "|must\\s+be\\s+expressed\\s+as\\s+a[^.|\\n]{0,40}(?:stream|pipeline)"
                    + "|must\\**\\s+be\\s+implemented\\s+as\\s+(?:a\\s+)?[^.|\\n]{0,30}\\bif\\p{Pd}?else\\b"
                    + "|(?<![-\\w])(?:iterative|looping|loops?|recursion|iteration)\\s+(?:constructs?\\s+)?(?:are|is)\\s+not\\s+allowed"
                    + "(?=\\s*[.;,|\\n]|\\s*$|\\s+(?:in|for)\\s+(?:your|the|this)\\s+(?:implementation|solution|method|code|answer))",
            Pattern.CASE_INSENSITIVE);

    /**
     * Prose describing <em>how</em> an implementation is written rather than what it does. Distinct from {@link #TECHNIQUE_MANDATE}, which reads a specification rule; this reads
     * a critic finding, written in the critic's voice ("an iterative implementation using an explicit stack") and so almost never containing "must".
     * <p>
     * Demands a technique <em>contrast</em> or a named implementation shape: merely mentioning the topic is not enough, because on a recursion exercise nearly every finding says
     * "recursive" somewhere, and "the recursive helper's base case is untested" is an ordinary repairable gap that must keep its repair round.
     */
    private static final Pattern TECHNIQUE_CLAIM = Pattern
            .compile("(?:instead\\s+of|rather\\s+than|without|not)\\s+(?:[\\w*]+\\s+){0,3}?\\b(?:recursi\\w*|loops?|looping|iterat\\w*|streams?|lambdas?|if\\p{Pd}?else)\\b"
                    + "|\\b(?:iterativ\\w*|recursiv\\w*|non-recursive|loop-based|stack-based)\\s+(?:implementation|version|solution|approach|variant)"
                    + "|(?:uses?|using|written\\s+with|replaces?\\s+\\w+\\s+with)\\s+(?:an?\\s+|the\\s+)?(?:for|while|do-while)\\s+loops?"
                    + "|(?:is|be|being)\\s+(?:actually\\s+)?(?:implemented\\s+)?(?:recursiv\\w*|iterativ\\w*)\\b", Pattern.CASE_INSENSITIVE);

    /** File-reading entry points a behavioural test has no reason to call. */
    private static final Pattern FILE_READING_API = Pattern.compile("Files\\s*\\.\\s*(read|exists|lines|newBufferedReader)|new\\s+FileReader|new\\s+FileInputStream");

    /**
     * A literal naming a repository <em>source tree</em>: one of the directories production lays out, followed by a source root or source file. Naming the directory alone is
     * deliberately not enough — "fixtures/template/simple.mustache" is an ordinary fixture in a template-rendering exercise, and rejecting it would discard valid work.
     */
    private static final Pattern ASSIGNMENT_DIRECTORY_LITERAL = Pattern.compile(
            "\"(?:[^\"]*/)?(?:solution|template|assignment)/(?:[^\"]*/)?" + "(?:src/[^\"]*|[^\"/]*\\.(?:java|kt|py|ts|js|cpp|cc|c|h|hpp|rs|go|rb|cs|swift|hs|dart|scala|php|m))\"");

    /**
     * Implementation-technique mandates stated as {@code ## Rules} — that a method be recursive, use a stream pipeline, avoid loops. No assertion over the public API separates a
     * recursive implementation from an iterative one returning identical values, so an agent obliged to cover every rule either leaves the mandate ungraded or reaches for the
     * student's source text, which {@link #gradedTestsReadingSourceTreeReasons} then has to reject. Deliberately narrow, and scoped to {@code ## Rules}: a technique named as
     * guidance in the student-facing statement is fine and often desirable.
     *
     * @param spec the specification document
     * @return the distinct mandates stated as rules, in encounter order
     */
    public static List<String> techniqueMandatesInRules(@Nullable String spec) {
        if (spec == null || spec.isBlank()) {
            return List.of();
        }
        String rules = markdownSectionBody(spec, "## Rules");
        if (rules.isBlank()) {
            return List.of();
        }
        List<String> mandates = new ArrayList<>();
        Matcher matcher = TECHNIQUE_MANDATE.matcher(rules);
        while (matcher.find()) {
            // Markdown emphasis is presentation, not content: "must be recursive" and "must be **recursive**" state one mandate and must be reported once.
            String mandate = matcher.group().strip().replace("*", "").replaceAll("\\s+", " ").strip();
            if (mandates.stream().noneMatch(seen -> seen.equalsIgnoreCase(mandate))) {
                mandates.add(mandate);
            }
        }
        return List.copyOf(mandates);
    }

    /**
     * Whether a critic finding is about implementation technique rather than observable behaviour, and so cannot be repaired by strengthening the tests. Callers must first
     * establish that the specification really mandates a technique: a finding may contrast two implementations while still describing a behavioural difference the tests can see.
     *
     * @param text the finding's requirement and detail, concatenated
     * @return true when the text makes a technique claim
     */
    public static boolean describesTechniqueRatherThanBehaviour(@Nullable String text) {
        return text != null && !text.isBlank() && TECHNIQUE_CLAIM.matcher(text).find();
    }

    /** The body of one markdown section, up to the next heading at any level. */
    static String markdownSectionBody(String document, String heading) {
        int start = document.indexOf(heading);
        if (start < 0) {
            return "";
        }
        int bodyStart = start + heading.length();
        int next = document.indexOf("\n## ", bodyStart);
        int nextSubsection = document.indexOf("\n### ", bodyStart);
        if (nextSubsection >= 0 && (next < 0 || nextSubsection < next)) {
            next = nextSubsection;
        }
        return next < 0 ? document.substring(bodyStart) : document.substring(bodyStart, next);
    }

    /**
     * Rejects a graded test that reads the exercise's own source tree instead of exercising behaviour through the public API. Production checks the student's repository out as
     * {@code assignment/}, so such a test grades source text — an otherwise-correct submission that still carries a {@code TODO} comment fails. Reading those directories is also
     * how a test learns which assignment it is grading, so one that branches on the answer can pass on both solution and template, subverting the differential.
     */
    static List<String> gradedTestsReadingSourceTreeReasons(Map<String, String> producedTestsFiles) {
        if (producedTestsFiles == null || producedTestsFiles.isEmpty()) {
            return List.of();
        }
        List<String> reasons = new ArrayList<>();
        for (Map.Entry<String, String> file : producedTestsFiles.entrySet()) {
            String content = file.getValue();
            if (content == null || isHarnessFile(file.getKey())) {
                continue;
            }
            if (FILE_READING_API.matcher(content).find() && ASSIGNMENT_DIRECTORY_LITERAL.matcher(content).find()) {
                reasons.add("the graded test file '" + file.getKey() + "' reads the exercise's own source tree (it names the solution/template/assignment directories and opens "
                        + "files). Production checks the student's repository out as 'assignment', so such a test grades the student's SOURCE TEXT rather than their behaviour "
                        + "and can fail correct work — an implementation that still carries a TODO comment, for instance. It also lets the test discover which assignment it is "
                        + "running against, so it can pass on the template as well as the solution. Assert the behaviour through the public API instead, and drop the check "
                        + "entirely if the property is not observable that way.");
            }
        }
        return List.copyOf(reasons);
    }

    /**
     * Rejects template/solution sources that inspect the grading context (stack traces, stack walking) to change behaviour per caller: a stub gamed this way fails exactly the
     * bound test while behaving implemented everywhere else, subverting the fails-on-template contract in code that ships to students. Fails open on empty input.
     */
    static List<String> gradingContextSniffingReasons(Map<String, String> templateFiles, Map<String, String> solutionFiles) {
        List<String> offendingPaths = new ArrayList<>();
        for (Map<String, String> files : List.of(templateFiles == null ? Map.<String, String>of() : templateFiles,
                solutionFiles == null ? Map.<String, String>of() : solutionFiles)) {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                if (isLeakIgnoredFile(entry.getKey()) || offendingPaths.contains(entry.getKey())) {
                    continue;
                }
                if (entry.getValue() != null && GRADING_CONTEXT_SNIFFING.matcher(entry.getValue()).find()) {
                    offendingPaths.add(entry.getKey());
                }
            }
        }
        if (offendingPaths.isEmpty()) {
            return List.of();
        }
        return List.of("these template/solution files inspect the grading context (stack traces / StackWalker) to change behavior per caller: " + offendingPaths
                + ". A starter stub must fail the same way for every caller. If a member cannot be stubbed without cascading failures (constructors, shared plumbing), implement it"
                + " in the template and do not bind a behavioural test to it.");
    }

    /** Residue, harness files (owned by the harness gate) and dotfiles carry no answer, so the leak comparison skips them. */
    private static boolean isLeakIgnoredFile(String path) {
        return isResidueOutsideCanonicalRoot(path) || isHarnessFile(path) || basename(path).startsWith(".");
    }
}
