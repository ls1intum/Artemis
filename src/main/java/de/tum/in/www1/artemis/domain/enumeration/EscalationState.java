package de.tum.in.www1.artemis.domain.enumeration;

/**
 * states of escalation a conflict goes through
 */
public enum EscalationState {
    UNHANDLED, ESCALATED_TO_TUTORS_IN_CONFLICT, ESCALATED_TO_INSTRUCTOR, RESOLVED_BY_CAUSER, RESOLVED_BY_OTHER_TUTORS, RESOLVED_BY_INSTRUCTOR
}
