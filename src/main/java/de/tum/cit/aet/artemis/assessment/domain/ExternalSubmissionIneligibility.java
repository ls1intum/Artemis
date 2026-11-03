package de.tum.cit.aet.artemis.assessment.domain;

/**
 * Why a particular student cannot receive an external submission result for an exercise. The constants are ordered by priority: when several apply, the first one is
 * reported, both by the search for candidates and by the endpoint that creates the result.
 */
public enum ExternalSubmissionIneligibility {

    /** The user does not exist or is not a member of the exercise's course. */
    NOT_IN_COURSE("studentNotFound", "The student could not be found in this course."),

    /** Exam exercise: the student has no student exam, so there is no working period to attach the result to. */
    NO_STUDENT_EXAM("noWorkingPeriod", "This student has no working period in this exam, because no student exam exists for them. Generate the student exams first."),

    /** Exam exercise: the newest student exam of the student does not contain the exercise, so the result would be left out of the exam result. */
    EXERCISE_NOT_IN_STUDENT_EXAM("exerciseNotInStudentExam", "The student exam of this student does not contain this exercise."),

    /** The student already has a result for the exercise. */
    RESULT_ALREADY_EXISTS("resultAlreadyExists", "A result already exists for this student in this exercise.");

    private final String errorKey;

    private final String defaultMessage;

    ExternalSubmissionIneligibility(String errorKey, String defaultMessage) {
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
