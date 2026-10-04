package de.tum.cit.aet.artemis.notification.dto.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The payload of the new announcement notification.
 *
 * @param postId              the post the notification is about
 * @param postTitle           the title of the post
 * @param postMarkdownContent the content of the post, as markdown
 * @param authorName          the display name of the author
 * @param authorImageUrl      the profile picture of the author
 * @param authorId            the author, so a client can link to them
 * @param channelId           the channel involved
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record NewAnnouncementPayloadDTO(Long postId, String postTitle, String postMarkdownContent, String authorName, String authorImageUrl, Long authorId, Long channelId)
        implements CourseNotificationPayloadDTO {
}
