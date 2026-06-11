package de.tum.cit.aet.artemis.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.notification.domain.push_notification.PushNotificationDeviceType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PushNotificationUnregisterRequestDTO(String token, PushNotificationDeviceType deviceType) {
}
