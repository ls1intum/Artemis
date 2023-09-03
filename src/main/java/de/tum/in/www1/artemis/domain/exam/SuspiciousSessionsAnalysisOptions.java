package de.tum.in.www1.artemis.domain.exam;

/**
 * Options for the analysis of suspicious sessions.
 * The options define which criteria are used to determine whether a session is suspicious.
 *
 * @param sameBrowserFingerprint      whether sessions with the same browser fingerprint are considered suspicious
 * @param sameIpAddress               whether sessions with the same IP address are considered suspicious
 * @param differentBrowserFingerprint whether sessions with different browser fingerprints are considered suspicious
 * @param differentIpAddress          whether sessions with different IP addresses are considered suspicious
 * @param ipAddressOutsideOfRange     whether sessions with an IP address outside a specific range are considered suspicious
 */
public record SuspiciousSessionsAnalysisOptions(boolean sameBrowserFingerprint, boolean sameIpAddress, boolean differentBrowserFingerprint, boolean differentIpAddress,
        boolean ipAddressOutsideOfRange) {
}
