package de.tum.cit.aet.artemis.exercise.util;

import static de.tum.cit.aet.artemis.exercise.util.TeamStudentUniquenessViolation.CONSTRAINT_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.Locale;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unit tests for the discriminator that decides whether a failed write was rejected by the team-student uniqueness
 * constraint.
 * <p>
 * {@code TeamRepository.save(Exercise, Team)} only reaches this decision when a concurrent request wins the race to the
 * constraint, which no test can force deterministically. Driving it through the repository would need either a real race
 * (flaky either way: it can pass while the translation is broken) or a shared repository spy. Both are avoided by
 * testing the decision directly against synthetic exception chains, which is exactly the shape the drivers produce.
 * <p>
 * A false negative here turns the client-facing 400 into a 500; a false positive would mask an unrelated constraint
 * failure. Both directions are therefore covered.
 */
class TeamStudentUniquenessViolationTest {

    @Test
    void matchesTheConstraintReportedDirectly() {
        assertThat(TeamStudentUniquenessViolation.matches(constraintViolation(CONSTRAINT_NAME))).isTrue();
    }

    /**
     * Each database reports the constraint name in its own casing, and some decorate it, so the comparison is
     * case-insensitive and a substring match.
     *
     * @param reportedName the name as a database might report it
     */
    @ParameterizedTest
    @ValueSource(strings = { "uk_team_student_exercise_student", "UK_TEAM_STUDENT_EXERCISE_STUDENT", "UK_Team_Student_Exercise_Student",
            "team_student.uk_team_student_exercise_student", "public.uk_team_student_exercise_student_idx" })
    void matchesEveryCasingAndDecorationDatabasesReport(String reportedName) {
        assertThat(TeamStudentUniquenessViolation.matches(constraintViolation(reportedName))).isTrue();
    }

    @Test
    void matchesTheConstraintWrappedByTheSpringTranslation() {
        // This is the shape the repository actually catches: Spring wraps Hibernate's exception.
        DataIntegrityViolationException wrapped = new DataIntegrityViolationException("could not execute statement", constraintViolation(CONSTRAINT_NAME));

        assertThat(TeamStudentUniquenessViolation.matches(wrapped)).isTrue();
    }

    @Test
    void matchesTheConstraintNestedDeepInTheCauseChain() {
        Throwable deeplyNested = new IllegalStateException("outer",
                new RuntimeException("middle", new DataIntegrityViolationException("inner", constraintViolation(CONSTRAINT_NAME))));

        assertThat(TeamStudentUniquenessViolation.matches(deeplyNested)).isTrue();
    }

    @Test
    void doesNotMaskAViolationOfADifferentConstraint() {
        // A different constraint must keep escaping as the original failure, otherwise an unrelated bug is reported as a team conflict.
        DataIntegrityViolationException other = new DataIntegrityViolationException("could not execute statement", constraintViolation("uk_team_exercise_short_name"));

        assertThat(TeamStudentUniquenessViolation.matches(other)).isFalse();
    }

    @Test
    void doesNotMatchAnIntegrityViolationWithoutAConstraintName() {
        assertThat(TeamStudentUniquenessViolation.matches(constraintViolation(null))).isFalse();
        assertThat(TeamStudentUniquenessViolation.matches(new DataIntegrityViolationException("no cause at all"))).isFalse();
    }

    @Test
    void doesNotMatchNull() {
        assertThat(TeamStudentUniquenessViolation.matches(null)).isFalse();
    }

    /**
     * The case folding must not depend on the JVM's default locale. Under {@code tr_TR}, {@code "I".toLowerCase()} is the
     * dotless {@code "ı"}, so an upper-cased constraint name would fold to {@code "…exercıse_student"}, stop matching, and
     * turn the client-facing 400 into a 500 for every concurrent team conflict on that node.
     */
    @Test
    @ResourceLock(Resources.LOCALE)
    void matchesRegardlessOfTheDefaultLocale() {
        // The default locale is JVM-wide state, so the resource lock keeps a parallel test from observing tr_TR. Both
        // categories are captured because setDefault(Locale) overwrites DISPLAY and FORMAT, which need not be equal.
        Locale displayLocale = Locale.getDefault(Locale.Category.DISPLAY);
        Locale formatLocale = Locale.getDefault(Locale.Category.FORMAT);
        try {
            Locale.setDefault(Locale.of("tr", "TR"));

            assertThat(TeamStudentUniquenessViolation.matches(constraintViolation("UK_TEAM_STUDENT_EXERCISE_STUDENT"))).isTrue();
        }
        finally {
            Locale.setDefault(Locale.Category.DISPLAY, displayLocale);
            Locale.setDefault(Locale.Category.FORMAT, formatLocale);
        }
    }

    @Test
    void terminatesOnACyclicCauseChain() {
        // A Throwable may override getCause(); the walk must not hang on a chain that points back at itself.
        Throwable selfReferential = new RuntimeException("cyclic") {

            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };

        assertThat(TeamStudentUniquenessViolation.matches(selfReferential)).isFalse();
    }

    private static ConstraintViolationException constraintViolation(String constraintName) {
        return new ConstraintViolationException("could not execute statement", new SQLException("duplicate key value violates unique constraint"), constraintName);
    }
}
