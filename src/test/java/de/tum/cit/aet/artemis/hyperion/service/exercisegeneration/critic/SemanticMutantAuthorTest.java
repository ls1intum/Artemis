package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class SemanticMutantAuthorTest {

    private static final String SPEC = "## Rules\n| R1 | choose the globally cheapest eligible request |\n";

    private static final String SOURCE = "package example; public class Scheduler { public int choose() { return 1; } }";

    private final SemanticMutantAuthor author = new SemanticMutantAuthor(mock(ReviewerClient.class), new ObjectMapper());

    @Test
    void parsesACompleteOneFileMutantAndCounterexample() {
        var result = author.parse(response("src/example/Scheduler.java", "R1", "org.junit.jupiter.api.Assertions.assertEquals(1, new Scheduler().choose(), \"R1 global choice\");"),
                SPEC, Map.of("src/example/Scheduler.java", SOURCE));

        assertThat(result).singleElement().satisfies(mutant -> {
            assertThat(mutant.solutionPath()).isEqualTo("src/example/Scheduler.java");
            assertThat(mutant.originalSolutionSource()).isEqualTo(SOURCE);
            assertThat(mutant.counterexample().testName()).isEqualTo("globalChoice");
        });
    }

    @Test
    void rejectsTraversalInventedRulesVacuousTestsAndUnchangedSources() {
        String validAssertion = "org.junit.jupiter.api.Assertions.assertEquals(1, new Scheduler().choose(), \"R1 global choice\");";
        assertThat(author.parse(response("../Scheduler.java", "R1", validAssertion), SPEC, Map.of("../Scheduler.java", SOURCE))).isEmpty();
        assertThat(author.parse(response("src/example/Scheduler.java", "R9", validAssertion), SPEC, Map.of("src/example/Scheduler.java", SOURCE))).isEmpty();
        assertThat(author.parse(response("src/example/Scheduler.java", "globally", validAssertion), SPEC + "globally", Map.of("src/example/Scheduler.java", SOURCE))).isEmpty();
        assertThat(author.parse(response("src/example/Scheduler.java", "R1", "new Scheduler().choose();"), SPEC, Map.of("src/example/Scheduler.java", SOURCE))).isEmpty();

        String unchanged = response("src/example/Scheduler.java", "R1", validAssertion).replace("return 2", "return 1");
        assertThat(author.parse(unchanged, SPEC, Map.of("src/example/Scheduler.java", SOURCE))).isEmpty();
    }

    @Test
    void capsIndependentExecutionWorkAtFourMutants() {
        String five = "{\"mutants\":[" + item("src/example/Scheduler.java", "R1", "globalChoice") + "," + item("src/example/Scheduler.java", "R1", "globalChoiceAgain") + ","
                + item("src/example/Scheduler.java", "R1", "thirdGlobalChoice") + "," + item("src/example/Scheduler.java", "R1", "fourthGlobalChoice") + ","
                + item("src/example/Scheduler.java", "R1", "fifthGlobalChoice") + "]}";

        assertThat(author.parse(five, SPEC, Map.of("src/example/Scheduler.java", SOURCE))).hasSize(4);
    }

    @Test
    void rejectsTestsThatDependOnBorrowedJunitImports() {
        String shorthand = response("src/example/Scheduler.java", "R1", "assertEquals(1, new Scheduler().choose(), \"R1\");").replace("@org.junit.jupiter.api.Test", "@Test");

        assertThat(author.parse(shorthand, SPEC, Map.of("src/example/Scheduler.java", SOURCE))).isEmpty();
    }

    @Test
    void sourceEvidenceContainsOnlyWholeFilesWithinTheBound() {
        String oversized = "class TooLarge {" + "x".repeat(100_000) + "}";

        Map<String, String> visible = SemanticMutantAuthor.boundedSolutionFiles(Map.of("src/a/TooLarge.java", oversized, "src/b/Scheduler.java", SOURCE));

        assertThat(visible).containsOnlyKeys("src/b/Scheduler.java");
        assertThat(visible.get("src/b/Scheduler.java")).isEqualTo(SOURCE);
    }

    @Test
    void routesOnlySourceBackedOracleRisksWithoutExposingTestBodies() {
        List<SpecFidelityReport.Finding> findings = List.of(
                new SpecFidelityReport.Finding(SpecFidelityReport.Kind.WEAK_TEST_ORACLE, "equality boundary", "a boundary misconception may survive"),
                new SpecFidelityReport.Finding(SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT, "state preservation", "no assertion observes the transition"),
                new SpecFidelityReport.Finding(SpecFidelityReport.Kind.EXECUTABLE_ORACLE_PENDING_SPEC_APPROVAL, "hard-coded configuration",
                        "the environment already proved fixed thresholds survive"),
                new SpecFidelityReport.Finding(SpecFidelityReport.Kind.MISSING_FAILURE_MESSAGE, "SchedulerTest.java", "presentation only"));

        assertThat(SemanticMutantAuthor.renderReviewTargets(findings))
                .contains("[T1] equality boundary", "[T2] state preservation", "[T3] hard-coded configuration", "already proved fixed thresholds")
                .doesNotContain("SchedulerTest.java", "presentation only");
    }

    @Test
    void preservesExactReviewProvenanceAndRejectsAMismatchedEcho() {
        SpecFidelityReport.Finding target = new SpecFidelityReport.Finding(SpecFidelityReport.Kind.WEAK_TEST_ORACLE, "equality boundary", "an exclusive comparison may pass");
        String targeted = response("src/example/Scheduler.java", "R1", "org.junit.jupiter.api.Assertions.assertEquals(1, new Scheduler().choose(), \"R1 global choice\");")
                .replace("\"testCode\":", "\"target\":\"T1\",\"targetHypothesis\":\"equality boundary: an exclusive comparison may pass\",\"testCode\":");

        assertThat(author.parse(targeted, SPEC, Map.of("src/example/Scheduler.java", SOURCE), List.of(target))).singleElement().extracting(SemanticMutant::reviewTarget)
                .isEqualTo(target);

        String mismatched = targeted.replace("\"targetHypothesis\":\"equality boundary: an exclusive comparison may pass\"", "\"targetHypothesis\":\"different claim\"");
        assertThat(author.parse(mismatched, SPEC, Map.of("src/example/Scheduler.java", SOURCE), List.of(target))).singleElement().extracting(SemanticMutant::reviewTarget).isNull();
    }

    private static String response(String path, String rule, String assertion) {
        return "{\"mutants\":[" + item(path, rule, "globalChoice").replace("org.junit.jupiter.api.Assertions.assertEquals(1, new Scheduler().choose(), \\\"R1\\\");",
                assertion.replace("\"", "\\\"")) + "]}";
    }

    private static String item(String path, String rule, String testName) {
        return """
                {
                  "rule":"%s",
                  "solutionPath":"%s",
                  "misconception":"chooses within the first batch instead of globally",
                  "mutantSource":"package example; public class Scheduler { public int choose() { return 2; } }",
                  "testName":"%s",
                  "testCode":"@org.junit.jupiter.api.Test void %s() { org.junit.jupiter.api.Assertions.assertEquals(1, new Scheduler().choose(), \\"R1\\"); }"
                }
                """.formatted(rule, path, testName, testName);
    }
}
