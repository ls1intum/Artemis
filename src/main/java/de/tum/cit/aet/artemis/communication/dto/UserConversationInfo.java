package de.tum.cit.aet.artemis.communication.dto;

import java.time.ZonedDateTime;

import de.tum.cit.aet.artemis.communication.domain.ConversationParticipantSettingsView;

/**
 * Stores user-related information about a conversation
 */
public record UserConversationInfo(long conversationId, ConversationParticipantSettingsView conversationParticipantSettingsView, long unreadMessagesCount) {

    public UserConversationInfo(Long conversationId, Long participantId, Boolean isModerator, Boolean isFavorite, Boolean isHidden, Boolean isMuted, ZonedDateTime lastRead,
            long unreadMessagesCount) {
        this(conversationId, new ConversationParticipantSettingsView(participantId, isModerator, isFavorite, isHidden, isMuted, lastRead), unreadMessagesCount);
    }
}
