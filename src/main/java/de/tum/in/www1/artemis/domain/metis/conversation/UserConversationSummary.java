package de.tum.in.www1.artemis.domain.metis.conversation;

public class UserConversationSummary<T extends Conversation> {

    private final T conversation;

    private final Long unreadMessagesCount;

    public UserConversationSummary(T channel, Long unreadMessagesCount) {
        this.conversation = channel;
        this.unreadMessagesCount = unreadMessagesCount;
    }

    public T getConversation() {
        return conversation;
    }

    public Long getUnreadMessagesCount() {
        return unreadMessagesCount;
    }
}
