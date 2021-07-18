package de.tum.in.www1.artemis.domain.enumeration;

/**
 * MANUAL: submission triggered by a student
 * EXTERNAL: submission that was added through the add external submission dialogue
 * ILLEGAL: submission of an exam programming exercise that is submitted before the exercise start date or after the individual exercise end date
 */
public enum SubmissionType {
    MANUAL, TIMEOUT, TEST, OTHER, INSTRUCTOR, EXTERNAL, ILLEGAL
}
