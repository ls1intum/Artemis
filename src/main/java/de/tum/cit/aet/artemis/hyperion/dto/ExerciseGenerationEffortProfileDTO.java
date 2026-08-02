package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One selectable generation effort profile, as offered to an instructor.
 * <p>
 * Deliberately carries no model id, decoding parameter, or budget: those are admin-owned procurement details, an instructor cannot act on them, and what actually ran is attested
 * separately through the run's terminal usage, provider request ids, and spans. Keeping the two concerns apart means widening the selectable set never widens what a caller learns
 * about the deployment.
 *
 * @param name  the stable identifier a request sends back as {@code effortProfile}
 * @param label the instructor-facing name
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseGenerationEffortProfileDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String label) {
}
