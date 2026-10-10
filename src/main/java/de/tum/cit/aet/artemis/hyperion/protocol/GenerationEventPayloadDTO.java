package de.tum.cit.aet.artemis.hyperion.protocol;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Hyperion-specific progress inside the generic execution transport. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GenerationEventPayloadDTO(@Nullable GenerationActivity activity, @Nullable GenerationProgress progress) {
}
