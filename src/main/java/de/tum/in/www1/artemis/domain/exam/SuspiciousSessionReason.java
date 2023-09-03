package de.tum.in.www1.artemis.domain.exam;

/**
 * Enum representing reasons why a session is considered suspicious.
 */
public enum SuspiciousSessionReason {
    SAME_IP_ADDRESS, SAME_BROWSER_FINGERPRINT, DIFFERENT_IP_ADDRESS, DIFFERENT_BROWSER_FINGERPRINT, IP_ADDRESS_OUTSIDE_OF_RANGE
}
