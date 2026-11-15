package de.tum.cit.aet.artemis.hyperion.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionFailureMode;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO describing the filter's judgement of one generated quiz question on one failure mode.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "The filter's judgement of one generated quiz question on one failure mode")
public record QuizQuestionFilterVerdictDTO(@NotNull @Schema(description = "The failure mode judged") QuizQuestionFailureMode mode,
        @Schema(description = "Severity of the defect from 0 (absent) to 1 (severe)") double severity,
        @Schema(description = "Whether the defect is severe enough that an instructor would reject or rewrite the question") boolean triggered,
        @Schema(description = "One-sentence justification of the severity") String justification,
        @Schema(description = "Whether this verdict asks a reviewer to look at an accepted question") boolean flagged) {

    /**
     * Creates a verdict whose flag is not derived yet.
     *
     * @param mode          the failure mode judged
     * @param severity      the severity from 0 to 1
     * @param triggered     whether the defect is severe enough to reject or rewrite the question
     * @param justification the justification of the severity
     */
    public QuizQuestionFilterVerdictDTO(QuizQuestionFailureMode mode, double severity, boolean triggered, String justification) {
        this(mode, severity, triggered, justification, false);
    }
}
