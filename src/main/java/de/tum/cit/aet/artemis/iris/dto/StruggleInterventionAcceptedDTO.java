package de.tum.cit.aet.artemis.iris.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * {@code 202 Accepted} body of the struggle-intervention trigger. {@code accepted} is false
 * when the run was NOT enqueued: a run is already in flight for this {@code (user, exercise)}
 * (single-flight), the student reached their Iris rate limit, or proactive is off for the course. In every
 * one of those cases {@code jobId} is null and no terminal frame is owed. The
 * {@code courseDisabled} flag is true ONLY for the deliberate course-off case, so the client can pause
 * proactive for the session (no no-AI lamp) without mis-reading a transient skip as a course
 * disable. No {@code sessionId} — none exists yet (it is materialized only on an {@code active} outcome).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StruggleInterventionAcceptedDTO(boolean accepted, boolean courseDisabled, long exerciseId, @Nullable String jobId) {
}
