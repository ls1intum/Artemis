package de.tum.cit.aet.artemis.domain.metis.conversation;

import java.time.ZonedDateTime;

import de.tum.cit.aet.artemis.domain.metis.ConversationParticipantSettingsView;

/**
 * Stores user-related information about a conversation
 */
public class UserConversationInfo {

    private final long conversationId;

    private final ConversationParticipantSettingsView conversationParticipant;

    private final long unreadMessagesCount;

    public UserConversationInfo(Long conversationId, Long participantId, Boolean isModerator, Boolean isFavorite, Boolean isHidden, Boolean isMuted, ZonedDateTime lastRead,
            long unreadMessagesCount) {
        this.conversationId = conversationId;
        this.conversationParticipant = new ConversationParticipantSettingsView(participantId, isModerator, isFavorite, isHidden, isMuted, lastRead);
        this.unreadMessagesCount = unreadMessagesCount;
    }

    public long getConversationId() {
        return conversationId;
    }

    public ConversationParticipantSettingsView getConversationParticipantSettingsView() {
        return conversationParticipant;
    }

    public Long getUnreadMessagesCount() {
        return unreadMessagesCount;
    }
}
