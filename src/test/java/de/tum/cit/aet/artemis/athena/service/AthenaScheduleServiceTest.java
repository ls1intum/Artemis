package de.tum.cit.aet.artemis.athena.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseAthenaConfig;
import de.tum.cit.aet.artemis.course.repository.CourseAthenaConfigRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseLifecycle;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseLifecycleService;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * The scheduler decides on {@code Exercise#areFeedbackSuggestionsEnabled()}, which reads the lazy Athena configuration
 * off the course. Neither of the two callers hands over an exercise that carries it - the startup query filters on it
 * with a join rather than a fetch, and the message listener reads the exercise by id - so the configuration has to be
 * resolved here. Without that the predicate answers false for every exercise and cancels the tasks it just selected.
 */
@ExtendWith(MockitoExtension.class)
class AthenaScheduleServiceTest {

    private static final long EXERCISE_ID = 42L;

    @Mock
    private ExerciseLifecycleService exerciseLifecycleService;

    @Mock
    private ExerciseRepository exerciseRepository;

    @Mock
    private ProfileService profileService;

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private AthenaSubmissionSendingService athenaSubmissionSendingService;

    @Mock
    private CourseAthenaConfigRepository courseAthenaConfigRepository;

    @InjectMocks
    private AthenaScheduleService athenaScheduleService;

    /** An exercise the way both callers hand it over: a course whose Athena configuration nobody resolved. */
    private static Exercise exerciseWithUnresolvedConfiguration() {
        Course course = new Course();
        course.setId(7L);
        Exercise exercise = new TextExercise();
        exercise.setId(EXERCISE_ID);
        exercise.setCourse(course);
        exercise.setDueDate(ZonedDateTime.now().plusDays(1));
        return exercise;
    }

    private void answerWithGradingFeedback(boolean enabled) {
        doAnswer(invocation -> {
            Exercise exercise = invocation.getArgument(0);
            CourseAthenaConfig athenaConfig = new CourseAthenaConfig();
            athenaConfig.setGradingFeedbackEnabled(enabled);
            exercise.getCourseViaExerciseGroupOrCourseMember().setAthenaConfig(athenaConfig);
            return null;
        }).when(courseAthenaConfigRepository).attachToCourseOf(any());
    }

    @Test
    void shouldScheduleWhenTheCourseHasGradingFeedbackEnabled() {
        answerWithGradingFeedback(true);
        Exercise exercise = exerciseWithUnresolvedConfiguration();

        athenaScheduleService.scheduleExerciseForAthenaIfRequired(exercise);

        verify(exerciseLifecycleService).scheduleTask(eq(exercise), eq(ExerciseLifecycle.DUE), any(Runnable.class));
    }

    @Test
    void shouldNotScheduleWhenTheCourseHasGradingFeedbackDisabled() {
        answerWithGradingFeedback(false);

        athenaScheduleService.scheduleExerciseForAthenaIfRequired(exerciseWithUnresolvedConfiguration());

        verify(exerciseLifecycleService, never()).scheduleTask(any(Exercise.class), any(ExerciseLifecycle.class), any(Runnable.class));
    }
}
