package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One selectable generation effort profile, as offered to an instructor.
 * <p>
 * Carries no model id, decoding parameter, or budget: those are admin-owned, so widening the selectable set never widens what a caller learns about the deployment.
 *
 * @param name  the stable identifier a request sends back as {@code effortProfile}
 * @param label the instructor-facing name
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseGenerationEffortProfileDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String label) {
}
