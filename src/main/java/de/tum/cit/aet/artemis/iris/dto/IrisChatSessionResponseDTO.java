package de.tum.cit.aet.artemis.iris.dto;

import java.time.ZonedDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;

/**
 * Detail DTO for a single Iris chat session, returned by per-mode session controllers (create, get-current, get-all-for-entity).
 * Includes optional messages and AI metadata (suggestions, citations).
 * <p>
 * For the cross-mode listing endpoint ({@code /api/iris/chat-history/{courseId}/sessions}), use {@link IrisChatSessionDTO} instead,
 * which additionally provides an {@code entityName} field computed from a custom query projection.
 *
 * @param id                the session ID
 * @param mode              the chat mode enum
 * @param entityId          the ID of the associated domain entity (course, exercise, lecture, or post);
 *                              nullable for {@link de.tum.cit.aet.artemis.iris.domain.session.IrisTutorSuggestionSession}
 *                              since its {@code postId} field is a boxed {@code Long}
 * @param userId            the owning user's ID
 * @param title             optional user-assigned session title
 * @param creationDate      when the session was created
 * @param messages          optional list of messages (populated by {@link #ofWithMessages}, null otherwise)
 * @param latestSuggestions optional JSON string of the latest follow-up suggestions
 * @param citationInfo      optional citation metadata resolved from message content
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisChatSessionResponseDTO(long id, IrisChatMode mode, @Nullable Long entityId, long userId, @Nullable String title, ZonedDateTime creationDate,
        @Nullable List<IrisMessageResponseDTO> messages, @Nullable String latestSuggestions, @Nullable List<IrisCitationMetaDTO> citationInfo) {

    /**
     * Creates a DTO without messages (for list/create endpoints).
     *
     * @param session the session entity to convert
     * @return the corresponding response DTO without messages
     */
    public static IrisChatSessionResponseDTO of(IrisSession session) {
        return new IrisChatSessionResponseDTO(session.getId(), session.getMode(), session.getEntityId(), session.getUserId(), session.getTitle(), session.getCreationDate(), null,
                session.getLatestSuggestions(), session.getCitationInfo());
    }

    /**
     * Creates a DTO with messages (for current-session/detail endpoints).
     *
     * @param session the session entity to convert
     * @return the corresponding response DTO with messages
     */
    public static IrisChatSessionResponseDTO ofWithMessages(IrisSession session) {
        List<IrisMessageResponseDTO> messageDTOs = session.getMessages().stream().map(IrisMessageResponseDTO::of).toList();
        return new IrisChatSessionResponseDTO(session.getId(), session.getMode(), session.getEntityId(), session.getUserId(), session.getTitle(), session.getCreationDate(),
                messageDTOs.isEmpty() ? null : messageDTOs, session.getLatestSuggestions(), session.getCitationInfo());
    }
}
