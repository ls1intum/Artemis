package de.tum.cit.aet.artemis.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.admin.config.DataCleanupProperties;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseConfiguration;
import de.tum.cit.aet.artemis.notification.dto.MailRecipientDTO;
import de.tum.cit.aet.artemis.notification.service.notifications.MailSendingService;

/**
 * Pure unit tests for the two-phase, data-privacy retention selection and gating logic (grade vs non-grade cutoffs, grace
 * anchored to the actual warning, test-course skip, one-shot warned→reset lifecycle). Collaborators are mocked so no
 * archiving/reset/mail actually happens.
 */
@ExtendWith(MockitoExtension.class)
class CourseDataRetentionServiceTest {

    @Mock
    private CourseTestRepository courseRepository;

    @Mock
    private CourseArchiveService courseArchiveService;

    @Mock
    private CourseResetService courseResetService;

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private MailSendingService mailSendingService;

    // 5-year retention for grade-relevant, 1-year for non-grade-relevant courses, 30-day reset grace.
    private final DataCleanupProperties properties = new DataCleanupProperties(5, 1, 30, 8, 8, 6, 30, false, false, false, false, false, false);

    private CourseDataRetentionService service() {
        return new CourseDataRetentionService(courseRepository, courseArchiveService, courseResetService, userRepository, mailSendingService, properties);
    }

    private Course course(long id, ZonedDateTime endDate, Boolean gradeRelevant, boolean testCourse, ZonedDateTime warnedDate, ZonedDateTime resetDate) {
        Course course = new Course();
        course.setId(id);
        course.setTitle("Course " + id);
        course.setEndDate(endDate);
        course.setTestCourse(testCourse);
        if (gradeRelevant != null || warnedDate != null || resetDate != null) {
            CourseConfiguration configuration = new CourseConfiguration();
            configuration.setGradeRelevant(gradeRelevant == null || gradeRelevant);
            configuration.setResetWarningSentDate(warnedDate);
            configuration.setStudentDataResetDate(resetDate);
            course.setCourseConfiguration(configuration);
        }
        return course;
    }

    @Test
    void findsOnlyCoursesPastTheirRetentionDeadlineAndNotYetWarnedForWarning() {
        ZonedDateTime now = ZonedDateTime.now();
        Course nonGradeDue = course(1, now.minusYears(2), false, false, null, null); // > 1y, not warned -> due
        Course nonGradeTooRecent = course(2, now.minusMonths(6), false, false, null, null); // < 1y -> not due
        Course alreadyWarned = course(3, now.minusYears(2), false, false, now.minusDays(5), null); // warned -> skip
        Course alreadyReset = course(4, now.minusYears(2), false, false, now.minusDays(40), now.minusDays(5)); // reset -> skip
        Course testCourse = course(5, now.minusYears(2), false, true, null, null); // test course -> skip
        Course gradeNotYetDue = course(6, now.minusYears(2), true, false, null, null); // needs 5y -> not due
        Course gradeDue = course(7, now.minusYears(6), true, false, null, null); // > 5y -> due

        when(courseRepository.findAllWithCourseConfigurationByEndDateBefore(any()))
                .thenReturn(List.of(nonGradeDue, nonGradeTooRecent, alreadyWarned, alreadyReset, testCourse, gradeNotYetDue, gradeDue));

        assertThat(service().findCoursesDueForWarning()).extracting(Course::getId).containsExactlyInAnyOrder(1L, 7L);
    }

    @Test
    void findsOnlyWarnedCoursesPastGraceAndNotYetResetForReset() {
        ZonedDateTime now = ZonedDateTime.now();
        Course warnedPastGrace = course(1, now.minusYears(2), false, false, now.minusDays(40), null); // warned > 30d ago -> due
        Course warnedWithinGrace = course(2, now.minusYears(2), false, false, now.minusDays(10), null); // warned < 30d ago -> not due
        Course alreadyReset = course(3, now.minusYears(2), false, false, now.minusDays(40), now.minusDays(5)); // already reset -> skip
        Course warnedTestCourse = course(4, now.minusYears(2), false, true, now.minusDays(40), null); // test course -> skip

        when(courseRepository.findAllWithResetWarningSent()).thenReturn(List.of(warnedPastGrace, warnedWithinGrace, alreadyReset, warnedTestCourse));

        assertThat(service().findCoursesDueForReset()).extracting(Course::getId).containsExactly(1L);
    }

