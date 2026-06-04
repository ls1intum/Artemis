package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pure (sandbox-free) correctness gates that the {@link AuthoritativeVerificationService} applies on top of the differential build oracle. They catch two classes of broken
 * exercise the build oracle alone cannot see, because the sandbox build can pass while production is broken or the solution is leaked:
 * <ul>
 * <li><b>Harness tampering.</b> The seeded TESTS-repo build/harness/manifest/report files are graded verbatim in production. If the agent edits one (e.g. rewrites a
 * {@code *.cabal} {@code hs-source-dirs} or a {@code tsconfig.json} project reference away from the CI layout), the sandbox build can still pass while production fails because the
 * CI placeholder substitution lays the tree out differently. We snapshot those files at seed time and reject any post-generation change, modulo the same CI placeholder
 * substitution the pipeline itself applies (so an agent that does NOT touch the harness — the expected case once {@code verify.sh} substitutes placeholders for it — is never
 * penalized).</li>
 * <li><b>Solution leak.</b> The template repository ships to students. If it contains a copy of a reference-solution IMPLEMENTATION file at a path the tests do not grade, students
 * get the answer for free while the build still passes. The primary defence is the residue strip (nested {@code solution/…}/{@code template/assignment/…} copies are dropped on
 * read-back); the leak gate is the backstop that rejects a solution implementation copied into an extra, non-graded template file — without flagging shared interfaces/headers or
 * git config that are legitimately identical between template and solution (a graded-path copy that makes the template pass is left to the differential oracle).</li>
 * </ul>
 * The gates are deliberately static and side-effect-free so they are exhaustively unit-testable without Docker, and so the residue-strip half can be reused by
 * {@link GenerationWorkspaceService} when reading repositories back out.
 */
final class ExerciseIntegrityGate {

    /**
     * The CI checkout directory names. A template/solution repository never legitimately contains a top-level directory with one of these names — they are the sibling repositories
     * the CI lays out next to each other, not source folders. A file whose first path component is one of these is therefore orphan residue (e.g. a nested {@code solution/src/…}
     * left inside the template or solution repo) and is stripped on read-back, and never counted as a harness or source file by the gates.
     */
    private static final Set<String> CI_CHECKOUT_DIRECTORY_NAMES = Set.of("assignment", "solution", "template", "tests");

    /**
     * Exact filenames of build/harness/manifest/report files in the tests repository. These are graded verbatim in production, so the agent must not change them; it edits only
     * the test SOURCE files (which are deliberately NOT in this set). Matched case-insensitively on the file's basename.
     */
    private static final Set<String> HARNESS_FILE_NAMES = Set.of("pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "gradle.properties",
            "package.json", "package-lock.json", "pnpm-lock.yaml", "yarn.lock", "tsconfig.json", "jest.config.js", "jest.config.ts", "cargo.toml", "cargo.lock", "cabal.project",
            "stack.yaml", "stack.yaml.lock", "dune", "dune-project", "rakefile", "gemfile", "gemfile.lock", "pubspec.yaml", "pubspec.lock", "go.mod", "go.sum", "package.swift",
            "cmakelists.txt", "tests.py", "run.sh", "build.sh", "makefile", "description", "namespace", "assignment_path.rb", "test_helper.rb", ".clang-format");

    /**
     * Filename suffixes that always denote a build/harness/manifest file regardless of the basename ({@code *.cabal}, {@code *.csproj}, {@code *.fsproj}, {@code *.sln}). Matched
     * case-insensitively.
     */
    private static final List<String> HARNESS_FILE_SUFFIXES = List.of(".cabal", ".csproj", ".fsproj", ".vbproj", ".sln");

    private ExerciseIntegrityGate() {
    }

    /**
     * Returns the path's first component (the segment before the first {@code /}), or the whole path if it has none.
     */
    private static String firstComponent(String path) {
        int slash = path.indexOf('/');
        return slash < 0 ? path : path.substring(0, slash);
    }

