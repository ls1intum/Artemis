package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

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
        assertThat(ExerciseIntegrityGate.harnessTamperingReasons(map("test.cabal", SEED_TEST_CABAL), map("test.cabal", producedAfterPipelineSubstitution), false)).isEmpty();
    }

    @Test
    void harness_rejectsWhenBuildLayoutLineChangedAwayFromSeed() {
        // The exact Haskell defect: the agent rewrote the solution library's hs-source-dirs to assignment/solution/src, which is NOT where production lays the solution out.
        String tampered = SEED_TEST_CABAL.replace("${studentParentWorkingDirectoryName}/src", "assignment/src").replace("${solutionWorkingDirectory}/src",
                "assignment/solution/src");
        var reasons = ExerciseIntegrityGate.harnessTamperingReasons(map("test.cabal", SEED_TEST_CABAL), map("test.cabal", tampered), false);
        assertThat(reasons).hasSize(1);
        assertThat(reasons.getFirst()).contains("tests/test.cabal").contains("harness is graded");
    }

    @Test
    void harness_rejectsCsprojProjectReferencePathChange() {
        String seed = "<Project>\n  <ItemGroup>\n    <ProjectReference Include=\"${studentParentWorkingDirectoryName}/assignment.csproj\"/>\n  </ItemGroup>\n</Project>";
        String tampered = "<Project>\n  <ItemGroup>\n    <ProjectReference Include=\"../solution/assignment.csproj\"/>\n  </ItemGroup>\n</Project>";
        var reasons = ExerciseIntegrityGate.harnessTamperingReasons(map("Test.csproj", seed), map("Test.csproj", tampered), false);
        assertThat(reasons).hasSize(1);
        assertThat(reasons.getFirst()).contains("tests/Test.csproj");
    }

    @Test
    void harness_rejectsDeletedHarnessFileThatHadBuildLayout() {
        // A seeded *.cabal that defined hs-source-dirs and was deleted: production grades it verbatim, so its absence breaks the build.
        var reasons = ExerciseIntegrityGate.harnessTamperingReasons(map("test.cabal", SEED_TEST_CABAL), map(), false);
        assertThat(reasons).hasSize(1);
        assertThat(reasons.getFirst()).contains("deleted").contains("tests/test.cabal");
    }

    @Test
    void harness_rejectsAnAddedBuildManifest() {
        var reasons = ExerciseIntegrityGate.harnessTamperingReasons("solution", map("src/Sorter.java", "class Sorter {}"),
                map("src/Sorter.java", "class Sorter {}", "pom.xml", "<project/>"), false);

        assertThat(reasons).singleElement().asString().contains("added", "solution/pom.xml");
    }

    @Test
    void harness_rejectsSemanticYamlIndentationChanges() {
        String seed = "steps:\n  test:\n    command: ./gradlew test\n";
        String tampered = "steps:\n  test:\n  command: ./gradlew test\n";

        assertThat(ExerciseIntegrityGate.harnessTamperingReasons("tests", map("build.yml", seed), map("build.yml", tampered), false)).hasSize(1);
    }

    @Test
    void harness_rejectsChangesToNonLayoutPlaceholderLines() {
        // The real Dart-class case: the agent/scaffold substituted a NON-layout creation-time placeholder (${packageName} -> test_package) that the sandbox does not substitute.
        // Even this is rejected now: harness files are graded verbatim, so only CI checkout-placeholder substitution is normalized.
        String seedPubspec = "name: ${packageName}\nenvironment:\n  sdk: '>=3.0.0 <4.0.0'\n";
        String producedPubspec = "name: test_package\nenvironment:\n  sdk: '>=3.0.0 <4.0.0'\n";
        assertThat(ExerciseIntegrityGate.harnessTamperingReasons(map("pubspec.yaml", seedPubspec), map("pubspec.yaml", producedPubspec), false)).hasSize(1);
    }

    @Test
    void harness_rejectsWhitespaceChangesInsideQuotedArguments() {
        String seed = "run: printf 'a  b\\n'\n";
        String tampered = "run: printf 'a b\\n'\n";

        assertThat(ExerciseIntegrityGate.harnessTamperingReasons(map("build.yml", seed), map("build.yml", tampered), false)).hasSize(1);
    }

    @Test
    void harness_ignoresTestSourceFiles_onlyBuildFilesAreGraded() {
        // The agent is SUPPOSED to edit test source files; a changed Test.hs / *.test.ts must not be flagged.
        var reasons = ExerciseIntegrityGate.harnessTamperingReasons(map("test/Test.hs", "old", "src/stack.test.ts", "old"),
                map("test/Test.hs", "completely rewritten", "src/stack.test.ts", "completely rewritten"), false);
        assertThat(reasons).isEmpty();
    }

    @Test
    void harness_rejectsAddedManifestWithoutSeedSnapshot() {
        assertThat(ExerciseIntegrityGate.harnessTamperingReasons(Map.of(), map("test.cabal", "anything"), false)).hasSize(1);
    }

    @Test
    void harness_requireNonEmptySnapshot_failsClosedOnAnEmptySeedButStaysSilentOnACapturedOne() {
        // A language that always ships a harness passes requireNonEmptySnapshot=true: an empty seed is then a FAILED CAPTURE, not a harness-free exercise, and accepting on that
        // doubt would silently disable the whole immutability gate. It must not fire once the snapshot actually arrived.
        assertThat(ExerciseIntegrityGate.harnessTamperingReasons(Map.of(), map("test.cabal", "anything"), true))
                .anyMatch(reason -> reason.contains("seeded test-harness snapshot is empty"));
        assertThat(ExerciseIntegrityGate.harnessTamperingReasons(map("test.cabal", "seeded"), map("test.cabal", "seeded"), true)).isEmpty();
    }

    @Test
    void isHarnessFile_recognizesBuildAndManifestFilesAcrossLanguages() {
        // One case per branch: the suffix list (.cabal, .csproj) and the name set, which is matched case-insensitively (Cargo.toml, DESCRIPTION).
        assertThat(ExerciseIntegrityGate.isHarnessFile("test.cabal")).isTrue();
        assertThat(ExerciseIntegrityGate.isHarnessFile("Test.csproj")).isTrue();
        assertThat(ExerciseIntegrityGate.isHarnessFile("Cargo.toml")).isTrue();
        assertThat(ExerciseIntegrityGate.isHarnessFile("DESCRIPTION")).isTrue();
        // NOT harness: the test sources the agent edits.
        assertThat(ExerciseIntegrityGate.isHarnessFile("test/Test.hs")).isFalse();
        assertThat(ExerciseIntegrityGate.isHarnessFile("src/stack.test.ts")).isFalse();
        assertThat(ExerciseIntegrityGate.isHarnessFile("test/test_stack.rb")).isFalse();
        // A nested *.yml data fixture is not a root build descriptor.
        assertThat(ExerciseIntegrityGate.isHarnessFile("test/fixtures/data.yml")).isFalse();
    }

    // --- Java/Ares convention gate ---

    @Test
    void javaAresConvention_acceptsArtemisStyleJavaTests() {
        String pom = aresPom();
        String test = """
                package de.test;

                import org.junit.jupiter.api.Test;
                import de.tum.in.test.api.BlacklistPath;
                import de.tum.in.test.api.StrictTimeout;
                import de.tum.in.test.api.WhitelistPath;
                import de.tum.in.test.api.jupiter.Public;

                @Public
                @WhitelistPath("target")
                @BlacklistPath("target/test-classes")
                class StackTest {

                    @Test
                    @StrictTimeout(1)
                    void pushesAndPops() {
                    }
                }
                """;

        assertThat(ExerciseIntegrityGate.javaAresConventionReasons(map("pom.xml", pom, "test/de/test/StackTest.java", test))).isEmpty();
    }

    @Test
    void javaAresConvention_adaptationChecksOnlyNewOrModifiedTestSources() {
        String legacyTest = """
                package de.test;
                import org.junit.jupiter.api.Test;
                class LegacyTest {
                    @Test
                    void existingBehaviour() {}
                }
                """;
        String generatedTest = """
                package de.test;
                import org.junit.jupiter.api.Test;
                import de.tum.in.test.api.BlacklistPath;
                import de.tum.in.test.api.StrictTimeout;
                import de.tum.in.test.api.WhitelistPath;
                import de.tum.in.test.api.jupiter.Public;
                @Public @WhitelistPath("target") @BlacklistPath("target/test-classes")
                class GeneratedTest {
                    @Test
                    @StrictTimeout(1)
                    void adaptedBehaviour() {}
                }
                """;
        Map<String, String> seed = map("pom.xml", aresPom(), "test/de/test/LegacyTest.java", legacyTest);
        Map<String, String> adapted = map("pom.xml", aresPom(), "test/de/test/LegacyTest.java", legacyTest, "test/de/test/GeneratedTest.java", generatedTest);

        assertThat(ExerciseIntegrityGate.javaAresConventionReasons(seed, adapted, true)).isEmpty();

        adapted = map("pom.xml", aresPom(), "test/de/test/LegacyTest.java", legacyTest, "test/de/test/GeneratedTest.java", generatedTest.replace("@StrictTimeout(1)\n", ""));
        assertThat(ExerciseIntegrityGate.javaAresConventionReasons(seed, adapted, true)).anyMatch(reason -> reason.contains("GeneratedTest.java"))
                .noneMatch(reason -> reason.contains("LegacyTest.java"));
    }

    @Test
    void javaAresConvention_rejectsPlainJunitTestsThatBypassArtemisSandbox() {
        String pom = """
                <project>
                    <dependencies>
                        <dependency><artifactId>junit-jupiter</artifactId></dependency>
                    </dependencies>
                </project>
                """;
        String test = """
                package de.test;

                import org.junit.jupiter.api.Test;

                class CalculatorTest {

                    @Test
                    void addsNumbers() {
                    }
                }
                """;

        var reasons = ExerciseIntegrityGate.javaAresConventionReasons(map("pom.xml", pom, "test/de/test/CalculatorTest.java", test));

        assertThat(reasons).hasSize(4);
        assertThat(reasons).anyMatch(reason -> reason.contains("artemis-java-test-sandbox"));
        assertThat(reasons).anyMatch(reason -> reason.contains("enforcer plugin"));
        assertThat(reasons).anyMatch(reason -> reason.contains("@Public"));
        assertThat(reasons).anyMatch(reason -> reason.contains("@StrictTimeout"));
    }

    @Test
    void javaAresConvention_rejectsPlainJunitTestsInGradleBehaviorSourceSet() {
        String test = """
                package de.test;

                import org.junit.jupiter.api.Test;

                class CalculatorTest {

                    @Test
                    void addsNumbers() {
                    }
                }
                """;

        var reasons = ExerciseIntegrityGate.javaAresConventionReasons(map("build.gradle", aresBuildGradle(), "behavior/test/de/test/CalculatorTest.java", test));

        assertThat(reasons).hasSize(2);
        assertThat(reasons).anyMatch(reason -> reason.contains("@Public"));
        assertThat(reasons).anyMatch(reason -> reason.contains("@StrictTimeout"));
    }

    @Test
    void javaAresConvention_acceptsGradleAresHarnessAndClassLevelTimeout() {
        String test = """
                package de.test;

                import org.junit.jupiter.params.ParameterizedTest;
                import de.tum.in.test.api.BlacklistPath;
                import de.tum.in.test.api.StrictTimeout;
                import de.tum.in.test.api.WhitelistPath;
                import de.tum.in.test.api.jupiter.Public;

                @Public
                @WhitelistPath("target")
                @BlacklistPath("target/test-classes")
                @StrictTimeout(1)
                class StackTest {

                    @ParameterizedTest
                    void pushesAndPops(int value) {
                    }
                }
                """;

        assertThat(ExerciseIntegrityGate.javaAresConventionReasons(map("build.gradle", aresBuildGradle(), "test/de/test/StackTest.java", test))).isEmpty();
    }

    @Test
    void javaAresConvention_acceptsTheBoundedStrictTimeoutArtemisItselfSeedsForStructuralTests() {
        // Regression: Artemis's own seeded structural test classes (ClassTest/MethodTest/AttributeTest/ConstructorTest, seeded by StructuralOracleSeedingService for a missing
        // public class) carry @StrictTimeout(10), not the exact @StrictTimeout(1) the gate used to demand. The gate's purpose is bounding an unbounded test, not pinning one
        // constant, so any value within the trusted bounded range must be accepted.
        String test = """
                package de.test;

                import org.junit.jupiter.api.TestFactory;
                import de.tum.in.test.api.BlacklistPath;
                import de.tum.in.test.api.StrictTimeout;
                import de.tum.in.test.api.WhitelistPath;
                import de.tum.in.test.api.jupiter.Public;

                @Public
                @WhitelistPath("target")
                @BlacklistPath("target/test-classes")
                class ClassTest {

                    @TestFactory
                    @StrictTimeout(10)
                    Object generateTestsForAllClasses() {
                        return null;
                    }
                }
                """;

        assertThat(ExerciseIntegrityGate.javaAresConventionReasons(map("pom.xml", aresPom(), "test/de/test/ClassTest.java", test))).isEmpty();
    }

    @Test
    void javaAresConvention_rejectsARealStrictTimeoutValueAboveTheTrustedBound() {
        // A genuinely-imported (not shadowed) @StrictTimeout whose value is absurdly large defeats the timeout's purpose just as a missing one would.
        String test = """
                package de.test;

                import org.junit.jupiter.api.Test;
                import de.tum.in.test.api.BlacklistPath;
                import de.tum.in.test.api.StrictTimeout;
                import de.tum.in.test.api.WhitelistPath;
                import de.tum.in.test.api.jupiter.Public;

                @Public
                @WhitelistPath("target")
                @BlacklistPath("target/test-classes")
                class CalculatorTest {

                    @Test
                    @StrictTimeout(9999)
                    void addsNumbers() {
                    }
                }
                """;

        assertThat(ExerciseIntegrityGate.javaAresConventionReasons(map("pom.xml", aresPom(), "test/de/test/CalculatorTest.java", test)))
                .anyMatch(reason -> reason.contains("@StrictTimeout") && reason.contains("bounded"));
    }

    @Test
    void javaAresConvention_rejectsCommentSpoofedAnnotationsAndGeneratedBuildOutput() {
        String pom = aresPom();
        String test = """
                package de.test;

                import org.junit.jupiter.api.Test;

                // @Public @WhitelistPath("target") @BlacklistPath("target/test-classes")
                class CalculatorTest {

                    @Test
                    // @StrictTimeout(1)
                    void addsNumbers() {
                    }
                }
                """;

        var reasons = ExerciseIntegrityGate
                .javaAresConventionReasons(map("pom.xml", pom, "test/de/test/CalculatorTest.java", test, "target/test-classes/CalculatorTest.java", test));

        assertThat(reasons).anyMatch(reason -> reason.contains("target/"));
        assertThat(reasons).anyMatch(reason -> reason.contains("@Public"));
        assertThat(reasons).anyMatch(reason -> reason.contains("@StrictTimeout"));
    }

    @Test
    void javaAresConvention_rejectsParameterizedAndFactoryTestsWithoutTimeout() {
        String pom = aresPom();
        String test = """
                package de.test;

                import org.junit.jupiter.api.TestFactory;
                import org.junit.jupiter.params.ParameterizedTest;
                import de.tum.in.test.api.BlacklistPath;
                import de.tum.in.test.api.WhitelistPath;
                import de.tum.in.test.api.jupiter.Public;

                @Public
                @WhitelistPath("target")
                @BlacklistPath("target/test-classes")
                class CalculatorTest {

                    @ParameterizedTest
                    void parameterized(int value) {
                    }

                    @TestFactory
                    java.util.stream.Stream<org.junit.jupiter.api.DynamicTest> dynamicTests() {
                        return java.util.stream.Stream.empty();
                    }
                }
                """;

        assertThat(ExerciseIntegrityGate.javaAresConventionReasons(map("pom.xml", pom, "test/de/test/CalculatorTest.java", test)))
                .anyMatch(reason -> reason.contains("@StrictTimeout"));
    }

    @Test
    void javaAresConvention_rejectsAresAnnotationsThatAreNeverImported() {
        // Every annotation carries a value the gate would accept if it resolved (@StrictTimeout(1) is squarely inside the trusted range), so ONLY the missing import can reject
        // this file: a package-local look-alike must not pass for the trusted annotation. The oversize-value bound is covered separately, on a genuinely imported annotation.
        String test = """
                package de.test;

                import org.junit.jupiter.api.Test;

                @interface Public {}
                @interface WhitelistPath { String value(); }
                @interface BlacklistPath { String value(); }
                @interface StrictTimeout { int value(); }

                @Public
                @WhitelistPath("target")
                @BlacklistPath("target/test-classes")
                @StrictTimeout(1)
                class CalculatorTest {

                    @Test
                    void addsNumbers() {
                    }
                }
                """;

        var reasons = ExerciseIntegrityGate.javaAresConventionReasons(map("pom.xml", aresPom(), "test/de/test/CalculatorTest.java", test));

        assertThat(reasons).anyMatch(reason -> reason.contains("trusted Ares annotations"));
        assertThat(reasons).anyMatch(reason -> reason.contains("@StrictTimeout(1)"));
    }

    @Test
    void javaAresConvention_rejectsImportedAnnotationsShadowedByLocalTypes() {
        String test = """
                package de.test;

                import de.tum.in.test.api.BlacklistPath;
                import de.tum.in.test.api.StrictTimeout;
                import de.tum.in.test.api.WhitelistPath;
                import de.tum.in.test.api.jupiter.Public;
                import org.junit.jupiter.api.Test;

                @Public
                @WhitelistPath("target")
                @BlacklistPath("target/test-classes")
                class CalculatorTest {

                    @interface StrictTimeout { int value(); }

                    @Test
                    @StrictTimeout(1)
                    void addsNumbers() {
                    }
                }
                """;

        assertThat(ExerciseIntegrityGate.javaAresConventionReasons(map("pom.xml", aresPom(), "test/de/test/CalculatorTest.java", test)))
                .anyMatch(reason -> reason.contains("@StrictTimeout(1)"));
    }

    @Test
    void javaAresConvention_rejectsCommentSpoofedMavenHarnessAndTrustedPackageSources() {
        String pom = """
                <project>
                    <!--
                    <dependency>
                        <groupId>de.tum.in.ase</groupId>
                        <artifactId>artemis-java-test-sandbox</artifactId>
                    </dependency>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-enforcer-plugin</artifactId>
                        <file>de/tum/in/test/api/</file>
                        <file>org/junit/</file>
                    </plugin>
                    -->
                </project>
                """;
        String test = """
                package de.test;

                import org.junit.jupiter.api.Test;
                import de.tum.in.test.api.BlacklistPath;
                import de.tum.in.test.api.StrictTimeout;
                import de.tum.in.test.api.WhitelistPath;
                import de.tum.in.test.api.jupiter.Public;

                @Public
                @WhitelistPath("target")
                @BlacklistPath("target/test-classes")
                class StackTest {

                    @Test
                    @StrictTimeout(1)
                    void pushesAndPops() {
                    }
                }
                """;

        var reasons = ExerciseIntegrityGate.javaAresConventionReasons(map("pom.xml", pom, "test/de/test/StackTest.java", test, "behavior/test/org/junit/FakeTest.java", "fake"));

        assertThat(reasons).anyMatch(reason -> reason.contains("artemis-java-test-sandbox"));
        assertThat(reasons).anyMatch(reason -> reason.contains("enforcer plugin"));
    }

    @Test
    void javaGeneratedSourceLayout_rejectsChangedFilesOutsideTheExercisePackage() {
        String packageName = "de.tum.cit.aet.exercise";
        Map<String, String> producedTests = map("build.gradle", "seed", "test/de/tum/cit/aet/exercise/ExerciseTest.java", "package de.tum.cit.aet.exercise;",
                "test/net/bytebuddy/ByteBuddy.java", "package net.bytebuddy;", "buildSrc/src/main/java/FakePlugin.java", "class FakePlugin {}");
        Map<String, String> producedTemplate = map("src/de/tum/cit/aet/exercise/Exercise.java", "package de.tum.cit.aet.exercise;", "src/de/tum/in/ase/test/Public.java",
                "package de.tum.in.ase.test;");

        var reasons = ExerciseIntegrityGate.javaGeneratedSourceLayoutReasons(packageName, Map.of("build.gradle", "seed"), Map.of(), Map.of(), producedTests, producedTemplate,
                Map.of("src/de/tum/cit/aet/exercise/Exercise.java", "package de.tum.cit.aet.exercise;"));

        assertThat(reasons).singleElement()
                .satisfies(reason -> assertThat(reason).contains("canonical source roots").contains("ByteBuddy.java").contains("FakePlugin.java").contains("Public.java"));
    }

    @Test
    void javaGeneratedSourceLayout_explainsTheExactPackageAlignedTestPath() {
        String packageName = "de.tum.cit.aet.hyperion";
        Map<String, String> producedTests = Map.of("test/LibrarySummaryTest.java", "package de.tum.cit.aet.hyperion;");

        var reasons = ExerciseIntegrityGate.javaGeneratedSourceLayoutReasons(packageName, Map.of(), Map.of(), Map.of(), producedTests, Map.of(), Map.of());

        assertThat(reasons).singleElement().satisfies(reason -> assertThat(reason).contains("tests/test/de/tum/cit/aet/hyperion/")
                .contains("tests/behavior/test/de/tum/cit/aet/hyperion/").contains("tests/structural/test/de/tum/cit/aet/hyperion/").contains("not tests/src/test/java/"));
    }

    @Test
    void javaGeneratedSourceLayout_acceptsPackageScopedChangesAndUnchangedLegacyFiles() {
        String packageName = "de.tum.cit.aet.exercise";
        Map<String, String> seedTests = map("build.gradle", "seed", "test/legacy/LegacyTest.java", "package legacy;");
        Map<String, String> producedTests = map("build.gradle", "seed", "test/legacy/LegacyTest.java", "package legacy;", "test/de/tum/cit/aet/exercise/ExerciseTest.java",
                "package de.tum.cit.aet.exercise;", "test/de/tum/cit/aet/exercise/test.json", "[]");
        Map<String, String> seedTemplate = Map.of("src/legacy/Legacy.java", "package legacy;");
        Map<String, String> producedTemplate = map("src/legacy/Legacy.java", "package legacy;", "src/de/tum/cit/aet/exercise/Exercise.java", "package de.tum.cit.aet.exercise;");

        assertThat(ExerciseIntegrityGate.javaGeneratedSourceLayoutReasons(packageName, seedTests, seedTemplate, Map.of(), producedTests, producedTemplate,
                Map.of("src/de/tum/cit/aet/exercise/Exercise.java", "package de.tum.cit.aet.exercise;"))).isEmpty();
    }

    @Test
    void javaGeneratedSourceLayout_rejectsChangedSourceWhoseDeclaredPackageDoesNotMatchItsPath() {
        String packageName = "de.tum.cit.aet.exercise";
        Map<String, String> producedTests = Map.of("test/de/tum/cit/aet/exercise/FakeAres.java", "package de.tum.in.test.api; public @interface Public {}");

        var reasons = ExerciseIntegrityGate.javaGeneratedSourceLayoutReasons(packageName, Map.of(), Map.of(), Map.of(), producedTests, Map.of(), Map.of());

        assertThat(reasons).singleElement().satisfies(reason -> assertThat(reason).contains("canonical source roots").contains("FakeAres.java"));
    }

    @Test
    void javaGeneratedSourceLayout_rejectsObfuscatedPackageDeclarations() {
        String packageName = "de.tum.cit.aet.exercise";
        Map<String, String> producedTemplate = map("src/de/tum/cit/aet/exercise/Commented.java", "package/* hidden */net.bytebuddy; class Commented {}",
                "src/de/tum/cit/aet/exercise/Unicode.java", "package de.tum.cit.aet.exercis" + "\\u0065; class Unicode {}");

        var reasons = ExerciseIntegrityGate.javaGeneratedSourceLayoutReasons(packageName, Map.of(), Map.of(), Map.of(), Map.of(), producedTemplate, Map.of());

        assertThat(reasons).singleElement().satisfies(reason -> assertThat(reason).contains("Commented.java").contains("Unicode.java"));
    }

    @Test
    void javaGeneratedSourceLayout_rejectsAUnicodeEscapeSmuggledIntoTheCommentBeforeThePackageDeclaration() {
        // javac translates \\u000a into a newline BEFORE lexing, which terminates the line comment: the real declaration is `package net.bytebuddy;` while the path-matching one a
        // reader (and the comment-stripping matcher) sees is inert. Only the escape guard catches this — the visible declaration matches its path perfectly.
        String source = "//\\u000a package net.bytebuddy;\npackage de.tum.cit.aet.exercise; class Smuggled {}";

        var reasons = ExerciseIntegrityGate.javaGeneratedSourceLayoutReasons("de.tum.cit.aet.exercise", Map.of(), Map.of(), Map.of(),
                Map.of("test/de/tum/cit/aet/exercise/Smuggled.java", source), Map.of(), Map.of());

        assertThat(reasons).singleElement().satisfies(reason -> assertThat(reason).contains("Smuggled.java"));
    }

    @Test
    void javaGeneratedSourceLayout_acceptsUnicodeEscapesAfterThePackageDeclaration() {
        Map<String, String> producedSolution = Map.of("src/de/tum/cit/aet/exercise/UnicodeExercise.java",
                "package de.tum.cit.aet.exercise; class UnicodeExercise { String check = \"" + "\\u2713" + "\"; }");

        assertThat(ExerciseIntegrityGate.javaGeneratedSourceLayoutReasons("de.tum.cit.aet.exercise", Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), producedSolution)).isEmpty();
    }

    @Test
    void javaGeneratedSourceLayout_acceptsConventionalPackageDeclarationWithComments() {
        String packageName = "de.tum.cit.aet.exercise";
        Map<String, String> producedSolution = Map.of("src/de/tum/cit/aet/exercise/internal/Helper.java",
                "/* copyright */ package/* separator */ de.tum.cit.aet.exercise.internal; class Helper {}");

        assertThat(ExerciseIntegrityGate.javaGeneratedSourceLayoutReasons(packageName, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), producedSolution)).isEmpty();
    }

    // --- Adapt total-wipe (zero-retention) gate ---

    @Test
    void adaptWipe_rejectsWhenNoBaselineGradedTestIsRetained() {
        // The exercise had two graded tests; the adapt produced an entirely new suite that keeps none of them — a from-scratch regeneration mislabeled as an adapt.
        var reasons = ExerciseIntegrityGate.adaptWipedGradedTestsReasons(Set.of("testEvictsLru", "testCapacity"), List.of("testFooBar"));
        assertThat(reasons).hasSize(1);
        assertThat(reasons.getFirst()).contains("retained NONE").contains("previously-graded").contains("testCapacity");
    }

    @Test
    void adaptWipe_acceptsWhenAtLeastOneBaselineGradedTestSurvives() {
        // A legitimate adapt: one graded test kept, one added/renamed — coverage is refined, not wiped.
        var reasons = ExerciseIntegrityGate.adaptWipedGradedTestsReasons(Set.of("testEvictsLru", "testCapacity"), List.of("testEvictsLru", "testCapacityAndResize"));
        assertThat(reasons).isEmpty();
    }

    @Test
    void adaptWipe_isInertOnAnEmptyBaseline() {
        // GENERATE, or a never-graded exercise: an empty baseline must never fabricate a rejection.
        assertThat(ExerciseIntegrityGate.adaptWipedGradedTestsReasons(Set.of(), List.of("testAnything"))).isEmpty();
    }

    @Test
    void adaptWipe_isInertWhenBaselineNormalizesToBlank() {
        // A non-empty baseline whose entries all normalize to blank must stay inert, not fabricate a "retained NONE of 0" rejection.
        assertThat(ExerciseIntegrityGate.adaptWipedGradedTestsReasons(Set.of("  "), List.of("testAnything"))).isEmpty();
    }

    @Test
    void adaptWipe_usesProductionExactTestNames() {
        // Production task binding is exact: "testEvictsLru" and "testEvictsLru()" are different names, so an adapt that only keeps the parenthesized variant wiped the old test.
        assertThat(ExerciseIntegrityGate.adaptWipedGradedTestsReasons(Set.of("testEvictsLru"), List.of("testEvictsLru()"))).hasSize(1);
    }

    // --- Approved specification contract ---

    @Test
    void approvedSpecification_rejectsAContractThatSuppliesNoScaffoldAtAll() {
        // Observed live: every type was marked student-creates, the template shipped EMPTY, and the run was accepted as SUCCESS. An empty template compiles (no sources) and
        // fails every test (none run), so the differential cannot discriminate it — acceptance is decided here, and repair attempts never re-run the staged gates.
        String spec = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | `BaseShape` | abstract base | student-creates |
                | `Rectangle` | concrete shape | student-creates |
                """;
        Map<String, String> solution = map("src/BaseShape.java", "public abstract class BaseShape {}", "src/Rectangle.java", "public class Rectangle {}");

        assertThat(ExerciseIntegrityGate.approvedSpecificationReasons(spec, Map.of(), solution)).singleElement()
                .satisfies(reason -> assertThat(reason).contains("every type 'student-creates'", "clone an empty project", "'given' or 'stubbed'"));
    }

    /**
     * The template scaffold-presence matrix. A template legitimately ships stubs rather than implementations, so presence cannot be inferred from file counts or from the
     * template merely differing from the solution: the frozen '## Design' table is the authority on which types must be there ({@code given}, {@code stubbed}) and which must not
     * ({@code student-creates}). This gate rejects, and a rejected candidate is discarded without an instructor ever seeing it, so every doubt-on-read-back row must stay inert.
     */
    private static Stream<Arguments> scaffoldPresenceCases() {
        String stubbedPlayer = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | `Player` | context students complete | stubbed |
                """;
        String genericStack = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | `Stack<T>` | generic container students complete | stubbed |
                """;
        String givenAndCreated = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | `Track` | supplied data type | given |
                | `PlaybackStrategy` | abstraction students design | student-creates |
                """;
        String track = "public record Track(String title) {}";
        Map<String, String> playerSolution = map("src/Player.java", "public class Player { int score() { return 1; } }");
        return Stream.of(
                // A stub is the correct shape for a template, so a declared-but-unimplemented type must pass.
                Arguments.of("stubbed type present as an unimplemented stub", stubbedPlayer,
                        map("src/Player.java", "public class Player { int score() { throw new UnsupportedOperationException(); } }"), playerSolution, null),
                Arguments.of("stubbed type absent from the template", stubbedPlayer, map("src/Other.java", "class Other {}"), playerSolution, "Player"),
                // The specification gate demands a bare type name of student-creates rows only, so a generic cell reaches this gate. Searching for "Stack<T>" verbatim finds
                // nothing in a source that declares "class Stack<T>", which rejected a sound exercise; the given arm already fails open on the same input.
                Arguments.of("generic stubbed cell whose template declaration is sound", genericStack, map("src/Stack.java", "public class Stack<T> { void push(T item) {} }"),
                        map("src/Stack.java", "public class Stack<T> { void push(T item) { store.add(item); } }"), null),
                // The trap: a student-created type is REQUIRED to be absent from the template, so its absence must never be read as a missing scaffold.
                Arguments.of("student-creates type absent from the template", givenAndCreated, map("src/Track.java", track),
                        map("src/Track.java", track, "src/PlaybackStrategy.java", "public interface PlaybackStrategy {}"), null),
                // A given type is supplied complete; absent from the template it is not a starting point at all.
                Arguments.of("given type absent from the template", givenAndCreated, map("src/Unrelated.java", "class Unrelated {}"),
                        map("src/Track.java", track, "src/PlaybackStrategy.java", "public interface PlaybackStrategy {}"), "Track"),
                // Fail-open rows: no readable contract means no evidence, and this gate must not manufacture a rejection from doubt.
                Arguments.of("no approved specification", "", map("src/Anything.java", "class Anything {}"), map("src/Anything.java", "class Anything {}"), null),
                Arguments.of("specification without a Design section", "## Rules\n1. Sort ascending.\n", Map.of(), Map.of(), null),
                Arguments.of("Design section whose table has no parseable data rows", "## Design\n\nSee the table above.\n", Map.of(), Map.of(), null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scaffoldPresenceCases")
    void approvedSpecification_enforcesTheTemplateScaffoldOnlyWhereTheContractSaysSo(String description, String spec, Map<String, String> template, Map<String, String> solution,
            String expectedRejectedType) {
        List<String> reasons = ExerciseIntegrityGate.approvedSpecificationReasons(spec, template, solution);

        if (expectedRejectedType == null) {
            assertThat(reasons).isEmpty();
        }
        else {
            assertThat(reasons).singleElement().satisfies(reason -> assertThat(reason).contains(expectedRejectedType).contains("template"));
        }
    }

    @Test
    void approvedSpecification_rejectsAStubbedTypeMissingFromTheTemplate() {
        // A stubbed type is the student's starting point; without it the graded tests cannot name it and the starter teaches nothing.
        String spec = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | `Player` | context students complete | stubbed |
                """;
        Map<String, String> solution = map("src/Player.java", "public class Player { int score() { return 1; } }");

        assertThat(ExerciseIntegrityGate.approvedSpecificationReasons(spec, Map.of("src/Other.java", "class Other {}"), solution)).singleElement()
                .satisfies(reason -> assertThat(reason).contains("'stubbed'", "does not declare them", "Player"));
    }

    @Test
    void approvedSpecification_acceptsAStubbedTypePresentInTheTemplate() {
        String spec = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | `Player` | context students complete | stubbed |
                """;
        Map<String, String> solution = map("src/Player.java", "public class Player { int score() { return 1; } }");
        Map<String, String> template = map("src/Player.java", "public class Player { int score() { throw new UnsupportedOperationException(); } }");

        assertThat(ExerciseIntegrityGate.approvedSpecificationReasons(spec, template, solution)).isEmpty();
    }

    @Test
    void approvedSpecification_rejectsStudentCreatedTypesThatLeakIntoTheTemplate() {
        String spec = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | `Track` | data | given |
                | `PlaybackStrategy` | abstraction students design | student-creates |
                | `Player` | context students wire | student-creates |
                """;
        Map<String, String> solution = map("src/Track.java", "public record Track(String title) {}", "src/PlaybackStrategy.java", "public interface PlaybackStrategy {}",
                "src/Player.java", "public class Player {}");
        Map<String, String> template = map("src/Track.java", "public record Track(String title) {}", "src/PlaybackStrategy.java", "public interface PlaybackStrategy {}",
                "src/Player.java", "public class Player { /* TODO */ }");

        assertThat(ExerciseIntegrityGate.approvedSpecificationReasons(spec, template, solution)).singleElement()
                .satisfies(reason -> assertThat(reason).contains("template already declares them", "PlaybackStrategy", "Player", "changing SPEC.md after approval cannot"));
    }

    @Test
    void approvedSpecification_acceptsStudentCreatedTypesOnlyInTheSolutionIncludingSecondaryDeclarations() {
        String spec = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | `Track` | supplied data type | given |
                | `PlaybackStrategy` | abstraction students design | student-creates |
                | `Player` | context students wire | student-creates |
                """;
        String track = "public record Track(String title) {}";
        Map<String, String> solution = map("src/ExerciseTypes.java", "interface PlaybackStrategy {}\nclass Player {}", "src/Track.java", track);

        assertThat(ExerciseIntegrityGate.approvedSpecificationReasons(spec, map("src/Track.java", track), solution)).isEmpty();
    }

    @Test
    void approvedSpecification_doesNotMistakeATodoBreadcrumbForATypeDeclaration() {
        String spec = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | `Track` | supplied data type | given |
                | `PlaybackStrategy` | abstraction students design | student-creates |
                """;
        String track = "public record Track(String title) {}";
        Map<String, String> solution = map("src/PlaybackStrategy.java", "public interface PlaybackStrategy {}", "src/Track.java", track);
        Map<String, String> template = map("src/Player.java", "public class Player { // TODO: create interface PlaybackStrategy\n}", "src/Track.java", track);

        assertThat(ExerciseIntegrityGate.approvedSpecificationReasons(spec, template, solution)).isEmpty();
    }

    @Test
    void approvedSpecification_rejectsANamedTypeArtifactAndASameLineNestedDeclaration() {
        String spec = "## Design\n| Type | Role | Template status |\n|---|---|---|\n| `Track` | supplied data type | given |\n"
                + "| `PlaybackStrategy` | students create it | student-creates |\n";
        String track = "public record Track(String title) {}";
        Map<String, String> solution = map("src/PlaybackStrategy.java", "public interface PlaybackStrategy {}", "src/Track.java", track);

        assertThat(ExerciseIntegrityGate.approvedSpecificationReasons(spec, map("src/PlaybackStrategy.java", "", "src/Track.java", track), solution)).singleElement()
                .satisfies(reason -> assertThat(reason).contains("template already declares", "PlaybackStrategy"));
        assertThat(ExerciseIntegrityGate.approvedSpecificationReasons(spec, map("src/Player.java", "class Player { interface PlaybackStrategy {} }", "src/Track.java", track),
                solution)).singleElement().satisfies(reason -> assertThat(reason).contains("template already declares", "PlaybackStrategy"));
    }

    @Test
    void approvedSpecification_rejectsAnIncompleteReferenceSolution() {
        String spec = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | `Track` | supplied data type | given |
                | `PlaybackStrategy` | abstraction students design | student-creates |
                """;
        String track = "public record Track(String title) {}";
        Map<String, String> supplied = map("src/Track.java", track);

        assertThat(ExerciseIntegrityGate.approvedSpecificationReasons(spec, supplied, supplied)).singleElement()
                .satisfies(reason -> assertThat(reason).contains("reference solution does not declare", "PlaybackStrategy"));
    }

    @Test
    void approvedSpecification_acceptsIdenticalGivenTypesInSolutionAndTemplate() {
        String spec = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | `Ingredient` | supplied immutable value | given |
                """;
        String ingredient = "public record Ingredient(String name, int potency) {}\n";

        assertThat(ExerciseIntegrityGate.approvedSpecificationReasons(spec, map("src/Ingredient.java", ingredient), map("src/Ingredient.java", ingredient))).isEmpty();
    }

    @Test
    void approvedSpecification_rejectsDivergentGivenTypes() {
        String spec = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | `Ingredient` | supplied immutable value | given |
                """;
        Map<String, String> solution = map("src/Ingredient.java", "public record Ingredient(String name, int potency) {}\n");
        Map<String, String> template = map("src/Ingredient.java", "public class Ingredient { public void setPotency(int potency) {} }\n");

        assertThat(ExerciseIntegrityGate.approvedSpecificationReasons(spec, template, solution)).singleElement()
                .satisfies(reason -> assertThat(reason).contains("given type", "Ingredient", "byte-for-byte identical", "solution and template"));
    }

    @Test
    void templateTodoSeams_rejectMissingAndUnknownWorkMarkers() {
        String spec = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | Player | player | stubbed |
                | Context | context | stubbed |
                ## Testing Strategy
                | Seam | Owner type | Observable responsibility | Weight | Hidden variant |
                |---|---|---|---|---|
                | S1 | Player | create strategy | 3 | no |
                | S2 | Context | wire strategy | 2 | no |
                """;
        Map<String, String> template = Map.of("src/Player.java", "class Player { // TODO S1: create strategy\n// TODO S9: stale work\n}");

        assertThat(ExerciseIntegrityGate.templateTodoSeamReasons(spec, template)).hasSize(2).anySatisfy(reason -> assertThat(reason).contains("declaring source", "S2"))
                .anySatisfy(reason -> assertThat(reason).contains("does not declare", "S9"));
    }

    @Test
    void templateTodoSeams_acceptExactCoverageAcrossFiles() {
        String spec = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | Player | player | stubbed |
                | Context | context | stubbed |
                ## Testing Strategy
                | Seam | Owner type | Observable responsibility | Weight | Hidden variant |
                |---|---|---|---|---|
                | S1 | Player | create strategy | 3 | no |
                | S2 | Context | wire strategy | 2 | no |
                """;
        Map<String, String> template = Map.of("src/Player.java", "class Player { // TODO S1: create strategy\n}", "src/Context.java",
                "class Context { // TODO S2: wire strategy\n}");

        assertThat(ExerciseIntegrityGate.templateTodoSeamReasons(spec, template)).isEmpty();
    }

    @Test
    void approvedTestPlan_rejectsMissingHiddenVariantsInsteadOfSilentlyPublishingEverything() {
        String spec = """
                ## Testing Strategy
                | Seam | Partitions | Weight | Hidden-variant (yes/no) |
                |---|---|---|---|
                | S1 | swap collaborator | 3 | yes |
                """;
        String visibleOnly = "{\"tests\":[{\"name\":\"delegates\",\"seam\":\"S1\",\"seamWeightTier\":3,\"visibility\":\"ALWAYS\"}]}";

        assertThat(ExerciseIntegrityGate.approvedTestPlanReasons(spec, visibleOnly, List.of("delegates"))).singleElement()
                .satisfies(reason -> assertThat(reason).contains("requires AFTER_DUE_DATE", "S1"));
    }

    @Test
    void approvedTestPlan_acceptsAValidPlanWithFreshHiddenCoverage() {
        String spec = """
                ## Testing Strategy
                | Seam | Partitions | Weight | Hidden-variant (yes/no) |
                |---|---|---|---|
                | S1 | swap collaborator | 3 | yes |
                """;
        String plan = """
                {"tests":[
                  {"name":"delegates","seam":"S1","seamWeightTier":3,"visibility":"ALWAYS"},
                  {"name":"delegatesWithFreshValues","seam":"S1","seamWeightTier":3,"visibility":"AFTER_DUE_DATE"}
                ]}
                """;

        assertThat(ExerciseIntegrityGate.approvedTestPlanReasons(spec, plan, List.of("delegates", "delegatesWithFreshValues"))).isEmpty();
    }

    @Test
    void approvedTestPlan_rejectsUnplannedTestsWithoutASpecification() {
        String plan = """
                {"tests":[{"name":"planned","seam":"S1","seamWeightTier":1,"visibility":"ALWAYS"}]}
                """;

        assertThat(ExerciseIntegrityGate.approvedTestPlanReasons("", plan, List.of("planned", "addedDuringRepair"))).singleElement().asString()
                .contains("omits verified gradable test(s)", "addedDuringRepair");
    }

    @Test
    void approvedTestPlan_requiresHiddenCoverageForEveryDeclaredSeam() {
        String spec = """
                ## Testing Strategy
                | Seam | Partitions | Weight | Hidden-variant (yes/no) |
                |---|---|---|---|
                | S1 | ordinary | 3 | yes |
                | S2 | boundary | 2 | yes |
                """;
        String plan = """
                {"tests":[
                  {"name":"ordinary","seam":"S1","seamWeightTier":3,"visibility":"AFTER_DUE_DATE"},
                  {"name":"boundary","seam":"S2","seamWeightTier":2,"visibility":"ALWAYS"}
                ]}
                """;

        assertThat(ExerciseIntegrityGate.approvedTestPlanReasons(spec, plan, List.of("ordinary", "boundary"))).singleElement().asString().contains("S2").doesNotContain("S1, S2");
    }

    @Test
    void templateTodoSeams_ignoreMarkersOutsideJavaSources() {
        String spec = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | Player | player | stubbed |
                ## Testing Strategy
                | Seam | Owner type | Observable responsibility | Weight | Hidden variant |
                |---|---|---|---|---|
                | S1 | Player | create strategy | 3 | no |
                """;
        Map<String, String> template = Map.of("src/Player.java", "class Player { // TODO S1: create strategy\n}", "README.md", "TODO S9: author note", "target/Stale.java",
                "// TODO S8: generated output");

        assertThat(ExerciseIntegrityGate.templateTodoSeamReasons(spec, template)).isEmpty();
    }

    @Test
    void templateTodoSeams_rejectBreadcrumbsForAbsentStudentCreatedOwnersAndWrongStubbedFiles() {
        String spec = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | FireSpell | strategy | student-creates |
                | IceSpell | strategy | student-creates |
                | Mage | context | stubbed |
                ## Testing Strategy
                | Seam | Owner type | Observable responsibility | Weight | Hidden variant |
                |---|---|---|---|---|
                | S1 | FireSpell | formula | 2 | yes |
                | S2 | IceSpell | formula | 2 | yes |
                | S3 | Mage | delegation and switching | 3 | yes |
                """;
        Map<String, String> misleading = Map.of("src/Mage.java", "class Mage { // TODO S1: store\n// TODO S2: switch\n// TODO S3: cast\n}");
        assertThat(ExerciseIntegrityGate.templateTodoSeamReasons(spec, misleading)).hasSize(2).allSatisfy(reason -> assertThat(reason).contains("student-created", "Mage.java"));

        Map<String, String> honest = Map.of("src/Mage.java", "class Mage { // TODO S3: delegate and switch\n}");
        assertThat(ExerciseIntegrityGate.templateTodoSeamReasons(spec, honest)).isEmpty();

        Map<String, String> wrongFile = Map.of("src/Mage.java", "class Mage {}", "src/Helper.java", "class Helper { // TODO S3: delegate\n}");
        assertThat(ExerciseIntegrityGate.templateTodoSeamReasons(spec, wrongFile)).anySatisfy(reason -> assertThat(reason).contains("outside its approved owner", "Helper.java"));

        Map<String, String> ownerMentionedOnlyInAComment = Map.of("src/Helper.java", "// class Mage would own this\nclass Helper { // TODO S3: delegate\n}");
        assertThat(ExerciseIntegrityGate.templateTodoSeamReasons(spec, ownerMentionedOnlyInAComment)).anySatisfy(reason -> assertThat(reason).contains("declaring source", "Mage"));
    }

    @Test
    void statementTasks_requireInstructionTextBeforeTheNextTask() {
        assertThat(ExerciseIntegrityGate.statementTaskInstructionReasons("[task][First](one)\n[task][Second](two)\n")).singleElement().asString().contains("First", "Second",
                "no student-facing instruction");
        assertThat(
                ExerciseIntegrityGate.statementTaskInstructionReasons("[task][First](one)\nImplement the first behavior.\n[task][Second](two)\nImplement the second behavior.\n"))
                .isEmpty();
    }

    @Test
    void approvedTraceability_rejectsMissingPlanSeamsAndStatementTaskSplitting() {
        String spec = """
                ## Testing Strategy
                | Seam | Partitions | Weight | Hidden-variant (yes/no) |
                |---|---|---|---|
                | S1 | ordinary and boundary values | 3 | no |
                """;
        String noSeam = "{\"tests\":[{\"name\":\"ordinary\",\"seamWeightTier\":3,\"visibility\":\"ALWAYS\"}]}";
        assertThat(ExerciseIntegrityGate.approvedTestPlanReasons(spec, noSeam, List.of("ordinary"))).singleElement().asString().contains("has no seam", "S1");

        String plan = "{\"tests\":[{\"name\":\"ordinary\",\"seam\":\"S1\",\"seamWeightTier\":3,\"visibility\":\"ALWAYS\"},"
                + "{\"name\":\"boundary\",\"seam\":\"S1\",\"seamWeightTier\":2,\"visibility\":\"ALWAYS\"}]}";
        String splitStatement = "[task][Ordinary](ordinary)\n[task][Boundary](boundary)";

        assertThat(ExerciseIntegrityGate.statementTraceabilityReasons(plan, splitStatement)).singleElement().asString().contains("S1", "split");
    }

    @Test
    void approvedTestPlan_rejectsWeightVisibilityCoverageAndDueDateDriftAtTheFinalGate() {
        String spec = """
                ## Testing Strategy
                | Seam | Owner type | Observable responsibility | Weight | Hidden variant |
                |---|---|---|---|---|
                | S1 | Strategy | transform the input | 3 | no |
                """;

        String wrongWeight = "{\"tests\":[{\"name\":\"transforms\",\"seam\":\"S1\",\"seamWeightTier\":2,\"visibility\":\"ALWAYS\"}]}";
        assertThat(ExerciseIntegrityGate.approvedTestPlanReasons(spec, wrongWeight, List.of("transforms"))).singleElement().asString().contains("weights do not match",
                "requires 3");

        String unexpectedHidden = "{\"tests\":[{\"name\":\"transforms\",\"seam\":\"S1\",\"seamWeightTier\":3,\"visibility\":\"AFTER_DUE_DATE\"}]}";
        assertThat(ExerciseIntegrityGate.approvedTestPlanReasons(spec, unexpectedHidden, List.of("transforms"))).singleElement().asString().contains("says no hidden variant");

        String completePlan = "{\"tests\":[{\"name\":\"transforms\",\"seam\":\"S1\",\"seamWeightTier\":3,\"visibility\":\"ALWAYS\"}]}";
        assertThat(ExerciseIntegrityGate.approvedTestPlanReasons(spec, completePlan, List.of("transforms", "edgeCase"))).singleElement().asString()
                .contains("omits verified gradable test", "edgeCase");
        assertThat(ExerciseIntegrityGate.approvedTestPlanReasons(spec, completePlan, List.of("transforms", "testClass[Strategy]"), true, Set.of("testClass[Strategy]")))
                .as("server-authored structural feedback is not part of the agent-authored grading plan").isEmpty();

        String buildGatePlan = "{\"tests\":[{\"name\":\"GBS-Tester-1.36.CompileSort\",\"seam\":\"S1\",\"seamWeightTier\":3,\"visibility\":\"ALWAYS\"}]}";
        assertThat(ExerciseIntegrityGate.approvedTestPlanReasons(spec, buildGatePlan, List.of("GBS-Tester-1.36.CompileSort"))).singleElement().asString().contains("build-gate",
                "zero-weight", "cannot satisfy");

        assertThat(ExerciseIntegrityGate.approvedTestPlanReasons(spec, unexpectedHidden, List.of("transforms"), false)).singleElement().asString().contains("has no due date",
                "hidden indefinitely");
    }

    @Test
    void approvedTestPlan_doesNotLetAHiddenStructuralCheckStandInForABehavioralWitness() {
        String spec = """
                ## Testing Strategy
                | Seam | Owner type | Observable responsibility | Weight | Hidden variant |
                |---|---|---|---|---|
                | S1 | Strategy | delegate through the selected strategy | 3 | yes |
                """;
        String plan = """
                {"tests":[
                  {"name":"delegates","seam":"S1","seamWeightTier":3,"visibility":"ALWAYS"},
                  {"name":"testClass[Strategy]","seam":"S1","seamWeightTier":3,"visibility":"AFTER_DUE_DATE"}
                ]}
                """;

        assertThat(ExerciseIntegrityGate.approvedTestPlanReasons(spec, plan, List.of("delegates", "testClass[Strategy]"), true, Set.of("testClass[Strategy]"))).singleElement()
                .asString().contains("server-seeded structural", "Remove them", "behavioral witness");
    }

    // --- Graded tests reading the source tree ---

    @Test
    void sourceTreeReading_rejectsATestThatGradesTheStudentsSourceTextInsteadOfBehaviour() {
        // Shape taken verbatim from a live run: a test named testNoLoopsInImplementation that never looks for a loop. It hunts for the implementation file across the
        // repository directories, reads it, and asserts the source carries no TODO. Production checks the student's repository out as 'assignment', so a correct solution that
        // still has a TODO comment fails a graded test.
        Map<String, String> tests = map("test/de/tum/cit/aet/nodraft/RecursionUtilsLoopTest.java", """
                class RecursionUtilsLoopTest {
                    @Test
                    void testNoLoopsInImplementation() throws Exception {
                        String[] candidates = { "solution/src/de/tum/cit/aet/nodraft/RecursionUtils.java", "assignment/src/de/tum/cit/aet/nodraft/RecursionUtils.java" };
                        Path source = null;
                        for (String candidate : candidates) {
                            if (Files.exists(Path.of(candidate))) { source = Path.of(candidate); break; }
                        }
                        String code = Files.readString(source, StandardCharsets.UTF_8);
                        assertFalse(code.contains("TODO"), "Implementation must not contain TODO markers");
                    }
                }
                """);

        assertThat(ExerciseIntegrityGate.gradedTestsReadingSourceTreeReasons(tests)).singleElement().asString().contains("RecursionUtilsLoopTest.java")
                .contains("reads the exercise's own source tree").contains("can fail correct work");
    }

    @Test
    void sourceTreeReading_acceptsATestThatOnlyExercisesThePublicApi() {
        Map<String, String> tests = map("test/de/tum/cit/aet/nodraft/RecursionUtilsTest.java", """
                class RecursionUtilsTest {
                    @Test
                    void testDigitalRoot() {
                        assertEquals(3, RecursionUtils.digitalRoot(9876), "digital root of 9876 is 3");
                    }
                }
                """);

        assertThat(ExerciseIntegrityGate.gradedTestsReadingSourceTreeReasons(tests)).isEmpty();
    }

    @Test
    void sourceTreeReading_acceptsATestThatReadsAFixtureWithoutNamingARepositoryDirectory() {
        // Reading a file is not itself the defect — knowing the repository layout is. A fixture under the test's own resources must not be flagged.
        Map<String, String> tests = map("test/de/tum/cit/aet/nodraft/ParserTest.java", """
                class ParserTest {
                    @Test
                    void testParsesFixture() throws Exception {
                        String csv = Files.readString(Path.of("fixtures/sample.csv"), StandardCharsets.UTF_8);
                        assertEquals(3, Parser.count(csv), "three records");
                    }
                }
                """);

        assertThat(ExerciseIntegrityGate.gradedTestsReadingSourceTreeReasons(tests)).isEmpty();
    }

    @Test
    void sourceTreeReading_acceptsATestThatReadsAFixtureUnderADirectoryNamedLikeARepository() {
        // A template-rendering exercise legitimately keeps fixtures under "template/". Naming the directory is not the defect; naming a source tree is. Rejecting this would
        // discard a sound exercise, and the instructor never sees what was discarded.
        Map<String, String> tests = map("test/de/tum/cit/aet/nodraft/RendererTest.java", """
                class RendererTest {
                    @Test
                    void testRendersTemplate() throws Exception {
                        String tpl = Files.readString(Path.of("src/test/resources/fixtures/template/simple.mustache"), StandardCharsets.UTF_8);
                        assertEquals("Hello Ada", Renderer.render(tpl, Map.of("name", "Ada")), "renders the name");
                    }
                }
                """);

        assertThat(ExerciseIntegrityGate.gradedTestsReadingSourceTreeReasons(tests)).isEmpty();
    }

    @Test
    void sourceTreeReading_rejectsATestThatHoistsTheSourceRootsAwayFromTheRead() {
        // The same defect written the way a competent author would write it: candidate roots as a constant at the top, the read far below. A proximity rule missed this.
        Map<String, String> tests = map("test/de/tum/cit/aet/nodraft/RecursionUtilsTest.java", """
                class RecursionUtilsTest {
                    private static final List<String> ROOTS = List.of("assignment/src/main/java/de/tum/cit/aet/nodraft/", "src/main/java/de/tum/cit/aet/nodraft/");

                    @Test
                    void testDigitalRoot() {
                        assertEquals(3, RecursionUtils.digitalRoot(9876), "digital root of 9876 is 3");
                    }

                    @Test
                    void testSumToN() {
                        assertEquals(15, RecursionUtils.sumToN(5), "sum to five");
                    }

                    private String source() throws Exception {
                        return Files.readString(Path.of(ROOTS.get(0) + "RecursionUtils.java"), StandardCharsets.UTF_8);
                    }
                }
                """);

        assertThat(ExerciseIntegrityGate.gradedTestsReadingSourceTreeReasons(tests)).singleElement().asString().contains("RecursionUtilsTest.java");
    }

    // --- Technique-mandate detection ---

    @ParameterizedTest
    @ValueSource(strings = { "`refine()` must use the previous iteration's estimate as its starting point.",
            "`step()` must use the simulation loop's current tick when stamping an event.", "The client must use the loopback address 127.0.0.1 when no host is configured.",
            "`process` must use the pipeline stages in their declared order.", "The evaluator must use the lambda body's free variables to decide capture.",
            "`retry` must use the loop counter supplied by the caller.", "The reducer must use an iteration limit of at most 100.",
            "`render` must use the lambda passed to `forEach` exactly once per element.", "The parser must use the provided input stream and must not close it.",
            "Self-loops are not allowed in the dependency graph.", "The result must be iteratively refined until it converges." })
    void techniqueMandates_doNotFireOnRulesThatMerelyNameATechniqueWord(String rule) {
        // Every one of these is observable through the public API, so every one must keep its repair round. All were false rejections in earlier revisions of the pattern.
        assertThat(ExerciseIntegrityGate.techniqueMandatesInRules("## Rules\n" + rule)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "`sum` must be implemented **recursively**.", "The implementation must not use loops.", "The transformation must use the Stream API.",
            "Loops are not allowed; solve this with recursion.", "Recursion is not allowed.", "Implement `sum` recursively without any loop.",
            "Do not use loops or iteration in your implementation.", "The method should be implemented recursively.", "The implementation must avoid loops entirely.",
            "Iterative constructs are not allowed.", "The solution must use recursion, not iteration." })
    void techniqueMandates_fireOnRulesNoAssertionCanObserve(String rule) {
        assertThat(ExerciseIntegrityGate.techniqueMandatesInRules("## Rules\n" + rule)).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "an implementation that uses a for loop instead of recursion", "an iterative implementation using an explicit stack",
            "a version that computes the result with a while loop rather than recursively", "the tests do not check that the implementation is recursive" })
    void techniqueClaims_recogniseTheCriticsOwnPhrasing(String finding) {
        // Findings are written in the critic's voice, not the specification's: a weak-oracle finding carries the surviving mutant's description and never says "must".
        assertThat(ExerciseIntegrityGate.describesTechniqueRatherThanBehaviour(finding)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "the recursive helper's base case is untested", "no test covers the empty list, so the recursion's terminating branch is unchecked",
            "the recursion depth limit of 100 stated in R3 is not enforced by any test", "the lambda passed to forEach is invoked twice per element and no test detects it" })
    void techniqueClaims_leaveRepairableGapsThatMerelyMentionTheTopic(String finding) {
        // On a recursion exercise almost every finding says "recursive" somewhere. Mentioning the topic must not cost a finding its repair round.
        assertThat(ExerciseIntegrityGate.describesTechniqueRatherThanBehaviour(finding)).isFalse();
    }

    // --- Nondeterministic graded test gate ---

    @Test
    void nondeterminism_rejectsAGradedTestThatShufflesItsInput() {
        // Verbatim shape from a live run. The suite shuffled its input "to ensure order-independence"; an implementation that never sorted at all passed 5 of 20 identical runs,
        // so the same submission scored differently on re-run.
        Map<String, String> tests = map("test/de/tum/cit/aet/nodraft/BookAggregatorTest.java", """
                class BookAggregatorTest {
                    @Test
                    void testGroupBooksByGenreSorted() {
                        List<Book> books = new ArrayList<>(SAMPLE);
                        java.util.Collections.shuffle(books);
                        assertEquals("A Game of Thrones", BookAggregator.groupBooksByGenreSorted(books).get("Fantasy").get(0).getTitle(), "sorted by title");
                    }
                }
                """);

        assertThat(ExerciseIntegrityGate.nondeterministicGradedTestReasons(tests)).singleElement().asString().contains("BookAggregatorTest.java", "Collections.shuffle",
                "score differently on re-run", "already deliberately out of order");
    }

    @Test
    void nondeterminism_acceptsAFixedOutOfOrderInputAndASeededGenerator() {
        // The two forms the rejection message points at: deliberately unsorted fixed data, and an explicitly seeded generator.
        Map<String, String> tests = map("test/OrderTest.java", """
                class OrderTest {
                    @Test
                    void testSorts() {
                        List<Book> books = List.of(new Book("Fantasy", "The Hobbit", 310), new Book("Fantasy", "A Game of Thrones", 694));
                        Random random = new Random(42);
                        assertEquals("A Game of Thrones", Aggregator.sorted(books).get(0).getTitle(), "sorted by title");
                    }
                }
                """);

        assertThat(ExerciseIntegrityGate.nondeterministicGradedTestReasons(tests)).isEmpty();
    }

    @Test
    void nondeterminism_acceptsATimestampUsedToConstructAValue() {
        // Instant.now() is not randomness: a test that builds a value object with the current time and never asserts on it stays reproducible. Matching it would reject correct
        // suites — one generated exercise does exactly this.
        Map<String, String> tests = map("test/SalesAggregatorTest.java", """
                class SalesAggregatorTest {
                    private static Transaction tx(String id, double amount) {
                        return new Transaction(id, amount, Instant.now());
                    }
                }
                """);

        assertThat(ExerciseIntegrityGate.nondeterministicGradedTestReasons(tests)).isEmpty();
    }

    @Test
    void nondeterminism_ignoresTheSeededBuildHarness() {
        // Harness files are graded verbatim and owned by the tampering gate; a build script is not a graded test.
        assertThat(ExerciseIntegrityGate.nondeterministicGradedTestReasons(map("pom.xml", "<!-- Math.random() -->"))).isEmpty();
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
    void leak_failsOpenWhenEitherSideEmpty() {
        assertThat(ExerciseIntegrityGate.solutionLeakReasons(Map.of(), map("a", SOLUTION_EXERCISE_HS))).isEmpty();
        assertThat(ExerciseIntegrityGate.solutionLeakReasons(map("a", SOLUTION_EXERCISE_HS), Map.of())).isEmpty();
    }

    // --- Grading-context sniffing gate ---

    @Test
    void sniffing_rejectsATemplateStubThatSensesTheCallingTestViaTheStackTrace() {
        // Observed live: a template constructor walked Thread.currentThread().getStackTrace() and threw only when the bound test's method name was on the stack — faking
        // "fails on the template" for exactly one test while behaving implemented everywhere else.
        String gamedStub = "public class Processor {\n    Processor() {\n        for (StackTraceElement e : Thread.currentThread().getStackTrace()) {\n"
                + "            if (\"testConstruction\".equals(e.getMethodName())) { throw new UnsupportedOperationException(); }\n        }\n    }\n}\n";
        var reasons = ExerciseIntegrityGate.gradingContextSniffingReasons(map("src/Processor.java", gamedStub), map("src/Processor.java", "public class Processor {}\n"));
        assertThat(reasons).hasSize(1);
        assertThat(reasons.getFirst()).contains("inspect the grading context").contains("src/Processor.java").contains("must fail the same way for every caller");
    }

    @Test
    void sniffing_rejectsStackWalkerUseInTheSolution() {
        String walker = "public class Sneaky {\n    void check() { StackWalker.getInstance().walk(frames -> null); }\n}\n";
        var reasons = ExerciseIntegrityGate.gradingContextSniffingReasons(map("src/Ok.java", "public class Ok {}\n"), map("src/Sneaky.java", walker));
        assertThat(reasons).hasSize(1);
        assertThat(reasons.getFirst()).contains("src/Sneaky.java");
    }

    @Test
    void sniffing_acceptsOrdinaryExceptionHandlingAndCleanStubs() {
        // e.printStackTrace() and exception.getStackTrace() are ordinary exercise code, not grading-context introspection; only Thread.currentThread().getStackTrace and
        // StackWalker are the gaming vectors.
        String honest = "public class Calc {\n    int add(int a, int b) {\n        try { return a + b; } catch (RuntimeException e) { e.printStackTrace(); throw e; }\n    }\n"
                + "    void log(Exception e) { StackTraceElement[] frames = e.getStackTrace(); }\n}\n";
        assertThat(ExerciseIntegrityGate.gradingContextSniffingReasons(map("src/Calc.java", honest), map("src/Calc.java", honest))).isEmpty();
        assertThat(ExerciseIntegrityGate.gradingContextSniffingReasons(Map.of(), Map.of())).isEmpty();
        assertThat(ExerciseIntegrityGate.gradingContextSniffingReasons(null, null)).isEmpty();
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

    /**
     * The REAL seeded Java/Maven test harness, not a replica: this is the exact pom Artemis ships into every generated tests repository, so the build fails if someone edits it
     * into a shape the Ares-convention gate would reject.
     */
    private static String aresPom() {
        return classpathResource("templates/java/maven_maven/test/projectTemplate/pom.xml");
    }

    /** The REAL seeded Java/Gradle test harness, for the same reason as {@link #aresPom()}. */
    private static String aresBuildGradle() {
        return classpathResource("templates/java/test/gradle/projectTemplate/build.gradle");
    }

    private static String classpathResource(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        }
        catch (IOException exception) {
            throw new UncheckedIOException("Could not read the seeded exercise scaffold resource " + path, exception);
        }
    }
}
