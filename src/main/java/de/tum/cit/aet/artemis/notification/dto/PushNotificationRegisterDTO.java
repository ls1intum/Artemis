package de.tum.cit.aet.artemis.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Encryption information returned after successful native device registration.
 *
 * @param secretKey Base64-encoded generated AES key; never a device token
 * @param algorithm the existing push encryption algorithm
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PushNotificationRegisterDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String secretKey,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String algorithm) {
}
