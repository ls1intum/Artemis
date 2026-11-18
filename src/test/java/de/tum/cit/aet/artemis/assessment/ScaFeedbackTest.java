package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.assessment.domain.ScaFeedback;

class ScaFeedbackTest {

    private static ScaFeedback scaFeedback(Long id) {
        ScaFeedback feedback = new ScaFeedback();
        feedback.setId(id);
        return feedback;
    }

    @Test
    void equalsIsBasedOnIdWhileHashCodeIsConstant() {
        ScaFeedback first = scaFeedback(1L);
        ScaFeedback sameId = scaFeedback(1L);
        ScaFeedback otherId = scaFeedback(2L);

        assertThat(first).isEqualTo(first).isEqualTo(sameId).isNotEqualTo(otherId).isNotEqualTo(null).isNotEqualTo("1");
        assertThat(first.hashCode()).isEqualTo(otherId.hashCode());
    }

    @Test
    void feedbackWithoutIdIsNeverEqualToAnotherInstance() {
        assertThat(scaFeedback(null)).isNotEqualTo(scaFeedback(null));
    }
}
