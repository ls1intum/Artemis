package de.tum.cit.aet.artemis.domain.enumeration;

/**
 * The TextAssessmentEventType enumeration.
 * Enumerates the names of events that are tracked when users interact with the assessment
 * system in text exercises
 * More detailed info in the documentation:
 * <a href="https://docs.artemis.cit.tum.de/dev/setup/#configure-text-assessment-analytics-service">...</a>
 */
public enum TextAssessmentEventType {
    ADD_FEEDBACK_AUTOMATICALLY_SELECTED_BLOCK, ADD_FEEDBACK_MANUALLY_SELECTED_BLOCK, DELETE_FEEDBACK, EDIT_AUTOMATIC_FEEDBACK, SUBMIT_ASSESSMENT, ASSESS_NEXT_SUBMISSION
}
