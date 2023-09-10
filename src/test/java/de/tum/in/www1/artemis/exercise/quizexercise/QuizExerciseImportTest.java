package de.tum.in.www1.artemis.exercise.quizexercise;

import static org.assertj.core.api.AssertionsForClassTypes.*;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.service.QuizExerciseImportService;

public class QuizExerciseImportTest {

    @Test
    void checkIfBackgroundFilePathIsABackgroundFilePathElseThrowTestForThrows() {
        assertThatThrownBy(
                () -> QuizExerciseImportService.checkIfBackgroundFilePathIsABackgroundFilePathElseThrow("/api/files/drag-and-drop/backgrounds/1/../../BackgroundFile.jpg"));
    }

    @Test
    void checkIfBackgroundFilePathIsABackgroundFilePathElseThrowTestForNotThrows() {
        assertThatCode(() -> QuizExerciseImportService.checkIfBackgroundFilePathIsABackgroundFilePathElseThrow("/api/files/drag-and-drop/backgrounds/1/BackgroundFile.jpg"))
                .doesNotThrowAnyException();
    }

}
