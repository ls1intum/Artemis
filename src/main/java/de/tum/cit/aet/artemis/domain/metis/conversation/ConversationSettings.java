package de.tum.cit.aet.artemis.domain.metis.conversation;

public final class ConversationSettings {

    // ToDo: Make these limits configurable via application.yml
    public static final Integer MAX_GROUP_CHAT_PARTICIPANTS = 10;

    public static final Integer MAX_GROUP_CHATS_PER_USER_PER_COURSE = 50;

    public static final Integer MAX_ONE_TO_ONE_CHAT_PARTICIPANTS = 2;

    public static final Integer MAX_ONE_TO_ONE_CHATS_PER_USER_PER_COURSE = 500;

    private ConversationSettings() {
        // No need to instantiate the class, we can hide its constructor
    }
}
