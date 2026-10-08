package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class ExerciseAssociationTest {

    @Test
    void correctionRounds_requireAnUnmaskedExam() {
        var exercise = new TextExercise();
        exercise.setId(42L);
        exercise.setExerciseGroup(new ExerciseGroup());

        assertThat(exercise.getExam()).isNull();
        assertThatThrownBy(exercise::getNumberOfCorrectionRounds).isInstanceOf(IllegalStateException.class).hasMessage("The exam of exercise 42 cannot be resolved");
    }

    @Test
    void correctionRounds_useTheExamSetting() {
        var exercise = new TextExercise();
        var exam = new Exam();
        exam.setNumberOfCorrectionRoundsInExam(2);
        var group = new ExerciseGroup();
        group.setExam(exam);
        exercise.setExerciseGroup(group);

        assertThat(exercise.getExamElseThrow()).isSameAs(exam);
        assertThat(exercise.getNumberOfCorrectionRounds()).isEqualTo(2);
    }

    @Test
    void courseExercise_keepsOneCorrectionRound() {
        var exercise = new TextExercise();

        assertThat(exercise.getNumberOfCorrectionRounds()).isEqualTo(1);
        assertThatThrownBy(exercise::getExamElseThrow).isInstanceOf(IllegalStateException.class);
    }
}
