package de.tum.cit.aet.artemis.course.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseOperationType;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.course.service.CourseArchiveService;
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

        when(courseRepository.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(COURSE_ID)).thenReturn(course);
        doThrow(conflict).when(progressService).startOperation(eq(COURSE_ID), eq(CourseOperationType.ARCHIVE), eq("Creating directories"), eq(4), any(ZonedDateTime.class));

        var resource = new CourseArchiveResource(courseRepository, authorizationCheckService, mock(UserRepository.class), courseArchiveService, progressService);

        assertThatThrownBy(() -> resource.archiveCourse(COURSE_ID)).isSameAs(conflict);
        verifyNoInteractions(courseArchiveService);
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

        when(courseRepository.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(COURSE_ID)).thenReturn(course);
        doThrow(error).when(courseArchiveService).archiveCourse(eq(course), any(ZonedDateTime.class));

        var resource = new CourseArchiveResource(courseRepository, mock(AuthorizationCheckService.class), mock(UserRepository.class), courseArchiveService, progressService);

        assertThatThrownBy(() -> resource.archiveCourse(COURSE_ID)).isSameAs(error);
        verify(progressService).releaseOperationClaim(eq(COURSE_ID), eq(CourseOperationType.ARCHIVE), any(ZonedDateTime.class));
    }
}
