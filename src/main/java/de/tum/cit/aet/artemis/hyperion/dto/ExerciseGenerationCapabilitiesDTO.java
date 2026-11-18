package de.tum.cit.aet.artemis.hyperion.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Authoritative programming-authoring actions. Eligibility is separate from temporary capacity or mutation ownership.
 *
 * @param supported         the verifier supports the repository configuration
 * @param canGenerate       the current exercise may be rewritten from a brief
 * @param canAdapt          the current exercise may be adapted in place
 * @param canCreateVariant  the exercise may be used as the source of a new variant
 * @param busy              another operation holds the exercise mutation slot
 * @param capacityAvailable a generation worker currently advertises a free slot; not a reservation
 * @param restriction       stable error key explaining why in-place authoring is unavailable, or null
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseGenerationCapabilitiesDTO(boolean supported, boolean canGenerate, boolean canAdapt, boolean canCreateVariant, boolean busy, boolean capacityAvailable,
        @Nullable String restriction) {
}
