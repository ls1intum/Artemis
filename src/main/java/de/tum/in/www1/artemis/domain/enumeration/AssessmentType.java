package de.tum.in.www1.artemis.domain.enumeration;

/**
 * The AssessmentType enumeration.
 */
public enum AssessmentType {

    AUTOMATIC, COMPLAINT_BASED, SEMI_AUTOMATIC, MANUAL;

    public static boolean hasNoManualFeedback(AssessmentType assessmentType) {
        return assessmentType == AUTOMATIC || assessmentType == COMPLAINT_BASED;
    }
}
