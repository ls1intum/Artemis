package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Deterministic unit tests for the sandbox-free correctness gates: the harness-immutability check, the solution-leak check, and the orphan-residue strip. The fixtures mirror the
 * real matrix exports: the genuinely-good ones (TypeScript, Haskell post-fix) must pass, and the broken Haskell run (tampered {@code test.cabal} build layout, a solution copy
 * shipped in the template, an orphan residue tree) must be rejected/stripped.
 */
class ExerciseIntegrityGateTest {

    // The reference solution body, long enough to clear the leak gate's minimum-length guard.
    private static final String SOLUTION_EXERCISE_HS = """
            module Exercise (factorial) where

            factorial :: Integer -> Integer
            factorial n
              | n < 0     = error "factorial: negative input"
              | n == 0    = 1
              | otherwise = n * factorial (n - 1)
            """;

    private static final String TEMPLATE_STUB_HS = """
            module Exercise (factorial) where

            factorial :: Integer -> Integer
            factorial _ = error "not implemented"
            """;

    // The seeded Haskell test.cabal, with the CI placeholders still raw (as the scaffold seeds them).
    private static final String SEED_TEST_CABAL = """
            library submission
              hs-source-dirs: ${studentParentWorkingDirectoryName}/src
              exposed-modules: Exercise

            library solution
              hs-source-dirs: ${solutionWorkingDirectory}/src
              exposed-modules: Exercise
            """;

