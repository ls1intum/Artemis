package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.GenerationWorkspaceService;

/**
 * Pure (sandbox-free) correctness gates {@link DifferentialVerificationService} applies on top of the differential build oracle, catching broken-exercise classes the build
 * oracle alone cannot see (the sandbox build can pass while production is broken, the solution is leaked, or the approved student work has been scaffolded away):
 * <ul>
 * <li><b>Harness tampering.</b> The seeded tests-repo build/harness/manifest files are graded verbatim in production. If the agent rewrites one, the sandbox build can still pass
 * while production fails because CI lays the tree out differently or because dependencies/plugins/scripts changed. We snapshot those files at seed time and reject any
 * post-generation harness change, modulo only the CI checkout-placeholder substitution the pipeline applies (so an agent that does not touch the harness is not penalized).</li>
 * <li><b>Solution leak.</b> The template repository ships to students. A reference-solution implementation copied into a non-graded template path hands students the answer while
 * the build still passes. The residue strip is the primary defence; this gate is the backstop, rejecting such a copy without flagging shared interfaces/headers or git config that
 * are
 * legitimately identical between template and solution (a graded-path copy that makes the template pass is left to the differential oracle).</li>
 * <li><b>Specification contract loss.</b> The differential proves that tests distinguish solution and template, but not that students still perform the work the approved spec
 * assigned to them. The final candidate maps are therefore checked against the immutable approved ownership decisions before persistence.</li>
 * </ul>
 * The gates are static and side-effect-free so they are unit-testable without Docker, and so the residue-strip half can be reused by {@link GenerationWorkspaceService} on
 * read-back.
 */
public final class ExerciseIntegrityGate {

    private static final Pattern TODO_SEAM = Pattern.compile("\\bTODO\\s+(S[1-9][0-9]*)\\s*:");

    /**
     * The CI checkout directory names — the sibling repositories CI lays out next to each other, not legitimate top-level source folders. A file whose first path component is one
     * of these is orphan residue (e.g. a nested {@code solution/src/…} left inside another repo): stripped on read-back, never counted as a harness or source file.
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

    private static final Pattern JAVA_PACKAGE_DECLARATION = Pattern.compile("^\\s*package\\s+([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)\\s*;");

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

    /**
     * Whether a repository-relative path is orphan residue: its first path component is a CI checkout directory name. The canonical layout places sources directly under the repo
     * root, so a path re-entering an {@code assignment/}/{@code solution/}/{@code template/}/{@code tests/} directory duplicates the CI sibling-checkout structure, not a real
     * source.
     *
     * @param path the repository-relative path
     * @return {@code true} if the file is residue and should be stripped
     */
    static boolean isResidueOutsideCanonicalRoot(String path) {
        return CI_CHECKOUT_DIRECTORY_NAMES.contains(firstComponent(path).toLowerCase(Locale.ROOT));
    }

    /**
     * Strips paths that re-enter a CI checkout directory from a TEMPLATE or SOLUTION file map while preserving canonical-root files and order.
     *
     * @param files the produced files keyed by repository-relative path
     * @return the same map without orphan residue files
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
     * Whether a tests-repo-relative path is a build/harness/manifest/report file that is graded verbatim in production and must not be changed by the agent.
     *
     * @param path the tests-repo-relative path
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
        // Build-config YAML at the tests-repo root (e.g. a CI build descriptor) but not arbitrary nested *.yml data files used by tests.
        return (base.endsWith(".yml") || base.endsWith(".yaml")) && !path.contains("/");
    }

    /** Applies only the checkout-placeholder substitutions performed by the build pipeline. */
    static String normalizeLayoutLine(String line) {
        return line.replace("${studentWorkingDirectory}", "/assignment/src").replace("${studentParentWorkingDirectoryName}", "assignment")
                .replace("${solutionWorkingDirectory}", "assignment").replace("${testWorkingDirectory}", ".");
    }

    /**
     * Splits content into normalized lines (CRLF folded and checkout placeholders substituted), preserving every other byte.
     */
    private static List<String> normalizedLines(String content) {
        List<String> lines = new ArrayList<>();
        for (String line : content.replace("\r\n", "\n").split("\n", -1)) {
            lines.add(normalizeLayoutLine(line));
        }
        return lines;
    }

