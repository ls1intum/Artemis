package de.tum.cit.aet.artemis.iris.domain.message;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;

/**
 * Typed JSON payload of a {@link IrisMessageSender#CTXSWAP} marker message. The field names and the
 * {@link #transition} values are a contract shared with the Iris client and the Pyris pipeline; keep them in sync.
 * <p>
 * ADDED and CHANGED markers describe the newly selected entity. A REMOVED marker has no target entity, so
 * {@link #entityMode}, {@link #entityId} and {@link #name} stay empty and are dropped from the JSON via {@link JsonInclude}.
 *
 * @param transition the kind of transition
 * @param entityMode the target entity's chat mode (for the client icon); empty for REMOVED
 * @param entityId   the target entity's id; empty for REMOVED
 * @param name       the target entity's title; empty for REMOVED or when it cannot be resolved
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisContextSwitchMarker(IrisContextSwitchTransition transition, @Nullable IrisChatMode entityMode, @Nullable Long entityId, String name) {

    /**
     * Builds the marker for a context switch. {@code previousMode} only distinguishes ADDED (entering an entity from the
     * course chat) from CHANGED (swapping one entity for another); a switch back to the course chat yields REMOVED.
     */
    public static IrisContextSwitchMarker forSwitch(IrisChatMode previousMode, IrisChatMode newMode, long newEntityId, String newEntityName) {
        if (newMode == IrisChatMode.COURSE_CHAT) {
            return new IrisContextSwitchMarker(IrisContextSwitchTransition.REMOVED, null, null, "");
        }
        if (previousMode == IrisChatMode.COURSE_CHAT) {
            return new IrisContextSwitchMarker(IrisContextSwitchTransition.ADDED, newMode, newEntityId, newEntityName);
        }
        return new IrisContextSwitchMarker(IrisContextSwitchTransition.CHANGED, newMode, newEntityId, newEntityName);
    }
}
