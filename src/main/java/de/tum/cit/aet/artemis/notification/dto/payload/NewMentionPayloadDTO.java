package de.tum.cit.aet.artemis.notification.dto.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The payload of the new mention notification.
 *
 * @param postMarkdownContent  the content of the post, as markdown
 * @param postCreationDate     when the post was written
 * @param postAuthorName       the display name of the post author
 * @param postId               the post the notification is about
 * @param replyMarkdownContent the content of the reply, as markdown
 * @param replyCreationDate    when the reply was written
 * @param replyAuthorName      the display name of the reply author
 * @param replyAuthorId        the reply author, so a client can link to them
 * @param replyImageUrl        the profile picture of the reply author
 * @param replyId              the reply the notification is about
 * @param channelName          the name of that channel
 * @param channelId            the channel involved
 * @param replyIsBot           whether the reply came from a bot
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record NewMentionPayloadDTO(String postMarkdownContent, String postCreationDate, String postAuthorName, Long postId, String replyMarkdownContent, String replyCreationDate,
        String replyAuthorName, Long replyAuthorId, String replyImageUrl, Long replyId, String channelName, Long channelId, boolean replyIsBot)
        implements CourseNotificationPayloadDTO {
}
