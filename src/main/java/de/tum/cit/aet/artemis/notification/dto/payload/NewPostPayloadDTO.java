package de.tum.cit.aet.artemis.notification.dto.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The payload of the new post notification.
 *
 * @param postId              the post the notification is about
 * @param postMarkdownContent the content of the post, as markdown
 * @param channelId           the channel involved
 * @param channelName         the name of that channel
 * @param channelType         the kind of channel, which decides how it is addressed
 * @param authorName          the display name of the author
 * @param authorImageUrl      the profile picture of the author
 * @param authorId            the author, so a client can link to them
 * @param authorIsBot         whether the author is a bot
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record NewPostPayloadDTO(Long postId, String postMarkdownContent, Long channelId, String channelName, String channelType, String authorName, String authorImageUrl,
        Long authorId, boolean authorIsBot) implements CourseNotificationPayloadDTO {
}
