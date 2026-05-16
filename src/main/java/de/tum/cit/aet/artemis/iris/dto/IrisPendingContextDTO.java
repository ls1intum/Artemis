package de.tum.cit.aet.artemis.iris.dto;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;

/**
 * Pending context change forwarded with a new user message so the server can apply the switch
 * atomically (CTXSWAP marker, then user message) in one round trip.
 *
 * @param mode     the new chat mode
 * @param entityId the new entity id (exerciseId / lectureId / courseId depending on mode)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisPendingContextDTO(@NonNull IrisChatMode mode, long entityId) {
}
