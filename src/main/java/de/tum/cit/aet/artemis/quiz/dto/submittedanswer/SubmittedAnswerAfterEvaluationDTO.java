package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionWithSolutionDTO;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;

@Schema(discriminatorProperty = "type", discriminatorMapping = { @DiscriminatorMapping(value = "multiple-choice", schema = MultipleChoiceSubmittedAnswerAfterEvaluationDTO.class),
        @DiscriminatorMapping(value = "drag-and-drop", schema = DragAndDropSubmittedAnswerAfterEvaluationDTO.class),
        @DiscriminatorMapping(value = "short-answer", schema = ShortAnswerSubmittedAnswerAfterEvaluationDTO.class) }, oneOf = {
                MultipleChoiceSubmittedAnswerAfterEvaluationDTO.class, DragAndDropSubmittedAnswerAfterEvaluationDTO.class, ShortAnswerSubmittedAnswerAfterEvaluationDTO.class })

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmittedAnswerAfterEvaluationDTO(Long id, Double scoreInPoints, QuizQuestionWithSolutionDTO quizQuestion,
        @JsonUnwrapped MultipleChoiceSubmittedAnswerWithSolutionDTO multipleChoiceSubmittedAnswer, @JsonUnwrapped DragAndDropSubmittedAnswerDTO dragAndDropSubmittedAnswer,
        @JsonUnwrapped ShortAnswerSubmittedAnswerDTO shortAnswerSubmittedAnswer) {

    /**
     * Creates a SubmittedAnswerAfterEvaluationDTO object from a SubmittedAnswer object.
     *
     * @param submittedAnswer the SubmittedAnswer object
     * @return the created SubmittedAnswerAfterEvaluationDTO object
     */
    public static SubmittedAnswerAfterEvaluationDTO of(final SubmittedAnswer submittedAnswer) {
        MultipleChoiceSubmittedAnswerWithSolutionDTO multipleChoiceSubmittedAnswer = null;
        DragAndDropSubmittedAnswerDTO dragAndDropSubmittedAnswer = null;
        ShortAnswerSubmittedAnswerDTO shortAnswerSubmittedAnswer = null;
        switch (submittedAnswer) {
            case MultipleChoiceSubmittedAnswer multipleChoiceSubmittedAnswer1 ->
                multipleChoiceSubmittedAnswer = MultipleChoiceSubmittedAnswerWithSolutionDTO.of(multipleChoiceSubmittedAnswer1);
            case DragAndDropSubmittedAnswer dragAndDropSubmittedAnswer1 -> dragAndDropSubmittedAnswer = DragAndDropSubmittedAnswerDTO.of(dragAndDropSubmittedAnswer1);
            case ShortAnswerSubmittedAnswer shortAnswerSubmittedAnswer1 -> shortAnswerSubmittedAnswer = ShortAnswerSubmittedAnswerDTO.of(shortAnswerSubmittedAnswer1);
            default -> {
            }
        }
        return new SubmittedAnswerAfterEvaluationDTO(submittedAnswer.getId(), submittedAnswer.getScoreInPoints(), QuizQuestionWithSolutionDTO.of(submittedAnswer.getQuizQuestion()),
                multipleChoiceSubmittedAnswer, dragAndDropSubmittedAnswer, shortAnswerSubmittedAnswer);

    }

}

// These definitions are used for OpenAPI generation because polymorphic types with @JsonUnwrapped do not work here
@Schema(requiredProperties = { "type" })
@SchemaProperty(name = "type", schema = @Schema(type = "string", allowableValues = { "multiple-choice" }, defaultValue = "multiple-choice"))
@JsonInclude(JsonInclude.Include.NON_EMPTY)
record MultipleChoiceSubmittedAnswerAfterEvaluationDTO(Long id, Double scoreInPoints, QuizQuestionWithSolutionDTO quizQuestion,
        @JsonUnwrapped MultipleChoiceSubmittedAnswerWithSolutionDTO multipleChoiceSubmittedAnswer) {
}

@Schema(requiredProperties = { "type" })
@SchemaProperty(name = "type", schema = @Schema(type = "string", allowableValues = { "drag-and-drop" }, defaultValue = "drag-and-drop"))
@JsonInclude(JsonInclude.Include.NON_EMPTY)
record DragAndDropSubmittedAnswerAfterEvaluationDTO(Long id, Double scoreInPoints, QuizQuestionWithSolutionDTO quizQuestion,
        @JsonUnwrapped DragAndDropSubmittedAnswerDTO dragAndDropSubmittedAnswer) {
}

@Schema(requiredProperties = { "type" })
@SchemaProperty(name = "type", schema = @Schema(type = "string", allowableValues = { "short-answer" }, defaultValue = "short-answer"))
@JsonInclude(JsonInclude.Include.NON_EMPTY)
record ShortAnswerSubmittedAnswerAfterEvaluationDTO(Long id, Double scoreInPoints, QuizQuestionWithSolutionDTO quizQuestion,
        @JsonUnwrapped ShortAnswerSubmittedAnswerDTO shortAnswerSubmittedAnswer) {
}
