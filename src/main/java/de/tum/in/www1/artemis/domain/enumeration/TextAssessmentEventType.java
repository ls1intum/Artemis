package de.tum.in.www1.artemis.domain.enumeration;

/**
 * The TextAssessmentEventType enumeration.
 * Enumerates the names of events that are tracked when users interact with the assessment
 * system in text exercises
 * More detailed info in the documentation:
 * https://artemis-platform.readthedocs.io/en/latest/dev/setup.html#configure-text-assessment-analytics-service
 */
public enum TextAssessmentEventType {
    ADD_FEEDBACK_AUTOMATICALLY_SELECTED_BLOCK, ADD_FEEDBACK_MANUALLY_SELECTED_BLOCK, CLICK_TO_RESOLVE_CONFLICT, HOVER_OVER_IMPACT_WARNING, VIEW_AUTOMATIC_SUGGESTION_ORIGIN,
    DELETE_FEEDBACK, EDIT_AUTOMATIC_FEEDBACK, SUBMIT_ASSESSMENT, ASSESS_NEXT_SUBMISSION
}
