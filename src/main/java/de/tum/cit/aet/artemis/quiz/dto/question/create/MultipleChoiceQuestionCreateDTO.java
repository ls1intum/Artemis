package de.tum.cit.aet.artemis.quiz.dto.question.create;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ScoringType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MultipleChoiceQuestionCreateDTO(@NotNull String title, String text, String hint, String explanation, double points, @NotNull ScoringType scoringType,
        boolean randomizeOrder, @NotEmpty List<AnswerOptionCreateDTO> answerOptions, boolean singleChoice) implements QuizQuestionCreateDTO {

    /**
     * Converts this DTO to a {@link MultipleChoiceQuestion} domain object.
     * <p>
     * Maps the DTO properties to the corresponding fields in the domain object and transforms the list
     * of {@link AnswerOptionCreateDTO} into a list of {@link AnswerOption} objects by invoking their
     * respective {@code toDomainObject} methods.
     *
     * @return the {@link MultipleChoiceQuestion} domain object with properties set from this DTO
     */
    public MultipleChoiceQuestion toDomainObject() {
        MultipleChoiceQuestion question = new MultipleChoiceQuestion();
        question.setTitle(title);
        question.setText(text);
        question.setHint(hint);
        question.setExplanation(explanation);
        question.setPoints(points);
        question.setScoringType(scoringType);
        question.setRandomizeOrder(randomizeOrder);
        question.setSingleChoice(singleChoice);

        List<AnswerOption> options = answerOptions.stream().map(AnswerOptionCreateDTO::toDomainObject).toList();
        question.setAnswerOptions(options);
        return question;
    }

}
