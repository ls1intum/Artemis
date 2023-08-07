package de.tum.in.www1.artemis.domain.metis.conversation;

public class UserGroupChatSummary extends UserConversationSummary {

    private final GroupChat groupChat;

    public UserGroupChatSummary(GroupChat groupChat) {
        super(null);
        this.groupChat = groupChat;
    }

    public GroupChat getGroupChat() {
        return groupChat;
    }
}
