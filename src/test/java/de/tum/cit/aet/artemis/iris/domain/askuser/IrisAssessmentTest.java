package de.tum.cit.aet.artemis.iris.domain.askuser;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

class IrisAssessmentTest {

    @Test
    void settersUpdateStudentAndExercise() {
        var assessment = new IrisAssessment();
        var student = new User();
        student.setId(1L);
        var exercise = new ProgrammingExercise();
        exercise.setId(2L);

        assessment.setStudent(student);
        assessment.setExercise(exercise);

        assertThat(assessment.getStudent()).isEqualTo(student);
        assertThat(assessment.getExercise()).isEqualTo(exercise);
    }

    @Test
    void settersUpdateVerdictAndReasoning() {
        var student = new User();
        student.setId(1L);
        var exercise = new ProgrammingExercise();
        exercise.setId(2L);
        var assessment = new IrisAssessment(student, exercise);

        assessment.setVerdict(IrisVerdict.SUSPICIOUS);
        assessment.setVerdictReview(IrisVerdictReview.ACCEPTED);
        assessment.setReasoning(List.of("reason1", "reason2"));

        assertThat(assessment.getVerdict()).isEqualTo(IrisVerdict.SUSPICIOUS);
        assertThat(assessment.getVerdictReview()).isEqualTo(IrisVerdictReview.ACCEPTED);
        assertThat(assessment.getReasoning()).containsExactly("reason1", "reason2");
        assertThat(assessment.getStudent()).isEqualTo(student);
        assertThat(assessment.getExercise()).isEqualTo(exercise);
    }

    @Test
    void toStringContainsIdentifyingInformation() {
        var student = new User();
        student.setId(1L);
        var exercise = new ProgrammingExercise();
        exercise.setId(2L);
        var assessment = new IrisAssessment(student, exercise);

        assertThat(assessment.toString()).contains("userId=1").contains("exerciseId=2");
    }
}
