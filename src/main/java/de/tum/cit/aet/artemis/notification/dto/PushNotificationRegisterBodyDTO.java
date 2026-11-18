package de.tum.cit.aet.artemis.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.notification.domain.push_notification.PushNotificationApiType;
import de.tum.cit.aet.artemis.notification.domain.push_notification.PushNotificationDeviceType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PushNotificationRegisterBodyDTO(String token, PushNotificationDeviceType deviceType, PushNotificationApiType apiType, String versionCode) {

    public PushNotificationRegisterBodyDTO(String token, PushNotificationDeviceType deviceType) {
        this(token, deviceType, PushNotificationApiType.DEFAULT, null);
    }
}
