package de.tum.cit.aet.artemis.quiz.dto.question.create;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ScoringType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MultipleChoiceQuestionCreateDTO(@NotNull String title, String text, String hint, String explanation, @NotNull @Positive Double points,
        @NotNull ScoringType scoringType, Boolean randomizeOrder, @NotEmpty List<@Valid AnswerOptionCreateDTO> answerOptions, @NotNull Boolean singleChoice)
        implements QuizQuestionCreateDTO {

    /**
     * Creates a {@link MultipleChoiceQuestionCreateDTO} from the given {@link MultipleChoiceQuestion} domain object.
     * <p>
     * Maps the domain object's properties to the corresponding DTO fields and transforms the list
     * of {@link AnswerOption} into a list of {@link AnswerOptionCreateDTO} by invoking their respective
     * {@code of} methods.
     *
     * @param question the {@link MultipleChoiceQuestion} domain object to convert
     * @return the {@link MultipleChoiceQuestionCreateDTO} with properties and child DTOs set from the domain object
     */
    public static MultipleChoiceQuestionCreateDTO of(MultipleChoiceQuestion question) {
        List<AnswerOptionCreateDTO> optionDTOs = question.getAnswerOptions().stream().map(AnswerOptionCreateDTO::of).toList();
        return new MultipleChoiceQuestionCreateDTO(question.getTitle(), question.getText(), question.getHint(), question.getExplanation(), question.getPoints(),
                question.getScoringType(), question.isRandomizeOrder(), optionDTOs, question.isSingleChoice());
    }

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
        question.setRandomizeOrder(randomizeOrder != null ? randomizeOrder : Boolean.FALSE);
        question.setSingleChoice(singleChoice);

        List<AnswerOption> options = answerOptions.stream().map(AnswerOptionCreateDTO::toDomainObject).toList();
        question.setAnswerOptions(options);
        return question;
    }
}
