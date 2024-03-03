package de.tum.in.www1.artemis.domain.notification;

import static de.tum.in.www1.artemis.domain.notification.NotificationConstants.MESSAGE_REPLY_IN_CHANNEL_TEXT;
import static de.tum.in.www1.artemis.domain.notification.NotificationConstants.MESSAGE_REPLY_IN_CONVERSATION_TEXT;

import java.time.ZonedDateTime;

public sealed interface NotificationPlaceholders {

    @NotificationPlaceholderClass(values = { MESSAGE_REPLY_IN_CONVERSATION_TEXT, MESSAGE_REPLY_IN_CHANNEL_TEXT })
    record ConversationReplyPlaceholders(String courseTitle, String postContent, ZonedDateTime postCreationDate, String postAuthorName, String answerPostContent,
            ZonedDateTime answerPostCreationDate, String answerPostAuthorName, String conversationTitle) implements NotificationPlaceholders {
    }
}
