package de.tum.in.www1.artemis.domain.metis.conversation;

public class ConversationSummary {

    private final Conversation conversation;

    private final UserConversationInfo userConversationInfo;

    private final GeneralConversationInfo generalConversationInfo;

    public ConversationSummary(Conversation conversation, UserConversationInfo userConversationInfo, GeneralConversationInfo generalConversationInfo) {
        this.conversation = conversation;
        this.userConversationInfo = userConversationInfo;
        this.generalConversationInfo = generalConversationInfo;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public UserConversationInfo getUserConversationInfo() {
        return userConversationInfo;
    }

    public GeneralConversationInfo getGeneralConversationInfo() {
        return generalConversationInfo;
    }
}
