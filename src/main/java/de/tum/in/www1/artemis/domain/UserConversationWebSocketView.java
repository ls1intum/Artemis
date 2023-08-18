package de.tum.in.www1.artemis.domain;

public class UserConversationWebSocketView {

    private final User user;

    private final boolean isChannelHidden;

    public UserConversationWebSocketView(User user, boolean isChannelHidden) {
        this.user = user;
        this.isChannelHidden = isChannelHidden;
    }

    public User getUser() {
        return user;
    }

    public boolean isChannelHidden() {
        return isChannelHidden;
    }
}