    private static Map<String, String> map(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    // --- Harness-immutability gate ---

    @Test
    void harness_acceptsUnchangedHarness_evenWhenPipelineSubstitutedPlaceholders() {
        // The seed carries raw ${...}; the produced file has the placeholders substituted to the sandbox layout exactly as verify.sh does. Normalization makes them equal ->
        // accept.
        String producedAfterPipelineSubstitution = SEED_TEST_CABAL.replace("${studentParentWorkingDirectoryName}", "assignment").replace("${solutionWorkingDirectory}",
                "assignment");
        assertThat(ExerciseIntegrityGate.harnessTamperingReasons(map("test.cabal", SEED_TEST_CABAL), map("test.cabal", producedAfterPipelineSubstitution))).isEmpty();
    }

    @Test
    void harness_acceptsByteIdenticalHarness() {
        assertThat(ExerciseIntegrityGate.harnessTamperingReasons(map("test.cabal", SEED_TEST_CABAL), map("test.cabal", SEED_TEST_CABAL))).isEmpty();
    }

    @Test
    void harness_rejectsWhenBuildLayoutLineChangedAwayFromSeed() {
        // The exact Haskell defect: the agent rewrote the solution library's hs-source-dirs to assignment/solution/src, which is NOT where production lays the solution out.
        String tampered = SEED_TEST_CABAL.replace("${studentParentWorkingDirectoryName}/src", "assignment/src").replace("${solutionWorkingDirectory}/src",
                "assignment/solution/src");
        var reasons = ExerciseIntegrityGate.harnessTamperingReasons(map("test.cabal", SEED_TEST_CABAL), map("test.cabal", tampered));
        assertThat(reasons).hasSize(1);
        assertThat(reasons.getFirst()).contains("tests/test.cabal").contains("harness is graded");
    }

    @Test
    void harness_rejectsCsprojProjectReferencePathChange() {
        String seed = "<Project>\n  <ItemGroup>\n    <ProjectReference Include=\"${studentParentWorkingDirectoryName}/assignment.csproj\"/>\n  </ItemGroup>\n</Project>";
        String tampered = "<Project>\n  <ItemGroup>\n    <ProjectReference Include=\"../solution/assignment.csproj\"/>\n  </ItemGroup>\n</Project>";
        var reasons = ExerciseIntegrityGate.harnessTamperingReasons(map("Test.csproj", seed), map("Test.csproj", tampered));
        assertThat(reasons).hasSize(1);
        assertThat(reasons.getFirst()).contains("tests/Test.csproj");
    }

    @Test
    void harness_rejectsDeletedHarnessFileThatHadBuildLayout() {
        // A seeded *.cabal that defined hs-source-dirs and was deleted: production grades it verbatim, so its absence breaks the build.
        var reasons = ExerciseIntegrityGate.harnessTamperingReasons(map("test.cabal", SEED_TEST_CABAL), map());
        assertThat(reasons).hasSize(1);
        assertThat(reasons.getFirst()).contains("deleted").contains("tests/test.cabal");
    }

    @Test
    void harness_acceptsChangesToNonLayoutPlaceholderLines() {
        // The real Dart-class case: the agent/scaffold substituted a NON-layout creation-time placeholder (${packageName} -> test_package) that the sandbox does not substitute.
        // This is not build-layout, so it must NOT be flagged — only the hs-source-dirs/path lines are enforced.
        String seedPubspec = "name: ${packageName}\nenvironment:\n  sdk: '>=3.0.0 <4.0.0'\n";
        String producedPubspec = "name: test_package\nenvironment:\n  sdk: '>=3.0.0 <4.0.0'\n";
        assertThat(ExerciseIntegrityGate.harnessTamperingReasons(map("pubspec.yaml", seedPubspec), map("pubspec.yaml", producedPubspec))).isEmpty();
    }

    @Test
    void harness_ignoresTestSourceFiles_onlyBuildFilesAreGraded() {
        // The agent is SUPPOSED to edit test source files; a changed Test.hs / *.test.ts must not be flagged.
        var reasons = ExerciseIntegrityGate.harnessTamperingReasons(map("test/Test.hs", "old", "src/stack.test.ts", "old"),
                map("test/Test.hs", "completely rewritten", "src/stack.test.ts", "completely rewritten"));
        assertThat(reasons).isEmpty();
    }

    @Test
    void isBuildLayoutLine_flagsSourcePathDirectivesButNotPackageNames() {
        assertThat(ExerciseIntegrityGate.isBuildLayoutLine("  hs-source-dirs: ${solutionWorkingDirectory}/src")).isTrue();
        assertThat(ExerciseIntegrityGate.isBuildLayoutLine("  hs-source-dirs: assignment/solution/src")).isTrue();
        assertThat(ExerciseIntegrityGate.isBuildLayoutLine("    <ProjectReference Include=\"../solution/assignment.csproj\"/>")).isTrue();
        assertThat(ExerciseIntegrityGate.isBuildLayoutLine("      \"path\": \"${studentParentWorkingDirectoryName}\"")).isTrue();
        assertThat(ExerciseIntegrityGate.isBuildLayoutLine("        \"assignment\"")).isFalse(); // a bare workspaces entry without a directive keyword is not matched
        assertThat(ExerciseIntegrityGate.isBuildLayoutLine("name: ${packageName}")).isFalse();
        assertThat(ExerciseIntegrityGate.isBuildLayoutLine("name: test_package")).isFalse();
        assertThat(ExerciseIntegrityGate.isBuildLayoutLine("version: 0.1")).isFalse();
    }

    @Test
    void harness_failsOpenWithoutSeedSnapshot() {
        assertThat(ExerciseIntegrityGate.harnessTamperingReasons(Map.of(), map("test.cabal", "anything"))).isEmpty();
    }

    @Test
    void isHarnessFile_recognizesBuildAndManifestFilesAcrossLanguages() {
        assertThat(ExerciseIntegrityGate.isHarnessFile("test.cabal")).isTrue();
        assertThat(ExerciseIntegrityGate.isHarnessFile("Test.csproj")).isTrue();
        assertThat(ExerciseIntegrityGate.isHarnessFile("package.json")).isTrue();
        assertThat(ExerciseIntegrityGate.isHarnessFile("package-lock.json")).isTrue();
        assertThat(ExerciseIntegrityGate.isHarnessFile("tsconfig.json")).isTrue();
        assertThat(ExerciseIntegrityGate.isHarnessFile("Cargo.toml")).isTrue();
        assertThat(ExerciseIntegrityGate.isHarnessFile("CMakeLists.txt")).isTrue();
        assertThat(ExerciseIntegrityGate.isHarnessFile("Rakefile")).isTrue();
        assertThat(ExerciseIntegrityGate.isHarnessFile("pubspec.yaml")).isTrue();
        assertThat(ExerciseIntegrityGate.isHarnessFile("run.sh")).isTrue();
        assertThat(ExerciseIntegrityGate.isHarnessFile("DESCRIPTION")).isTrue();
        // NOT harness: the test sources the agent edits.
        assertThat(ExerciseIntegrityGate.isHarnessFile("test/Test.hs")).isFalse();
        assertThat(ExerciseIntegrityGate.isHarnessFile("src/stack.test.ts")).isFalse();
        assertThat(ExerciseIntegrityGate.isHarnessFile("test/test_stack.rb")).isFalse();
        // A nested *.yml data fixture is not a root build descriptor.
        assertThat(ExerciseIntegrityGate.isHarnessFile("test/fixtures/data.yml")).isFalse();
    }

    // --- Solution-leak gate ---

    @Test
    void leak_rejectsSolutionImplementationCopiedToANonGradedTemplatePath() {
        // The leak the differential oracle CANNOT see: the solution implementation copied into an EXTRA template file at a non-graded path. The graded src/ holds a proper stub, so
        // the template still fails its tests, yet lib/Reference.hs ships the answer to students.
        Map<String, String> template = map("src/Exercise.hs", TEMPLATE_STUB_HS, "lib/Reference.hs", SOLUTION_EXERCISE_HS);
        Map<String, String> solution = map("src/Exercise.hs", SOLUTION_EXERCISE_HS);
        var reasons = ExerciseIntegrityGate.solutionLeakReasons(template, solution);
        assertThat(reasons).hasSize(1);
        assertThat(reasons.getFirst()).contains("template leaks the reference solution").contains("lib/Reference.hs");
    }

    @Test
    void leak_doesNotDoubleReportASolutionCopyAtTheSameGradedPath() {
        // A template that copies the solution into the SAME graded path makes the template PASS its tests; that is the differential oracle's job to reject (template must fail),
        // not
        // the leak gate's — so the leak gate must not double-report it.
        Map<String, String> template = map("src/Exercise.hs", SOLUTION_EXERCISE_HS);
        Map<String, String> solution = map("src/Exercise.hs", SOLUTION_EXERCISE_HS);
        assertThat(ExerciseIntegrityGate.solutionLeakReasons(template, solution)).isEmpty();
    }

    @Test
    void leak_acceptsAGenuineStubTemplate() {
        // A proper template: placeholder bodies, NOT a copy of the solution.
        Map<String, String> template = map("src/Exercise.hs", TEMPLATE_STUB_HS);
        Map<String, String> solution = map("src/Exercise.hs", SOLUTION_EXERCISE_HS);
        assertThat(ExerciseIntegrityGate.solutionLeakReasons(template, solution)).isEmpty();
    }

    @Test
    void leak_acceptsSharedInterfaceHeaderIdenticalInTemplateAndSolution() {
        // The C++ case: include/stack.hpp (the interface) and a harness main.cpp are LEGITIMATELY identical between template and solution; only the .cpp implementation differs.
        // The
        // identical interface must NOT be called a leak.
        String header = "#pragma once\n#include <vector>\nclass Stack {\npublic:\n  void push(int v);\n  int pop();\n  bool empty() const;\n};\n";
        Map<String, String> template = map("include/stack.hpp", header, "src/stack.cpp", "// TODO: implement\nint Stack::pop() { return 0; }\n");
        Map<String, String> solution = map("include/stack.hpp", header, "src/stack.cpp", "#include \"stack.hpp\"\nint Stack::pop() { /* real */ return top(); }\n");
        assertThat(ExerciseIntegrityGate.solutionLeakReasons(template, solution)).isEmpty();
    }

    @Test
    void leak_ignoresDotfilesIdenticalAcrossTemplateAndSolution() {
        // .gitignore / .gitattributes are legitimately identical and contain no answer; they must never be flagged.
        String gitignore = "/build\n/node_modules\n*.log\n# generated artifacts and caches\n";
        Map<String, String> template = map("src/Exercise.hs", TEMPLATE_STUB_HS, ".gitignore", gitignore);
        Map<String, String> solution = map("src/Exercise.hs", SOLUTION_EXERCISE_HS, ".gitignore", gitignore);
        assertThat(ExerciseIntegrityGate.solutionLeakReasons(template, solution)).isEmpty();
    }

    @Test
    void leak_ignoresTriviallyShortBodies() {
        // An empty .gitkeep or a one-line identical scaffold marker must not be called a leak.
        Map<String, String> template = map("src/marker.txt", "x", "src/.gitkeep", "");
        Map<String, String> solution = map("src/marker.txt", "x", "src/.gitkeep", "");
        assertThat(ExerciseIntegrityGate.solutionLeakReasons(template, solution)).isEmpty();
    }

    @Test
    void leak_failsOpenWhenEitherSideEmpty() {
        assertThat(ExerciseIntegrityGate.solutionLeakReasons(Map.of(), map("a", SOLUTION_EXERCISE_HS))).isEmpty();
        assertThat(ExerciseIntegrityGate.solutionLeakReasons(map("a", SOLUTION_EXERCISE_HS), Map.of())).isEmpty();
    }

    // --- Residue strip ---

    @Test
    void residueStrip_removesNestedOrphanSourceTrees_keepsCanonicalRoots() {
        // The real Haskell template export: canonical src/Exercise.hs plus orphan template/assignment/solution/src/... and template/solution/src/... residue (solution leaks).
        Map<String, String> templateFiles = map("src/Exercise.hs", TEMPLATE_STUB_HS, "exercise.cabal", "name: exercise\n", "assignment/solution/src/Exercise.hs",
                SOLUTION_EXERCISE_HS, "solution/src/Exercise.hs", SOLUTION_EXERCISE_HS);
        Map<String, String> cleaned = ExerciseIntegrityGate.stripResidueOutsideCanonicalRoots(templateFiles);
        assertThat(cleaned).containsOnlyKeys("src/Exercise.hs", "exercise.cabal");
    }

    @Test
    void residueStrip_removesOrphanSolutionCopyInSolutionRepo() {
        // The real Haskell solution export: canonical src/Exercise.hs plus an orphan solution/src/Exercise.hs (the buggy duplicate).
        Map<String, String> solutionFiles = map("src/Exercise.hs", SOLUTION_EXERCISE_HS, "solution.cabal", "name: solution\n", "solution/src/Exercise.hs", "buggy copy here");
        Map<String, String> cleaned = ExerciseIntegrityGate.stripResidueOutsideCanonicalRoots(solutionFiles);
        assertThat(cleaned).containsOnlyKeys("src/Exercise.hs", "solution.cabal");
    }

    @Test
    void residueStrip_isNoOpForGoodExportLayouts() {
        // TypeScript / C# / R / Dart canonical layouts: src/, root manifests, Calculator.cs, R/, lib/, include/ — none re-enter a CI checkout directory, so nothing is stripped.
        Map<String, String> good = map("src/stack.ts", "x", "tsconfig.json", "{}", "package.json", "{}", "include/stack.hpp", "h", "Calculator.cs", "c", "R/column_sums.R", "r",
                "lib/string_utils.dart", "d", "Sources/testPackageLib/Stack.swift", "s");
        assertThat(ExerciseIntegrityGate.stripResidueOutsideCanonicalRoots(good)).isEqualTo(good);
    }

    @Test
    void isResidueOutsideCanonicalRoot_flagsOnlyCiCheckoutTopComponents() {
        assertThat(ExerciseIntegrityGate.isResidueOutsideCanonicalRoot("assignment/solution/src/Exercise.hs")).isTrue();
        assertThat(ExerciseIntegrityGate.isResidueOutsideCanonicalRoot("solution/src/Exercise.hs")).isTrue();
        assertThat(ExerciseIntegrityGate.isResidueOutsideCanonicalRoot("template/src/Exercise.hs")).isTrue();
        assertThat(ExerciseIntegrityGate.isResidueOutsideCanonicalRoot("tests/foo")).isTrue();
        assertThat(ExerciseIntegrityGate.isResidueOutsideCanonicalRoot("src/Exercise.hs")).isFalse();
        assertThat(ExerciseIntegrityGate.isResidueOutsideCanonicalRoot("Calculator.cs")).isFalse();
        assertThat(ExerciseIntegrityGate.isResidueOutsideCanonicalRoot("R/column_sums.R")).isFalse();
    }
}
