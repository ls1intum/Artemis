package de.tum.in.www1.artemis.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.ZonedDateTime;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

class CourseTest {

    private static Stream<Arguments> validateOnlineCourseAndEnrollmentEnabledProvider() {
        Course course1 = new Course();
        course1.setOnlineCourse(true);
        course1.setEnrollmentEnabled(false);

        Course course2 = new Course();
        course2.setOnlineCourse(false);
        course2.setEnrollmentEnabled(true);

        Course course3 = new Course();
        course3.setOnlineCourse(false);
        course3.setEnrollmentEnabled(false);

        Course course4 = new Course();
        course4.setOnlineCourse(true);
        course4.setEnrollmentEnabled(true);

        return Stream.of(Arguments.of(course1, false), Arguments.of(course2, false), Arguments.of(course3, false), Arguments.of(course4, true));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("validateOnlineCourseAndEnrollmentEnabledProvider")
    void testValidateOnlineCourseAndEnrollmentEnabled(Course course, boolean expectException) {
        if (expectException) {
            assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> course.validateOnlineCourseAndEnrollmentEnabled());
        }
        else {
            assertThatCode(() -> course.validateOnlineCourseAndEnrollmentEnabled()).doesNotThrowAnyException();
        }
    }

    private static Stream<Arguments> validateEnrollmentConfirmationMessageProvider() {
        Course course1 = new Course();
        course1.setEnrollmentConfirmationMessage("some valid message");

        Course course2 = new Course();
        course2.setEnrollmentConfirmationMessage("some invalid message" + "x".repeat(2000));

        return Stream.of(Arguments.of(course1, false), Arguments.of(course2, true));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("validateEnrollmentConfirmationMessageProvider")
    void testValidateEnrollmentConfirmationMessage(Course course, boolean expectException) {
        if (expectException) {
            assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> course.validateEnrollmentConfirmationMessage());
        }
        else {
            assertThatCode(() -> course.validateEnrollmentConfirmationMessage()).doesNotThrowAnyException();
        }
    }

    private static Stream<Arguments> validateStartAndEndDateProvider() {
        ZonedDateTime pastTimeStamp = ZonedDateTime.now().minusDays(1);
        ZonedDateTime futureTimeStamp = ZonedDateTime.now().plusDays(1);

        Course course1 = new Course();
        course1.setStartDate(pastTimeStamp);
        course1.setEndDate(futureTimeStamp);

        Course course2 = new Course();
        course2.setStartDate(futureTimeStamp);
        course2.setEndDate(pastTimeStamp);

        return Stream.of(Arguments.of(course1, false), Arguments.of(course2, true));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("validateStartAndEndDateProvider")
    void testValidateStartAndEndDate(Course course, boolean expectException) {
        if (expectException) {
            assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> course.validateStartAndEndDate());
        }
        else {
            assertThatCode(() -> course.validateStartAndEndDate()).doesNotThrowAnyException();
        }
    }

    private static Course createCourse(ZonedDateTime start, ZonedDateTime end, ZonedDateTime enrollmentStart, ZonedDateTime enrollmentEnd) {
        Course course = new Course();
        course.setStartDate(start);
        course.setEndDate(end);
        course.setEnrollmentEnabled(true);
        course.setEnrollmentStartDate(enrollmentStart);
        course.setEnrollmentEndDate(enrollmentEnd);
        return course;
    }

    private static Stream<Arguments> validateEnrollmentStartAndEndDateProvider() {
        ZonedDateTime pastTimeStamp1 = ZonedDateTime.now().minusDays(1);
        ZonedDateTime pastTimeStamp2 = ZonedDateTime.now().minusDays(2);
        ZonedDateTime futureTimeStamp1 = ZonedDateTime.now().plusDays(1);
        ZonedDateTime futureTimeStamp2 = ZonedDateTime.now().plusDays(2);

        return Stream.of(Arguments.of(createCourse(pastTimeStamp1, futureTimeStamp2, pastTimeStamp2, futureTimeStamp1), false),
                Arguments.of(createCourse(pastTimeStamp1, futureTimeStamp2, futureTimeStamp1, pastTimeStamp2), true),
                Arguments.of(createCourse(null, null, pastTimeStamp2, futureTimeStamp1), true),
                Arguments.of(createCourse(futureTimeStamp2, pastTimeStamp1, pastTimeStamp2, futureTimeStamp1), true),
                Arguments.of(createCourse(pastTimeStamp2, futureTimeStamp2, pastTimeStamp1, futureTimeStamp1), true),
                Arguments.of(createCourse(pastTimeStamp1, futureTimeStamp1, pastTimeStamp2, futureTimeStamp2), true));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("validateEnrollmentStartAndEndDateProvider")
    void testValidateEnrollmentStartAndEndDate(Course course, boolean expectException) {
        if (expectException) {
            assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> course.validateEnrollmentStartAndEndDate());
        }
        else {
            assertThatCode(() -> course.validateEnrollmentStartAndEndDate()).doesNotThrowAnyException();
        }
    }

    private static Stream<Arguments> validateUnenrollmentEndDateProvider() {
        ZonedDateTime pastTimeStamp1 = ZonedDateTime.now().minusDays(1);
        ZonedDateTime pastTimeStamp2 = ZonedDateTime.now().minusDays(3);
        ZonedDateTime futureTimeStamp1 = ZonedDateTime.now().plusDays(1);
        ZonedDateTime futureTimeStamp2 = ZonedDateTime.now().plusDays(3);

        Course course1 = createCourse(pastTimeStamp1, futureTimeStamp2, pastTimeStamp2, futureTimeStamp1);
        course1.setUnenrollmentEnabled(true);
        course1.setUnenrollmentEndDate(ZonedDateTime.now().plusDays(2));

        Course course2 = createCourse(pastTimeStamp1, futureTimeStamp2, null, null);
        course2.setUnenrollmentEnabled(true);
        course2.setUnenrollmentEndDate(ZonedDateTime.now().plusDays(2));

        Course course3 = createCourse(pastTimeStamp1, futureTimeStamp2, pastTimeStamp2, futureTimeStamp1);
        course3.setUnenrollmentEnabled(true);
        course3.setUnenrollmentEndDate(ZonedDateTime.now());

        Course course4 = createCourse(pastTimeStamp1, futureTimeStamp2, pastTimeStamp2, futureTimeStamp1);
        course4.setUnenrollmentEnabled(true);
        course4.setUnenrollmentEndDate(ZonedDateTime.now().plusDays(4));

        return Stream.of(Arguments.of(course1, false), Arguments.of(course2, true));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("validateUnenrollmentEndDateProvider")
    void testValidateUnenrollmentEndDate(Course course, boolean expectException) {
        if (expectException) {
            assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> course.validateUnenrollmentEndDate());
        }
        else {
            assertThatCode(() -> course.validateUnenrollmentEndDate()).doesNotThrowAnyException();
        }
    }
}
