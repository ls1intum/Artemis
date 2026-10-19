package de.tum.cit.aet.artemis.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.course.domain.Course;

class CourseValidatorBoundaryTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = { -1, 6 })
    void requiresExplicitScoreAccuracyWithinSupportedBounds(Integer accuracy) {
        var course = new Course();
        course.setAccuracyOfScores(accuracy);
        assertThatThrownBy(() -> CourseValidator.validateAccuracyOfScores(course)).isInstanceOfSatisfying(BadRequestAlertException.class,
                failure -> assertThat(failure.getErrorKey()).isEqualTo(accuracy == null ? "accuracyOfScoresNotSet" : "accuracyOfScoresInvalid"));
    }

    @Test
    void permitsExactAccuracyAndPresentationLimits() {
        var course = new Course();
        for (int accuracy : new int[] { 0, 5 }) {
            course.setAccuracyOfScores(accuracy);
            assertThatCode(() -> CourseValidator.validateAccuracyOfScores(course)).doesNotThrowAnyException();
        }
        course.setPresentationScore(Constants.MAX_PRESENTATION_SCORE);
        assertThatCode(() -> CourseValidator.validatePointBounds(course)).doesNotThrowAnyException();
        course.setPresentationScore(Constants.MAX_PRESENTATION_SCORE + 1);
        assertThatThrownBy(() -> CourseValidator.validatePointBounds(course)).isInstanceOfSatisfying(BadRequestAlertException.class,
                failure -> assertThat(failure.getErrorKey()).isEqualTo("presentationScoreInvalid"));
    }

    @Test
    void fillsComplaintDefaultsButRejectsTextLimitsAboveTheStorageBoundary() {
        var course = new Course();
        course.setMaxComplaints(null);
        course.setMaxTeamComplaints(null);
        course.setMaxComplaintTimeDays(7);
        CourseValidator.validateComplaintsAndRequestMoreFeedbackConfig(course);
        assertThat(course.getMaxComplaints()).isEqualTo(3);
        assertThat(course.getMaxTeamComplaints()).isEqualTo(3);
        course.setMaxComplaintTextLimit(Constants.COMPLAINT_TEXT_LIMIT);
        course.setMaxComplaintResponseTextLimit(Constants.COMPLAINT_RESPONSE_TEXT_LIMIT);
        assertThatCode(() -> CourseValidator.validateComplaintsAndRequestMoreFeedbackConfig(course)).doesNotThrowAnyException();
        course.setMaxComplaintTextLimit(Constants.COMPLAINT_TEXT_LIMIT + 1);
        assertThatThrownBy(() -> CourseValidator.validateComplaintsAndRequestMoreFeedbackConfig(course)).isInstanceOfSatisfying(BadRequestAlertException.class,
                failure -> assertThat(failure.getErrorKey()).isEqualTo("maxComplaintTextLimitInvalid"));
        course.setMaxComplaintTextLimit(Constants.COMPLAINT_TEXT_LIMIT);
        course.setMaxComplaintResponseTextLimit(Constants.COMPLAINT_RESPONSE_TEXT_LIMIT + 1);
        assertThatThrownBy(() -> CourseValidator.validateComplaintsAndRequestMoreFeedbackConfig(course)).isInstanceOfSatisfying(BadRequestAlertException.class,
                failure -> assertThat(failure.getErrorKey()).isEqualTo("maxComplaintResponseTextLimitInvalid"));
    }

    @ParameterizedTest
    @CsvSource({ "0,0,0,true", "1,0,0,false", "0,1,0,false", "0,0,1,false", "1,0,1,true", "0,1,1,true", "1,1,1,true" })
    void complaintWindowMustMatchWhetherIndividualOrTeamComplaintsAreEnabled(int individual, int team, int days, boolean valid) {
        var course = new Course();
        course.setMaxComplaints(individual);
        course.setMaxTeamComplaints(team);
        course.setMaxComplaintTimeDays(days);
        if (valid) {
            assertThatCode(() -> CourseValidator.validateComplaintsAndRequestMoreFeedbackConfig(course)).doesNotThrowAnyException();
            assertThat(course.getMaxComplaints()).isEqualTo(individual);
            assertThat(course.getMaxTeamComplaints()).isEqualTo(team);
        }
        else {
            assertThatThrownBy(() -> CourseValidator.validateComplaintsAndRequestMoreFeedbackConfig(course)).isInstanceOfSatisfying(BadRequestAlertException.class,
                    failure -> assertThat(failure.getErrorKey()).isEqualTo("complaintsConfigInvalid"));
        }
    }

    @Test
    void unenrollmentRequiresACompleteEnrollmentWindowAndMustEndWithinTheCourse() {
        var course = new Course();
        var start = ZonedDateTime.parse("2026-01-01T00:00:00Z");
        course.setStartDate(start);
        course.setEndDate(start.plusMonths(6));
        course.setEnrollmentEndDate(start.plusMonths(1));
        course.setUnenrollmentEndDate(start.plusMonths(2));
        assertUnenrollmentInvalid(course);
        course.setEnrollmentStartDate(start);
        course.setEnrollmentEndDate(null);
        assertUnenrollmentInvalid(course);
        course.setEnrollmentEndDate(start.plusMonths(1));
        course.setUnenrollmentEndDate(start.plusMonths(1));
        assertUnenrollmentInvalid(course);
        course.setUnenrollmentEndDate(start.plusMonths(7));
        assertUnenrollmentInvalid(course);
        course.setUnenrollmentEndDate(course.getEndDate());
        assertThatCode(() -> CourseValidator.validateUnenrollmentEndDate(course)).doesNotThrowAnyException();
        assertThat(course.getUnenrollmentEndDate()).isEqualTo(course.getEndDate());
    }

    private static void assertUnenrollmentInvalid(Course course) {
        assertThatThrownBy(() -> CourseValidator.validateUnenrollmentEndDate(course)).isInstanceOfSatisfying(BadRequestAlertException.class,
                failure -> assertThat(failure.getErrorKey()).isEqualTo("unenrollmentEndDateInvalid"));
    }
}
