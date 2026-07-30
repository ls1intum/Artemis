package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class CriticVerdictParserTest {

    private final CriticVerdictParser parser = new CriticVerdictParser(new ObjectMapper());

    @Test
    void intendedIncompleteStubReportedAsUnreachableRequiresCorrection() {
        List<SpecFidelityReport.Finding> findings = parser.parseCritique("""
                {
                  "exampleChecks": [],
                  "apiChecks": [],
                  "templateChecks": [{
                    "ownerType": "Graphemes",
                    "test": "count",
                    "targetReached": false,
                    "blockingCause": "INTENDED_INCOMPLETE_STUB",
                    "reason": "the method contains a TODO and throws UnsupportedOperationException",
                    "evidenceQuote": "// TODO: implement"
                  }],
                  "contradictions": [],
                  "hiddenRequirements": [],
                  "missingExamples": [],
                  "invented": [],
                  "unrequestedChanges": [],
                  "missingRequestedChanges": []
                }
                """, CriticVerdictParser.ReviewPass.CONTRACT, false, "Implement count.", "// TODO: implement", "", false, false, true, false, Map.of("Graphemes", "stubbed"));

        assertThat(findings).isNull();
    }

    @Test
    void liveIntendedStubPhraseRequiresCorrectionRatherThanAFalseBlocker() {
        List<SpecFidelityReport.Finding> findings = parser.parseCritique("""
                {
                  "exampleChecks": [],
                  "apiChecks": [],
                  "templateChecks": [{
                    "ownerType": "DependencyGraph",
                    "test": "findOrder",
                    "targetReached": false,
                    "blockingCause": "INTENDED_INCOMPLETE_STUB",
                    "reason": "the intended findOrder stub throws UnsupportedOperationException before any student implementation can be exercised",
                    "evidenceQuote": "throw new UnsupportedOperationException(\\"TODO\\");"
                  }],
                  "contradictions": [],
                  "hiddenRequirements": [],
                  "missingExamples": [],
                  "invented": [],
                  "unrequestedChanges": [],
                  "missingRequestedChanges": []
                }
                """, CriticVerdictParser.ReviewPass.CONTRACT, false, "Implement findOrder.", "throw new UnsupportedOperationException(\"TODO\");", "", false, false, true, false,
                Map.of("DependencyGraph", "stubbed"));

        assertThat(findings).isNull();
    }

    @Test
    void passingTemplateCheckCannotCarryAContradictoryBlockingCause() {
        List<SpecFidelityReport.Finding> findings = parseSingleTemplateCheck("""
                "targetReached": true,
                "blockingCause": "DIFFERENT_STUDENT_SEAM",
                "reason": "Parser blocks the intended scheduler seam"
                """);

        assertThat(findings).isNull();
    }

    @Test
    void failedTemplateCheckRequiresArtifactEvidence() {
        List<SpecFidelityReport.Finding> findings = parseSingleTemplateCheck("""
                "targetReached": false,
                "blockingCause": "PROVIDED_SCAFFOLD_DEFECT",
                "reason": "the provided fixture prevents execution",
                "evidenceQuote": ""
                """);

        assertThat(findings).isNull();
    }

    @Test
    void stubInADifferentOwnerBeforeTheIntendedSeamRemainsAReachabilityDefect() {
        List<SpecFidelityReport.Finding> findings = parser.parseCritique("""
                {
                  "exampleChecks": [],
                  "apiChecks": [],
                  "templateChecks": [{
                    "ownerType": "Scheduler",
                    "test": "schedule",
                    "targetReached": false,
                    "blockingCause": "DIFFERENT_STUDENT_SEAM",
                    "reason": "the Parser TODO throws UnsupportedOperationException before Scheduler.schedule can be reached",
                    "evidenceQuote": "throw new UnsupportedOperationException(\\"TODO Parser.parse\\");"
                  }],
                  "contradictions": [],
                  "hiddenRequirements": [],
                  "missingExamples": [],
                  "invented": [],
                  "unrequestedChanges": [],
                  "missingRequestedChanges": []
                }
                """, CriticVerdictParser.ReviewPass.CONTRACT, false, "Implement Scheduler.schedule.", "throw new UnsupportedOperationException(\"TODO Parser.parse\");", "", false,
                false, true, false, Map.of("Scheduler", "stubbed"));

        assertThat(findings).singleElement().extracting(SpecFidelityReport.Finding::kind).isEqualTo(SpecFidelityReport.Kind.TEMPLATE_QUALITY_GAP);
    }

    private List<SpecFidelityReport.Finding> parseSingleTemplateCheck(String checkFields) {
        return parser.parseCritique("""
                {
                  "exampleChecks": [],
                  "apiChecks": [],
                  "templateChecks": [{
                    "ownerType": "Scheduler",
                    "test": "schedule",
                    %s
                  }],
                  "contradictions": [],
                  "hiddenRequirements": [],
                  "missingExamples": [],
                  "invented": [],
                  "unrequestedChanges": [],
                  "missingRequestedChanges": []
                }
                """.formatted(checkFields), CriticVerdictParser.ReviewPass.CONTRACT, false, "Implement Scheduler.schedule.", "class Scheduler {}", "", false, false, true, false,
                Map.of("Scheduler", "stubbed"));
    }

    @Test
    void contradictionRequiresGroundedEvidenceFromTheAllegedlyConflictingArtifact() {
        String designRow = "| Dispatcher | delegates to a strategy | student-creates |";
        List<SpecFidelityReport.Finding> findings = parser.parseCritique("""
                {
                  "exampleChecks": [],
                  "apiChecks": [],
                  "templateChecks": [],
                  "contradictions": [{
                    "requirement": "Dispatcher must be student-created",
                    "sourceQuote": "| Dispatcher | delegates to a strategy | student-creates |",
                    "evidenceArtifact": "TEMPLATE: src/Dispatcher.java",
                    "evidenceQuote": "public class Dispatcher",
                    "reason": "Template supplies a full implementation (or omits the stub)."
                  }, {
                    "requirement": "Dispatcher delegation must remain student-owned.",
                    "sourceQuote": "| Dispatcher | delegates to a strategy | student-creates |",
                    "evidenceArtifact": "TEMPLATE: src/Client.java",
                    "evidenceQuote": "class Client { Dispatcher dispatch; }",
                    "reason": "The starter's provided Client directly implements the Dispatcher responsibility instead of delegating through it."
                  }, {
                    "requirement": "A produced implementation authorizes its own contract.",
                    "sourceQuote": "public class Dispatcher",
                    "evidenceArtifact": "SOLUTION: src/Dispatcher.java",
                    "evidenceQuote": "public class Dispatcher",
                    "reason": "The solution conflicts with itself."
                  }],
                  "hiddenRequirements": [],
                  "missingExamples": [],
                  "invented": [],
                  "unrequestedChanges": [],
                  "missingRequestedChanges": []
                }
                """, CriticVerdictParser.ReviewPass.CONTRACT, false, designRow + "\npublic class Dispatcher", designRow,
                "public class Dispatcher\nclass Client { Dispatcher dispatch; }",
                Map.of("SOLUTION: src/Dispatcher.java", "public class Dispatcher", "TEMPLATE: src/Client.java", "class Client { Dispatcher dispatch; }"), "", false, false, false,
                false, Map.of("Dispatcher", "student-creates"));

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION);
            assertThat(finding.requirement()).contains("Dispatcher delegation must remain student-owned");
        });
    }

    @Test
    void contradictionWithoutArtifactBoundEvidenceFailsClosed() {
        List<SpecFidelityReport.Finding> findings = parser.parseCritique("""
                {
                  "exampleChecks": [],
                  "apiChecks": [],
                  "templateChecks": [],
                  "contradictions": [{
                    "requirement": "conflict",
                    "sourceQuote": "authoritative rule",
                    "evidenceArtifact": "TEMPLATE: src/Thing.java",
                    "reason": "the template conflicts"
                  }],
                  "hiddenRequirements": [],
                  "missingExamples": [],
                  "invented": [],
                  "unrequestedChanges": [],
                  "missingRequestedChanges": []
                }
                """, CriticVerdictParser.ReviewPass.CONTRACT, false, "authoritative rule", "template content", "", false, false, false, false, Map.of());

        assertThat(findings).isNull();
    }

    @Test
    void javaReferenceTypeDoesNotAuthorizeNullHandling() {
        List<SpecFidelityReport.Finding> findings = parser.parseCritique("""
                {
                  "mutantChecks": [{
                    "mutant": "accept a null list",
                    "killed": false,
                    "sourceQuote": "P1",
                    "reason": "no null assertion exists"
                  }],
                  "uncovered": [{
                    "requirement": "reject a null list",
                    "sourceQuote": "P1",
                    "reason": "no null assertion exists"
                  }],
                  "weakOracle": [{
                    "requirement": "reject a null strategy",
                    "sourceQuote": "P1",
                    "reason": "no null assertion exists"
                  }]
                }
                """, CriticVerdictParser.ReviewPass.ORACLE, false, "The dispatcher holds a list and an active strategy.", "", "", false, false, false, true, Map.of());

        assertThat(findings).isEmpty();
    }

    @Test
    void explicitNullContractRemainsActionable() {
        List<SpecFidelityReport.Finding> findings = parser.parseCritique("""
                {
                  "mutantChecks": [{
                    "mutant": "accept a null list",
                    "killed": false,
                    "sourceQuote": "P1",
                    "reason": "no null assertion exists"
                  }],
                  "uncovered": [],
                  "weakOracle": []
                }
                """, CriticVerdictParser.ReviewPass.ORACLE, false, "A null list is rejected.", "", "", false, false, false, true, Map.of());

        assertThat(findings).singleElement().extracting(SpecFidelityReport.Finding::kind).isEqualTo(SpecFidelityReport.Kind.WEAK_TEST_ORACLE);
    }

    @Test
    void oracleReviewCannotTurnGivenSupportIntoStudentWork() {
        List<SpecFidelityReport.Finding> findings = parser.parseCritique("""
                {
                  "mutantChecks": [{
                    "mutant": "given dispatcher accepts an empty list",
                    "killed": false,
                    "sourceQuote": "P1",
                    "ownerType": "Dispatcher",
                    "reason": "no graded assertion exists"
                  }],
                  "uncovered": [{
                    "requirement": "given dispatcher rejects an empty list",
                    "sourceQuote": "P1",
                    "ownerType": "Dispatcher",
                    "reason": "no graded assertion exists"
                  }],
                  "weakOracle": [{
                    "requirement": "given dispatcher can return normally for an empty list",
                    "sourceQuote": "P1",
                    "ownerType": "Dispatcher",
                    "reason": "no graded assertion exists"
                  }]
                }
                """, CriticVerdictParser.ReviewPass.ORACLE, false, "The dispatcher rejects an empty list.", "", "", false, false, false, true,
                Map.of("Dispatcher", "given", "Strategy", "student-creates"));

        assertThat(findings).isEmpty();
    }

    @Test
    void oracleReviewRetainsFindingsForStudentOwnedTypes() {
        List<SpecFidelityReport.Finding> findings = parser.parseCritique("""
                {
                  "mutantChecks": [{
                    "mutant": "strategy ignores the requested floor",
                    "killed": false,
                    "sourceQuote": "P1",
                    "ownerType": "Strategy",
                    "reason": "every fixture uses floor zero"
                  }],
                  "uncovered": [{
                    "requirement": "strategy uses the requested floor",
                    "sourceQuote": "P1",
                    "ownerType": "Strategy",
                    "reason": "no fixture uses a nonzero floor"
                  }],
                  "weakOracle": [{
                    "requirement": "strategy can always use floor zero",
                    "sourceQuote": "P1",
                    "ownerType": "Strategy",
                    "reason": "every fixture uses floor zero"
                  }]
                }
                """, CriticVerdictParser.ReviewPass.ORACLE, false, "The strategy uses the requested floor.", "", "", false, false, false, true,
                Map.of("Dispatcher", "given", "Strategy", "student-creates"));

        assertThat(findings).hasSize(3).extracting(SpecFidelityReport.Finding::kind).containsExactly(SpecFidelityReport.Kind.WEAK_TEST_ORACLE,
                SpecFidelityReport.Kind.WEAK_TEST_ORACLE, SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT);
    }

    @Test
    void oracleReviewRejectsMissingOrUnknownOwnerAgainstApprovedDesign() {
        List<SpecFidelityReport.Finding> findings = parser.parseCritique("""
                {
                  "mutantChecks": [{
                    "mutant": "strategy ignores the requested floor",
                    "killed": false,
                    "sourceQuote": "P1",
                    "reason": "every fixture uses floor zero"
                  }],
                  "uncovered": [],
                  "weakOracle": [{
                    "requirement": "strategy uses the requested floor",
                    "sourceQuote": "P1",
                    "ownerType": "UnknownType",
                    "reason": "every fixture uses floor zero"
                  }]
                }
                """, CriticVerdictParser.ReviewPass.ORACLE, false, "The strategy uses the requested floor.", "", "", false, false, false, true, Map.of("Strategy", "stubbed"));

        assertThat(findings).isNull();
    }

    @Test
    void fixedListDoesNotAuthorizeAnUnmodifiableViewRequirement() {
        List<SpecFidelityReport.Finding> findings = parser.parseCritique("""
                {
                  "mutantChecks": [{
                    "mutant": "forward a mutable list instead of an unmodifiable view",
                    "killed": false,
                    "sourceQuote": "P1",
                    "reason": "the collaborator can mutate it"
                  }],
                  "uncovered": [],
                  "weakOracle": [{
                    "requirement": "the dispatcher must pass an unmodifiable list",
                    "sourceQuote": "P1",
                    "reason": "the test only compares contents"
                  }]
                }
                """, CriticVerdictParser.ReviewPass.ORACLE, false, "The building has a fixed list of elevators.", "", "", false, false, false, true, Map.of());

        assertThat(findings).isEmpty();
    }

    @Test
    void explicitUnmodifiableContractRemainsActionable() {
        List<SpecFidelityReport.Finding> findings = parser.parseCritique("""
                {
                  "mutantChecks": [{
                    "mutant": "forward a mutable list instead of an unmodifiable view",
                    "killed": false,
                    "sourceQuote": "P1",
                    "reason": "the collaborator can mutate it"
                  }],
                  "uncovered": [],
                  "weakOracle": []
                }
                """, CriticVerdictParser.ReviewPass.ORACLE, false, "The strategy receives an unmodifiable view.", "", "", false, false, false, true, Map.of());

        assertThat(findings).singleElement().extracting(SpecFidelityReport.Finding::kind).isEqualTo(SpecFidelityReport.Kind.WEAK_TEST_ORACLE);
    }

    @Test
    void explicitExceptionMessageIsNotReportedAsHidden() {
        String verdict = """
                {
                  "exampleChecks": [],
                  "apiChecks": [],
                  "templateChecks": [],
                  "contradictions": [],
                  "hiddenRequirements": [{
                    "requirement": "IllegalArgumentException must have message \\"Elevator list is empty\\"",
                    "sourceQuote": "assertEquals(\\"Elevator list is empty\\", exception.getMessage())",
                    "reason": "the message is not stated"
                  }],
                  "missingExamples": [],
                  "invented": [],
                  "unrequestedChanges": [],
                  "missingRequestedChanges": []
                }
                """;
        String testSource = "assertEquals(\"Elevator list is empty\", exception.getMessage())";
        String statement = "If the list is empty, throw IllegalArgumentException with the message \"Elevator list is empty\".";

        List<SpecFidelityReport.Finding> findings = parser.parseCritique(verdict, CriticVerdictParser.ReviewPass.CONTRACT, false, testSource, testSource, statement, false, false,
                false, false, Map.of());

        assertThat(findings).isEmpty();
    }

    @Test
    void unstatedExceptionMessageRemainsHidden() {
        String verdict = """
                {
                  "exampleChecks": [],
                  "apiChecks": [],
                  "templateChecks": [],
                  "contradictions": [],
                  "hiddenRequirements": [{
                    "requirement": "IllegalArgumentException must have message \\"Elevator list is empty\\"",
                    "sourceQuote": "assertEquals(\\"Elevator list is empty\\", exception.getMessage())",
                    "reason": "the message is not stated"
                  }],
                  "missingExamples": [],
                  "invented": [],
                  "unrequestedChanges": [],
                  "missingRequestedChanges": []
                }
                """;
        String testSource = "assertEquals(\"Elevator list is empty\", exception.getMessage())";

        List<SpecFidelityReport.Finding> findings = parser.parseCritique(verdict, CriticVerdictParser.ReviewPass.CONTRACT, false, testSource, testSource,
                "If the list is empty, throw IllegalArgumentException.", false, false, false, false, Map.of());

        assertThat(findings).singleElement().extracting(SpecFidelityReport.Finding::kind).isEqualTo(SpecFidelityReport.Kind.HIDDEN_GRADED_REQUIREMENT);
    }
}
