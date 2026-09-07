package de.tum.cit.aet.artemis.iris.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * {@code 202 Accepted} body of the struggle-intervention trigger. {@code accepted} is false in exactly two cases,
 * both with a null {@code jobId}, and {@code courseDisabled} tells them apart: true means proactive is off for the
 * course and the client pauses proactive for the session (no no-AI lamp); false means a run is already in flight
 * for this {@code (user, exercise)} and owes the client its terminal frame, so the client waits for that run.
 * <p>
 * Since an unaccepted body reads as "wait for the running job", a rejection that leaves no run behind cannot use
 * it: the spent Iris budget and the admission cooldown answer {@code 429}.
 * <p>
 * No {@code sessionId}: none exists yet, it is materialized only on an {@code active} outcome.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StruggleInterventionAcceptedDTO(boolean accepted, boolean courseDisabled, long exerciseId, @Nullable String jobId) {
}
