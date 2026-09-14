package de.tum.cit.aet.artemis.hyperion.service.variants;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the task-marker test-reference normalization.
 * <p>
 * Artemis resolves a marker's reference either by plain test NAME or, when wrapped in {@code <testid>}, by parsing
 * the content as a numeric id. A model reproducing the stored {@code <testid>} shape but filling in the name it
 * knows produces {@code <testid>testBubbleSort()</testid>}, which matches neither branch and silently unlinks the
 * task from grading — observed exhausting a whole attempt budget on one variant.
 */
class ProgrammingVariantToolsTestIdTest {

    @Test
    void shouldUnwrapTestIdTagWrappingATestName() {
        assertThat(ProgrammingVariantTools.normalizeTestIdReferences("[task][A](<testid>testBubbleSort()</testid>)")).isEqualTo("[task][A](testBubbleSort())");
    }

    @Test
    void shouldKeepNumericTestIdsUntouched() {
        assertThat(ProgrammingVariantTools.normalizeTestIdReferences("[task][B](<testid>27</testid>)")).isEqualTo("[task][B](<testid>27</testid>)");
    }

    @Test
    void shouldNormalizeOnlyTheMalformedReferenceInAMixedMarker() {
        assertThat(ProgrammingVariantTools.normalizeTestIdReferences("[task][C](<testid>testClass[SortStrategy]</testid>,<testid>18</testid>)"))
                .isEqualTo("[task][C](testClass[SortStrategy],<testid>18</testid>)");
    }

    @Test
    void shouldLeavePlainTestNamesUnchanged() {
        String statement = "[task][D](testBubbleSort(),testMergeSort())";
        assertThat(ProgrammingVariantTools.normalizeTestIdReferences(statement)).isEqualTo(statement);
    }

    @Test
    void shouldUnwrapSeveralCommaSeparatedNamesSharingOneTag() {
        assertThat(ProgrammingVariantTools.normalizeTestIdReferences("[task][E](<testid>testA(),testB()</testid>)")).isEqualTo("[task][E](testA(),testB())");
    }

    @Test
    void shouldLeaveAStatementWithoutTestIdTagsUnchanged() {
        String statement = "# Title\n\nSome prose mentioning testBubbleSort() but no task markers.";
        assertThat(ProgrammingVariantTools.normalizeTestIdReferences(statement)).isEqualTo(statement);
    }
}
