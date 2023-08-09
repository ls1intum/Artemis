package de.tum.in.www1.artemis.domain.metis.conversation;

import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;

public class UserConversationInfo {

    private final long conversationId;

    private final ConversationParticipant conversationParticipant;

    private final long unreadMessagesCount;

    public UserConversationInfo(Long conversationId, ConversationParticipant conversationParticipant, long unreadMessagesCount) {
        this.conversationId = conversationId;
        this.conversationParticipant = conversationParticipant;
        this.unreadMessagesCount = unreadMessagesCount;
    }

    public long getConversationId() {
        return conversationId;
    }

    public ConversationParticipant getConversationParticipant() {
        return conversationParticipant;
    }

    public Long getUnreadMessagesCount() {
        return unreadMessagesCount;
    }
}
