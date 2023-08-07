package de.tum.in.www1.artemis.domain.metis.conversation;

public record UserChannelSummary(Channel channel, Long unreadMessagesCount) implements UserConversationSummary {

    public UserChannelSummary(Channel channel) {
        this(channel, null);
    }
}
