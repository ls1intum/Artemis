package de.tum.cit.aet.artemis.domain.exam;

/**
 * Enum representing reasons why a session is considered suspicious.
 */
public enum SuspiciousSessionReason {
    DIFFERENT_STUDENT_EXAMS_SAME_IP_ADDRESS, DIFFERENT_STUDENT_EXAMS_SAME_BROWSER_FINGERPRINT, SAME_STUDENT_EXAM_DIFFERENT_IP_ADDRESSES,
    SAME_STUDENT_EXAM_DIFFERENT_BROWSER_FINGERPRINTS, IP_ADDRESS_OUTSIDE_OF_RANGE
}
