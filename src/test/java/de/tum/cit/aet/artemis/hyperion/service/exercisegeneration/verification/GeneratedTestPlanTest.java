package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the TESTS stage's grading plan. Every rejection message is what the agent reads back from the stage gate, so each test pins the actionable part of the message,
 * not just the fact that parsing failed — a rejection the agent cannot act on is the same as no feedback at all.
 */
class GeneratedTestPlanTest {

    @Test
    void parsesWeightsAndVisibility() {
        GeneratedTestPlan plan = GeneratedTestPlan.parse("""
                {"tests":[
                  {"name":"earn_subDollarPurchaseEarnsZeroPoints","weight":3,"visibility":"ALWAYS"},
                  {"name":"redeem_throwsWhenPointsInsufficient","weight":1.5,"visibility":"AFTER_DUE_DATE"}
                ]}
                """);

        assertThat(plan.tests()).hasSize(2);
        assertThat(plan.tests().getFirst()).isEqualTo(new GeneratedTestPlan.Entry("earn_subDollarPurchaseEarnsZeroPoints", 3.0, "ALWAYS"));
        assertThat(plan.hiddenEntries()).singleElement().extracting(GeneratedTestPlan.Entry::name).isEqualTo("redeem_throwsWhenPointsInsufficient");
    }

    @Test
    void rejectsMalformedJsonWithTheParserReason() {
        assertThatIllegalArgumentException().isThrownBy(() -> GeneratedTestPlan.parse("{\"tests\": [")).withMessageContaining("not valid JSON");
    }

    @Test
    void rejectsAPlanWithoutEntries_namingTheExpectedShape() {
        assertThatIllegalArgumentException().isThrownBy(() -> GeneratedTestPlan.parse("{\"tests\":[]}")).withMessageContaining("AFTER_DUE_DATE").withMessageContaining("at least");
    }

    @Test
    void rejectsAnEntryWithoutAName() {
        assertThatIllegalArgumentException().isThrownBy(() -> GeneratedTestPlan.parse("{\"tests\":[{\"weight\":1,\"visibility\":\"ALWAYS\"}]}"))
                .withMessageContaining("non-empty \"name\"");
    }

    @Test
    void rejectsANonNumericWeight_soAStringWeightIsNeverSilentlyCoercedToZero() {
        assertThatIllegalArgumentException().isThrownBy(() -> GeneratedTestPlan.parse("{\"tests\":[{\"name\":\"testFoo\",\"weight\":\"high\",\"visibility\":\"ALWAYS\"}]}"))
                .withMessageContaining("numeric \"weight\"").withMessageContaining("testFoo");
    }

    @Test
    void rejectsAWeightOutsideTheGradingRange_namingTheOffendingTest() {
        // A weight of 0 would silently make a graded test worth nothing, and an unbounded weight would let one test dominate the whole exercise.
        assertThatIllegalArgumentException().isThrownBy(() -> GeneratedTestPlan.parse("{\"tests\":[{\"name\":\"testFoo\",\"weight\":0,\"visibility\":\"ALWAYS\"}]}"))
                .withMessageContaining("testFoo").withMessageContaining("between 1 and 3");
        assertThatIllegalArgumentException().isThrownBy(() -> GeneratedTestPlan.parse("{\"tests\":[{\"name\":\"testFoo\",\"weight\":9,\"visibility\":\"ALWAYS\"}]}"))
                .withMessageContaining("between 1 and 3");
    }

    @Test
    void rejectsAnUnknownVisibility_soATypoNeverSilentlyPublishesAHiddenTest() {
        assertThatIllegalArgumentException().isThrownBy(() -> GeneratedTestPlan.parse("{\"tests\":[{\"name\":\"testFoo\",\"weight\":1,\"visibility\":\"HIDDEN\"}]}"))
                .withMessageContaining("visibility 'HIDDEN'").withMessageContaining("AFTER_DUE_DATE");
    }

    @Test
    void rejectsDuplicateTestNames_becauseTheLastEntryWouldSilentlyWin() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> GeneratedTestPlan
                        .parse("{\"tests\":[{\"name\":\"testFoo\",\"weight\":1,\"visibility\":\"ALWAYS\"},{\"name\":\"testFoo\",\"weight\":3,\"visibility\":\"AFTER_DUE_DATE\"}]}"))
                .withMessageContaining("more than once").withMessageContaining("testFoo");
    }
}
