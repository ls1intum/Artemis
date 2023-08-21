package de.tum.in.www1.artemis.domain;

public class UserConversationWebSocketView {

    private final Long userId;

    private final boolean isConversationHidden;

    private final boolean isAtLeastTutor;

    public UserConversationWebSocketView(Long userId, boolean isConversationHidden, boolean isAtLeastTutor) {
        this.userId = userId;
        this.isConversationHidden = isConversationHidden;
        this.isAtLeastTutor = isAtLeastTutor;
    }

    public Long getUserId() {
        return userId;
    }

    public boolean isConversationHidden() {
        return isConversationHidden;
    }

    public boolean isAtLeastTutor() {
        return isAtLeastTutor;
    }
}
