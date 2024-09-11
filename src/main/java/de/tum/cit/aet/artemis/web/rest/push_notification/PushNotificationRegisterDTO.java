package de.tum.cit.aet.artemis.web.rest.push_notification;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PushNotificationRegisterDTO(String secretKey, String algorithm) {
}
