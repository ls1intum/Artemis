package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.hyperion.protocol.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.BriefLearningGoals;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.BriefLearningGoals.ConstructorBaseline;

class BriefScaffoldingGateTest {

    private static final String BRIEF = "Add a constructor to the existing class that had none.";

    private static final BriefLearningGoals GOALS = new BriefLearningGoals(BRIEF, List.of(new BriefLearningGoals.Goal("G1", BRIEF, ConstructorBaseline.NO_EXPLICIT_CONSTRUCTORS)));

    private static String specification(String constructors) {
        return """
                ## Design
                | Type | Template status |
                | --- | --- |
                | Meter | stubbed |
                ## Public API
                ```java
                public class Meter {
                %s
                    public int reading();
                }
                ```
                """.formatted(constructors);
    }

    @Test
    void aSuppliedOverloadCannotReplaceAddingTheFirstConstructor() {
        var result = BriefScaffoldingGate.checkSpecification(GOALS, specification("public Meter(int value);\npublic Meter();"));
        assertThat(result.complete()).isTrue();
        assertThat(result.findings()).singleElement().asString().contains("G1", "first explicit constructor");
    }

    @Test
    void aStudentCreatedConstructorLeavesAnExistingClassConstructorless() {
        var result = BriefScaffoldingGate.checkSpecification(GOALS, specification("/** @studentCreates */ public Meter(int value);"));
        assertThat(result.complete()).isTrue();
        assertThat(result.findings()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "public Meter() {}", "private Meter() {}", "public Meter(int value) {}" })
    void anActualStarterConstructorRemainsAReviewFindingEvenWithAnApprovedPlan(String suppliedConstructor) {
        var findings = BriefScaffoldingGate.artifactFindings(GOALS, specification("/** @studentCreates */ public Meter(int value);"),
                Map.of("sample/Meter.java", "public class Meter { " + suppliedConstructor + " }"),
                Map.of("sample/Meter.java", "public class Meter { public Meter(int value) {} }"));
        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.TEMPLATE_QUALITY_GAP);
            assertThat(finding.location().path()).isEqualTo("sample/Meter.java");
        });
    }

    @Test
    void anImplicitDefaultConstructorDoesNotCountAsSuppliedWork() {
        assertThat(BriefScaffoldingGate.artifactFindings(GOALS, specification("/** @studentCreates */ public Meter(int value);"), Map.of("Meter.java", "public class Meter {}"),
                Map.of("Meter.java", "public class Meter { public Meter(int value) {} }"))).isEmpty();
    }

    @Test
    void anUnavailableSpecificationCannotEraseTheSourceRequirement() {
        assertThat(BriefScaffoldingGate.artifactFindings(GOALS, null, Map.of(), Map.of())).singleElement().extracting(SpecFidelityReport.Finding::kind)
                .isEqualTo(SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE);
    }

    @Test
    void ordinaryOverloadingDoesNotAcquireAnUnstatedStartingState() {
        var goals = new BriefLearningGoals("Implement an overloaded constructor.", List.of(new BriefLearningGoals.Goal("G1", "Implement an overloaded constructor.")));
        assertThat(BriefScaffoldingGate.checkSpecification(goals, specification("public Meter(); public Meter(int value);")).findings()).isEmpty();
    }

    @Test
    void aWrongFrozenPlanRemainsAQualityFindingOnAnOtherwiseVerifiedDraft() {
        assertThat(BriefScaffoldingGate.artifactFindings(GOALS, specification("public Meter();"), Map.of(), Map.of())).singleElement().extracting(SpecFidelityReport.Finding::kind)
                .isEqualTo(SpecFidelityReport.Kind.SPECIFICATION_REVIEW_FINDING);
    }

    @Test
    void anAmbiguousOwnerIsUnknownInsteadOfAnApproval() {
        String specification = specification("/** @studentCreates */ public Meter(int value);").replace("| Meter | stubbed |", "| Meter | stubbed |\n| Gauge | stubbed |")
                .replace("```\n", "public class Gauge { /** @studentCreates */ public Gauge(int value); }\n```\n");
        assertThat(BriefScaffoldingGate.checkSpecification(GOALS, specification).complete()).isFalse();
    }
}
