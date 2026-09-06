package de.tum.cit.aet.artemis.quiz.dto.question;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;

@Schema(discriminatorProperty = "type", discriminatorMapping = { @DiscriminatorMapping(value = "multiple-choice", schema = MultipleChoiceQuizQuestionWithSolutionDTO.class),
        @DiscriminatorMapping(value = "drag-and-drop", schema = DragAndDropQuizQuestionWithSolutionDTO.class),
        @DiscriminatorMapping(value = "short-answer", schema = ShortAnswerQuizQuestionWithSolutionDTO.class) }, oneOf = { MultipleChoiceQuizQuestionWithSolutionDTO.class,
                DragAndDropQuizQuestionWithSolutionDTO.class, ShortAnswerQuizQuestionWithSolutionDTO.class })
@JsonInclude(JsonInclude.Include.NON_EMPTY)
// Note: Only one of the three questions will be non-null depending on the question type
public record QuizQuestionWithSolutionDTO(@JsonUnwrapped QuizQuestionBaseDTO quizQuestionBaseDTO, String explanation,
        @Nullable @JsonUnwrapped MultipleChoiceQuestionWithSolutionDTO multipleChoiceQuestionWithSolutionDTO,
        @Nullable @JsonUnwrapped DragAndDropQuestionWithSolutionDTO dragAndDropQuestionWithSolutionDTO,
        @Nullable @JsonUnwrapped ShortAnswerQuestionWithMappingDTO shortAnswerQuestionWithMappingDTO) implements QuizQuestionForExamDTO {

    /**
     * Creates a QuizQuestionWithSolutionDTO object from a QuizQuestion object.
     *
     * @param quizQuestion the QuizQuestion object
     * @return the created QuizQuestionWithSolutionDTO object
     */
    public static QuizQuestionWithSolutionDTO of(final QuizQuestion quizQuestion) {
        QuizQuestionBaseDTO quizQuestionBaseDTO = QuizQuestionBaseDTO.of(quizQuestion);
        MultipleChoiceQuestionWithSolutionDTO multipleChoiceQuestionDTO = null;
        DragAndDropQuestionWithSolutionDTO dragAndDropQuestionDTO = null;
        ShortAnswerQuestionWithMappingDTO shortAnswerQuestionDTO = null;
        switch (quizQuestion) {
            case MultipleChoiceQuestion multipleChoiceQuestion -> multipleChoiceQuestionDTO = MultipleChoiceQuestionWithSolutionDTO.of(multipleChoiceQuestion);
            case DragAndDropQuestion dragAndDropQuestion -> dragAndDropQuestionDTO = DragAndDropQuestionWithSolutionDTO.of(dragAndDropQuestion);
            case ShortAnswerQuestion shortAnswerQuestion -> shortAnswerQuestionDTO = ShortAnswerQuestionWithMappingDTO.of(shortAnswerQuestion);
            default -> {
                // TODO: Potentially figure out what to do here
            }
        }
        return new QuizQuestionWithSolutionDTO(quizQuestionBaseDTO, quizQuestion.getExplanation(), multipleChoiceQuestionDTO, dragAndDropQuestionDTO, shortAnswerQuestionDTO);
    }
}

// These definitions are used for OpenAPI generation because polymorphic types with @JsonUnwrapped do not work here
@Schema(requiredProperties = { "type" })
@SchemaProperty(name = "type", schema = @Schema(type = "string", allowableValues = { "multiple-choice" }, defaultValue = "multiple-choice"))
@JsonInclude(JsonInclude.Include.NON_EMPTY)
record MultipleChoiceQuizQuestionWithSolutionDTO(@JsonUnwrapped QuizQuestionBaseDTO quizQuestionBaseDTO, String explanation,
        @JsonUnwrapped MultipleChoiceQuestionWithSolutionDTO multipleChoiceQuestionWithSolutionDTO) {
}

@Schema(requiredProperties = { "type" })
@SchemaProperty(name = "type", schema = @Schema(type = "string", allowableValues = { "drag-and-drop" }, defaultValue = "drag-and-drop"))
@JsonInclude(JsonInclude.Include.NON_EMPTY)
record DragAndDropQuizQuestionWithSolutionDTO(@JsonUnwrapped QuizQuestionBaseDTO quizQuestionBaseDTO, String explanation,
        @JsonUnwrapped DragAndDropQuestionWithSolutionDTO dragAndDropQuestionWithSolutionDTO) {
}

@Schema(requiredProperties = { "type" })
@SchemaProperty(name = "type", schema = @Schema(type = "string", allowableValues = { "short-answer" }, defaultValue = "short-answer"))
@JsonInclude(JsonInclude.Include.NON_EMPTY)
record ShortAnswerQuizQuestionWithSolutionDTO(@JsonUnwrapped QuizQuestionBaseDTO quizQuestionBaseDTO, String explanation,
        @JsonUnwrapped ShortAnswerQuestionWithMappingDTO shortAnswerQuestionWithMappingDTO) {
}
