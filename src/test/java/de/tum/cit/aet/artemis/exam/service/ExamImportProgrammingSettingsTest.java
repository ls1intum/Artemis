package de.tum.cit.aet.artemis.exam.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * The programming branch of the exam import copies the caller-owned settings off its own eager fetch of the source
 * exercise instead of a second lookup. The end-to-end programming import test is disabled (it needs a real LocalCI), so
 * pin the copy here: none of the four settings may fall back to its default.
 */
class ExamImportProgrammingSettingsTest {

    @Test
    void copiesCallerOwnedSettingsFromSource() {
        ProgrammingExercise source = new ProgrammingExercise();
        source.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_AS_BONUS);
        source.setPresentationScoreEnabled(true);
        source.setSecondCorrectionEnabled(true);
        source.setAllowComplaintsForAutomaticAssessments(true);

        ProgrammingExercise skeleton = new ProgrammingExercise();
        ExamImportService.copyProgrammingExerciseInformationForExamImport(source, skeleton);

        assertThat(skeleton.getIncludedInOverallScore()).isEqualTo(IncludedInOverallScore.INCLUDED_AS_BONUS);
        assertThat(skeleton.getPresentationScoreEnabled()).isTrue();
        assertThat(skeleton.getSecondCorrectionEnabled()).isTrue();
        assertThat(skeleton.getAllowComplaintsForAutomaticAssessments()).isTrue();
    }
}
