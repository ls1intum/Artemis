package de.tum.cit.aet.artemis.notification.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.notification.domain.push_notification.PushNotificationApiType;
import de.tum.cit.aet.artemis.notification.domain.push_notification.PushNotificationDeviceType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Native device registration. Schema metadata does not add field validation to the existing endpoint.
 *
 * @param token       device token used as part of the stored registration identity
 * @param deviceType  device transport used as part of the stored registration identity
 * @param apiType     native payload API; omitted or null selects DEFAULT
 * @param versionCode optional native app version; a nonnull value must match the existing dotted numeric format
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PushNotificationRegisterBodyDTO(String token, PushNotificationDeviceType deviceType,
        @Schema(nullable = true, defaultValue = "DEFAULT", description = "Omitted or null selects DEFAULT") @Nullable PushNotificationApiType apiType,
        @Schema(nullable = true, pattern = "^\\d+\\.\\d+\\.\\d+$", description = "Optional native app version; invalid nonnull values currently reach the generic server error handler") @Nullable String versionCode) {

    /**
     * Registers a device using the original payload API without an app version.
     *
     * @param token      the device token
     * @param deviceType the transport
     */
    public PushNotificationRegisterBodyDTO(String token, PushNotificationDeviceType deviceType) {
        this(token, deviceType, PushNotificationApiType.DEFAULT, null);
    }
}
