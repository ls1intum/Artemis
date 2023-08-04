package de.tum.in.www1.artemis.domain.metis.conversation;

public class UserConversationSummary {

    private Channel channel;

    private Long unreadMessagesCount;

    public UserConversationSummary(Channel channel, Long unreadMessagesCount) {
        this.channel = channel;
        this.unreadMessagesCount = unreadMessagesCount;
    }

    public Channel getChannelId() {
        return channel;
    }

    public Long getUnreadMessagesCount() {
        return unreadMessagesCount;
    }
}
