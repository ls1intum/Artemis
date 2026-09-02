package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVariantGroupRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.ParticipationTestRepository;
import de.tum.cit.aet.artemis.lecture.api.SlideApi;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCreationUpdateService;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;

/**
 * A variant group is persisted before it can be attached to its course — the course owns the collection that
 * writes the FK — and the codebase runs services without {@code @Transactional}, so the two writes have to be
 * made safe by hand. These tests pin that neither half can leave a course-less group row behind: no course query
 * would ever return it, so nothing would clean it up.
 */
class ExerciseVariantGroupServiceCreateGroupTest {

    private static final long COURSE_ID = 3L;

    private ExerciseVariantGroupRepository exerciseVariantGroupRepository;

    private CourseTestRepository courseRepository;

    private ExerciseVariantGroupService service;

    private ExerciseVariantGroup group;

    private ExerciseVariantGroup savedGroup;

    @BeforeEach
    void setUp() {
        exerciseVariantGroupRepository = mock(ExerciseVariantGroupRepository.class);
        courseRepository = mock(CourseTestRepository.class);
        service = new ExerciseVariantGroupService(exerciseVariantGroupRepository, mock(ExerciseTestRepository.class), courseRepository,
                mock(ProgrammingExerciseCreationUpdateService.class), mock(ParticipationTestRepository.class), mock(ExerciseService.class), mock(ExerciseVersionService.class),
                mock(InstanceMessageSendService.class), mock(QuizExerciseService.class), Optional.<SlideApi>empty());

        group = new ExerciseVariantGroup();
        group.setTitle("Variants of the quiz");
        savedGroup = new ExerciseVariantGroup();
        savedGroup.setId(9L);
        savedGroup.setTitle(group.getTitle());
        when(exerciseVariantGroupRepository.save(group)).thenReturn(savedGroup);

        Course course = new Course();
        course.setId(COURSE_ID);
        // The repository's ElseThrow lookup is a default method, which a mocked interface does not execute.
        when(courseRepository.findWithEagerExerciseVariantGroupsByIdElseThrow(COURSE_ID)).thenReturn(course);
    }

    @Test
    void deletesTheNewGroupWhenAttachingItToTheCourseFails() {
        IllegalStateException attachmentFailed = new IllegalStateException("course save failed");
        when(courseRepository.save(any(Course.class))).thenThrow(attachmentFailed);

        assertThat(catchThrowable(() -> service.createGroup(COURSE_ID, group))).isSameAs(attachmentFailed);
        verify(exerciseVariantGroupRepository).delete(savedGroup);
    }

    @Test
    void persistsNothingWhenTheCourseDoesNotExist() {
        EntityNotFoundException unknownCourse = new EntityNotFoundException("Course", 404L);
        when(courseRepository.findWithEagerExerciseVariantGroupsByIdElseThrow(404L)).thenThrow(unknownCourse);

        assertThat(catchThrowable(() -> service.createGroup(404L, group))).isSameAs(unknownCourse);
        verify(exerciseVariantGroupRepository, never()).save(any());
    }

    @Test
    void returnsThePersistedGroupOnceItIsAttached() {
        assertThat(service.createGroup(COURSE_ID, group)).isSameAs(savedGroup);
        verify(courseRepository).save(any(Course.class));
        verify(exerciseVariantGroupRepository, never()).delete(any());
    }
}
