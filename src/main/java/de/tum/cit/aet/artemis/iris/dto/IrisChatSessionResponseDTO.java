package de.tum.cit.aet.artemis.iris.dto;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;

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
                session.getCreationDate().truncatedTo(ChronoUnit.MICROS), null, session.getLatestSuggestions(), session.getCitationInfo());
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
                session.getCreationDate().truncatedTo(ChronoUnit.MICROS), messageDTOs.isEmpty() ? null : messageDTOs, session.getLatestSuggestions(), session.getCitationInfo());
    }
}
