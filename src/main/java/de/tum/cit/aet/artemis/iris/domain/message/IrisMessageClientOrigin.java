package de.tum.cit.aet.artemis.iris.domain.message;

import org.jspecify.annotations.Nullable;

/**
 * The client a user message was sent from. Persisted on a user {@link IrisMessage} and used to decide whether the
 * asynchronous Iris response should be delivered as a push notification (currently only for messages sent from iOS).
 * <p>
 * IOS: The native iOS app
 * <p>
 * WEB: The web client
 * <p>
 * UNKNOWN: A user message whose client could not be identified (e.g. an older client or an unrecognized header value)
 */
public enum IrisMessageClientOrigin {

    IOS, WEB, UNKNOWN;

    /**
     * Resolves the client origin from the raw {@code X-Artemis-Client} HTTP header value. Matching is case-insensitive.
     * Since the value originates from an untrusted external header, any missing, blank or unrecognized value maps to
     * {@link #UNKNOWN} rather than throwing.
     *
     * @param header the raw header value, may be {@code null}
     * @return the matching client origin, or {@link #UNKNOWN} if the value is missing, blank or unrecognized
     */
    public static IrisMessageClientOrigin fromHeader(@Nullable String header) {
        if (header == null || header.isBlank()) {
            return UNKNOWN;
        }
        for (IrisMessageClientOrigin origin : values()) {
            if (origin.name().equalsIgnoreCase(header.strip())) {
                return origin;
            }
        }
        return UNKNOWN;
    }
}
