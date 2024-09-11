package de.tum.cit.aet.artemis.domain.exam;

import java.util.Set;

/**
 * A set of related exam sessions that are suspicious.
 * An exam session is suspicious if it fulfills one of the criteria defined in {@link SuspiciousSessionReason}.
 * exam session that attempts a different student exam.
 *
 * @param examSessions the set of exam sessions that are suspicious
 */
public record SuspiciousExamSessions(Set<ExamSession> examSessions) {
}
