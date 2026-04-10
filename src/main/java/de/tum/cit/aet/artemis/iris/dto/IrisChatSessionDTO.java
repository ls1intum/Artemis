package de.tum.cit.aet.artemis.iris.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;

/**
 * Listing DTO for the cross-mode chat-history endpoint ({@code /api/iris/chat-history/{courseId}/sessions}).
 * Provides a flat summary of a session with the associated entity context ({@code entityId}/{@code entityName})
 * that is computed from a custom query projection rather than stored on the session entity.
 * <p>
 * For detail/create/get-current endpoints that return a full session (potentially with messages),
 * use {@link IrisChatSessionResponseDTO} instead.
 *
 * @param id               the session ID
 * @param entityId         the ID of the associated entity (course, exercise, lecture, or post)
 * @param entityName       the display name of the associated entity
 * @param title            optional user-assigned session title
 * @param creationDate     when the session was created
 * @param lastActivityDate when the session was last active (latest message timestamp)
 * @param chatMode         the chat mode enum
 */
// TODO: REFACTORING ASLAN: EXERCISEID / LECTUREID MITSCHICKEN?
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisChatSessionDTO(long id, long entityId, @Nullable String entityName, @Nullable String title, ZonedDateTime creationDate, @Nullable ZonedDateTime lastActivityDate,
        IrisChatMode chatMode) {
}
