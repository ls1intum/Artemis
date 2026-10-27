package de.tum.cit.aet.artemis.notification.dto.payload;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The payload of the iris response needs review notification.
 *
 * @param postMarkdownContent  the content of the post, as markdown
 * @param postCreationDate     when the post was written
 * @param postAuthorName       the display name of the post author
 * @param postId               the post the notification is about
 * @param replyMarkdownContent the content of the reply, as markdown
 * @param replyCreationDate    when the reply was written
 * @param replyId              the reply the notification is about
 * @param replyConfidence      how confident the assistant is in the reply
 * @param channelName          the name of that channel
 * @param channelId            the channel involved
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisResponseNeedsReviewPayloadDTO(@Nullable String postMarkdownContent, @Nullable String postCreationDate, @Nullable String postAuthorName, @Nullable Long postId,
        @Nullable String replyMarkdownContent, @Nullable String replyCreationDate, @Nullable Long replyId, @Nullable Double replyConfidence, @Nullable String channelName,
        @Nullable Long channelId) implements CourseNotificationPayloadDTO {
}