    /**
     * Detects harness tampering (see class javadoc): seeded build/harness/manifest files must stay byte-equivalent after line-ending and CI checkout-placeholder normalization.
     * <p>
     * An empty snapshot normally disables the gate for harness-free languages. When {@code requireNonEmptySnapshot} is set, it instead indicates a failed capture and is rejected.
     *
     * @param seedTestsFiles          the tests-repo files snapshotted at seed time (repository-relative)
     * @param producedTestsFiles      the tests-repo files read back after generation (repository-relative)
     * @param requireNonEmptySnapshot whether the language guarantees a tests harness, so an empty snapshot is a failed capture that must fail closed
     * @return one rejection reason per offending file (empty when the harness layout is intact or the gate is disabled)
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
     * Rejects an adaptation that retains none of the exercise's existing graded test names. Partial test changes remain allowed.
     * <p>
     * Fails open on an empty baseline (generate, or a never-graded exercise). Names are trimmed but otherwise kept exact, matching Artemis task-binding semantics. Post-loop only:
     * the baseline graded names come from the authoritative pre-adapt persisted state the agent loop does not have mid-session, so this gate lives alongside
     * the other read-back integrity gates, not the in-loop self-check.
     *
     * @param baselineGradedTestNames   the exercise's graded test names captured before the adapt ran (empty for generate or a never-graded exercise; the gate is then inert)
     * @param producedSolutionTestNames the test names the produced tests ran against the solution (the post-adapt graded set)
     * @return a single rejection reason when a non-empty baseline is retained by nothing produced, otherwise an empty list
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
     * Enforces the student/template ownership decisions from the specification that passed the SPEC gate against the exact repository maps final verification hands to
     * persistence. A differential build cannot detect this contract violation: tests can pass against a fully stubbed template even when the approved exercise deliberately
     * required students to create those types themselves.
     *
     * @param approvedSpec          the immutable SPEC.md snapshot accepted before implementation began
     * @param producedTemplateFiles the exact template candidate that would be saved
     * @param producedSolutionFiles the exact solution candidate that would be saved
     * @return actionable contract violations, or an empty list when every student-created type exists only in the solution
     */
    static List<String> approvedSpecificationReasons(String approvedSpec, Map<String, String> producedTemplateFiles, Map<String, String> producedSolutionFiles) {
        if (approvedSpec == null || approvedSpec.isBlank()) {
            return List.of();
        }
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
        // The template is the student's starting scaffold. A contract that supplies nothing produces an empty starter repository, and an empty template defeats the differential
        // itself: it "compiles" (no sources) and "fails every test" (none run), so the degenerate candidate satisfies the very checks meant to reject it. The stage gate refuses
        // such a design at approval time, but acceptance is decided here — and repair attempts do not re-run the staged gates.
        if (StageCheckService.designTableRows(approvedSpec).stream().noneMatch(row -> "given".equals(row.status()) || "stubbed".equals(row.status()))) {
            reasons.add("the approved specification marks every type 'student-creates', so the template ships no starting scaffold and students would clone an empty project. "
                    + "Supply at least one type as 'given' or 'stubbed' so the exercise has a teaching scaffold the differential can actually discriminate.");
        }
        List<String> missingStubbedTypes = StageCheckService.designTableRows(approvedSpec).stream().filter(row -> "stubbed".equals(row.status()))
                .map(StageCheckService.DesignRow::type).filter(type -> !repositoryDeclaresType(producedTemplateFiles, type)).toList();
        if (!missingStubbedTypes.isEmpty()) {
            reasons.add("the approved specification marks these types 'stubbed', but the template does not declare them: " + missingStubbedTypes
                    + ". A stubbed type ships in the template as the real signatures with TODO bodies, so the student has something to complete and the graded tests can name "
                    + "it; restore them instead of deleting the tests that need them.");
        }
        List<String> divergentGivenTypes = StageCheckService.designTableRows(approvedSpec).stream().filter(row -> "given".equals(row.status()))
                .map(StageCheckService.DesignRow::type).filter(type -> !canonicalGivenFileMatches(type, producedTemplateFiles, producedSolutionFiles)).toList();
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
            return (path.equals(type + ".java") || path.endsWith(suffix)) && sourceDeclaresType(entry.getValue(), type);
        }).toList();
    }

    /** Ensures the exact grading plan headed to persistence still implements the approved specification and names only tests the verifier actually ran. */
    static List<String> approvedTestPlanReasons(String approvedSpec, String testPlanJson, List<String> verifiedTestNames) {
        return approvedTestPlanReasons(approvedSpec, testPlanJson, verifiedTestNames, true, Set.of());
    }

    /** Ensures plan traceability, approved grading emphasis, formative visibility, and due-date-compatible hidden coverage remain intact through final verification. */
    static List<String> approvedTestPlanReasons(String approvedSpec, String testPlanJson, List<String> verifiedTestNames, boolean hasDueDate) {
        return approvedTestPlanReasons(approvedSpec, testPlanJson, verifiedTestNames, hasDueDate, Set.of());
    }

    /**
     * Ensures plan traceability, approved grading emphasis, formative visibility, and due-date-compatible hidden coverage remain intact through final verification. Structural
     * tests are identified from the server-seeded authority rather than a forgeable name pattern.
     */
    static List<String> approvedTestPlanReasons(String approvedSpec, String testPlanJson, List<String> verifiedTestNames, boolean hasDueDate,
            Set<String> seededStructuralTestNames) {
        if (approvedSpec == null || approvedSpec.isBlank()) {
            return List.of();
        }
        if (testPlanJson == null || testPlanJson.isBlank()) {
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
        // Old saved plans/specifications predate seam metadata. New staged SPECs always declare S1..., so enforce traceability exactly where the new contract exists without
        // making legacy/adaptation candidates invent metadata that cannot be persisted.
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

    /** Ensures post-loop repairs did not split one student-work seam into test-shaped statement tasks or mix unrelated seams into one checkbox. */
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
     * Verifies the specification's exact seam-to-owner links against the template. Stubbed owners carry their seam TODO in the source declaring that owner; an omitted
     * student-created owner has no truthful template location and therefore must not have a fabricated breadcrumb in an unrelated collaborator.
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
                : templateFiles.entrySet().stream().filter(entry -> isJavaSource(entry.getKey()) && entry.getValue() != null)
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
            Set<String> ownerFiles = javaFiles.entrySet().stream().filter(entry -> sourceDeclaresType(entry.getValue(), row.ownerType())).map(Map.Entry::getKey)
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

    private static boolean isJavaSource(String path) {
        if (path == null) {
            return false;
        }
        String normalized = path.replace('\\', '/');
        return normalized.endsWith(".java") && !normalized.startsWith("target/") && !normalized.contains("/target/") && !normalized.startsWith("build/")
                && !normalized.contains("/build/");
    }

    /** Finds a top-level, nested, or secondary type declaration. */
    private static boolean repositoryDeclaresType(Map<String, String> files, String type) {
        if (files == null || files.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, String> file : files.entrySet()) {
            String lowerPath = file.getKey().toLowerCase(Locale.ROOT);
            if (lowerPath.startsWith("target/") || lowerPath.startsWith("build/") || NON_SOURCE_TYPE_FILE_SUFFIXES.stream().anyMatch(lowerPath::endsWith)) {
                continue;
            }
            if (sourceDeclaresType(file.getValue(), type)) {
                return true;
            }
        }
        return false;
    }

    /** The template must not contain either the declaration or a source artifact named after a type assigned to the student. */
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

    /** Shared filename-or-declaration predicate used by prospective writes and final repository verification. */
    static boolean pathOrContentRepresentsType(String path, String content, String type) {
        String normalizedPath = path.replace('\\', '/');
        String fileName = normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1);
        return fileName.startsWith(type + ".") || sourceDeclaresType(content, type);
    }

    /** Shared declaration matcher for the prospective write guard and the authoritative final repository check. */
    static boolean sourceDeclaresType(String content, String type) {
        String declarationStart = "(?:^|[;{}])\\s*";
        String modifiers = "(?:(?:public|protected|private|static|abstract|final|sealed|non-sealed)\\s+)*";
        return Pattern.compile(declarationStart + modifiers + "(?:class|interface|enum|record|trait|struct|protocol)\\s+" + Pattern.quote(type) + "\\b", Pattern.MULTILINE)
                .matcher(content).find();
    }

    /** A short, deterministic sample of names for a rejection message, sorted and capped so a large baseline suite never floods the reason text. */
    private static String sampleNames(Set<String> names) {
        return names.stream().sorted().limit(5).collect(Collectors.joining(", "));
    }

    /**
     * Java/JUnit exercises in Artemis are graded through the Ares test sandbox. A plain JUnit suite can pass the differential oracle while bypassing Ares' public-test and
     * sandbox/timeout conventions.
     *
     * @param producedTestsFiles the read-back tests repository (repository-relative path -> content)
     * @return actionable rejection reasons when Java tests do not follow the Artemis/Ares conventions
     */
    static List<String> javaAresConventionReasons(Map<String, String> producedTestsFiles) {
        return javaAresConventionReasons(Map.of(), producedTestsFiles, false);
    }

    /**
     * Applies Ares conventions to every generated test source, while allowing an adaptation to preserve untouched legacy tests. Build-harness protections are always checked on
     * the complete produced repository. A touched legacy test is generated output for this run and must meet the current conventions.
     */
    static List<String> javaAresConventionReasons(Map<String, String> seedTestsFiles, Map<String, String> producedTestsFiles, boolean preserveUnchangedLegacyTests) {
        if (producedTestsFiles == null || producedTestsFiles.isEmpty()) {
            return List.of();
        }
        Map<String, String> seed = safeFiles(seedTestsFiles);
        List<Map.Entry<String, String>> javaTests = producedTestsFiles.entrySet().stream().filter(entry -> isJavaTestSourcePath(entry.getKey()))
                .filter(entry -> !preserveUnchangedLegacyTests || !java.util.Objects.equals(seed.get(entry.getKey()), entry.getValue())).toList();

        List<String> reasons = new ArrayList<>();
        List<String> generatedBuildOutput = producedTestsFiles.keySet().stream().filter(path -> path.startsWith("target/") || path.startsWith("build/")).toList();
        if (!generatedBuildOutput.isEmpty()) {
            reasons.add("Java tests repository must not contain generated build output such as target/ or build/ files; remove "
                    + sampleNames(new LinkedHashSet<>(generatedBuildOutput)) + ".");
        }
        String pom = producedTestsFiles.get("pom.xml");
        String gradle = firstNonNull(producedTestsFiles.get("build.gradle"), producedTestsFiles.get("build.gradle.kts"));
        if (pom != null) {
            String pomWithoutComments = stripXmlComments(pom);
            if (!hasMavenDependency(pomWithoutComments, "de.tum.in.ase", "artemis-java-test-sandbox")) {
                reasons.add(
                        "Java Maven tests must keep the Artemis Ares dependency in tests/pom.xml (de.tum.in.ase:artemis-java-test-sandbox); do not replace it with plain JUnit.");
            }
            if (!hasMavenPlugin(pomWithoutComments, "org.apache.maven.plugins", "maven-enforcer-plugin") || !hasXmlElementText(pomWithoutComments, "file", "de/tum/in/test/api/")
                    || !hasXmlElementText(pomWithoutComments, "file", "org/junit/")) {
                reasons.add("Java Maven tests must keep the seeded Maven enforcer plugin in tests/pom.xml so student code cannot shadow trusted packages.");
            }
        }
        else if (gradle != null) {
            String gradleWithoutComments = stripJavaComments(gradle);
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
            JavaTestAnnotationSummary annotationSummary = javaTestAnnotationSummary(content);
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
            reasons.add("Every Java @Test method must carry the trusted de.tum.in.test.api.StrictTimeout, set to a bounded number of seconds between " + MIN_STRICT_TIMEOUT_SECONDS
                    + " and " + MAX_STRICT_TIMEOUT_SECONDS
                    + " inclusive (e.g. @StrictTimeout(1)), so an infinite loop cannot hang grading and a generous but still-bounded structural check is not falsely rejected; "
                    + "missing, shadowed, or out of that range in " + sampleNames(new LinkedHashSet<>(missingTimeouts)) + ".");
        }
        return reasons;
    }

    /**
     * Allows Java generation to change only exercise-package-scoped source artifacts. Files that already existed at seed time may remain unchanged, preserving legacy adaptation;
     * newly added or modified files cannot impersonate dependencies, alter repository infrastructure, or escape the exercise package.
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
        paths.stream().filter(path -> !java.util.Objects.equals(safeSeed.get(path), safeProduced.get(path))).filter(path -> {
            boolean inSourceRoot = allowedPrefixes.stream().anyMatch(path::startsWith);
            boolean allowedFile = path.endsWith(".java") || allowStructuralOracle && path.endsWith("/test.json");
            boolean packageMatchesPath = !path.endsWith(".java") || !safeProduced.containsKey(path) || declaresPackageMatchingPath(path, safeProduced.get(path), sourceRoots);
            return !inSourceRoot || !allowedFile || !packageMatchesPath;
        }).map(repository::concat).forEach(target::add);
    }

    private static boolean declaresPackageMatchingPath(String path, String content, List<String> sourceRoots) {
        String sourceRoot = sourceRoots.stream().filter(path::startsWith).findFirst().orElse(null);
        int filenameSeparator = path.lastIndexOf('/');
        if (sourceRoot == null || filenameSeparator < sourceRoot.length() || content == null) {
            return false;
        }
        Matcher matcher = JAVA_PACKAGE_DECLARATION.matcher(stripJavaComments(content));
        if (!matcher.find() || Pattern.compile("\\\\u+").matcher(content.substring(0, matcher.end())).find()) {
            return false;
        }
        String expectedPackage = path.substring(sourceRoot.length(), filenameSeparator).replace('/', '.');
        return matcher.group(1).equals(expectedPackage);
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

    private static boolean hasMavenDependency(String pom, String groupId, String artifactId) {
        return xmlBlocks(pom, "dependency").stream().anyMatch(block -> hasXmlElementText(block, "groupId", groupId) && hasXmlElementText(block, "artifactId", artifactId));
    }

    private static boolean hasMavenPlugin(String pom, String groupId, String artifactId) {
        return xmlBlocks(pom, "plugin").stream().anyMatch(block -> hasXmlElementText(block, "groupId", groupId) && hasXmlElementText(block, "artifactId", artifactId));
    }

    private static List<String> xmlBlocks(String content, String element) {
        Matcher matcher = Pattern.compile("(?s)<" + Pattern.quote(element) + "\\b[^>]*>.*?</" + Pattern.quote(element) + ">").matcher(content);
        List<String> blocks = new ArrayList<>();
        while (matcher.find()) {
            blocks.add(matcher.group());
        }
        return blocks;
    }

    private static boolean hasXmlElementText(String content, String element, String expectedText) {
        Matcher matcher = Pattern.compile("(?s)<" + Pattern.quote(element) + "\\b[^>]*>\\s*([^<]*?)\\s*</" + Pattern.quote(element) + ">").matcher(content);
        while (matcher.find()) {
            if (matcher.group(1).contains(expectedText)) {
                return true;
            }
        }
        return false;
    }

    private static String stripXmlComments(String content) {
        return content.replaceAll("(?s)<!--.*?-->", "");
    }

    private record JavaClassAnnotation(int start, String annotations) {
    }

    private record JavaTestAnnotationSummary(boolean hasTestMethods, boolean classWithMissingAresAnnotations, boolean testMethodWithoutStrictTimeout) {
    }

    private static final Pattern JAVA_CLASS_DECLARATION = Pattern.compile("\\b(?:public\\s+)?(?:abstract\\s+)?class\\s+\\w+");

    private static final Pattern JAVA_METHOD_DECLARATION = Pattern
            .compile("\\b(?:public|protected|private)?\\s*(?:static\\s+)?[\\w<>\\[\\], ?]+\\s+\\w+\\s*\\([^;{}]*\\)\\s*(?:throws\\s+[^{}]+)?\\{");

    private static JavaTestAnnotationSummary javaTestAnnotationSummary(String content) {
        String withoutComments = stripJavaComments(content);
        Set<String> imports = new HashSet<>();
        Matcher importMatcher = Pattern.compile("(?m)^\\s*import\\s+([\\w.]+)\\s*;").matcher(withoutComments);
        while (importMatcher.find()) {
            imports.add(importMatcher.group(1));
        }
        Matcher localTypeMatcher = Pattern.compile("\\b(?:class|interface|enum|record|@interface)\\s+([A-Za-z_$][\\w$]*)").matcher(withoutComments);
        while (localTypeMatcher.find()) {
            String localType = localTypeMatcher.group(1);
            imports.removeIf(importedType -> importedType.endsWith("." + localType));
        }
        String[] lines = withoutComments.split("\\R", -1);
        List<JavaClassAnnotation> classes = new ArrayList<>();
        boolean hasTestMethods = false;
        boolean missingClassAnnotations = false;
        boolean missingTimeouts = false;
        StringBuilder annotations = new StringBuilder();
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex].trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("@")) {
                annotations.append(line).append('\n');
                lineIndex = appendAnnotationContinuation(lines, lineIndex, annotations);
                continue;
            }
            if (annotations.isEmpty()) {
                continue;
            }
            int declarationLine = lineIndex;
            String declaration = line;
            while (!declaration.contains("{") && !declaration.contains(";") && lineIndex + 1 < lines.length) {
                String nextLine = lines[lineIndex + 1].trim();
                if (nextLine.startsWith("@")) {
                    break;
                }
                declaration += " " + nextLine;
                lineIndex++;
            }
            String annotationBlock = annotations.toString();
            if (JAVA_CLASS_DECLARATION.matcher(declaration).find()) {
                classes.add(new JavaClassAnnotation(declarationLine, annotationBlock));
            }
            else if (JAVA_METHOD_DECLARATION.matcher(declaration).find() && hasJUnitTestAnnotation(annotationBlock)) {
                hasTestMethods = true;
                String classAnnotations = enclosingClassAnnotations(classes, declarationLine);
                if (!hasAresClassAnnotations(classAnnotations, imports)) {
                    missingClassAnnotations = true;
                }
                if (!hasStrictTimeout(annotationBlock, imports) && !hasStrictTimeout(classAnnotations, imports)) {
                    missingTimeouts = true;
                }
            }
            annotations.setLength(0);
        }
        return new JavaTestAnnotationSummary(hasTestMethods, missingClassAnnotations, missingTimeouts);
    }

    private static int appendAnnotationContinuation(String[] lines, int startLine, StringBuilder annotations) {
        int parenthesisBalance = parenthesisBalance(lines[startLine]);
        int lineIndex = startLine;
        while (parenthesisBalance > 0 && lineIndex + 1 < lines.length) {
            lineIndex++;
            String line = lines[lineIndex].trim();
            annotations.append(line).append('\n');
            parenthesisBalance += parenthesisBalance(line);
        }
        return lineIndex;
    }

    private static int parenthesisBalance(String line) {
        int balance = 0;
        for (int i = 0; i < line.length(); i++) {
            char character = line.charAt(i);
            if (character == '(') {
                balance++;
            }
            else if (character == ')') {
                balance--;
            }
        }
        return balance;
    }

    private static String enclosingClassAnnotations(List<JavaClassAnnotation> classes, int line) {
        String annotations = "";
        for (JavaClassAnnotation javaClass : classes) {
            if (javaClass.start() > line) {
                break;
            }
            annotations = javaClass.annotations();
        }
        return annotations;
    }

    private static boolean hasAresClassAnnotations(String annotations, Set<String> imports) {
        return hasTrustedAnnotation(annotations, imports, "de.tum.in.test.api.jupiter.Public", "Public", null)
                && hasTrustedAnnotation(annotations, imports, "de.tum.in.test.api.WhitelistPath", "WhitelistPath", "\"target\"")
                && hasTrustedAnnotation(annotations, imports, "de.tum.in.test.api.BlacklistPath", "BlacklistPath", "\"target/test-classes\"");
    }

    /**
     * The trusted {@code @StrictTimeout} bound, in seconds. The gate exists to prevent an UNBOUNDED test (e.g. an infinite loop) from hanging grading, not to pin one magic
     * constant: Artemis's own seeded structural test classes ({@code templates/java/test/testFiles/structural/ClassTest.java} and its three siblings) carry
     * {@code @StrictTimeout(10)} for {@link StructuralOracleSeedingService}'s reflection-heavy generated tests, which a gate demanding exactly {@code 1} would reject as soon as a
     * missing public class is seeded — a false rejection of Artemis's own trusted output. Any value in this bounded range is accepted; only an unset, shadowed, or unbounded
     * timeout is rejected.
     */
    private static final int MIN_STRICT_TIMEOUT_SECONDS = 1;

    private static final int MAX_STRICT_TIMEOUT_SECONDS = 15;

    private static boolean hasStrictTimeout(String annotations, Set<String> imports) {
        return hasBoundedStrictTimeout(annotations, "de.tum.in.test.api.StrictTimeout")
                || (imports.contains("de.tum.in.test.api.StrictTimeout") && hasBoundedStrictTimeout(annotations, "StrictTimeout"));
    }

    /** Whether {@code annotations} carries an {@code @<name>(<seconds>)} annotation whose numeric argument falls within the trusted bounded range. */
    private static boolean hasBoundedStrictTimeout(String annotations, String name) {
        Matcher matcher = Pattern.compile("@" + Pattern.quote(name) + "\\s*\\(\\s*(\\d+)\\s*\\)").matcher(annotations);
        while (matcher.find()) {
            try {
                long seconds = Long.parseLong(matcher.group(1));
                if (seconds >= MIN_STRICT_TIMEOUT_SECONDS && seconds <= MAX_STRICT_TIMEOUT_SECONDS) {
                    return true;
                }
            }
            catch (NumberFormatException e) {
                // An unrepresentably large literal is certainly out of the trusted range; keep scanning any further match on the same annotated element.
            }
        }
        return false;
    }

    private static boolean hasTrustedAnnotation(String annotations, Set<String> imports, String qualifiedName, String simpleName, String argument) {
        String suffix = argument == null ? "\\b" : "\\s*\\(\\s*" + Pattern.quote(argument) + "\\s*\\)";
        boolean fullyQualified = Pattern.compile("@" + Pattern.quote(qualifiedName) + suffix).matcher(annotations).find();
        boolean imported = imports.contains(qualifiedName) && Pattern.compile("@" + Pattern.quote(simpleName) + suffix).matcher(annotations).find();
        return fullyQualified || imported;
    }

    private static boolean hasJUnitTestAnnotation(String annotations) {
        return hasAnnotation(annotations, "Test") || hasAnnotation(annotations, "ParameterizedTest") || hasAnnotation(annotations, "RepeatedTest")
                || hasAnnotation(annotations, "TestFactory") || hasAnnotation(annotations, "TestTemplate");
    }

    private static boolean hasAnnotation(String annotations, String simpleName) {
        return Pattern.compile("@(?:[\\w.]+\\.)?" + Pattern.quote(simpleName) + "\\b").matcher(annotations).find();
    }

    private static String stripJavaComments(String content) {
        StringBuilder stripped = new StringBuilder(content.length());
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inString = false;
        boolean inChar = false;
        for (int i = 0; i < content.length(); i++) {
            char current = content.charAt(i);
            char next = i + 1 < content.length() ? content.charAt(i + 1) : '\0';
            if (inLineComment) {
                if (current == '\n') {
                    inLineComment = false;
                    stripped.append(current);
                }
                else {
                    stripped.append(' ');
                }
            }
            else if (inBlockComment) {
                if (current == '*' && next == '/') {
                    inBlockComment = false;
                    stripped.append("  ");
                    i++;
                }
                else {
                    stripped.append(current == '\n' ? '\n' : ' ');
                }
            }
            else if (inString || inChar) {
                stripped.append(current);
                if (current == '\\' && next != '\0') {
                    stripped.append(next);
                    i++;
                }
                else if ((inString && current == '"') || (inChar && current == '\'')) {
                    inString = false;
                    inChar = false;
                }
            }
            else if (current == '/' && next == '/') {
                inLineComment = true;
                stripped.append("  ");
                i++;
            }
            else if (current == '/' && next == '*') {
                inBlockComment = true;
                stripped.append("  ");
                i++;
            }
            else {
                inString = current == '"';
                inChar = current == '\'';
                stripped.append(current);
            }
        }
        return stripped.toString();
    }

    /** Normalizes a file body for content-equality: CRLF folded and surrounding whitespace stripped. */
    private static String normalizeBody(String content) {
        return content == null ? "" : content.replace("\r\n", "\n").strip();
    }

    /** Minimum normalized length for a body to be considered a meaningful source file (so an empty .gitkeep or a one-line marker is never called a leak). */
    private static final int MIN_LEAK_BODY_LENGTH = 40;

    /**
     * Detects a solution leak the differential oracle cannot see (see class javadoc). The hard part is what to flag:
     * <ul>
     * <li>Not files legitimately identical between template and solution at the same path — shared interfaces/headers, git dotfiles, harness files (an implementation file is one
     * that
     * differs from the template at its own path).</li>
     * <li>Not a template that copies the solution into the same graded path — that makes the template pass, already rejected by the oracle's "template must fail" gate.</li>
     * <li>Flags the solution implementation copied into an extra template file at a non-graded path.</li>
     * </ul>
     * Fails open when either side is empty.
     *
     * @param templateFiles the produced TEMPLATE repository files (repository-relative; residue already stripped)
     * @param solutionFiles the produced SOLUTION repository files (repository-relative; residue already stripped)
     * @return a single reason listing the leaked paths, or empty when no leak
     */
    /**
     * Unseeded randomness in a graded test source. Each construct below exists only to make a run differ from the last one, so in a test that decides a grade it makes the score
     * irreproducible: the same submission scores differently on re-run, and neither the student nor the instructor can tell a regression from a dice roll.
     * <p>
     * The differential oracle is structurally blind to this — it builds solution and template once each, and a test that passes probabilistically looks exactly like one that
     * passes. Observed live: a suite shuffled its input list "to ensure order-independence", and an implementation that never sorted at all passed 5 of 20 identical runs.
     * <p>
     * Time and identity sources ({@code Instant.now()}, {@code LocalDate.now()}) are deliberately NOT matched: constructing a value object with the current timestamp is
     * legitimate and common in a test that never asserts on it, so matching them would reject correct suites.
     *
     * @param producedTestsFiles the exact tests repository that would be saved
     * @return one actionable rejection per offending file, or an empty list when every graded test is deterministic
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

    static List<String> solutionLeakReasons(Map<String, String> templateFiles, Map<String, String> solutionFiles) {
        if (templateFiles == null || templateFiles.isEmpty() || solutionFiles == null || solutionFiles.isEmpty()) {
            return List.of();
        }
        // Solution implementation bodies: solution source whose content differs from the template's at the same path (so a shared interface/config identical there is excluded).
        Set<String> implementationBodies = new HashSet<>();
        for (Map.Entry<String, String> entry : solutionFiles.entrySet()) {
            if (isLeakIgnoredFile(entry.getKey())) {
                continue;
            }
            String body = normalizeBody(entry.getValue());
            if (body.length() < MIN_LEAK_BODY_LENGTH) {
                continue;
            }
            if (body.equals(normalizeBody(templateFiles.get(entry.getKey())))) {
                // Identical at the same path => a shared interface/header/config, not an answer.
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
            String body = normalizeBody(entry.getValue());
            if (body.length() < MIN_LEAK_BODY_LENGTH || !implementationBodies.contains(body)) {
                continue;
            }
            // A copy at the same graded path makes the template pass — already rejected by the oracle, so do not double-report it.
            if (body.equals(normalizeBody(solutionFiles.get(path)))) {
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
            // "must be recursive", "must use a Java **Stream** pipeline", "must be implemented *recursively*", "must not use any looping construct", and the prohibition form
            // "iterative constructs ... are not allowed". Real specifications put markdown emphasis around the construct and words between the verb and it, so both are
            // tolerated; the noun list stays narrow, which is what keeps an observable mandate such as "must use the injected collaborator" out of the match.
            "must\\s+(?:be\\s+(?:implemented\\s+)?\\**(?:recursive(?:ly)?|iterative(?:ly)?)"
                    + "|(?:not\\s+)?(?:use|using)\\s+(?:[\\w*]+\\s+){0,3}\\**(?:recursion|streams?|lambdas?|loops?|looping\\s+construct[s]?|iteration)\\**"
                    + "|be\\s+expressed\\s+as\\s+a[^.|\\n]{0,40}(?:stream|pipeline))[^.|\\n]{0,60}"
                    + "|(?:explicit\\s+)?(?:iterative\\s+constructs?|loops?|recursion)[^.|\\n]{0,60}?\\b(?:are|is)\\s+not\\s+allowed",
            Pattern.CASE_INSENSITIVE);

    /** File-reading entry points a behavioural test has no reason to call. */
    private static final Pattern FILE_READING_API = Pattern.compile("Files\\s*\\.\\s*(read|exists|lines|newBufferedReader)|new\\s+FileReader|new\\s+FileInputStream");

    /** A literal naming one of the repository directories production lays out; a graded test that knows these is grading layout, not behaviour. */
    private static final Pattern ASSIGNMENT_DIRECTORY_LITERAL = Pattern.compile("\"(?:[^\"]*/)?(?:solution|template|assignment)/[^\"]*\"");

    /**
     * Rejects produced template/solution sources that inspect the grading context (stack traces, stack walking) to change behavior per caller. A template stub gamed this way can
     * fail exactly the bound test while behaving implemented everywhere else, subverting the fails-on-template contract in code that ships to students. Fails open on empty input.
     *
     * @param templateFiles the produced TEMPLATE repository files (repository-relative)
     * @param solutionFiles the produced SOLUTION repository files (repository-relative)
     * @return one reason naming the offending files, or empty when clean
     */
    /**
     * Rejects a graded test that reads the exercise's own source tree instead of exercising behaviour through the public API.
     * <p>
     * Observed live: a test named {@code testNoLoopsInImplementation} that never looks for a loop. It searches
     * {@code solution/}, {@code template/} and {@code assignment/} for the implementation file, reads it, and asserts that the source does not contain the string
     * {@code TODO}. In production the student's repository is checked out as {@code assignment/}, so a student whose otherwise-correct solution still carries a TODO comment
     * fails a graded test for a reason that has nothing to do with their work — a false negative against correct work, which is the most damaging kind.
     * <p>
     * Reading those directories is also how a test learns which assignment it is grading, and a test that branches on that answer can pass on both the solution and the
     * template, quietly subverting the differential that is supposed to prove it discriminates. Behaviour is observable through the public API; the repository layout is not
     * the test's business.
     *
     * @param producedTestsFiles the tests repository as it would be saved
     * @return one actionable rejection per offending file, or empty when no graded test reads the source tree
     */
    /**
     * Implementation-technique mandates stated in a specification's {@code ## Rules} section — that a method be recursive, use a stream pipeline, avoid loops.
     * <p>
     * Behavioural tests cannot observe these: no assertion over the public API separates a recursive implementation from an iterative one returning identical values. Stating
     * one as a numbered rule is therefore a promise the exercise cannot keep, and it does active harm rather than merely being inert. Both outcomes were measured. An exercise
     * generated from "teach recursion" that stated the mandate awarded full marks to two iterative methods; another that stated it produced a graded test which read the
     * student's source file and failed anyone whose correct solution still carried a TODO comment — the agent trying to honour a rule it had no legitimate way to grade.
     * <p>
     * Deliberately narrow: only control-flow and API-use mandates match, and only inside {@code ## Rules}. A technique named as guidance in the student-facing statement is
     * fine and often desirable; what must not happen is a graded rule the tests are then obliged to cover.
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
            String mandate = matcher.group().strip().replaceAll("\\s+", " ");
            if (mandates.stream().noneMatch(seen -> seen.equalsIgnoreCase(mandate))) {
                mandates.add(mandate);
            }
        }
        return List.copyOf(mandates);
    }

    /** The body of one markdown section, up to the next top-level heading. */
    static String markdownSectionBody(String document, String heading) {
        int start = document.indexOf(heading);
        if (start < 0) {
            return "";
        }
        int bodyStart = start + heading.length();
        int next = document.indexOf("\n## ", bodyStart);
        return next < 0 ? document.substring(bodyStart) : document.substring(bodyStart, next);
    }

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

    /**
     * Whether a file is excluded from the solution-leak comparison: orphan residue, a build/harness/manifest file (covered by the harness gate), or a dotfile that is legitimately
     * identical between template and solution and contains no answer.
     *
     * @param path the repository-relative path
     * @return {@code true} if the file is ignored by the leak gate
     */
    private static boolean isLeakIgnoredFile(String path) {
        return isResidueOutsideCanonicalRoot(path) || isHarnessFile(path) || basename(path).startsWith(".");
    }
}
