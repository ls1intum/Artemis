package de.tum.cit.aet.artemis.notification.dto.payload;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The payload of the tutorial group unassigned notification.
 *
 * @param groupTitle    the title of that group
 * @param groupId       the tutorial group involved
 * @param moderatorName the tutor of that group
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupUnassignedPayloadDTO(@Nullable String groupTitle, @Nullable Long groupId, @Nullable String moderatorName) implements CourseNotificationPayloadDTO {
}
