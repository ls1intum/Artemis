package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.hyperion.protocol.VerificationResult;

class DifferentialBuildChecksTest {

    private static final String STRUCTURAL = "testClass[GenericApi:Level]";

    private static final String SPEC = """
            ## Design
            | Type | Role | Template status |
            |---|---|---|
            | Level | difficulty levels | stubbed |
            ## Public API
            ### Level
            ```java
            public enum Level { LOW, HIGH; }
            ```
            """;

    /** A solution with exactly the approved declarations, so only how the structural check failed separates a seeding defect from a candidate defect. */
    private static final Map<String, String> CONFORMING = Map.of("src/de/test/Level.java",
            "package de.test; public enum Level { LOW, HIGH; private static final int SEED = Integer.parseInt(\"invalid\"); }");

    @Test
    void aStructuralCheckFailingOnAnApiConformingSolutionIsAServerDefect() {
        BuildSummary solution = failing("org.opentest4j.AssertionFailedError: Declare the required class type parameters ==> expected: <0> but was: <1>");

        assertThat(DifferentialBuildChecks.structuralSeedingDefectReasons(solution, Set.of(STRUCTURAL), List.of(SPEC), CONFORMING, "de.test", true)).singleElement().asString()
                .startsWith(VerificationResult.SERVER_DEFECT_PREFIX);
    }

    @Test
    void aStructuralCheckFailingBecauseTheCandidateClassCannotInitializeIsNotAServerDefect() {
        for (String message : List.of("java.lang.ExceptionInInitializerError", "java.lang.NoClassDefFoundError: Could not initialize class de.test.Level")) {
            assertThat(DifferentialBuildChecks.structuralSeedingDefectReasons(failing(message), Set.of(STRUCTURAL), List.of(SPEC), CONFORMING, "de.test", true)).as(message)
                    .isEmpty();
        }
    }

    /**
     * Students write JUnit tests: the oracle lists no annotations, so Ares expects none on testNullInput and fails testMethods on the solution's @Test, although the signatures
     * match the approved API. The seeded check is then right about the solution, so this is no server defect.
     */
    @Test
    void anAnnotationTheStructuralOracleDoesNotListIsNotAServerDefect() {
        String spec = """
                ## Design
                | Type | Role | Template status |
                |---|---|---|
                | CommandLineValidatorTest | tests students write | stubbed |
                ## Public API
                ### CommandLineValidatorTest
                ```java
                public class CommandLineValidatorTest { public void testNullInput() {} }
                ```
                """;
        String structural = "testMethods[CommandLineValidatorTest]";
        BuildSummary solution = new BuildSummary(1, 1, 1, false, List.of(structural), List.of(structural), List.of(new AgentVerifyReport.TestFailureEvidence(structural,
                "org.opentest4j.AssertionFailedError: The annotation(s) of the expected method 'testNullInput' are not implemented as expected.")), "");
        Map<String, String> annotated = Map.of("src/de/test/CommandLineValidatorTest.java",
                "package de.test; import org.junit.jupiter.api.Test; public class CommandLineValidatorTest { @Test public void testNullInput() {} }");
        Map<String, String> plain = Map.of("src/de/test/CommandLineValidatorTest.java",
                "package de.test; public class CommandLineValidatorTest { @SuppressWarnings(\"unused\") public void testNullInput() {} }");

        assertThat(DifferentialBuildChecks.structuralSeedingDefectReasons(solution, Set.of(structural), List.of(spec), annotated, "de.test", true)).isEmpty();
        assertThat(DifferentialBuildChecks.structuralSeedingDefectReasons(solution, Set.of(structural), List.of(spec), plain, "de.test", true)).as("only source-level annotations")
                .singleElement().asString().startsWith(VerificationResult.SERVER_DEFECT_PREFIX);
    }

    @Test
    void aMatchingApiDeclaredOutsideTheExercisePackageIsNotAServerDefect() {
        BuildSummary solution = failing("java.lang.ClassNotFoundException: de.test.Level");
        Map<String, String> wrongPackage = Map.of("src/de/test/model/Level.java", "package de.test.model; public enum Level { LOW, HIGH; }");

        assertThat(DifferentialBuildChecks.structuralSeedingDefectReasons(solution, Set.of(STRUCTURAL), List.of(SPEC), wrongPackage, "de.test", true)).isEmpty();
    }

    @Test
    void onlyTheCheckThatFailedOnInitializationIsExcludedFromTheServerDefect() {
        String other = "testClass[GenericApi:Board]";
        BuildSummary solution = new BuildSummary(3, 2, 1, false, List.of(STRUCTURAL, other, "behaves"), List.of(STRUCTURAL, other),
                List.of(new AgentVerifyReport.TestFailureEvidence(STRUCTURAL, "java.lang.ExceptionInInitializerError"),
                        new AgentVerifyReport.TestFailureEvidence(other, "org.opentest4j.AssertionFailedError: Declare the required class type parameters")),
                "");

        // The initialization failure is the candidate's to repair, so the remaining structural failure is reported but cannot end the run.
        assertThat(DifferentialBuildChecks.structuralSeedingDefectReasons(solution, Set.of(STRUCTURAL, other), List.of(SPEC), CONFORMING, "de.test", true)).singleElement()
                .asString().startsWith("Possible server defect, reported for information").contains(other).doesNotContain(STRUCTURAL);
    }

    @Test
    void aStructuralFailureEndsTheRunOnlyWhenItIsTheOnlyVerificationFailure() {
        BuildSummary onlyStructural = failing("org.opentest4j.AssertionFailedError: Declare the required class type parameters");
        BuildSummary alsoBehavioral = new BuildSummary(2, 2, 1, false, List.of(STRUCTURAL, "behaves"), List.of(STRUCTURAL, "behaves"),
                List.of(new AgentVerifyReport.TestFailureEvidence(STRUCTURAL, "org.opentest4j.AssertionFailedError: Declare the required class type parameters")), "");

        assertThat(DifferentialBuildChecks.structuralSeedingDefectReasons(onlyStructural, Set.of(STRUCTURAL), List.of(SPEC), CONFORMING, "de.test", true)).singleElement()
                .satisfies(reason -> assertThat(new VerificationResult(false, false, true, 2, List.of(reason)).serverDefect()).isTrue());
        // Another gate failed, for example a task bound twice or test-plan weights off the approved Testing Strategy.
        assertThat(DifferentialBuildChecks.structuralSeedingDefectReasons(onlyStructural, Set.of(STRUCTURAL), List.of(SPEC), CONFORMING, "de.test", false)).singleElement()
                .satisfies(reason -> assertThat(new VerificationResult(false, false, true, 2, List.of(reason)).serverDefect()).isFalse()).asString().contains(STRUCTURAL);
        assertThat(DifferentialBuildChecks.structuralSeedingDefectReasons(alsoBehavioral, Set.of(STRUCTURAL), List.of(SPEC), CONFORMING, "de.test", true)).singleElement()
                .satisfies(reason -> assertThat(new VerificationResult(false, false, true, 2, List.of(reason)).serverDefect()).isFalse());
    }

    private static BuildSummary failing(String message) {
        return new BuildSummary(2, 1, 1, false, List.of(STRUCTURAL, "behaves"), List.of(STRUCTURAL), List.of(new AgentVerifyReport.TestFailureEvidence(STRUCTURAL, message)), "");
    }
}
