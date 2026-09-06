package de.tum.cit.aet.artemis.quiz.dto.question;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;

/**
 * The full post-publish projection of a ShortAnswer quiz question. Both components are unwrapped, so the payload is one flat
 * object carrying the shared question fields (including the {@code type} discriminator) and the ShortAnswer-specific ones.
 *
 * @param quizQuestionBaseDTO               the fields shared by every question type
 * @param explanation                       the explanation shown once solutions are published
 * @param shortAnswerQuestionWithMappingDTO the ShortAnswer-specific fields, including the solution
 */
@Schema(requiredProperties = { "type" })
@SchemaProperty(name = "type", schema = @Schema(type = "string", allowableValues = { "short-answer" }, defaultValue = "short-answer"))
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerQuizQuestionWithSolutionDTO(@JsonUnwrapped QuizQuestionBaseDTO quizQuestionBaseDTO, String explanation,
        @JsonUnwrapped ShortAnswerQuestionWithMappingDTO shortAnswerQuestionWithMappingDTO) implements QuizQuestionWithSolutionDTO {
}
