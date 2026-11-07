package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.json.JsonMapper;

class BriefLearningGoalExtractorTest {

    private final ReviewerClient reviewer = mock(ReviewerClient.class);

    private final BriefLearningGoalExtractor extractor = new BriefLearningGoalExtractor(reviewer, JsonMapper.builder().build());

    @Test
    void groundsWrappedGoalsInTheOriginalBriefAndAssignsTheirIdentities() {
        String brief = "Preserve arrival order; choose the holder with the\nsmallest count.";
        BriefLearningGoals result = extractor.parse("""
                {"goals":[{"quote":"Preserve arrival order"},{"quote":"choose the holder with the smallest count"}]}
                """, brief);

        assertThat(result).isNotNull();
        assertThat(result.sourceBrief()).isEqualTo(brief);
        assertThat(result.goals()).containsExactly(new BriefLearningGoals.Goal("G1", "Preserve arrival order"),
                new BriefLearningGoals.Goal("G2", "choose the holder with the smallest count"));
    }

    @Test
    void anInventedGoalInvalidatesTheInventoryInsteadOfLeavingAPartialScope() {
        assertThat(extractor.parse("""
                {"goals":[{"quote":"Preserve arrival order"},{"quote":"sort holders alphabetically"}]}
                """, "Preserve arrival order; choose the holder with the smallest count.")).isNull();
    }

    @Test
    void duplicateWrappedQuotesCannotMasqueradeAsDistinctGoals() {
        assertThat(extractor.parse("""
                {"goals":[{"quote":"Preserve arrival order"},{"quote":"Preserve  arrival\\norder"}]}
                """, "Preserve arrival order.")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "null", "{}", "{\"goals\":null}", "{\"goals\":[]}", "{\"goals\":[null]}", "{\"goals\":[{}]}" })
    void anUncheckedInventoryDoesNotBecomeAnEmptyKnownScope(String response) {
        assertThat(extractor.parse(response, "Preserve arrival order.")).isNull();
    }

    @Test
    void aMalformedInventoryCanBeCorrectedWithoutCandidateContext() {
        when(reviewer.configured()).thenReturn(true);
        when(reviewer.call(anyString(), anyString(), any(), any(), anyInt())).thenReturn("[]", """
                {"goals":[{"quote":"Preserve arrival order"},{"quote":"Choose the smallest holder"}]}
                """);

        var result = extractor.extract("Preserve arrival order. Choose the smallest holder.", null, () -> false);

        assertThat(result).isNotNull();
        assertThat(result.goals()).containsExactly(new BriefLearningGoals.Goal("G1", "Preserve arrival order"), new BriefLearningGoals.Goal("G2", "Choose the smallest holder"));
        assertThat(result.sourceBrief()).isEqualTo("Preserve arrival order. Choose the smallest holder.");
    }

    @Test
    void anUngroundedResponseCanBeCorrectedButCannotBecomePartialScope() {
        when(reviewer.configured()).thenReturn(true);
        when(reviewer.call(anyString(), anyString(), any(), any(), anyInt())).thenReturn("""
                {"goals":[{"quote":"Preserve arrival order"},{"quote":"Sort by surname"}]}
                """, """
                {"goals":[{"quote":"Preserve arrival order"},{"quote":"Choose the smallest holder"}]}
                """);

        var result = extractor.extract("Preserve arrival order. Choose the smallest holder.", null, () -> false);

        assertThat(result).isNotNull();
        assertThat(result.goals()).extracting(BriefLearningGoals.Goal::quote).containsExactly("Preserve arrival order", "Choose the smallest holder");
    }

    @Test
    void cancellationAfterAnInvalidResponseDoesNotStartACorrection() {
        var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        when(reviewer.configured()).thenReturn(true);
        when(reviewer.call(anyString(), anyString(), any(), any(), anyInt())).thenAnswer(invocation -> {
            cancelled.set(true);
            return "[]";
        });

        assertThat(extractor.extract("Preserve arrival order.", null, cancelled::get)).isNull();
        verify(reviewer, times(1)).call(anyString(), anyString(), any(), any(), anyInt());
    }

    @Test
    void aMalformedProviderResponseLeavesScopeUnknown() {
        when(reviewer.configured()).thenReturn(true);
        when(reviewer.call(anyString(), anyString(), any(), any(), anyInt())).thenReturn("{unclosed");

        assertThat(extractor.extract("Preserve arrival order.", null, () -> false)).isNull();
        verify(reviewer, times(2)).call(anyString(), anyString(), any(), any(), anyInt());
    }
}
