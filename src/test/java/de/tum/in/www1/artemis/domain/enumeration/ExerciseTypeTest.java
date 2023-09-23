package de.tum.in.www1.artemis.domain.enumeration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;

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
