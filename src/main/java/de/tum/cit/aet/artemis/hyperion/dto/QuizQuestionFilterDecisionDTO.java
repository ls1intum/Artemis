package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO describing the filter's accept/reject decision for one generated quiz question together with the verdicts it was derived from.
 * <p>
 * {@code accepted} is exactly {@code aggregateScore >= threshold}, where the aggregate is one minus the worst severity among the gating modes.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "The filter's decision for one generated quiz question and the per-mode verdicts behind it")
public record QuizQuestionFilterDecisionDTO(@Schema(description = "Whether the question passed the filter") boolean accepted,
        @Schema(description = "One minus the worst severity among the gating failure modes, from 0 to 1; higher is better") double aggregateScore,
        @NotEmpty @Schema(description = "One verdict per failure mode") List<@Valid QuizQuestionFilterVerdictDTO> verdicts,
        @Schema(description = "The filter's overall rationale") String rationale) {
}
