package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure (sandbox-free) correctness gates {@link AuthoritativeVerificationService} applies on top of the differential build oracle, catching two broken-exercise classes the build
 * oracle alone cannot see (the sandbox build can pass while production is broken or the solution is leaked):
 * <ul>
 * <li><b>Harness tampering.</b> The seeded TESTS-repo build/harness/manifest files are graded verbatim in production. If the agent rewrites one's source layout (e.g. a
 * {@code *.cabal} {@code hs-source-dirs} or a {@code tsconfig.json} project reference) away from the CI layout, the sandbox build can still pass while production fails because CI
 * lays the tree out differently. We snapshot those files at seed time and reject any post-generation layout change, modulo the CI placeholder substitution the pipeline applies (so
 * an agent that does NOT touch the harness is never penalized).</li>
 * <li><b>Solution leak.</b> The template repository ships to students. A reference-solution IMPLEMENTATION copied into a non-graded template path hands students the answer while
 * the
 * build still passes. The primary defence is the residue strip; this gate is the backstop, rejecting such a copy without flagging shared interfaces/headers or git config that are
 * legitimately identical between template and solution (a graded-path copy that makes the template pass is left to the differential oracle).</li>
 * </ul>
 * The gates are static and side-effect-free so they are unit-testable without Docker, and so the residue-strip half can be reused by {@link GenerationWorkspaceService} on
 * read-back.
 */
final class ExerciseIntegrityGate {

    /**
     * The CI checkout directory names — the sibling repositories CI lays out next to each other, never legitimate top-level source folders. A file whose first path component is
     * one
     * of these is orphan residue (e.g. a nested {@code solution/src/…} left inside another repo): stripped on read-back, never counted as a harness or source file.
     */
    private static final Set<String> CI_CHECKOUT_DIRECTORY_NAMES = Set.of("assignment", "solution", "template", "tests");

    /**
     * Exact basenames of build/harness/manifest files in the tests repository, graded verbatim in production so the agent must not change them (it edits only the test SOURCE
     * files,
     * deliberately NOT in this set). Matched case-insensitively.
     */
    private static final Set<String> HARNESS_FILE_NAMES = Set.of("pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "gradle.properties",
            "package.json", "package-lock.json", "pnpm-lock.yaml", "yarn.lock", "tsconfig.json", "jest.config.js", "jest.config.ts", "cargo.toml", "cargo.lock", "cabal.project",
            "stack.yaml", "stack.yaml.lock", "dune", "dune-project", "rakefile", "gemfile", "gemfile.lock", "pubspec.yaml", "pubspec.lock", "go.mod", "go.sum", "package.swift",
            "cmakelists.txt", "tests.py", "run.sh", "build.sh", "makefile", "description", "namespace", "assignment_path.rb", "test_helper.rb", ".clang-format");

