package de.tum.cit.aet.artemis.assessment.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Set;

import org.junit.jupiter.api.Test;

class FeedbackRepositoryCriteriaTest {

    @Test
    void absentGradingCriteria_haveNoStructuredFeedback() {
        var repository = mock(FeedbackRepository.class, CALLS_REAL_METHODS);

        assertThat(repository.hasFeedbackByExerciseGradingCriteria(null)).isFalse();
        assertThat(repository.hasFeedbackByExerciseGradingCriteria(Set.of())).isFalse();
        verify(repository, never()).hasFeedbackFromGradingInstructionIds(anyList());
    }
}
