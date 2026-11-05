package de.tum.cit.aet.artemis.notification.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.notification.domain.UserCourseNotificationStatusType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Record for notification status update requests
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserCourseNotificationStatusUpdateRequestDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Long> notificationIds,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UserCourseNotificationStatusType statusType) {
}
