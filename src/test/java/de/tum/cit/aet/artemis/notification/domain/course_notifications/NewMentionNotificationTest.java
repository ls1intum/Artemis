package de.tum.cit.aet.artemis.notification.domain.course_notifications;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link NewMentionNotification} URL generation. The conversation page treats {@code messageId} as the parent post that
 * seeds the thread and {@code focusReplyId} as the reply to highlight. A link that carries the reply in any other
 * parameter opens the conversation without the thread.
 */
class NewMentionNotificationTest {

    private static final Long COURSE_ID = 1L;

    private static final Long POST_ID = 42L;

    private static final Long REPLY_ID = 99L;

    private static final Long CHANNEL_ID = 7L;

    private static final ZonedDateTime FIXED_TIMESTAMP = ZonedDateTime.parse("2025-01-15T10:00:00+01:00");

    private static final String EXPECTED_URL = "/courses/1/communication?conversationId=7&focusPostId=42&openThreadOnFocus=1&messageId=42&focusReplyId=99";

    private static final Map<String, String> PARAMETERS = Map.ofEntries(Map.entry("postMarkdownContent", "question"), Map.entry("postCreationDate", "01.01.2025"),
            Map.entry("postAuthorName", "Student"), Map.entry("postId", "42"), Map.entry("replyMarkdownContent", "answer"), Map.entry("replyCreationDate", "02.01.2025"),
            Map.entry("replyAuthorName", "Tutor"), Map.entry("replyAuthorId", "5"), Map.entry("replyImageUrl", "image.png"), Map.entry("replyId", "99"),
            Map.entry("channelName", "Channel"), Map.entry("channelId", "7"), Map.entry("replyIsBot", "false"));

    @Test
    void testGetRelativeWebAppUrl_usesParentPostAsMessageIdAndReplyAsFocusReplyId() {
        var notification = new NewMentionNotification(COURSE_ID, "Test Course", "icon.png", "question", "01.01.2025", "Student", POST_ID, "answer", "02.01.2025", "Tutor", 5L,
                "image.png", REPLY_ID, "Channel", CHANNEL_ID, false);
        assertThat(notification.getRelativeWebAppUrl()).isEqualTo(EXPECTED_URL);
    }

    @Test
    void testGetRelativeWebAppUrl_fromDatabase() {
        var notification = new NewMentionNotification(1L, COURSE_ID, FIXED_TIMESTAMP, PARAMETERS);
        assertThat(notification.getRelativeWebAppUrl()).isEqualTo(EXPECTED_URL);
    }

    @Test
    void testGetRelativeWebAppUrl_mentionInPostOpensThePostWithoutThread() {
        var notification = new NewMentionNotification(COURSE_ID, "Test Course", "icon.png", "question", "01.01.2025", "Student", POST_ID, null, null, null, null, null, null,
                "Channel", CHANNEL_ID, false);
        assertThat(notification.getRelativeWebAppUrl()).isEqualTo("/courses/1/communication?conversationId=7&focusPostId=42");
    }
}
