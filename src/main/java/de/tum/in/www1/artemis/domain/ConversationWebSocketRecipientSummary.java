package de.tum.in.www1.artemis.domain;

public class ConversationWebSocketRecipientSummary {

    private final User user;

    private final boolean isConversationHidden;

    public ConversationWebSocketRecipientSummary(User user, boolean isConversationHidden) {
        this.user = user;
        this.isConversationHidden = isConversationHidden;
    }

    public User getUser() {
        return user;
    }

    public boolean isConversationHidden() {
        return isConversationHidden;
    }
}
