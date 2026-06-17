package de.tum.cit.aet.artemis.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;
import de.tum.cit.aet.artemis.quiz.domain.AnswerOptionInput;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;

class MultipleChoiceQuestionTest {

    @Test
    void replaceAnswerOptionsAllocatesQuestionScopedIdsWithoutReusingDeletedIds() {
        MultipleChoiceQuestion question = questionWithTwoOptions();

        Long firstId = question.getAnswerOptions().getFirst().getId();
        Long secondId = question.getAnswerOptions().getLast().getId();
        var changeSet = question.replaceAnswerOptions(List.of(input(secondId, "B", false), input(null, "C", true)));

        assertThat(changeSet.deletedIds()).containsExactly(firstId);
        assertThat(changeSet.addedIds()).containsExactly(3L);
        assertThat(changeSet.requiresRecalculation()).isTrue();
        assertThat(question.getAnswerOptions()).extracting(AnswerOption::getId).containsExactly(secondId, 3L);
        assertThat(question.getNextComponentId()).isEqualTo(4L);
    }

    @Test
    void replaceAnswerOptionsOnlyRequestsRecalculationForScoringChanges() {
        MultipleChoiceQuestion question = questionWithTwoOptions();
        Long firstId = question.getAnswerOptions().getFirst().getId();
        Long secondId = question.getAnswerOptions().getLast().getId();

        var textAndOrderChange = question.replaceAnswerOptions(List.of(input(secondId, "B changed", false), input(firstId, "A", true)));

        assertThat(textAndOrderChange.updatedIds()).containsExactly(secondId);
        assertThat(textAndOrderChange.requiresRecalculation()).isFalse();

        var correctnessChange = question.replaceAnswerOptions(List.of(input(secondId, "B changed", true), input(firstId, "A", true)));

        assertThat(correctnessChange.updatedIds()).containsExactly(secondId);
        assertThat(correctnessChange.requiresRecalculation()).isTrue();
    }

    @Test
    void answerOptionsCanOnlyBeReplacedThroughTheAggregateRoot() {
        MultipleChoiceQuestion question = questionWithTwoOptions();

        assertThatThrownBy(() -> question.getAnswerOptions().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> question.replaceAnswerOptions(List.of(input(99L, "unknown", false)))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown answer option ID 99");
        assertThatThrownBy(() -> question.replaceAnswerOptions(List.of(input(1L, "A", true), input(1L, "duplicate", false)))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate answer option ID 1");
    }

    @Test
    void addAnswerOptionIgnoresIncomingIds() {
        MultipleChoiceQuestion question = new MultipleChoiceQuestion();
        AnswerOption externallyIdentifiedOption = new AnswerOption(42L, "A", null, null, true, false);

        question.addAnswerOption(externallyIdentifiedOption);

        assertThat(question.getAnswerOptions().getFirst().getId()).isEqualTo(1L);
        assertThat(question.getNextComponentId()).isEqualTo(2L);
    }

    @Test
    void setAnswerOptionsAcceptsSolutionFreeResponsePayloadsWithoutMakingThemPersistable() {
        MultipleChoiceQuestion question = new MultipleChoiceQuestion();

        question.setAnswerOptions(List.of(new AnswerOption(7L, "A", null, null, null, false)));

        assertThat(question.getAnswerOptions()).extracting(AnswerOption::getId).containsExactly(7L);
        assertThat(question.getNextComponentId()).isEqualTo(8L);
        assertThatThrownBy(question::validateAnswerOptions).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("boolean fields must not be null");
    }

    private static MultipleChoiceQuestion questionWithTwoOptions() {
        MultipleChoiceQuestion question = new MultipleChoiceQuestion();
        question.addAnswerOption(new AnswerOption().text("A").isCorrect(true));
        question.addAnswerOption(new AnswerOption().text("B").isCorrect(false));
        return question;
    }

    private static AnswerOptionInput input(Long id, String text, boolean correct) {
        return new AnswerOptionInput(id, text, null, null, correct, false);
    }
}
