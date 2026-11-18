package de.tum.cit.aet.artemis.plagiarism.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PlagiarismComparisonSidesTest {

    @Test
    void elseThrowGetters_returnSubmissionsOfBothSides() {
        var comparison = new PlagiarismComparison();
        var a = new PlagiarismSubmission();
        var b = new PlagiarismSubmission();
        comparison.setSubmissionA(a);
        comparison.setSubmissionB(b);

        assertThat(comparison.getSubmissionAElseThrow()).isSameAs(a);
        assertThat(comparison.getSubmissionBElseThrow()).isSameAs(b);
    }

    @Test
    void getSubmissionAElseThrow_withEmptyFirstSide_throws() {
        var comparison = new PlagiarismComparison();
        comparison.setSubmissionB(new PlagiarismSubmission());

        assertThat(comparison.getSubmissionA()).isNull();
        assertThatThrownBy(comparison::getSubmissionAElseThrow).isInstanceOf(IllegalStateException.class).hasMessageContaining("first side");
    }

    @Test
    void getSubmissionBElseThrow_withEmptySecondSide_throws() {
        var comparison = new PlagiarismComparison();
        comparison.setSubmissionA(new PlagiarismSubmission());

        assertThat(comparison.getSubmissionB()).isNull();
        assertThatThrownBy(comparison::getSubmissionBElseThrow).isInstanceOf(IllegalStateException.class).hasMessageContaining("second side");
    }
}
