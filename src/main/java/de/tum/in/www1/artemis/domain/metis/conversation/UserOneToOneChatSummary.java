package de.tum.in.www1.artemis.domain.metis.conversation;

public class UserOneToOneChatSummary extends UserConversationSummary {

    private final OneToOneChat oneToOneChat;

    public UserOneToOneChatSummary(OneToOneChat oneToOneChat) {
        super(null);
        this.oneToOneChat = oneToOneChat;
    }

    public OneToOneChat getOneToOneChat() {
        return oneToOneChat;
    }
}
