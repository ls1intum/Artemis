package de.tum.cit.aet.artemis.quiz.domain;

/**
 * Immutable input used by {@link MultipleChoiceQuestion} to create or update an owned answer option.
 *
 * @param id          existing question-scoped ID, or {@code null} for a new option
 * @param text        option text
 * @param hint        optional hint
 * @param explanation optional explanation
 * @param isCorrect   whether the option is correct
 * @param invalid     whether the option is excluded from scoring
 */
public record AnswerOptionInput(Long id, String text, String hint, String explanation, Boolean isCorrect, Boolean invalid) {

    public static AnswerOptionInput of(AnswerOption answerOption) {
        return new AnswerOptionInput(answerOption.getId(), answerOption.getText(), answerOption.getHint(), answerOption.getExplanation(), answerOption.isIsCorrect(),
                answerOption.rawInvalid());
    }
}
