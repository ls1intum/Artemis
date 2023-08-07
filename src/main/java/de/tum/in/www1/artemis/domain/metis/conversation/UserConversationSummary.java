package de.tum.in.www1.artemis.domain.metis.conversation;

public abstract class UserConversationSummary {

    private final Long unreadMessagesCount;

    public UserConversationSummary(Long unreadMessagesCount) {
        this.unreadMessagesCount = unreadMessagesCount;
    }

    public Long getUnreadMessagesCount() {
        return unreadMessagesCount;
    }
}
