package de.tum.cit.aet.artemis.notification.service.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.CourseNotification;
import de.tum.cit.aet.artemis.notification.dto.payload.DuplicateTestCasePayloadDTO;
import de.tum.cit.aet.artemis.notification.dto.payload.NewExercisePayloadDTO;
import de.tum.cit.aet.artemis.notification.service.CourseNotificationService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class GroupNotificationServiceCourseTest {

    private final UserTestRepository userRepository = mock(UserTestRepository.class);

    private final CourseNotificationService courseNotificationService = mock(CourseNotificationService.class);

    private final GroupNotificationService groupNotificationService = new GroupNotificationService(userRepository, courseNotificationService);

    private void assertFailsWithoutCourse(Consumer<ProgrammingExercise> call) {
        var exercise = new ProgrammingExercise();
        exercise.setId(11L);

        assertThatThrownBy(() -> call.accept(exercise)).isInstanceOf(IllegalStateException.class).hasMessageContaining("11");
        verifyNoInteractions(userRepository, courseNotificationService);
    }

    @Test
    void notifyAboutChangedTestCases_withoutCourse_throws() {
        assertFailsWithoutCourse(groupNotificationService::notifyEditorAndInstructorGroupsAboutChangedTestCasesForProgrammingExercise);
    }

    @Test
    void notifyAboutBuildRunUpdate_withoutCourse_throws() {
        assertFailsWithoutCourse(groupNotificationService::notifyEditorAndInstructorGroupsAboutBuildRunUpdate);
    }

    @Test
    void notifyAboutDuplicateTestCases_withoutCourse_throws() {
        Consumer<Exercise> call = groupNotificationService::notifyEditorAndInstructorGroupAboutDuplicateTestCasesForExercise;
        assertFailsWithoutCourse(call::accept);
    }

    private TextExercise createExercise(String courseTimeZone, ZonedDateTime releaseDate, ZonedDateTime dueDate) {
        var course = new Course();
        course.setId(7L);
        course.setTitle("Course");
        course.setTimeZone(courseTimeZone);
        var exercise = new TextExercise();
        exercise.setId(11L);
        exercise.setTitle("Exercise");
        exercise.setMaxPoints(10.0);
        exercise.setCourse(course);
        exercise.setReleaseDate(releaseDate);
        exercise.setDueDate(dueDate);
        return exercise;
    }

    private CourseNotification captureSentNotification() {
        var captor = ArgumentCaptor.forClass(CourseNotification.class);
        verify(courseNotificationService).sendCourseNotification(captor.capture(), any());
        return captor.getValue();
    }

    private static String formatInZone(ZonedDateTime date, String zone) {
        return date.withZoneSameInstant(ZoneId.of(zone)).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm z", Locale.ENGLISH));
    }

    @Test
    void notifyAboutReleasedExercise_usesCourseTimeZoneWithTimeAndZone() {
        // both instants are late in the evening in UTC but already after midnight of the next day in Berlin
        var release = ZonedDateTime.of(2026, 7, 1, 22, 15, 0, 0, ZoneOffset.UTC);
        var due = ZonedDateTime.of(2026, 3, 10, 23, 30, 0, 0, ZoneOffset.UTC);

        groupNotificationService.notifyAllGroupsAboutReleasedExercise(createExercise("Europe/Berlin", release, due));

        var payload = (NewExercisePayloadDTO) captureSentNotification().payload();
        assertThat(payload.releaseDate()).isEqualTo("02.07.2026 00:15 CEST");
        assertThat(payload.dueDate()).isEqualTo("11.03.2026 00:30 CET");
    }

    @Test
    void notifyAboutReleasedExercise_supportsZonesBehindUtc() {
        var due = ZonedDateTime.of(2026, 1, 15, 3, 30, 0, 0, ZoneOffset.UTC);

        groupNotificationService.notifyAllGroupsAboutReleasedExercise(createExercise("America/New_York", null, due));

        var payload = (NewExercisePayloadDTO) captureSentNotification().payload();
        assertThat(payload.releaseDate()).isNull();
        assertThat(payload.dueDate()).isEqualTo("14.01.2026 22:30 EST");
    }

    @Test
    void notifyAboutReleasedExercise_withoutCourseTimeZoneFallsBackToServerZone() {
        var due = ZonedDateTime.of(2026, 3, 10, 23, 30, 0, 0, ZoneOffset.UTC);

        groupNotificationService.notifyAllGroupsAboutReleasedExercise(createExercise(null, null, due));

        var payload = (NewExercisePayloadDTO) captureSentNotification().payload();
        assertThat(payload.dueDate()).isEqualTo(formatInZone(due, ZoneId.systemDefault().getId()));
    }

    @Test
    void notifyAboutReleasedExercise_withInvalidCourseTimeZoneFallsBackToServerZone() {
        var due = ZonedDateTime.of(2026, 3, 10, 23, 30, 0, 0, ZoneOffset.UTC);

        groupNotificationService.notifyAllGroupsAboutReleasedExercise(createExercise("Not/AZone", null, due));

        var payload = (NewExercisePayloadDTO) captureSentNotification().payload();
        assertThat(payload.dueDate()).isEqualTo(formatInZone(due, ZoneId.systemDefault().getId()));
    }

    @Test
    void notifyAboutDuplicateTestCases_usesCourseTimeZoneWithTimeAndZone() {
        var release = ZonedDateTime.of(2026, 3, 10, 23, 30, 0, 0, ZoneOffset.UTC);

        groupNotificationService.notifyEditorAndInstructorGroupAboutDuplicateTestCasesForExercise(createExercise("Europe/Berlin", release, null));

        var payload = (DuplicateTestCasePayloadDTO) captureSentNotification().payload();
        assertThat(payload.releaseDate()).isEqualTo("11.03.2026 00:30 CET");
        assertThat(payload.dueDate()).isEqualTo("-");
    }
}
