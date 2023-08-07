package de.tum.in.www1.artemis.domain.metis.conversation;

public class UserChannelSummary extends UserConversationSummary {

    private final Channel channel;

    public UserChannelSummary(Channel channel, Long unreadMessagesCount) {
        super(unreadMessagesCount);
        this.channel = channel;
    }

    public UserChannelSummary(Channel channel) {
        super(null);
        this.channel = channel;
    }

    public Channel getChannel() {
        return channel;
    }
}
