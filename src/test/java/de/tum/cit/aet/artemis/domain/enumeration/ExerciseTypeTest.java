package de.tum.cit.aet.artemis.domain.enumeration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.domain.TextExercise;
import de.tum.cit.aet.artemis.domain.modeling.ModelingExercise;
import de.tum.cit.aet.artemis.domain.quiz.QuizExercise;

class ExerciseTypeTest {

    @Test
    void testGetExerciseTypeFromClass() {
        assertThat(ExerciseType.getExerciseTypeFromClass(TextExercise.class)).isEqualTo(ExerciseType.TEXT);
        assertThat(ExerciseType.getExerciseTypeFromClass(ProgrammingExercise.class)).isEqualTo(ExerciseType.PROGRAMMING);
        assertThat(ExerciseType.getExerciseTypeFromClass(ModelingExercise.class)).isEqualTo(ExerciseType.MODELING);
        assertThat(ExerciseType.getExerciseTypeFromClass(FileUploadExercise.class)).isEqualTo(ExerciseType.FILE_UPLOAD);
        assertThat(ExerciseType.getExerciseTypeFromClass(QuizExercise.class)).isEqualTo(ExerciseType.QUIZ);

        assertThatThrownBy(() -> ExerciseType.getExerciseTypeFromClass(Exercise.class)).isInstanceOf(IllegalArgumentException.class);
    }
}
