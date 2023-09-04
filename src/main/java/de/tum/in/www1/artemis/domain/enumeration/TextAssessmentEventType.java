package de.tum.in.www1.artemis.domain.enumeration;

/**
 * The TextAssessmentEventType enumeration.
 * Enumerates the names of events that are tracked when users interact with the assessment
 * system in text exercises
 * More detailed info in the documentation:
 * https://ls1intum.github.io/Artemis/dev/setup/#configure-text-assessment-analytics-service
 */
public enum TextAssessmentEventType {
    ADD_FEEDBACK_AUTOMATICALLY_SELECTED_BLOCK, ADD_FEEDBACK_MANUALLY_SELECTED_BLOCK, DELETE_FEEDBACK, EDIT_AUTOMATIC_FEEDBACK, SUBMIT_ASSESSMENT, ASSESS_NEXT_SUBMISSION
}
