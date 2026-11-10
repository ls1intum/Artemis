package de.tum.cit.aet.artemis.course.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseOperationType;

@ExtendWith(MockitoExtension.class)
class CourseOperationPreflightTest {

    private static final long COURSE_ID = 42L;

    @Mock
    private CourseAdminService courseAdminService;

    @Mock
    private CourseOperationProgressService progressService;

    @InjectMocks
    private CourseDeletionService deletionService;

    @InjectMocks
    private CourseResetService resetService;

    @ParameterizedTest
    @EnumSource(value = CourseOperationType.class, names = { "DELETE", "RESET" })
    void conflictingOperationDoesNotReadMutableCourseData(CourseOperationType type) {
        var conflict = new ConflictException("Course operation already running", Course.ENTITY_NAME, "courseOperationInProgress");
        when(progressService.startOperation(eq(COURSE_ID), eq(type), anyString(), anyInt(), any())).thenThrow(conflict);

        assertThatThrownBy(() -> runOperation(type)).isSameAs(conflict);

        verifyNoInteractions(courseAdminService);
    }

    @Test
    void resetPreflightFailureIsReportedAndReleasesClaim() {
        var claim = new CourseOperationClaim(COURSE_ID, CourseOperationType.RESET, ZonedDateTime.parse("2026-01-01T12:00:00Z"), UUID.randomUUID());
        var failure = new IllegalStateException("Cannot read course summary");
        when(progressService.startOperation(eq(COURSE_ID), eq(CourseOperationType.RESET), anyString(), anyInt(), any())).thenReturn(claim);
        when(courseAdminService.getCourseSummary(COURSE_ID)).thenThrow(failure);

        assertThatThrownBy(() -> resetService.resetStudentData(COURSE_ID)).isSameAs(failure);

        verify(progressService).failOperation(eq(claim), anyString(), eq(0), anyInt(), eq(0), eq(failure.getMessage()), anyDouble());
        verify(progressService).releaseOperationClaim(claim);
    }

    @Test
    void deletionPreflightFailureIsReportedAndReleasesClaimWithoutThrowing() {
        var claim = new CourseOperationClaim(COURSE_ID, CourseOperationType.DELETE, ZonedDateTime.parse("2026-01-01T12:00:00Z"), UUID.randomUUID());
        var failure = new IllegalStateException("Cannot read course summary");
        when(courseAdminService.getCourseSummary(COURSE_ID)).thenThrow(failure);

        // The deletion runs in the background, so there is no caller to throw to: the failure is reported as progress.
        deletionService.deleteAsync(COURSE_ID, null, claim, administrator());

        verify(progressService).failOperation(eq(claim), anyString(), eq(0), anyInt(), eq(0), eq(failure.getMessage()), anyDouble());
        verify(progressService).releaseOperationClaim(claim);
    }

    private static User administrator() {
        var administrator = new User();
        administrator.setLogin("admin");
        return administrator;
    }

    private void runOperation(CourseOperationType type) {
        if (type == CourseOperationType.DELETE) {
            deletionService.startDeletion(COURSE_ID);
        }
        else {
            resetService.resetStudentData(COURSE_ID);
        }
    }
}
