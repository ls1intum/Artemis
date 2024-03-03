package de.tum.in.www1.artemis.domain.notification;

import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.CONVERSATION_NEW_REPLY_MESSAGE;
import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.CONVERSATION_USER_MENTIONED;
import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.NEW_REPLY_FOR_COURSE_POST;
import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.NEW_REPLY_FOR_EXAM_POST;
import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.NEW_REPLY_FOR_EXERCISE_POST;
import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.NEW_REPLY_FOR_LECTURE_POST;

import java.time.ZonedDateTime;

public sealed interface NotificationPlaceholders {

    @NotificationPlaceholderClass(values = { NEW_REPLY_FOR_EXERCISE_POST, NEW_REPLY_FOR_LECTURE_POST, NEW_REPLY_FOR_COURSE_POST, NEW_REPLY_FOR_EXAM_POST,
            CONVERSATION_NEW_REPLY_MESSAGE, CONVERSATION_USER_MENTIONED })
    public record ConversationReplyPlaceholders(String courseTitle, String postContent, ZonedDateTime postCreationDate, String postAuthorName, String answerPostContent,
            ZonedDateTime answerPostCreationDate, String answerPostAuthorName, String conversationTitle) implements NotificationPlaceholders {
    }
}
