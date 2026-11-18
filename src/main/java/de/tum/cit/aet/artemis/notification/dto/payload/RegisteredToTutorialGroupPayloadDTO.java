package de.tum.cit.aet.artemis.notification.dto.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The payload of the registered to tutorial group notification.
 *
 * @param groupTitle    the title of that group
 * @param groupId       the tutorial group involved
 * @param moderatorName the tutor of that group
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RegisteredToTutorialGroupPayloadDTO(String groupTitle, Long groupId, String moderatorName) implements CourseNotificationPayloadDTO {
}
