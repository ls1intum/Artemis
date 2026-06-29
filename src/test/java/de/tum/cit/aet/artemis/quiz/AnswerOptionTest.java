package de.tum.cit.aet.artemis.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;

class AnswerOptionTest {

    @Test
    void equalsComparesSolutionFields() {
        AnswerOption correctOption = new AnswerOption(1L, "A", "hint", "explanation", true, false);
        AnswerOption hiddenSolutionOption = new AnswerOption(1L, "A", "hint", null, null, false);

        assertThat(correctOption).isNotEqualTo(hiddenSolutionOption);
        assertThat(Set.of(correctOption, hiddenSolutionOption)).hasSize(2);
    }

    @Test
    void sameStudentViewIgnoresHiddenSolutionFields() {
        AnswerOption correctOption = new AnswerOption(1L, "A", "hint", "explanation", true, false);
        AnswerOption hiddenSolutionOption = new AnswerOption(1L, "A", "hint", null, null, false);

        assertThat(correctOption.isSameStudentView(hiddenSolutionOption)).isTrue();
    }
}
