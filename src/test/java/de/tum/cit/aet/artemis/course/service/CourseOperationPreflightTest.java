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

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @ParameterizedTest
    @EnumSource(value = CourseOperationType.class, names = { "DELETE", "RESET" })
    void preflightFailureIsReportedAndReleasesClaim(CourseOperationType type) {
        var claim = new CourseOperationClaim(COURSE_ID, type, ZonedDateTime.parse("2026-01-01T12:00:00Z"), UUID.randomUUID());
        var failure = new IllegalStateException("Cannot read course summary");
        when(progressService.startOperation(eq(COURSE_ID), eq(type), anyString(), anyInt(), any())).thenReturn(claim);
        when(courseAdminService.getCourseSummary(COURSE_ID)).thenThrow(failure);

        assertThatThrownBy(() -> runOperation(type)).isSameAs(failure);

        verify(progressService).failOperation(eq(claim), anyString(), eq(0), anyInt(), eq(0), eq(failure.getMessage()), anyDouble());
        verify(progressService).releaseOperationClaim(claim);
    }

    private void runOperation(CourseOperationType type) {
        if (type == CourseOperationType.DELETE) {
            deletionService.delete(COURSE_ID);
        }
        else {
            resetService.resetStudentData(COURSE_ID);
        }
    }
}