    /**
     * Returns the path's basename (the segment after the last {@code /}), or the whole path if it has none.
     */
    private static String basename(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    /**
     * Whether a repository-relative path is orphan residue: its first path component is one of the CI checkout directory names. The canonical layout places sources directly under
     * the repository root (e.g. {@code src/…}, {@code R/…}, {@code Calculator.cs}); a path re-entering an {@code assignment/}, {@code solution/}, {@code template/}, or
     * {@code tests/} directory is a duplicate of the CI sibling-checkout structure left behind by the scaffold or the agent, not a real source file.
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
     * Normalizes a single harness line for build-layout comparison: collapses whitespace and applies the SAME CI directory-placeholder substitution the verification pipeline
     * applies to the build-tree copy, so an unchanged seed (which still carries the raw {@code ${…}} placeholders) compares equal to a produced file that the pipeline already
     * substituted. Mirrors {@code SandboxBuildCommandService.verifyScriptContent}: in the sandbox the chosen assignment is copied into {@code assignment/}, so both the student
     * parent and the solution working directory map to {@code assignment}.
     *
     * @param line the raw line
     * @return the normalized line
     */
    static String normalizeLayoutLine(String line) {
        return line.replace("${studentWorkingDirectory}", "/assignment/src").replace("${studentParentWorkingDirectoryName}", "assignment")
                .replace("${solutionWorkingDirectory}", "assignment").replace("${testWorkingDirectory}", ".").replaceAll("\\s+", " ").strip();
    }

    /**
     * Whether a harness line is BUILD-LAYOUT-relevant: it defines where the build looks for the submission/solution/template source. We track exactly these lines (rather than the
     * whole file) so that creation-time, non-layout placeholders that the pipeline does NOT substitute in the sandbox — {@code ${packageName}}, {@code ${exerciseName}}, … — never
     * count as tampering. A line is layout-relevant if it references one of the four CI directory placeholders, OR it is a recognized source-path directive that points at a CI
     * checkout directory name (a Haskell {@code hs-source-dirs}, a .NET {@code ProjectReference Include=}, a JS/TS {@code workspaces}/project {@code path}, a Maven
     * {@code sourceDirectory}, …). This is the narrow, certain signal the gate enforces.
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
     * Detects tampering with the seeded tests-repo harness: a harness/build/manifest file whose BUILD-LAYOUT lines (where the build resolves the submission/solution/template
     * sources) changed versus the seed, after normalizing the CI directory-placeholder substitution the pipeline itself applies, or a seeded harness file that was deleted. Only
     * the
     * seed's build-layout lines are compared (positionally, after placeholder normalization), NOT the whole file, so creation-time placeholders the sandbox does not substitute
     * ({@code ${packageName}}, {@code ${exerciseName}}) never count as tampering — exactly the narrow, certain win that catches the Haskell {@code hs-source-dirs} rewrite (and a
     * .NET {@code ProjectReference} / a JS {@code workspaces} repoint) while never penalizing a genuinely-good exercise. A change to the file's line structure (so the seed's
     * layout
     * line index no longer holds the matching directive) is itself treated as tampering. Returns one rejection reason per offending file. Fails OPEN when no seed snapshot is
     * available, so the gate never spuriously rejects when the snapshot could not be captured.
     *
     * @param seedTestsFiles     the tests-repo files snapshotted at seed time (repository-relative); empty disables the gate
     * @param producedTestsFiles the tests-repo files read back after generation (repository-relative)
     * @return the rejection reasons (empty when the harness layout is intact or the gate is disabled)
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
                // No build-layout directive to protect in this file (e.g. a lockfile, or a package.json with no path/workspaces entries); nothing to enforce here.
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
                // A removed/inserted line ahead of this index, or a changed directive at it, both mean the seeded source/solution path no longer resolves as production expects.
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

    /** Normalizes a file body for content-equality: CRLF folded and surrounding whitespace stripped. */
    private static String normalizeBody(String content) {
        return content == null ? "" : content.replace("\r\n", "\n").strip();
    }

    /** Minimum normalized length for a body to be considered a meaningful source file (so an empty .gitkeep or a one-line marker is never called a leak). */
    private static final int MIN_LEAK_BODY_LENGTH = 40;

    /**
     * Detects a solution leak in the template that the differential build oracle would NOT catch: a TEMPLATE file that copies a reference-solution IMPLEMENTATION file at a path
     * the
     * tests do not grade (so the build is unaffected, yet the answer ships to students). The hard cases this must DISTINGUISH:
     * <ul>
     * <li>It must NOT flag files that are legitimately identical between template and solution — shared interfaces/headers (e.g. a C++ {@code include/stack.hpp} declaration), git
     * config dotfiles ({@code .gitignore}, {@code .gitattributes}), or harness files. These are identical at the SAME path on purpose, so they are excluded by definition (an
     * implementation file is one that DIFFERS from the template at its own path).</li>
     * <li>It must NOT double-flag a template that copies the solution into the SAME, graded path (e.g. {@code template/src/Exercise.hs} == {@code solution/src/Exercise.hs}) — that
     * makes the template pass its tests and is already rejected by the differential oracle's "template must fail" gate.</li>
     * <li>It MUST flag the solution implementation copied into an EXTRA template file at a non-graded path (e.g. {@code template/lib/Reference.hs}, or a residue tree — though
     * residue is already stripped on read-back).</li>
     * </ul>
     * The rule: collect the SOLUTION implementation bodies (solution source files, non-dotfile, non-harness, that DIFFER from the template at the same path). Flag any template
     * file
     * (non-dotfile, non-harness) whose body equals one of those, EXCEPT a template file sitting at a path where the solution holds the same body (the differential-oracle case).
     * Fails OPEN when either side is empty.
     *
     * @param templateFiles the produced TEMPLATE repository files (repository-relative; residue already stripped)
     * @param solutionFiles the produced SOLUTION repository files (repository-relative; residue already stripped)
     * @return the rejection reasons (a single reason listing the leaked paths, or empty when no leak)
     */
    static List<String> solutionLeakReasons(Map<String, String> templateFiles, Map<String, String> solutionFiles) {
        if (templateFiles == null || templateFiles.isEmpty() || solutionFiles == null || solutionFiles.isEmpty()) {
            return List.of();
        }
        // Solution IMPLEMENTATION bodies: solution source files whose content is NOT the template's content at the same path (so a shared interface/config identical at the same
        // path is excluded). These are the genuinely solution-specific answers a template must never contain.
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
                // Identical at the same path => a shared interface/header/config, legitimately the same in template and solution; not an answer.
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
            // A template file that copies the solution into the SAME graded path makes the template pass — already rejected by the differential oracle, so do not double-report it.
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
     * Whether a file is excluded from the solution-leak comparison: orphan residue (already stripped, never shipped at a canonical path), a build/harness/manifest file (graded
     * verbatim, covered by the harness gate), or a dotfile ({@code .gitignore}, {@code .gitattributes}, {@code .clang-format}) that is legitimately identical between template and
     * solution and contains no answer.
     *
     * @param path the repository-relative path
     * @return {@code true} if the file is ignored by the leak gate
     */
    private static boolean isLeakIgnoredFile(String path) {
        return isResidueOutsideCanonicalRoot(path) || isHarnessFile(path) || basename(path).startsWith(".");
    }
}
