package de.tum.in.www1.artemis.domain.exam;

import java.util.Set;

/**
 * A set of related exam sessions that are suspicious.
 * An exam session is suspicious if it shares the same browser fingerprint hash or user agent or IP address with another
 * exam session that attempts a different student exam.
 *
 * @param examSessions the set of exam sessions that are suspicious
 */
public record SuspiciousExamSessions(Set<ExamSession> examSessions) {
}
