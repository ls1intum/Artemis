package de.tum.cit.aet.artemis.hyperion.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Discriminated response for a single quiz question refinement.
 * On success the {@code type} field is {@code "success"} and {@link SuccessDTO} fields are populated.
 * On per-question failure the {@code type} field is {@code "failure"} and {@link FailureDTO} fields are populated.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
@JsonSubTypes({ @JsonSubTypes.Type(value = QuizQuestionRefinementResponseDTO.SuccessDTO.class, name = "success"),
        @JsonSubTypes.Type(value = QuizQuestionRefinementResponseDTO.FailureDTO.class, name = "failure") })
@Schema(description = "Discriminated response for a single quiz question refinement, either a success with the refined question or a failure with an error message", oneOf = {
        QuizQuestionRefinementResponseDTO.SuccessDTO.class, QuizQuestionRefinementResponseDTO.FailureDTO.class }, discriminatorProperty = "type")
public sealed interface QuizQuestionRefinementResponseDTO permits QuizQuestionRefinementResponseDTO.SuccessDTO, QuizQuestionRefinementResponseDTO.FailureDTO {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description = "Successful refinement result containing the refined question and an explanation of the changes")
    record SuccessDTO(@NotNull @Valid @Schema(description = "The refined quiz question") GeneratedQuizQuestionDTO question,
            @NotBlank @Schema(description = "Brief explanation of what was changed during refinement") String reasoning, @JsonProperty("type") String type)
            implements QuizQuestionRefinementResponseDTO {

        public SuccessDTO(GeneratedQuizQuestionDTO question, String reasoning) {
            this(question, reasoning, "success");
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description = "Failed refinement result containing an error message")
    record FailureDTO(@NotBlank @Schema(description = "Error message describing why the refinement failed") String error, @JsonProperty("type") String type)
            implements QuizQuestionRefinementResponseDTO {

        public FailureDTO(String error) {
            this(error, "failure");
        }
    }
}
