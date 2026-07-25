package de.tum.cit.aet.artemis.notification.domain.course_notifications;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link IrisResponseNeedsReviewNotification} URL generation. The conversation page treats
 * {@code messageId} as the parent post that seeds the thread and {@code focusReplyId} as the reply to
 * highlight, so the deep link must carry the parent post id in {@code messageId} and the reply id in
 * {@code focusReplyId} (not the reply id in {@code messageId}).
 */
class IrisResponseNeedsReviewNotificationTest {

    private static final Long COURSE_ID = 1L;

    private static final Long POST_ID = 42L;

    private static final Long REPLY_ID = 99L;

    private static final Long CHANNEL_ID = 7L;

    private static final ZonedDateTime FIXED_TIMESTAMP = ZonedDateTime.parse("2025-01-15T10:00:00+01:00");

    private static final String EXPECTED_URL = "/courses/1/communication?conversationId=7&focusPostId=42&openThreadOnFocus=1&messageId=42&focusReplyId=99";

    @Test
    void testGetRelativeWebAppUrl_usesParentPostAsMessageIdAndReplyAsFocusReplyId() {
        var notification = new IrisResponseNeedsReviewNotification(COURSE_ID, "Test Course", "icon.png", "question", "01.01.2025", "Student", POST_ID, "answer", "02.01.2025",
                REPLY_ID, 0.87, "Channel", CHANNEL_ID);
        assertThat(notification.getRelativeWebAppUrl()).isEqualTo(EXPECTED_URL);
    }

    @Test
    void testGetRelativeWebAppUrl_fromDatabase() {
        var params = Map.of("postMarkdownContent", "question", "postCreationDate", "01.01.2025", "postAuthorName", "Student", "postId", "42", "replyMarkdownContent", "answer",
                "replyCreationDate", "02.01.2025", "replyId", "99", "replyConfidence", "0.87", "channelName", "Channel", "channelId", "7");
        var notification = new IrisResponseNeedsReviewNotification(1L, COURSE_ID, FIXED_TIMESTAMP, params);
        assertThat(notification.getRelativeWebAppUrl()).isEqualTo(EXPECTED_URL);
    }
}
