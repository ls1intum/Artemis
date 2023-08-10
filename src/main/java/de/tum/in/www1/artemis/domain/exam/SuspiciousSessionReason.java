package de.tum.in.www1.artemis.domain.exam;

/**
 * Enum representing reasons why a session is considered suspicious.
 */
public enum SuspiciousSessionReason {
    SAME_IP_ADDRESS, SAME_BROWSER_FINGERPRINT, NOT_SAME_IP_ADDRESS, NOT_SAME_BROWSER_FINGERPRINT
}
