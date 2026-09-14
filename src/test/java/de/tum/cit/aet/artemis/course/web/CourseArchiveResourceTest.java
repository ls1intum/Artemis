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

    @Test
    void archiveCourseRejectsConflictBeforeSchedulingAsyncWork() {
        long courseId = 42L;
        Course course = new Course();
        course.setId(courseId);
        course.setEndDate(ZonedDateTime.now().minusDays(1));

        CourseRepository courseRepository = mock(CourseRepository.class);
        AuthorizationCheckService authorizationCheckService = mock(AuthorizationCheckService.class);
        CourseArchiveService courseArchiveService = mock(CourseArchiveService.class);
        CourseOperationProgressService progressService = mock(CourseOperationProgressService.class);
        var conflict = new ConflictException("Another operation is already in progress for this course", Course.ENTITY_NAME, "courseOperationInProgress");

        when(courseRepository.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(courseId)).thenReturn(course);
        doThrow(conflict).when(progressService).startOperation(eq(courseId), eq(CourseOperationType.ARCHIVE), eq("Creating directories"), eq(4), any(ZonedDateTime.class));

        var resource = new CourseArchiveResource(courseRepository, authorizationCheckService, mock(UserRepository.class), courseArchiveService, progressService);

        assertThatThrownBy(() -> resource.archiveCourse(courseId)).isSameAs(conflict);
        verifyNoInteractions(courseArchiveService);
        verify(authorizationCheckService).checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
    }
}
