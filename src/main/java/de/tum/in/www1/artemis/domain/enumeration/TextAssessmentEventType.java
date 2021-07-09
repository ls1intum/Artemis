package de.tum.in.www1.artemis.domain.enumeration;

/**
 * The TextAssessmentEventType enumeration.
 * Contains a list of enumerations denoting the names of the events that are tracked when users interact with the assessment
 * system in Text exercises
 */
public enum TextAssessmentEventType {
    ADD_FEEDBACK_AUTOMATICALLY_SELECTED_BLOCK, ADD_FEEDBACK_MANUALLY_SELECTED_BLOCK, CLICK_TO_RESOLVE_CONFLICT, HOVER_OVER_IMPACT_WARNING, VIEW_AUTOMATIC_SUGGESTION_ORIGIN,
    DELETE_FEEDBACK, EDIT_AUTOMATIC_FEEDBACK, SUBMIT_ASSESSMENT, ASSESS_NEXT_SUBMISSION
}
