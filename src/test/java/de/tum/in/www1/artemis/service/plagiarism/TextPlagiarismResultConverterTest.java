package de.tum.in.www1.artemis.service.plagiarism;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.jplag.JPlagResult;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismResult;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;

class TextPlagiarismResultConverterTest {

    private final JPlagSubmissionDataExtractor jPlagSubmissionDataExtractor = mock();

    private final TextPlagiarismResultConverter converter = new TextPlagiarismResultConverter(jPlagSubmissionDataExtractor);

    @Test
    void shouldFailForQuizExercise() {
        // given
        var jPlagResult = new JPlagResult(emptyList(), null, 1, null);
        var quizExercise = new QuizExercise();

        // expect
        assertThatThrownBy(() -> converter.fromJplagResult(jPlagResult, quizExercise)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldFailForModellingExercise() {
        // given
        var jPlagResult = new JPlagResult(emptyList(), null, 1, null);
        var modelingExercise = new ModelingExercise();

        // expect
        assertThatThrownBy(() -> converter.fromJplagResult(jPlagResult, modelingExercise)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldConvertBasicResultFields() {
        // given
        var jPlagResult = new JPlagResult(emptyList(), null, 1234, null);
        var textExercise = new TextExercise();

        // when
        var result = converter.fromJplagResult(jPlagResult, textExercise);

        // then
        assertThat(result).extracting(PlagiarismResult::getComparisons).isEqualTo(emptySet());
        assertThat(result).extracting(PlagiarismResult::getDuration).isEqualTo(1234L);
        assertThat(result).extracting(PlagiarismResult::getSimilarityDistribution).isEqualTo(List.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        assertThat(result).extracting(PlagiarismResult::getExercise).isSameAs(textExercise);
    }
}
