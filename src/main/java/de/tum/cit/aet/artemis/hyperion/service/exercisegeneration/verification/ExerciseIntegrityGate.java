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

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.GenerationWorkspaceService;

/**
 * Pure (sandbox-free) correctness gates {@link DifferentialVerificationService} applies on top of the differential build oracle, catching two broken-exercise classes the build
 * oracle alone cannot see (the sandbox build can pass while production is broken or the solution is leaked):
 * <ul>
 * <li><b>Harness tampering.</b> The seeded tests-repo build/harness/manifest files are graded verbatim in production. If the agent rewrites one, the sandbox build can still pass
 * while production fails because CI lays the tree out differently or because dependencies/plugins/scripts changed. We snapshot those files at seed time and reject any
 * post-generation harness change, modulo only the CI checkout-placeholder substitution the pipeline applies (so an agent that does not touch the harness is not penalized).</li>
 * <li><b>Solution leak.</b> The template repository ships to students. A reference-solution implementation copied into a non-graded template path hands students the answer while
 * the build still passes. The residue strip is the primary defence; this gate is the backstop, rejecting such a copy without flagging shared interfaces/headers or git config that
 * are
 * legitimately identical between template and solution (a graded-path copy that makes the template pass is left to the differential oracle).</li>
 * </ul>
 * The gates are static and side-effect-free so they are unit-testable without Docker, and so the residue-strip half can be reused by {@link GenerationWorkspaceService} on
 * read-back.
 */
public final class ExerciseIntegrityGate {

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

    private static final Pattern JAVA_PACKAGE_DECLARATION = Pattern.compile("^\\s*package\\s+([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)\\s*;");

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

    /** A short, deterministic sample of names for a rejection message, sorted and capped so a large baseline suite never floods the reason text. */
    private static String sampleNames(Set<String> names) {
        return names.stream().sorted().limit(5).collect(Collectors.joining(", "));
    }

    /**
     * The cabal mixin that renames the reference solution's module so the tests can compare against it: {@code solution (Exercise as <Ref>)}. Capture group 1 is the reference
     * alias.
     */
    private static final Pattern CABAL_REFERENCE_MIXIN = Pattern.compile("solution\\s*\\(\\s*Exercise\\s+as\\s+(\\w+)\\s*\\)", Pattern.CASE_INSENSITIVE);

    /** A Haskell import line: {@code import [qualified] <Module> [as <Alias>]}. Capture group 1 is the imported module name. */
    private static final Pattern HASKELL_IMPORT = Pattern.compile("^import\\s+(?:qualified\\s+)?([\\w.]+)");

    /**
     * Detects a Haskell test harness that imports the submission as its reference and therefore compares the submission against itself.
     * <p>
     * Applies only to the cabal reference-mixin layout and rejects the unambiguous fingerprint: a bare {@code Exercise} import without a renamed-reference import.
     *
     * @param producedTestsFiles the read-back tests repository (repository-relative path -> content)
     * @return a single actionable reason when the self-comparison fingerprint is unambiguous, otherwise an empty list (gate passes)
     */
    static List<String> selfComparisonHarnessReasons(Map<String, String> producedTestsFiles) {
        try {
            if (producedTestsFiles == null || producedTestsFiles.isEmpty()) {
                return List.of();
            }
            // Scope: a Haskell cabal-mixin harness with a Test.hs driver; absent either -> not this shape -> fail open.
            String cabal = null;
            String testHs = null;
            for (Map.Entry<String, String> entry : producedTestsFiles.entrySet()) {
                String base = basename(entry.getKey());
                if (base.endsWith(".cabal")) {
                    cabal = entry.getValue();
                }
                else if ("Test.hs".equals(base)) {
                    testHs = entry.getValue();
                }
            }
            if (cabal == null || testHs == null) {
                return List.of();
            }
            // Learn the renamed-reference module name(s) from the cabal (do not hardcode "Solution"); require the Interface indirection too.
            Set<String> referenceModules = new LinkedHashSet<>();
            Matcher mixin = CABAL_REFERENCE_MIXIN.matcher(cabal);
            while (mixin.find()) {
                referenceModules.add(mixin.group(1));
            }
            if (referenceModules.isEmpty() || !cabal.contains("Interface")) {
                return List.of();
            }
            boolean importsBareExercise = false;
            boolean importsRenamedReference = false;
            for (String rawLine : testHs.split("\n", -1)) {
                String line = stripHaskellComments(rawLine).strip();
                Matcher importMatcher = HASKELL_IMPORT.matcher(line);
                if (!importMatcher.find()) {
                    continue;
                }
                String module = importMatcher.group(1);
                if ("Exercise".equals(module)) {
                    importsBareExercise = true;
                }
                else if (referenceModules.contains(module)) {
                    importsRenamedReference = true;
                }
            }
            // Reject only on the certain fingerprint: bare submission imported as the reference and no renamed reference imported. Both-present is ambiguous -> open.
            if (importsBareExercise && !importsRenamedReference) {
                String reference = referenceModules.iterator().next();
                return List.of(
                        "The test harness compares the submission against ITSELF, so wrong code would score 100%. In tests/test/Test.hs you imported the submission module as the "
                                + "reference (an `import qualified Exercise as ...` line): under the cabal `solution (Exercise as " + reference
                                + ")` mixin the bare `Exercise` module IS the "
                                + "submission, so every assertion becomes submission == submission and passes for any implementation. Fix: import the reference as the renamed module — "
                                + "`import qualified " + reference
                                + " as Sol` — and reach the student's code only through `import qualified Interface as Sub`. Do NOT edit the .cabal mixins.");
            }
            return List.of();
        }
        catch (RuntimeException e) {
            // Fail open on any unexpected parse problem — never block on a gate we could not evaluate confidently.
            return List.of();
        }
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
            reasons.add("Java test classes must use the trusted Ares annotations @Public (de.tum.in.test.api.jupiter.Public), @WhitelistPath(\"target\"), and "
                    + "@BlacklistPath(\"target/test-classes\"); missing or shadowed in " + sampleNames(new LinkedHashSet<>(missingClassAnnotations)) + ".");
        }
        if (!missingTimeouts.isEmpty()) {
            reasons.add("Every Java @Test method must carry the trusted de.tum.in.test.api.StrictTimeout as @StrictTimeout(1) so an infinite loop cannot hang grading; missing, "
                    + "shadowed, or set to another value in " + sampleNames(new LinkedHashSet<>(missingTimeouts)) + ".");
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

    private static boolean hasStrictTimeout(String annotations, Set<String> imports) {
        return hasTrustedAnnotation(annotations, imports, "de.tum.in.test.api.StrictTimeout", "StrictTimeout", "1");
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

    /** Removes Haskell comments from a line (inline {@code &#123;- ... -&#125;} blocks and {@code --} line comments), so a commented-out import can never trip the gate. */
    private static String stripHaskellComments(String line) {
        String withoutBlocks = line.replaceAll("\\{-.*?-\\}", " ");
        int lineComment = withoutBlocks.indexOf("--");
        return lineComment < 0 ? withoutBlocks : withoutBlocks.substring(0, lineComment);
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
