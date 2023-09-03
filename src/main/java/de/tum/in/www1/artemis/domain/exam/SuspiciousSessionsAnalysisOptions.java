package de.tum.in.www1.artemis.domain.exam;

/**
 * Options for the analysis of suspicious sessions.
 * The options define which criteria are used to determine whether a session is suspicious.
 *
 * @param sameBrowserFingerprint      whether sessions should be analyzed for the same browser fingerprint but different student exams
 * @param sameIpAddress               whether sessions should be analyzed for the same IP address but different student exams
 * @param differentBrowserFingerprint whether sessions should be analyzed for different browser fingerprints but the same student exam
 * @param differentIpAddress          whether sessions should be analyzed for different IP addresses but the same student exam
 * @param ipAddressOutsideOfRange     whether sessions should be analyzed for IP addresses outside the specified IP range
 */
public record SuspiciousSessionsAnalysisOptions(boolean sameBrowserFingerprint, boolean sameIpAddress, boolean differentBrowserFingerprint, boolean differentIpAddress,
        boolean ipAddressOutsideOfRange) {
}
