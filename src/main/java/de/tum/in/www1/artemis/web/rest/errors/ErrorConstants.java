package de.tum.in.www1.artemis.web.rest.errors;

import java.net.URI;

public final class ErrorConstants {

    public static final String REQ_400_REASON = "The request message was malformed";

    public static final String REQ_404_REASON = "Requested resource does not exist.";

    public static final String REQ_403_REASON = "Insufficient permission to perform this request";

    public static final String ERR_CONCURRENCY_FAILURE = "error.concurrencyFailure";

    public static final String ERR_VALIDATION = "error.validation";

    public static final String PROBLEM_BASE_URL = "https://www.jhipster.tech/problem";

    public static final URI DEFAULT_TYPE = URI.create(PROBLEM_BASE_URL + "/problem-with-message");

    public static final URI CONSTRAINT_VIOLATION_TYPE = URI.create(PROBLEM_BASE_URL + "/constraint-violation");

    public static final URI PARAMETERIZED_TYPE = URI.create(PROBLEM_BASE_URL + "/parameterized");

    public static final URI ENTITY_NOT_FOUND_TYPE = URI.create(PROBLEM_BASE_URL + "/entity-not-found");

    public static final URI INVALID_PASSWORD_TYPE = URI.create(PROBLEM_BASE_URL + "/invalid-password");

    public static final URI EMAIL_ALREADY_USED_TYPE = URI.create(PROBLEM_BASE_URL + "/email-already-used");

    public static final URI LOGIN_ALREADY_USED_TYPE = URI.create(PROBLEM_BASE_URL + "/login-already-used");

    public static final URI ACCOUNT_REGISTRATION_BLOCKED = URI.create(PROBLEM_BASE_URL + "/account-registration-blocked");

    public static final URI STUDENT_ALREADY_ASSIGNED_TYPE = URI.create(PROBLEM_BASE_URL + "/student-already-assigned");

    public static final URI REGISTRATION_NUMBER_NOT_FOUND_TYPE = URI.create(PROBLEM_BASE_URL + "/registration-number-not-found");

    public static final URI STUDENTS_APPEAR_MULTIPLE_TIMES_TYPE = URI.create(PROBLEM_BASE_URL + "/students-appear-multiple-times");

    public static final URI COMPLAINT_LOCKED = URI.create(PROBLEM_BASE_URL + "/complaint");

    public static final URI EXAM_PROGRAMMING_EXERCISE_SHORT_NAME_INVALID = URI.create(PROBLEM_BASE_URL + "/exam-programming-exercise-short-name-invalid");

    private ErrorConstants() {
    }
}
