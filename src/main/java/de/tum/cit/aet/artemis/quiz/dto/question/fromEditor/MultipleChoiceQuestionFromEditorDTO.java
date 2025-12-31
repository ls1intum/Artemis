package de.tum.cit.aet.artemis.quiz.dto.question.fromEditor;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ScoringType;

/**
 * DTO for multiple choice questions in the editor context.
 * Supports both creating new questions (id is null) and updating existing questions (id is non-null).
 *
 * @param id             the ID of the question, null for new questions
 * @param title          the title of the question
 * @param text           the question text
 * @param hint           the hint for the question
 * @param explanation    the explanation for the question
 * @param points         the points for the question
 * @param scoringType    the scoring type
 * @param randomizeOrder whether to randomize answer order
 * @param answerOptions  the list of answer options
 * @param singleChoice   whether only one answer can be selected
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MultipleChoiceQuestionFromEditorDTO(Long id, @NotNull String title, String text, String hint, String explanation, @NotNull @Positive Double points,
        @NotNull ScoringType scoringType, Boolean randomizeOrder, @NotEmpty List<@Valid AnswerOptionFromEditorDTO> answerOptions, @NotNull Boolean singleChoice)
        implements QuizQuestionFromEditorDTO {

    /**
     * Creates a MultipleChoiceQuestionFromEditorDTO from the given MultipleChoiceQuestion domain object.
     *
     * @param question the question to convert
     * @return the corresponding DTO
     */
    public static MultipleChoiceQuestionFromEditorDTO of(MultipleChoiceQuestion question) {
        List<AnswerOptionFromEditorDTO> optionDTOs = question.getAnswerOptions().stream().map(AnswerOptionFromEditorDTO::of).toList();
        return new MultipleChoiceQuestionFromEditorDTO(question.getId(), question.getTitle(), question.getText(), question.getHint(), question.getExplanation(),
                question.getPoints(), question.getScoringType(), question.isRandomizeOrder(), optionDTOs, question.isSingleChoice());
    }

    /**
     * Creates a new MultipleChoiceQuestion domain object from this DTO.
     *
     * @return a new MultipleChoiceQuestion domain object
     */
    @Override
    public MultipleChoiceQuestion toDomainObject() {
        MultipleChoiceQuestion question = new MultipleChoiceQuestion();
        question.setId(id);
        question.setTitle(title);
        question.setText(text);
        question.setHint(hint);
        question.setExplanation(explanation);
        question.setPoints(points);
        question.setScoringType(scoringType);
        question.setRandomizeOrder(randomizeOrder != null ? randomizeOrder : Boolean.FALSE);
        question.setSingleChoice(singleChoice);

        List<AnswerOption> options = answerOptions.stream().map(AnswerOptionFromEditorDTO::toDomainObject).toList();
        question.setAnswerOptions(options);
        return question;
    }
}