    /** Filename suffixes that always denote a build/harness/manifest file regardless of basename. Matched case-insensitively. */
    private static final List<String> HARNESS_FILE_SUFFIXES = List.of(".cabal", ".csproj", ".fsproj", ".vbproj", ".sln");

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
     * Strips orphan residue files (those whose first path component is a CI checkout directory name) from a TEMPLATE or SOLUTION repository's produced file map. Preserves order
     * and
     * leaves every canonical-root file untouched.
     *
     * @param files the produced files keyed by repository-relative path
     * @return the same map without orphan residue files
     */
    static Map<String, String> stripResidueOutsideCanonicalRoots(Map<String, String> files) {
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
    static boolean isHarnessFile(String path) {
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

    /**
     * Normalizes a harness line for build-layout comparison: collapses whitespace and applies the SAME CI directory-placeholder substitution the pipeline applies, so an unchanged
     * seed (still carrying raw {@code ${…}} placeholders) compares equal to a produced file the pipeline already substituted. Mirrors
     * {@code SandboxBuildCommandService.verifyScriptContent}.
     *
     * @param line the raw line
     * @return the normalized line
     */
    static String normalizeLayoutLine(String line) {
        return line.replace("${studentWorkingDirectory}", "/assignment/src").replace("${studentParentWorkingDirectoryName}", "assignment")
                .replace("${solutionWorkingDirectory}", "assignment").replace("${testWorkingDirectory}", ".").replaceAll("\\s+", " ").strip();
    }

    /**
     * Whether a harness line is BUILD-LAYOUT-relevant: it defines where the build looks for the submission/solution/template source. We track only these lines so creation-time
     * non-layout placeholders the pipeline does NOT substitute ({@code ${packageName}}, …) never count as tampering. A line qualifies if it references a CI directory placeholder,
     * or
     * is a recognized source-path directive ({@code hs-source-dirs}, {@code ProjectReference Include=}, {@code workspaces}/{@code path}, {@code sourceDirectory}, …) pointing at a
     * CI
     * checkout directory.
     *
     * @param line the raw harness line
     * @return {@code true} if the line defines build layout
     */
    static boolean isBuildLayoutLine(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        if (lower.contains("${studentworkingdirectory}") || lower.contains("${studentparentworkingdirectoryname}") || lower.contains("${solutionworkingdirectory}")
                || lower.contains("${testworkingdirectory}")) {
            return true;
        }
        boolean referencesCheckoutDir = lower.contains("assignment") || lower.contains("solution") || lower.contains("template");
        if (!referencesCheckoutDir) {
            return false;
        }
        return lower.contains("hs-source-dirs") || lower.contains("source-dirs") || lower.contains("sourcedirectory") || lower.contains("projectreference")
                || lower.contains("workspaces") || lower.contains("\"path\"") || lower.contains("path:") || lower.contains("include=") || lower.contains("srcdir")
                || lower.contains("add_subdirectory") || lower.contains("include_directories");
    }

    /**
     * Splits content into normalized lines (CRLF folded, whitespace collapsed, placeholders substituted), preserving order and blank lines so indices line up across seed/produced.
     */
    private static List<String> normalizedLines(String content) {
        List<String> lines = new ArrayList<>();
        for (String line : content.replace("\r\n", "\n").split("\n", -1)) {
            lines.add(normalizeLayoutLine(line));
        }
        return lines;
    }

    /** The 0-based indices of the seed lines that are build-layout-relevant (the lines the agent must not move the source/solution path on). */
    private static List<Integer> layoutLineIndices(List<String> seedRawLines) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < seedRawLines.size(); i++) {
            if (isBuildLayoutLine(seedRawLines.get(i))) {
                indices.add(i);
            }
        }
        return indices;
    }

    /**
     * Detects harness tampering (see class javadoc): only the seed's build-layout lines are compared positionally, after placeholder normalization, so a deleted harness file or a
     * moved source/solution/template path is flagged while creation-time placeholders the sandbox does not substitute never count. Fails OPEN when no seed snapshot is available.
     *
     * @param seedTestsFiles     the tests-repo files snapshotted at seed time (repository-relative); empty disables the gate
     * @param producedTestsFiles the tests-repo files read back after generation (repository-relative)
     * @return one rejection reason per offending file (empty when the harness layout is intact or the gate is disabled)
     */
    static List<String> harnessTamperingReasons(Map<String, String> seedTestsFiles, Map<String, String> producedTestsFiles) {
        if (seedTestsFiles == null || seedTestsFiles.isEmpty()) {
            return List.of();
        }
        List<String> reasons = new ArrayList<>();
        for (Map.Entry<String, String> seed : seedTestsFiles.entrySet()) {
            String path = seed.getKey();
            if (!isHarnessFile(path)) {
                continue;
            }
            List<String> seedRawLines = Arrays.asList(seed.getValue().replace("\r\n", "\n").split("\n", -1));
            List<Integer> layoutIndices = layoutLineIndices(seedRawLines);
            if (layoutIndices.isEmpty()) {
                // No build-layout directive to protect (e.g. a lockfile, or a package.json with no path/workspaces entries).
                continue;
            }
            String produced = producedTestsFiles == null ? null : producedTestsFiles.get(path);
            if (produced == null) {
                reasons.add("you deleted the seeded test harness file tests/" + path + "; the harness is graded verbatim in production — restore it unchanged.");
                continue;
            }
            List<String> seedNormalized = normalizedLines(seed.getValue());
            List<String> producedNormalized = normalizedLines(produced);
            boolean tampered = false;
            for (int index : layoutIndices) {
                // A removed/inserted line ahead of this index, or a changed directive at it, means the seeded source/solution path no longer resolves as production expects.
                if (index >= producedNormalized.size() || !seedNormalized.get(index).equals(producedNormalized.get(index))) {
                    tampered = true;
                    break;
                }
            }
            if (tampered) {
                reasons.add(
                        "you modified the build layout of the seeded test harness file tests/" + path + " (the source/solution paths the build resolves against); the harness is "
                                + "graded verbatim in production with the CI directory layout, so rewriting these paths breaks the real build — revert tests/" + path
                                + " to the seed and edit only the test source files.");
            }
        }
        return reasons;
    }

