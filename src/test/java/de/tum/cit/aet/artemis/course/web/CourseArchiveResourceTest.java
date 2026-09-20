package de.tum.cit.aet.artemis.course.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseOperationType;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.course.service.CourseArchiveService;
import de.tum.cit.aet.artemis.course.service.CourseOperationClaim;
import de.tum.cit.aet.artemis.course.service.CourseOperationProgressService;

class CourseArchiveResourceTest {

    private static final long COURSE_ID = 42L;

    private static final ZonedDateTime COURSE_END_DATE = ZonedDateTime.parse("2000-01-01T00:00:00Z");

    @Test
    void archiveCourseRejectsConflictBeforeSchedulingAsyncWork() {
        Course course = new Course();
        course.setId(COURSE_ID);
        course.setEndDate(COURSE_END_DATE);

        CourseRepository courseRepository = mock(CourseRepository.class);
        AuthorizationCheckService authorizationCheckService = mock(AuthorizationCheckService.class);
        CourseArchiveService courseArchiveService = mock(CourseArchiveService.class);
        CourseOperationProgressService progressService = mock(CourseOperationProgressService.class);
        var conflict = new ConflictException("Another operation is already in progress for this course", Course.ENTITY_NAME, "courseOperationInProgress");

        when(courseRepository.findByIdElseThrow(COURSE_ID)).thenReturn(course);
        doThrow(conflict).when(progressService).startOperation(eq(COURSE_ID), eq(CourseOperationType.ARCHIVE), eq("Creating directories"), eq(4), any(ZonedDateTime.class));

        var resource = new CourseArchiveResource(courseRepository, authorizationCheckService, mock(UserRepository.class), courseArchiveService, progressService);

        assertThatThrownBy(() -> resource.archiveCourse(COURSE_ID)).isSameAs(conflict);
        verifyNoInteractions(courseArchiveService);
        verify(courseRepository, never()).findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(COURSE_ID);
        verify(authorizationCheckService).checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
    }

    @Test
    void archiveCourseReleasesClaimWhenAsyncSchedulingExitsWithError() {
        Course course = new Course();
        course.setId(COURSE_ID);
        course.setEndDate(COURSE_END_DATE);

        CourseRepository courseRepository = mock(CourseRepository.class);
        CourseArchiveService courseArchiveService = mock(CourseArchiveService.class);
        CourseOperationProgressService progressService = mock(CourseOperationProgressService.class);
        var error = new AssertionError("executor failed");
        var operationClaim = new CourseOperationClaim(COURSE_ID, CourseOperationType.ARCHIVE, COURSE_END_DATE, UUID.randomUUID());

        when(courseRepository.findByIdElseThrow(COURSE_ID)).thenReturn(course);
        when(courseRepository.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(COURSE_ID)).thenReturn(course);
        when(progressService.startOperation(eq(COURSE_ID), eq(CourseOperationType.ARCHIVE), eq("Creating directories"), eq(4), any(ZonedDateTime.class)))
                .thenReturn(operationClaim);
        doThrow(error).when(courseArchiveService).archiveCourse(course, operationClaim);

        var resource = new CourseArchiveResource(courseRepository, mock(AuthorizationCheckService.class), mock(UserRepository.class), courseArchiveService, progressService);

        assertThatThrownBy(() -> resource.archiveCourse(COURSE_ID)).isSameAs(error);
        verify(progressService).releaseOperationClaim(operationClaim);
    }

    @Test
    void archiveCourseReleasesClaimWhenLoadingExportDataFails() {
        Course course = new Course();
        course.setId(COURSE_ID);
        course.setEndDate(COURSE_END_DATE);

        CourseRepository courseRepository = mock(CourseRepository.class);
        CourseArchiveService courseArchiveService = mock(CourseArchiveService.class);
        CourseOperationProgressService progressService = mock(CourseOperationProgressService.class);
        var failure = new IllegalStateException("Cannot load course exercises");
        var operationClaim = new CourseOperationClaim(COURSE_ID, CourseOperationType.ARCHIVE, COURSE_END_DATE, UUID.randomUUID());

        when(courseRepository.findByIdElseThrow(COURSE_ID)).thenReturn(course);
        when(progressService.startOperation(eq(COURSE_ID), eq(CourseOperationType.ARCHIVE), eq("Creating directories"), eq(4), any(ZonedDateTime.class)))
                .thenReturn(operationClaim);
        when(courseRepository.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(COURSE_ID)).thenThrow(failure);

        var resource = new CourseArchiveResource(courseRepository, mock(AuthorizationCheckService.class), mock(UserRepository.class), courseArchiveService, progressService);

        assertThatThrownBy(() -> resource.archiveCourse(COURSE_ID)).isSameAs(failure);
        verify(progressService).failOperation(operationClaim, "Archive failed", 0, 4, 0, failure.getMessage(), 0);
        verify(progressService).releaseOperationClaim(operationClaim);
        verifyNoInteractions(courseArchiveService);
    }
}
