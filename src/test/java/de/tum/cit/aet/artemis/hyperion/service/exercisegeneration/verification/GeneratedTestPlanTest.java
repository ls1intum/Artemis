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
    void parsesSeamWeightTiersAndVisibility() {
        GeneratedTestPlan plan = GeneratedTestPlan.parse("""
                {"tests":[
                  {"name":"earn_subDollarPurchaseEarnsZeroPoints","seam":"S1","seamWeightTier":3,"visibility":"ALWAYS"},
                  {"name":"redeem_throwsWhenPointsInsufficient","seam":"S2","seamWeightTier":1.5,"visibility":"AFTER_DUE_DATE"}
                ]}
                """);

        assertThat(plan.tests()).hasSize(2);
        assertThat(plan.tests().getFirst()).isEqualTo(new GeneratedTestPlan.Entry("earn_subDollarPurchaseEarnsZeroPoints", "S1", 3.0, "ALWAYS"));
        assertThat(plan.hiddenEntries()).singleElement().extracting(GeneratedTestPlan.Entry::name).isEqualTo("redeem_throwsWhenPointsInsufficient");
        assertThat(plan.visibleEntries()).singleElement().extracting(GeneratedTestPlan.Entry::seam).isEqualTo("S1");
    }

    @Test
    void readsTheOldWeightFieldWithoutTeachingNewGeneratorsToWriteIt() {
        GeneratedTestPlan plan = GeneratedTestPlan.parse("{\"tests\":[{\"name\":\"testFoo\",\"seam\":\"S1\",\"weight\":2,\"visibility\":\"ALWAYS\"}]}");

        assertThat(plan.tests().getFirst().seamWeightTier()).isEqualTo(2);
    }

    @Test
    void effectiveWeightsPreserveAggregateSeamImportanceAndKeepStructuralFeedbackFromDominatingBehavior() {
        GeneratedTestPlan plan = GeneratedTestPlan.parse("""
                {"tests":[
                  {"name":"behaviour","seam":"S1","seamWeightTier":3,"visibility":"ALWAYS"},
                  {"name":"testClass[Strategy]","seam":"S1","seamWeightTier":3,"visibility":"ALWAYS"},
                  {"name":"testMethods[Strategy]","seam":"S1","seamWeightTier":3,"visibility":"ALWAYS"},
                  {"name":"edge","seam":"S2","seamWeightTier":1,"visibility":"ALWAYS"}
                ]}
                """);

        assertThat(plan.effectiveWeightsByName()).containsEntry("behaviour", 3.0).containsEntry("testClass[Strategy]", 0.0).containsEntry("testMethods[Strategy]", 0.0)
                .containsEntry("edge", 1.0);
        assertThat(plan.effectiveWeightsByName().values().stream().mapToDouble(Double::doubleValue).sum()).isEqualTo(4.0);
    }

    @Test
    void structuralOnlyLegacySeamRetainsItsAggregateImportance() {
        GeneratedTestPlan plan = GeneratedTestPlan.parse("""
                {"tests":[
                  {"name":"testClass[Strategy]","seam":"S1","seamWeightTier":2,"visibility":"ALWAYS"},
                  {"name":"testMethods[Strategy]","seam":"S1","seamWeightTier":2,"visibility":"ALWAYS"}
                ]}
                """);

        assertThat(plan.effectiveWeightsByName()).containsEntry("testClass[Strategy]", 1.0).containsEntry("testMethods[Strategy]", 1.0);
    }

    @Test
    void retainsAPlanWithoutSeamsForBackwardCompatiblePersistence() {
        GeneratedTestPlan plan = GeneratedTestPlan.parse("{\"tests\":[{\"name\":\"testFoo\",\"seamWeightTier\":1,\"visibility\":\"ALWAYS\"}]}");

        assertThat(plan.tests().getFirst().seam()).isEmpty();
    }

    @Test
    void rejectsMalformedSeamIdsInsteadOfCreatingUntraceableTaskGroups() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> GeneratedTestPlan.parse("{\"tests\":[{\"name\":\"testFoo\",\"seam\":\"delegation\",\"seamWeightTier\":1,\"visibility\":\"ALWAYS\"}]}"))
                .withMessageContaining("seam 'delegation'").withMessageContaining("S1");
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
        assertThatIllegalArgumentException().isThrownBy(() -> GeneratedTestPlan.parse("{\"tests\":[{\"seamWeightTier\":1,\"visibility\":\"ALWAYS\"}]}"))
                .withMessageContaining("non-empty \"name\"");
    }

    @Test
    void rejectsANonNumericSeamWeightTier_soAStringTierIsNeverSilentlyCoercedToZero() {
        assertThatIllegalArgumentException().isThrownBy(() -> GeneratedTestPlan.parse("{\"tests\":[{\"name\":\"testFoo\",\"seamWeightTier\":\"high\",\"visibility\":\"ALWAYS\"}]}"))
                .withMessageContaining("numeric \"seamWeightTier\"").withMessageContaining("testFoo");
    }

    @Test
    void rejectsASeamWeightTierOutsideTheGradingRange_namingTheOffendingTest() {
        // A tier of 0 would silently make a graded seam worth nothing, and an unbounded tier would let one seam dominate the whole exercise.
        assertThatIllegalArgumentException().isThrownBy(() -> GeneratedTestPlan.parse("{\"tests\":[{\"name\":\"testFoo\",\"seamWeightTier\":0,\"visibility\":\"ALWAYS\"}]}"))
                .withMessageContaining("testFoo").withMessageContaining("between 1 and 3");
        assertThatIllegalArgumentException().isThrownBy(() -> GeneratedTestPlan.parse("{\"tests\":[{\"name\":\"testFoo\",\"seamWeightTier\":9,\"visibility\":\"ALWAYS\"}]}"))
                .withMessageContaining("between 1 and 3");
    }

    @Test
    void rejectsAnUnknownVisibility_soATypoNeverSilentlyPublishesAHiddenTest() {
        assertThatIllegalArgumentException().isThrownBy(() -> GeneratedTestPlan.parse("{\"tests\":[{\"name\":\"testFoo\",\"seamWeightTier\":1,\"visibility\":\"HIDDEN\"}]}"))
                .withMessageContaining("visibility 'HIDDEN'").withMessageContaining("AFTER_DUE_DATE");
    }

    @Test
    void rejectsDuplicateTestNames_becauseTheLastEntryWouldSilentlyWin() {
        assertThatIllegalArgumentException().isThrownBy(() -> GeneratedTestPlan.parse(
                "{\"tests\":[{\"name\":\"testFoo\",\"seamWeightTier\":1,\"visibility\":\"ALWAYS\"},{\"name\":\"testFoo\",\"seamWeightTier\":3,\"visibility\":\"AFTER_DUE_DATE\"}]}"))
                .withMessageContaining("more than once").withMessageContaining("testFoo");
    }
}
