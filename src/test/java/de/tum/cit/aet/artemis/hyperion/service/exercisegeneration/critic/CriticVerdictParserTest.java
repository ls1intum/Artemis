package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class CriticVerdictParserTest {

    private final CriticVerdictParser parser = new CriticVerdictParser(new ObjectMapper());

    @Test
    void intendedIncompleteStubIsNotAReachabilityDefect() {
        List<SpecFidelityReport.Finding> findings = parser.parseCritique("""
                {
                  "exampleChecks": [],
                  "apiChecks": [],
                  "templateChecks": [{
                    "ownerType": "Graphemes",
                    "test": "count",
                    "targetReached": false,
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

        assertThat(findings).isEmpty();
    }

    @Test
    void stubInTheWrongMethodRemainsAReachabilityDefect() {
        List<SpecFidelityReport.Finding> findings = parser.parseCritique("""
                {
                  "exampleChecks": [],
                  "apiChecks": [],
                  "templateChecks": [{
                    "ownerType": "Graphemes",
                    "test": "count",
                    "targetReached": false,
                    "reason": "the TODO is in a different method from the task target",
                    "evidenceQuote": "// TODO is in a different method"
                  }],
                  "contradictions": [],
                  "hiddenRequirements": [],
                  "missingExamples": [],
                  "invented": [],
                  "unrequestedChanges": [],
                  "missingRequestedChanges": []
                }
                """, CriticVerdictParser.ReviewPass.CONTRACT, false, "Implement count.", "// TODO is in a different method", "", false, false, true, false,
                Map.of("Graphemes", "stubbed"));

        assertThat(findings).singleElement().extracting(SpecFidelityReport.Finding::kind).isEqualTo(SpecFidelityReport.Kind.TEMPLATE_QUALITY_GAP);
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
