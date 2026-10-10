package de.tum.cit.aet.artemis.hyperion.dto;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Administrator-only ownership evidence for explicit recovery after the owning JVM has been stopped. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseGenerationWedgedSlotDTO(long exerciseId, String token, String kind, @Nullable String ownerNodeId, Instant startedAt, boolean ownerLeftCluster) {
}
