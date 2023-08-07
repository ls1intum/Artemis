package de.tum.in.www1.artemis.domain.metis.conversation;

public class UserConversationSummary<T extends Conversation> {

    private final T conversation;

    private final Long unreadMessagesCount;

    public UserConversationSummary(T conversation, Long unreadMessagesCount) {
        this.conversation = conversation;
        this.unreadMessagesCount = unreadMessagesCount;
    }

    public UserConversationSummary(T conversation) {
        this.conversation = conversation;
        this.unreadMessagesCount = null;
    }

    public T getConversation() {
        return conversation;
    }

    public Long getUnreadMessagesCount() {
        return unreadMessagesCount;
    }
}
