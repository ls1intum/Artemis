package de.tum.cit.aet.artemis.iris.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;

/**
 * Detail DTO for a single Iris chat session, returned by per-mode session controllers (create, get-current, get-all-for-entity).
 * Includes the {@code type} discriminator for backward compatibility with the old {@code @JsonTypeInfo}-based entity serialization,
 * optional messages, and AI metadata (suggestions, citations).
 * <p>
 * For the cross-mode listing endpoint ({@code /api/iris/chat-history/{courseId}/sessions}), use {@link IrisChatSessionDTO} instead,
 * which provides {@code entityId}/{@code entityName} fields computed from a custom query projection.
 *
 * @param id                the session ID
 * @param type              string discriminator (e.g. {@code "course_chat"}, {@code "programming_exercise_chat"})
 * @param mode              the chat mode enum
 * @param userId            the owning user's ID
 * @param title             optional user-assigned session title
 * @param creationDate      when the session was created
 * @param messages          optional list of messages (populated by {@link #ofWithMessages}, null otherwise)
 * @param latestSuggestions optional JSON string of the latest follow-up suggestions
 * @param citationInfo      optional citation metadata resolved from message content
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisChatSessionResponseDTO(long id, String type, IrisChatMode mode, long userId, @Nullable String title, ZonedDateTime creationDate,
        @Nullable List<IrisMessageResponseDTO> messages, @Nullable String latestSuggestions, @Nullable List<IrisCitationMetaDTO> citationInfo) {

    /**
     * Creates a DTO without messages (for list/create endpoints).
     *
     * @param session the chat session entity to convert
     * @return the corresponding response DTO without messages
     */
    public static IrisChatSessionResponseDTO of(IrisChatSession session) {
        return new IrisChatSessionResponseDTO(session.getId(), session.getMode().name().toLowerCase(Locale.ROOT), session.getMode(), session.getUserId(), session.getTitle(),
                session.getCreationDate(), null, session.getLatestSuggestions(), session.getCitationInfo());
    }

    /**
     * Creates a DTO with messages (for current-session/detail endpoints).
     *
     * @param session the chat session entity to convert
     * @return the corresponding response DTO with messages
     */
    public static IrisChatSessionResponseDTO ofWithMessages(IrisChatSession session) {
        List<IrisMessageResponseDTO> messageDTOs = session.getMessages().stream().map(IrisMessageResponseDTO::of).toList();
        return new IrisChatSessionResponseDTO(session.getId(), session.getMode().name().toLowerCase(Locale.ROOT), session.getMode(), session.getUserId(), session.getTitle(),
                session.getCreationDate(), messageDTOs.isEmpty() ? null : messageDTOs, session.getLatestSuggestions(), session.getCitationInfo());
    }
}
