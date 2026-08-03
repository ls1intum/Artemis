package de.tum.cit.aet.artemis.exercise.util;

import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.Nullable;

/**
 * Recognises the database rejection produced by the unique constraint on {@code team_student} that enforces
 * "a student belongs to at most one team per exercise".
 * <p>
 * The application-level conflict check is a non-locking read in its own transaction, so two concurrent requests can both
 * pass it and the constraint rejects the loser. That rejection is the same business conflict as the sequential case and
 * has to be reported to the client as such, instead of escaping as a generic server error. Telling that specific
 * violation apart from every other integrity violation is therefore load-bearing: a false negative turns a 400 into a
 * 500, and a false positive would mask an unrelated constraint failure.
 * <p>
 * Extracted from the repository so that this decision can be unit-tested against synthetic exception chains. Reaching it
 * through the repository requires losing a race, which no test can force deterministically.
 */
public final class TeamStudentUniquenessViolation {

    /**
     * Name of the unique constraint as declared in the Liquibase changelog, lower-cased for a case-insensitive
     * comparison against what the database reports.
     */
    public static final String CONSTRAINT_NAME = "uk_team_student_exercise_student";

    /**
     * Upper bound for the cause chain walk. A {@link Throwable} whose {@code getCause()} is overridden can be cyclic, and
     * a driver-supplied chain is untrusted input, so the walk is bounded rather than relying on it terminating.
     */
    private static final int MAXIMUM_CAUSE_DEPTH = 50;

    private TeamStudentUniquenessViolation() {
    }

    /**
     * Checks whether the given failure was caused by the team-student uniqueness constraint.
     * <p>
     * The constraint name is the only reliable discriminator, and each database reports it in its own casing, so the
     * comparison is case-insensitive. It is a {@code contains} check because databases also decorate the name (for
     * example by prefixing the table).
     *
     * @param failure the failure to inspect, may be null and may wrap the violation at any depth
     * @return true if the team-student uniqueness constraint rejected the write
     */
    public static boolean matches(@Nullable Throwable failure) {
        Throwable cause = failure;
        for (int depth = 0; cause != null && depth < MAXIMUM_CAUSE_DEPTH; depth++) {
            if (cause instanceof ConstraintViolationException constraintViolation) {
                String constraintName = constraintViolation.getConstraintName();
                if (constraintName != null && constraintName.toLowerCase().contains(CONSTRAINT_NAME)) {
                    return true;
                }
            }
            Throwable next = cause.getCause();
            if (next == cause) {
                return false;
            }
            cause = next;
        }
        return false;
    }
}
