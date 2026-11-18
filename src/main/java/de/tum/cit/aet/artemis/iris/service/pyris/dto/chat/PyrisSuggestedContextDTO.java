package de.tum.cit.aet.artemis.iris.service.pyris.dto.chat;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;

/**
 * Context switch suggested by the Pyris chat pipeline (automatic context switching).
 * The agent requests it via the {@code switch_chat_context} tool; it arrives on the
 * final result status update and is applied via
 * {@link de.tum.cit.aet.artemis.iris.service.session.IrisChatSessionService#applyContextChange}.
 *
 * @param mode     the target chat mode
 * @param entityId the target entity id (exerciseId / lectureId / courseId depending on mode)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisSuggestedContextDTO(@Nullable IrisChatMode mode, @Nullable Long entityId) {
}
