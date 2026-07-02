package de.tum.cit.aet.artemis.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceSubmittedAnswer;

class MultipleChoiceSubmittedAnswerTest {

    @Test
    void getSelectedOptionsReturnsQuestionOrder() {
        MultipleChoiceQuestion question = questionWithOptions();
        MultipleChoiceSubmittedAnswer answer = answerFor(question);

        answer.setSelectedOptionIds(Set.of(2L, 1L));

        assertThat(answer.getSelectedOptions()).extracting(AnswerOption::getId).containsExactly(1L, 2L);
    }

    @Test
    void setSelectedOptionsKeepsOnlyStableIds() {
        MultipleChoiceQuestion question = questionWithOptions();
        MultipleChoiceSubmittedAnswer answer = answerFor(question);
        Set<AnswerOption> selectedOptions = new HashSet<>();
        selectedOptions.add(null);
        selectedOptions.add(new AnswerOption(null, "unsaved", null, null, false, false));
        selectedOptions.add(question.getAnswerOptions().getFirst());

        answer.setSelectedOptions(selectedOptions);
        answer.addSelectedOptions(null);
        answer.addSelectedOptions(new AnswerOption(null, "unsaved", null, null, false, false));

        assertThat(answer.getSelectedOptionIds()).containsExactly(1L);
    }

    @Test
    void getSelectedOptionsRejectsUnknownReferencedIds() {
        MultipleChoiceQuestion question = questionWithOptions();
        MultipleChoiceSubmittedAnswer answer = answerFor(question);
        answer.setSelectedOptionIds(Set.of(3L));

        assertThatThrownBy(answer::getSelectedOptions).isInstanceOf(IllegalStateException.class).hasMessageContaining("missing answer option 3");
    }

    private static MultipleChoiceSubmittedAnswer answerFor(MultipleChoiceQuestion question) {
        MultipleChoiceSubmittedAnswer answer = new MultipleChoiceSubmittedAnswer();
        answer.setQuizQuestion(question);
        return answer;
    }

    private static MultipleChoiceQuestion questionWithOptions() {
        MultipleChoiceQuestion question = new MultipleChoiceQuestion();
        question.setAnswerOptions(List.of(new AnswerOption(1L, "A", null, null, true, false), new AnswerOption(2L, "B", null, null, false, false)));
        return question;
    }
}
