package de.tum.in.www1.artemis.domain.metis.conversation;

public class UserConversationSummary {

    private Long channelId;

    private Long unreadMessagesCount;

    public UserConversationSummary(Long channelId, Long unreadMessagesCount) {
        this.channelId = channelId;
        this.unreadMessagesCount = unreadMessagesCount;
    }

    public Long getChannelId() {
        return channelId;
    }

    public Long getUnreadMessagesCount() {
        return unreadMessagesCount;
    }
}
