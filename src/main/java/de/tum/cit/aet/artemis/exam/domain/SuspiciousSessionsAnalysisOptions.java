package de.tum.cit.aet.artemis.exam.domain;

/**
 * Options for the analysis of suspicious sessions.
 * The options define which criteria are used to determine whether a session is suspicious.
 *
 * @param sameBrowserFingerprintDifferentStudentExams whether sessions should be analyzed for the same browser fingerprint but different student exams
 * @param sameIpAddressDifferentStudentExams          whether sessions should be analyzed for the same IP address but different student exams
 * @param differentIpAddressesSameStudentExam         whether sessions should be analyzed for different IP addresses but the same student exam
 * @param differentBrowserFingerprintsSameStudentExam whether sessions should be analyzed for different browser fingerprints but the same student exam
 * @param ipAddressOutsideOfRange                     whether sessions should be analyzed for IP addresses outside the specified IP range
 */
public record SuspiciousSessionsAnalysisOptions(boolean sameIpAddressDifferentStudentExams, boolean sameBrowserFingerprintDifferentStudentExams,
        boolean differentIpAddressesSameStudentExam, boolean differentBrowserFingerprintsSameStudentExam, boolean ipAddressOutsideOfRange) {
}
