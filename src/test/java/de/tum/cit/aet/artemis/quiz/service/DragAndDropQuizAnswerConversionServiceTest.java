package de.tum.cit.aet.artemis.quiz.service;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropSubmittedAnswer;

/**
 * Unit tests for the validation of the {@link DragAndDropQuizAnswerConversionService}.
 */
class DragAndDropQuizAnswerConversionServiceTest {

    @TempDir
    Path outputDir;

    @Test
    void convertDragAndDropQuizAnswerAndStoreAsPdf_forAQuestionWithoutBackground_isRejected() {
        var question = new DragAndDropQuestion();
        question.setId(5L);
        var answer = new DragAndDropSubmittedAnswer();
        answer.setQuizQuestion(question);

        var service = new DragAndDropQuizAnswerConversionService();

        assertThatExceptionOfType(IOException.class).isThrownBy(() -> service.convertDragAndDropQuizAnswerAndStoreAsPdf(answer, outputDir, true)).withMessageContaining("5")
                .withMessageContaining("no background image");
    }
}
