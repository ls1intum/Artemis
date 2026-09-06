package de.tum.cit.aet.artemis.notification.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One notification type and the channels a user has enabled for it in a course.
 * <p>
 * This is the value of a distributed cache, read once per recipient while a notification is being filtered, which is
 * why it is a record of scalars rather than the entity. The entity references its {@code User} and {@code Course}, and
 * those reach most of the domain model.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserCourseNotificationSettingSpecificationDTO(short courseNotificationType, boolean email, boolean push, boolean webapp) implements Serializable {
}