    @Test
    void warnsAndArchivesDueCoursesEmailsInstructorsAndStampsWarningDate() {
        ZonedDateTime now = ZonedDateTime.now();
        Course due = course(1, now.minusYears(2), false, false, null, null);
        when(courseRepository.findAllWithCourseConfigurationByEndDateBefore(any())).thenReturn(List.of(due));
        when(courseRepository.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(1L)).thenReturn(due);
        when(courseArchiveService.archiveCourseSynchronously(due)).thenReturn(true);
        when(mailSendingService.isMailConfigured()).thenReturn(true);
        User instructor = new User();
        instructor.setLogin("instructor1");
        instructor.setEmail("instructor1@artemis.test");
        instructor.setActivated(true);
        when(userRepository.getInstructors(due)).thenReturn(Set.of(instructor));

        int warned = service().warnAndArchiveDueCourses();

        assertThat(warned).isEqualTo(1);
        verify(courseArchiveService).archiveCourseSynchronously(due);
        verify(mailSendingService).buildAndSendAsync(any(MailRecipientDTO.class), eq("email.courseStudentDataResetWarning.title"), anyList(),
                eq("mail/courseStudentDataResetWarningEmail"), anyMap());
        verify(courseRepository).save(due);
        assertThat(due.getCourseConfiguration()).isNotNull();
        assertThat(due.getCourseConfiguration().getResetWarningSentDate()).isNotNull();
    }

    @Test
    void doesNotAdvanceLifecycleWhenMailIsDisabled() {
        ZonedDateTime now = ZonedDateTime.now();
        Course due = course(1, now.minusYears(2), false, false, null, null);
        when(courseRepository.findAllWithCourseConfigurationByEndDateBefore(any())).thenReturn(List.of(due));
        when(courseRepository.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(1L)).thenReturn(due);
        when(courseArchiveService.archiveCourseSynchronously(due)).thenReturn(true);
        when(mailSendingService.isMailConfigured()).thenReturn(false);

        int warned = service().warnAndArchiveDueCourses();

        // Nobody could be warned, so the course must stay retryable and NOT be scheduled for a reset.
        assertThat(warned).isZero();
        verify(courseRepository, never()).save(any());
        assertThat(due.getCourseConfiguration() == null || due.getCourseConfiguration().getResetWarningSentDate() == null).isTrue();
    }

    @Test
    void doesNotAdvanceLifecycleWhenNoEligibleInstructor() {
        ZonedDateTime now = ZonedDateTime.now();
        Course due = course(1, now.minusYears(2), false, false, null, null);
        when(courseRepository.findAllWithCourseConfigurationByEndDateBefore(any())).thenReturn(List.of(due));
        when(courseRepository.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(1L)).thenReturn(due);
        when(courseArchiveService.archiveCourseSynchronously(due)).thenReturn(true);
        when(mailSendingService.isMailConfigured()).thenReturn(true);
        // instructor is not activated -> not eligible, so no warning is dispatched
        User instructor = new User();
        instructor.setLogin("instructor1");
        instructor.setEmail("instructor1@artemis.test");
        instructor.setActivated(false);
        when(userRepository.getInstructors(due)).thenReturn(Set.of(instructor));

        int warned = service().warnAndArchiveDueCourses();

        assertThat(warned).isZero();
        verify(mailSendingService, never()).buildAndSendAsync(any(), any(), anyList(), any(), anyMap());
        verify(courseRepository, never()).save(any());
    }

    @Test
    void doesNotWarnOrEmailWhenArchivingFails() {
        ZonedDateTime now = ZonedDateTime.now();
        Course due = course(1, now.minusYears(2), false, false, null, null);
        when(courseRepository.findAllWithCourseConfigurationByEndDateBefore(any())).thenReturn(List.of(due));
        when(courseRepository.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(1L)).thenReturn(due);
        when(courseArchiveService.archiveCourseSynchronously(due)).thenReturn(false);

        int warned = service().warnAndArchiveDueCourses();

        assertThat(warned).isZero();
        verifyNoInteractions(mailSendingService);
        verify(courseRepository, never()).save(any());
    }

    @Test
    void resetsStudentDataOfDueCoursesAndStampsResetDate() {
        ZonedDateTime now = ZonedDateTime.now();
        Course due = course(1, now.minusYears(2), false, false, now.minusDays(40), null);
        Course withinGrace = course(2, now.minusYears(2), false, false, now.minusDays(10), null);
        when(courseRepository.findAllWithResetWarningSent()).thenReturn(List.of(due, withinGrace));

        int reset = service().resetDueCourses();

        assertThat(reset).isEqualTo(1);
        verify(courseResetService).resetStudentData(1L);
        verify(courseResetService, never()).resetStudentData(2L);
        assertThat(due.getCourseConfiguration().getStudentDataResetDate()).isNotNull();
    }
}