    /**
     * The cabal mixin that renames the reference solution's module so the tests can compare against it: {@code solution (Exercise as <Ref>)}. Capture group 1 is the reference
     * alias.
     */
    private static final Pattern CABAL_REFERENCE_MIXIN = Pattern.compile("solution\\s*\\(\\s*Exercise\\s+as\\s+(\\w+)\\s*\\)", Pattern.CASE_INSENSITIVE);

    /** A Haskell import line: {@code import [qualified] <Module> [as <Alias>]}. Capture group 1 is the imported module name. */
    private static final Pattern HASKELL_IMPORT = Pattern.compile("^import\\s+(?:qualified\\s+)?([\\w.]+)");

    /**
     * Detects a Haskell test harness that compares the submission against ITSELF — a tautology the differential oracle is blind to (the template still errors, so the
     * solution-passes/template-fails invariant holds) yet which scores ANY submission 100%. Under the cabal mixin layout the tests rename the reference module
     * ({@code solution (Exercise as Solution)}) and reach the student code via an {@code Interface} indirection; the bug is {@code import qualified Exercise as Sol}, where the
     * bare
     * {@code Exercise} module IS the submission, so every assertion becomes {@code submission == submission}.
     * <p>
     * Haskell-only and contract-gated: fires ONLY when a cabal declares the {@code solution (Exercise as <Ref>)} mixin plus an {@code Interface} indirection, and REJECTS only on
     * the
     * unambiguous fingerprint (bare {@code Exercise} import present AND no renamed-reference import). Every other shape fails OPEN — a false reject (burning the retry budget) is
     * worse than the gap.
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
            // Learn the renamed-reference module name(s) FROM the cabal (never hardcode "Solution"); require the Interface indirection too.
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
            // REJECT only on the certain fingerprint: bare submission imported as the reference AND no renamed reference imported. Both-present is ambiguous -> open.
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
     * <li>NOT files legitimately identical between template and solution at the SAME path — shared interfaces/headers, git dotfiles, harness files (an implementation file is one
     * that
     * DIFFERS from the template at its own path).</li>
     * <li>NOT a template that copies the solution into the SAME graded path — that makes the template pass, already rejected by the oracle's "template must fail" gate.</li>
     * <li>MUST flag the solution implementation copied into an EXTRA template file at a non-graded path.</li>
     * </ul>
     * Fails OPEN when either side is empty.
     *
     * @param templateFiles the produced TEMPLATE repository files (repository-relative; residue already stripped)
     * @param solutionFiles the produced SOLUTION repository files (repository-relative; residue already stripped)
     * @return a single reason listing the leaked paths, or empty when no leak
     */
    static List<String> solutionLeakReasons(Map<String, String> templateFiles, Map<String, String> solutionFiles) {
        if (templateFiles == null || templateFiles.isEmpty() || solutionFiles == null || solutionFiles.isEmpty()) {
            return List.of();
        }
        // Solution IMPLEMENTATION bodies: solution source whose content differs from the template's at the same path (so a shared interface/config identical there is excluded).
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
            // A copy at the SAME graded path makes the template pass — already rejected by the oracle, so do not double-report it.
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
