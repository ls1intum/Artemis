package de.tum.cit.aet.artemis.communication.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PushNotificationDeviceConfigurationDTO(String deviceType, String token, String secretKey, Date expirationDate, String apiType, String userLogin) {
}
