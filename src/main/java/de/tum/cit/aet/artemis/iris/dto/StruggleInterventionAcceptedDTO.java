package de.tum.cit.aet.artemis.iris.dto;

import org.jspecify.annotations.Nullable;

/**
 * {@code 202 Accepted} body of the struggle-intervention trigger (spec §5.2). {@code accepted} is false
 * when the run was NOT enqueued — either a run is already in flight for this {@code (user, exercise)}
 * (single-flight, §11) or Iris is disabled for the course; then {@code jobId} is null. No {@code sessionId} —
 * none exists yet (it is materialized only on an {@code active} outcome).
 */
public record StruggleInterventionAcceptedDTO(boolean accepted, long exerciseId, @Nullable String jobId) {
}
