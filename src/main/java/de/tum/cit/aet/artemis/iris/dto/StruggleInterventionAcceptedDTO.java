package de.tum.cit.aet.artemis.iris.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * {@code 202 Accepted} body of the struggle-intervention trigger (spec §5.2). {@code accepted} is false
 * when the run was NOT enqueued — either a run is already in flight for this {@code (user, exercise)}
 * (single-flight, §11) or proactive is off for the course (spec §13); then {@code jobId} is null. The
 * {@code courseDisabled} flag is true ONLY for the deliberate course-off case, so the client can pause
 * proactive for the session (no no-AI lamp) without mis-reading a transient in-flight skip as a course
 * disable. No {@code sessionId} — none exists yet (it is materialized only on an {@code active} outcome).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StruggleInterventionAcceptedDTO(boolean accepted, boolean courseDisabled, long exerciseId, @Nullable String jobId) {
}
