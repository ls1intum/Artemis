package de.tum.cit.aet.artemis.communication.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.push_notification.PushNotificationDeviceType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PushNotificationUnregisterRequest(String token, PushNotificationDeviceType deviceType) {
}
