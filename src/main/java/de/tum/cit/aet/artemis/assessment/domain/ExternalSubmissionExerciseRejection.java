package de.tum.cit.aet.artemis.assessment.domain;

/**
 * Why an exercise cannot take external submission results at the moment, whoever the student is.
 */
public enum ExternalSubmissionExerciseRejection {

    /** Course exercise without a due date, or with a due date in the future. */
    BEFORE_DUE_DATE("externalSubmissionBeforeDueDate", "External submissions are not supported before the exercise due date."),

    /** Exam exercise whose exam has not ended for every student yet. */
    BEFORE_EXAM_END("externalSubmissionBeforeDueDate", "External submissions are not supported before the end of the exam."),

    /** Quiz exercises are assessed automatically and never take external submissions. */
    QUIZ_EXERCISE("externalSubmissionForQuizExercise", "External submissions are not supported for Quiz exercises.");

    private final String errorKey;

    private final String defaultMessage;

    ExternalSubmissionExerciseRejection(String errorKey, String defaultMessage) {
        this.errorKey = errorKey;
        this.defaultMessage = defaultMessage;
    }

    public String getErrorKey() {
        return errorKey;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
