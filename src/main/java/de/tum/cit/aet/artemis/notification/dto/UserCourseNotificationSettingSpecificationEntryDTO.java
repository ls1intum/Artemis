package de.tum.cit.aet.artemis.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One notification type and the channels a user has enabled for it, carrying the user so a whole cohort can be read in
 * one query.
 * <p>
 * The same shape as {@link UserCourseNotificationSettingSpecificationDTO}, which answers for a single user and stays
 * the shape the settings endpoint returns. This one exists so that filtering a notification's recipients does not have
 * to ask per user.
 *
 * @param userId                 the user the specification belongs to
 * @param courseNotificationType the notification type the flags apply to
 * @param email                  whether the user takes this type by e-mail
 * @param push                   whether the user takes this type by push
 * @param webapp                 whether the user takes this type in the web application
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserCourseNotificationSettingSpecificationEntryDTO(long userId, short courseNotificationType, boolean email, boolean push, boolean webapp) {
}
