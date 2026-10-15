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
import org.mockito.ArgumentCaptor;

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
 * A variant group holds its own {@code course_id}, so it is written once, with its course already set. These tests pin
 * that: the group reaches the repository naming its course, and an unknown course persists nothing at all. Before the
 * key moved onto the group the course had to be attached by a second statement, and a group that lost the race was
 * left belonging to nothing — invisible to every course query, so nothing would ever clean it up.
 */
class ExerciseVariantGroupServiceCreateGroupTest {

    private static final long COURSE_ID = 3L;

    private ExerciseVariantGroupRepository exerciseVariantGroupRepository;

    private CourseTestRepository courseRepository;

    private ExerciseVariantGroupService service;

    private ExerciseVariantGroup group;

    private ExerciseVariantGroup savedGroup;

    private Course course;

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

        course = new Course();
        course.setId(COURSE_ID);
        // The repository's ElseThrow lookup is a default method, which a mocked interface does not execute.
        when(courseRepository.findByIdElseThrow(COURSE_ID)).thenReturn(course);
    }

    @Test
    void namesTheCourseBeforeTheGroupIsWritten() {
        service.createGroup(COURSE_ID, group);

        ArgumentCaptor<ExerciseVariantGroup> written = ArgumentCaptor.forClass(ExerciseVariantGroup.class);
        verify(exerciseVariantGroupRepository).save(written.capture());
        assertThat(written.getValue().getCourse()).as("the row that reaches the database already belongs to its course").isSameAs(course);
    }

    @Test
    void persistsNothingWhenTheCourseDoesNotExist() {
        EntityNotFoundException unknownCourse = new EntityNotFoundException("Course", 404L);
        when(courseRepository.findByIdElseThrow(404L)).thenThrow(unknownCourse);

        assertThat(catchThrowable(() -> service.createGroup(404L, group))).isSameAs(unknownCourse);
        verify(exerciseVariantGroupRepository, never()).save(any());
    }

    @Test
    void returnsThePersistedGroup() {
        assertThat(service.createGroup(COURSE_ID, group)).isSameAs(savedGroup);
    }
}
