package de.tum.in.www1.artemis.domain.metis.conversation;

public final class ConversationSettings {

    private ConversationSettings() {
        // No need to instantiate the class, we can hide its constructor
    }

    // ToDo: Make this limit configurable
    public static final Integer MAX_GROUP_CHAT_PARTICIPANTS = 10;

    public static final Integer MAX_GROUP_CHATS_PER_USER_PER_COURSE = 20;

    public static final Integer MAX_ONE_TO_ONE_CHAT_PARTICIPANTS = 2;

    public static final Integer MAX_ONE_TO_ONE_CHATS_PER_USER_PER_COURSE = 500;

    public static final Integer MAX_REGISTRATIONS_TO_CHANNEL_AT_ONCE = 100;
}
