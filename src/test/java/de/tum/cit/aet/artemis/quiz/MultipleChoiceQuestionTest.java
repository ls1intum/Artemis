package de.tum.cit.aet.artemis.quiz;

import static de.tum.cit.aet.artemis.core.config.Constants.MAX_QUIZ_ANSWER_OPTION_EXPLANATION_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.MAX_QUIZ_ANSWER_OPTION_HINT_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.MAX_QUIZ_ANSWER_OPTION_TEXT_LENGTH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;
import de.tum.cit.aet.artemis.quiz.domain.AnswerOptionInput;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestionStatistic;

class MultipleChoiceQuestionTest {

    @Test
    void replaceAnswerOptionsAllocatesQuestionScopedIdsWithoutReusingDeletedIds() {
        MultipleChoiceQuestion question = questionWithTwoOptions();

        Long firstId = question.getAnswerOptions().getFirst().getId();
        Long secondId = question.getAnswerOptions().getLast().getId();
        var changeSet = question.replaceAnswerOptions(List.of(input(secondId, "B", false), input(null, "C", true)));

        assertThat(changeSet.deletedIds()).containsExactly(firstId);
        assertThat(changeSet.invalidIds()).isEmpty();
        assertThat(changeSet.addedIds()).containsExactly(3L);
        assertThat(changeSet.requiresRecalculation()).isTrue();
        assertThat(question.getAnswerOptions()).extracting(AnswerOption::getId).containsExactly(secondId, 3L);
        assertThat(question.getNextComponentId()).isEqualTo(4L);
    }

    @Test
    void replaceAnswerOptionsTombstonesReferencedDeletedOptions() {
        MultipleChoiceQuestion question = questionWithTwoOptions();

        Long firstId = question.getAnswerOptions().getFirst().getId();
        Long secondId = question.getAnswerOptions().getLast().getId();
        var changeSet = question.replaceAnswerOptions(List.of(input(secondId, "B", false)), Set.of(firstId));

        assertThat(changeSet.deletedIds()).isEmpty();
        assertThat(changeSet.invalidIds()).containsExactly(firstId);
        assertThat(changeSet.updatedIds()).containsExactly(firstId);
        assertThat(changeSet.requiresRecalculation()).isTrue();
        assertThat(question.getAnswerOptions()).extracting(AnswerOption::getId).containsExactly(secondId, firstId);
        assertThat(question.findAnswerOptionById(firstId).isInvalid()).isTrue();
        assertThat(question.findAnswerOptionById(firstId).getText()).isEqualTo("A");
        assertThat(question.getNextComponentId()).isEqualTo(3L);
    }

    @Test
    void replaceAnswerOptionsRejectsUnknownTombstoneIds() {
        MultipleChoiceQuestion question = questionWithTwoOptions();

        assertThatThrownBy(() -> question.replaceAnswerOptions(List.of(input(1L, "A", true)), Set.of(99L))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown answer option ID 99");
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
    void replaceAnswerOptionsRejectsInvalidAnswerOptionFields() {
        MultipleChoiceQuestion question = questionWithTwoOptions();

        assertThatThrownBy(() -> question.replaceAnswerOptions(List.of(input(1L, " ", false)))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Answer option text must not be blank");
        assertThatThrownBy(() -> question.replaceAnswerOptions(List.of(new AnswerOptionInput(1L, null, null, null, false, false)))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Answer option text must not be blank");
        assertThatThrownBy(() -> question.replaceAnswerOptions(List.of(new AnswerOptionInput(1L, "A", null, null, null, false)))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Answer option boolean fields must not be null");
        assertThatThrownBy(() -> question.replaceAnswerOptions(List.of(new AnswerOptionInput(1L, "A", null, null, false, null)))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Answer option boolean fields must not be null");
    }

    @Test
    void replaceAnswerOptionsRejectsFieldsThatExceedConfiguredLengths() {
        MultipleChoiceQuestion question = questionWithTwoOptions();

        assertThatThrownBy(() -> question.replaceAnswerOptions(List.of(new AnswerOptionInput(1L, tooLong(MAX_QUIZ_ANSWER_OPTION_TEXT_LENGTH), null, null, false, false))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exceeds its maximum length");
        assertThatThrownBy(() -> question.replaceAnswerOptions(List.of(new AnswerOptionInput(1L, "A", tooLong(MAX_QUIZ_ANSWER_OPTION_HINT_LENGTH), null, false, false))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exceeds its maximum length");
        assertThatThrownBy(() -> question.replaceAnswerOptions(List.of(new AnswerOptionInput(1L, "A", null, tooLong(MAX_QUIZ_ANSWER_OPTION_EXPLANATION_LENGTH), false, false))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exceeds its maximum length");
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

    @Test
    void setAnswerOptionsAdvancesHighWatermarkPastPreservedIds() {
        MultipleChoiceQuestion question = new MultipleChoiceQuestion();

        question.setAnswerOptions(List.of(new AnswerOption(7L, "A", null, null, true, false)));
        AnswerOption addedOption = question.addAnswerOption(new AnswerOption().text("B").isCorrect(false));

        assertThat(question.getAnswerOptions()).extracting(AnswerOption::getId).containsExactly(7L, 8L);
        assertThat(addedOption.getId()).isEqualTo(8L);
        assertThat(question.getNextComponentId()).isEqualTo(9L);
    }

    @Test
    void validateAnswerOptionsRejectsDuplicatePreservedIds() {
        MultipleChoiceQuestion question = new MultipleChoiceQuestion();
        question.setAnswerOptions(List.of(new AnswerOption(7L, "A", null, null, true, false), new AnswerOption(7L, "B", null, null, false, false)));

        assertThatThrownBy(question::validateAnswerOptions).isInstanceOf(IllegalStateException.class).hasMessageContaining("Answer option IDs must be non-null and unique");
    }

    @Test
    void setQuizQuestionStatisticMaintainsBackReference() {
        MultipleChoiceQuestion question = new MultipleChoiceQuestion();
        MultipleChoiceQuestionStatistic statistic = new MultipleChoiceQuestionStatistic();
        MultipleChoiceQuestionStatistic replacementStatistic = new MultipleChoiceQuestionStatistic();

        question.setQuizQuestionStatistic(statistic);

        assertThat(question.getQuizQuestionStatistic()).isSameAs(statistic);
        assertThat(statistic.getQuizQuestion()).isSameAs(question);

        statistic.setQuizQuestion(null);
        question.setQuizQuestionStatistic(statistic);

        assertThat(statistic.getQuizQuestion()).isSameAs(question);

        question.setQuizQuestionStatistic(replacementStatistic);

        assertThat(question.getQuizQuestionStatistic()).isSameAs(replacementStatistic);
        assertThat(statistic.getQuizQuestion()).isNull();
        assertThat(replacementStatistic.getQuizQuestion()).isSameAs(question);

        question.setQuizQuestionStatistic(null);

        assertThat(question.getQuizQuestionStatistic()).isNull();
        assertThat(replacementStatistic.getQuizQuestion()).isNull();
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

    private static String tooLong(int maxLength) {
        return "a".repeat(maxLength + 1);
    }
}
