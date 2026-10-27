package de.tum.cit.aet.artemis.notification.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.notification.domain.push_notification.PushNotificationDeviceType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Deprecated body accepted from older native clients. Each field independently falls back from its query parameter.
 *
 * @param token      fallback when the query token is absent or blank
 * @param deviceType fallback when the query device type is absent
 */
@Schema(description = "Deprecated compatibility body; pass token and deviceType as query parameters. Each field independently falls back to this body.")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PushNotificationUnregisterRequestDTO(@Schema(nullable = true) @Nullable String token, @Schema(nullable = true) @Nullable PushNotificationDeviceType deviceType) {
}
