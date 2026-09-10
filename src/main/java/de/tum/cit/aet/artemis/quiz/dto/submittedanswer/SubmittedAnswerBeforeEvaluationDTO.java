package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionWithoutSolutionDTO;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;

@Schema(discriminatorProperty = "type", discriminatorMapping = { @DiscriminatorMapping(value = "multiple-choice", schema = MultipleChoiceSubmittedAnswerBeforeEvaluationDTO.class),
        @DiscriminatorMapping(value = "drag-and-drop", schema = DragAndDropSubmittedAnswerBeforeEvaluationDTO.class),
        @DiscriminatorMapping(value = "short-answer", schema = ShortAnswerSubmittedAnswerBeforeEvaluationDTO.class) }, oneOf = {
                MultipleChoiceSubmittedAnswerBeforeEvaluationDTO.class, DragAndDropSubmittedAnswerBeforeEvaluationDTO.class, ShortAnswerSubmittedAnswerBeforeEvaluationDTO.class })
// Note: Only one of the three submitted answers will be non-null depending on the question type
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmittedAnswerBeforeEvaluationDTO(Long id, QuizQuestionWithoutSolutionDTO quizQuestion,
        @JsonUnwrapped MultipleChoiceSubmittedAnswerWithoutSolutionDTO multipleChoiceSubmittedAnswer, @JsonUnwrapped DragAndDropSubmittedAnswerDTO dragAndDropSubmittedAnswer,
        @JsonUnwrapped ShortAnswerSubmittedAnswerDTO shortAnswerSubmittedAnswer) {

    /**
     * Creates a SubmittedAnswerBeforeEvaluationDTO object from a SubmittedAnswer object.
     *
     * @param submittedAnswer the SubmittedAnswer object
     * @return the created SubmittedAnswerBeforeEvaluationDTO object
     */
    public static SubmittedAnswerBeforeEvaluationDTO of(final SubmittedAnswer submittedAnswer) {
        MultipleChoiceSubmittedAnswerWithoutSolutionDTO multipleChoiceSubmittedAnswer = null;
        DragAndDropSubmittedAnswerDTO dragAndDropSubmittedAnswer = null;
        ShortAnswerSubmittedAnswerDTO shortAnswerSubmittedAnswer = null;
        switch (submittedAnswer) {
            case MultipleChoiceSubmittedAnswer multipleChoiceSubmittedAnswer1 ->
                multipleChoiceSubmittedAnswer = MultipleChoiceSubmittedAnswerWithoutSolutionDTO.of(multipleChoiceSubmittedAnswer1);
            case DragAndDropSubmittedAnswer dragAndDropSubmittedAnswer1 -> dragAndDropSubmittedAnswer = DragAndDropSubmittedAnswerDTO.of(dragAndDropSubmittedAnswer1);
            case ShortAnswerSubmittedAnswer shortAnswerSubmittedAnswer1 -> shortAnswerSubmittedAnswer = ShortAnswerSubmittedAnswerDTO.of(shortAnswerSubmittedAnswer1);
            default -> {
            }
        }
        return new SubmittedAnswerBeforeEvaluationDTO(submittedAnswer.getId(), QuizQuestionWithoutSolutionDTO.of(submittedAnswer.getQuizQuestion()), multipleChoiceSubmittedAnswer,
                dragAndDropSubmittedAnswer, shortAnswerSubmittedAnswer);

    }

}

// These definitions are used for OpenAPI generation because polymorphic types with @JsonUnwrapped do not work here
@Schema(requiredProperties = { "type" })
@SchemaProperty(name = "type", schema = @Schema(type = "string", allowableValues = { "multiple-choice" }, defaultValue = "multiple-choice"))
@JsonInclude(JsonInclude.Include.NON_EMPTY)
record MultipleChoiceSubmittedAnswerBeforeEvaluationDTO(Long id, QuizQuestionWithoutSolutionDTO quizQuestion,
        @JsonUnwrapped MultipleChoiceSubmittedAnswerWithoutSolutionDTO multipleChoiceSubmittedAnswer) {
}

@Schema(requiredProperties = { "type" })
@SchemaProperty(name = "type", schema = @Schema(type = "string", allowableValues = { "drag-and-drop" }, defaultValue = "drag-and-drop"))
@JsonInclude(JsonInclude.Include.NON_EMPTY)
record DragAndDropSubmittedAnswerBeforeEvaluationDTO(Long id, QuizQuestionWithoutSolutionDTO quizQuestion,
        @JsonUnwrapped DragAndDropSubmittedAnswerDTO dragAndDropSubmittedAnswer) {
}

@Schema(requiredProperties = { "type" })
@SchemaProperty(name = "type", schema = @Schema(type = "string", allowableValues = { "short-answer" }, defaultValue = "short-answer"))
@JsonInclude(JsonInclude.Include.NON_EMPTY)
record ShortAnswerSubmittedAnswerBeforeEvaluationDTO(Long id, QuizQuestionWithoutSolutionDTO quizQuestion,
        @JsonUnwrapped ShortAnswerSubmittedAnswerDTO shortAnswerSubmittedAnswer) {
}
