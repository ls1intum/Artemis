package de.tum.cit.aet.artemis.quiz.dto.question;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import tools.jackson.databind.annotation.JsonDeserialize;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;

@Schema(discriminatorProperty = "type", discriminatorMapping = { @DiscriminatorMapping(value = "multiple-choice", schema = MultipleChoiceQuizQuestionWithoutSolutionDTO.class),
        @DiscriminatorMapping(value = "drag-and-drop", schema = DragAndDropQuizQuestionWithoutSolutionDTO.class),
        @DiscriminatorMapping(value = "short-answer", schema = ShortAnswerQuizQuestionWithoutSolutionDTO.class) }, oneOf = { MultipleChoiceQuizQuestionWithoutSolutionDTO.class,
                DragAndDropQuizQuestionWithoutSolutionDTO.class, ShortAnswerQuizQuestionWithoutSolutionDTO.class })
@JsonInclude(JsonInclude.Include.NON_EMPTY)
// Self-referencing override: Jackson inherits class-level annotations from implemented interfaces, so without this,
// deserializing a declared QuizQuestionWithoutSolutionDTO target would pick up QuizQuestionForExamDTO's
// @JsonDeserialize(as = QuizQuestionWithSolutionDTO.class) and fail ("not a subtype"). This restores the identity
// mapping for this concrete type without touching the interface's default, which other sites still rely on.
@JsonDeserialize(as = QuizQuestionWithoutSolutionDTO.class)
// Note: Only one of the three questions will be non-null depending on the question type
public record QuizQuestionWithoutSolutionDTO(@JsonUnwrapped QuizQuestionBaseDTO quizQuestionBaseDTO,
        @Nullable @JsonUnwrapped MultipleChoiceQuestionWithoutSolutionDTO multipleChoiceQuestionWithoutSolutionDTO,
        @Nullable @JsonUnwrapped DragAndDropQuestionWithoutSolutionDTO dragAndDropQuestionWithoutSolutionDTO,
        @Nullable @JsonUnwrapped ShortAnswerQuestionWithoutMappingDTO shortAnswerQuestionWithoutMappingDTO) implements QuizQuestionForExamDTO {

    /**
     * Creates a QuizQuestionWithoutSolutionDTO object from a QuizQuestion object.
     *
     * @param quizQuestion the QuizQuestion object
     * @return the created QuizQuestionWithoutSolutionDTO object
     */
    public static QuizQuestionWithoutSolutionDTO of(final QuizQuestion quizQuestion) {
        QuizQuestionBaseDTO quizQuestionBaseDTO = QuizQuestionBaseDTO.of(quizQuestion);
        MultipleChoiceQuestionWithoutSolutionDTO multipleChoiceQuestionDTO = null;
        DragAndDropQuestionWithoutSolutionDTO dragAndDropQuestionDTO = null;
        ShortAnswerQuestionWithoutMappingDTO shortAnswerQuestionDTO = null;
        switch (quizQuestion) {
            case MultipleChoiceQuestion multipleChoiceQuestion -> multipleChoiceQuestionDTO = MultipleChoiceQuestionWithoutSolutionDTO.of(multipleChoiceQuestion);
            case DragAndDropQuestion dragAndDropQuestion -> dragAndDropQuestionDTO = DragAndDropQuestionWithoutSolutionDTO.of(dragAndDropQuestion);
            case ShortAnswerQuestion shortAnswerQuestion -> shortAnswerQuestionDTO = ShortAnswerQuestionWithoutMappingDTO.of(shortAnswerQuestion);
            default -> {
                // TODO: Potentially figure out what to do here
            }
        }
        return new QuizQuestionWithoutSolutionDTO(quizQuestionBaseDTO, multipleChoiceQuestionDTO, dragAndDropQuestionDTO, shortAnswerQuestionDTO);
    }

}

// These definitions are used for OpenAPI generation because polymorphic types with @JsonUnwrapped do not work here
// The pinned single-value "type" enum is what makes the oneOf branches mutually exclusive.
@Schema(requiredProperties = { "type" })
@SchemaProperty(name = "type", schema = @Schema(type = "string", allowableValues = { "multiple-choice" }, defaultValue = "multiple-choice"))
@JsonInclude(JsonInclude.Include.NON_EMPTY)
record MultipleChoiceQuizQuestionWithoutSolutionDTO(@JsonUnwrapped QuizQuestionBaseDTO quizQuestionBaseDTO,
        @JsonUnwrapped MultipleChoiceQuestionWithoutSolutionDTO multipleChoiceQuestionWithoutSolutionDTO) {
}

@Schema(requiredProperties = { "type" })
@SchemaProperty(name = "type", schema = @Schema(type = "string", allowableValues = { "drag-and-drop" }, defaultValue = "drag-and-drop"))
@JsonInclude(JsonInclude.Include.NON_EMPTY)
record DragAndDropQuizQuestionWithoutSolutionDTO(@JsonUnwrapped QuizQuestionBaseDTO quizQuestionBaseDTO,
        @JsonUnwrapped DragAndDropQuestionWithoutSolutionDTO dragAndDropQuestionWithoutSolutionDTO) {
}

@Schema(requiredProperties = { "type" })
@SchemaProperty(name = "type", schema = @Schema(type = "string", allowableValues = { "short-answer" }, defaultValue = "short-answer"))
@JsonInclude(JsonInclude.Include.NON_EMPTY)
record ShortAnswerQuizQuestionWithoutSolutionDTO(@JsonUnwrapped QuizQuestionBaseDTO quizQuestionBaseDTO,
        @JsonUnwrapped ShortAnswerQuestionWithoutMappingDTO shortAnswerQuestionWithoutMappingDTO) {
}
