package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.task.TaskExecutor;

import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.hyperion.domain.HyperionCourseSettings;
import de.tum.cit.aet.artemis.hyperion.domain.HyperionCourseSettingsEntity;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolBuild;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolBuildTrigger;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolLastCompletedBuildDTO;
import de.tum.cit.aet.artemis.hyperion.repository.HyperionCourseSettingsRepository;
import de.tum.cit.aet.artemis.hyperion.repository.QuizQuestionPoolBuildRepository;

class HyperionQuizQuestionPoolScheduleServiceTest {

    @Mock
    private HyperionCourseSettingsRepository settingsRepository;

    @Mock
    private CourseTestRepository courseRepository;

    @Mock
    private QuizQuestionPoolBuildRepository buildRepository;

    @Mock
    private HyperionQuizQuestionPoolBuildService buildService;

    @Mock
    private TaskExecutor executor;

    private HyperionQuizQuestionPoolScheduleService scheduler;

    private AutoCloseable mocks;

    private final ZonedDateTime now = ZonedDateTime.parse("2026-10-16T00:00:00+02:00");

    private final ZonedDateTime openDeadline = ZonedDateTime.now().plusHours(1);

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        scheduler = new HyperionQuizQuestionPoolScheduleService(settingsRepository, courseRepository, buildRepository, buildService, executor, 7);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void buildEnabledPools_buildsEnabledCoursesThatHaveNotEnded() {
        Course running = course(1L, now.plusMonths(2));
        Course ended = course(3L, now.minusDays(1));
        Course openEnded = course(4L, null);
        when(settingsRepository.findAll()).thenReturn(List.of(settings(1L, true), settings(2L, false), settings(3L, true), settings(4L, true)));
        when(courseRepository.findById(1L)).thenReturn(Optional.of(running));
        when(courseRepository.findById(3L)).thenReturn(Optional.of(ended));
        when(courseRepository.findById(4L)).thenReturn(Optional.of(openEnded));
        when(buildService.tryStart(anyLong())).thenReturn("lease");
        when(buildService.enqueue(running)).thenReturn(12);
        when(buildService.enqueue(openEnded)).thenReturn(0);
        when(buildService.recordStart(any(), any(), anyInt())).thenAnswer(invocation -> {
            QuizQuestionPoolBuild build = new QuizQuestionPoolBuild();
            build.setId(((Course) invocation.getArgument(0)).getId() * 10);
            return build;
        });

        int built = scheduler.buildEnabledPools(now, openDeadline);

        assertThat(built).isEqualTo(2);
        verify(buildService).recordStart(running, QuizQuestionPoolBuildTrigger.SCHEDULED, 12);
        verify(buildService).buildAndRelease(1L, null, 10L, "lease", openDeadline);
        verify(buildService).buildAndRelease(4L, null, 40L, "lease", openDeadline);
        verify(buildService, never()).tryStart(2L);
        verify(buildService, never()).tryStart(3L);
        verify(courseRepository, never()).findById(2L);
    }

    @Test
    void buildEnabledPools_skipsACourseWhoseLeaseIsHeldAndReleasesOnEnqueueFailure() {
        Course first = course(1L, null);
        Course second = course(2L, null);
        when(settingsRepository.findAll()).thenReturn(List.of(settings(1L, true), settings(2L, true)));
        when(courseRepository.findById(1L)).thenReturn(Optional.of(first));
        when(courseRepository.findById(2L)).thenReturn(Optional.of(second));
        when(buildService.tryStart(1L)).thenReturn(null);
        when(buildService.tryStart(2L)).thenReturn("lease");
        when(buildService.enqueue(second)).thenThrow(new IllegalStateException("no material"));

        int built = scheduler.buildEnabledPools(now, openDeadline);

        assertThat(built).isZero();
        verify(buildService, never()).enqueue(first);
        verify(buildService).release(2L, "lease");
        verify(buildService, never()).buildAndRelease(anyLong(), any(), anyLong(), any(), any());
    }

    @Test
    void buildEnabledPools_buildsCoursesWithoutACompletedBuildFirstThenTheOldestCompleted() {
        Course recent = course(1L, null);
        Course never = course(2L, null);
        Course old = course(3L, null);
        when(settingsRepository.findAll()).thenReturn(List.of(settings(1L, true), settings(2L, true), settings(3L, true)));
        when(buildRepository.findLastCompletedBuildPerCourse())
                .thenReturn(List.of(new QuizQuestionPoolLastCompletedBuildDTO(1L, now.minusHours(20)), new QuizQuestionPoolLastCompletedBuildDTO(3L, now.minusDays(3))));
        when(courseRepository.findById(1L)).thenReturn(Optional.of(recent));
        when(courseRepository.findById(2L)).thenReturn(Optional.of(never));
        when(courseRepository.findById(3L)).thenReturn(Optional.of(old));
        when(buildService.tryStart(anyLong())).thenReturn("lease");
        when(buildService.recordStart(any(), any(), anyInt())).thenAnswer(invocation -> {
            QuizQuestionPoolBuild build = new QuizQuestionPoolBuild();
            build.setId(((Course) invocation.getArgument(0)).getId());
            return build;
        });

        scheduler.buildEnabledPools(now, openDeadline);

        InOrder order = inOrder(buildService);
        order.verify(buildService).buildAndRelease(2L, null, 2L, "lease", openDeadline);
        order.verify(buildService).buildAndRelease(3L, null, 3L, "lease", openDeadline);
        order.verify(buildService).buildAndRelease(1L, null, 1L, "lease", openDeadline);
    }

    @Test
    void buildEnabledPools_startsNoCourseOnceTheWindowHasEnded() {
        when(settingsRepository.findAll()).thenReturn(List.of(settings(1L, true)));

        int built = scheduler.buildEnabledPools(now, ZonedDateTime.now().minusMinutes(1));

        assertThat(built).isZero();
        verify(courseRepository, never()).findById(anyLong());
        verify(buildService, never()).tryStart(anyLong());
    }

    @Test
    void startScheduledBuilds_runsOnThePoolExecutorWithADeadlineAtTheEndOfTheWindow() {
        Course course = course(1L, null);
        when(settingsRepository.findAll()).thenReturn(List.of(settings(1L, true)));
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(buildService.tryStart(1L)).thenReturn("lease");
        QuizQuestionPoolBuild build = new QuizQuestionPoolBuild();
        build.setId(10L);
        when(buildService.recordStart(any(), any(), anyInt())).thenReturn(build);
        ZonedDateTime before = ZonedDateTime.now();

        scheduler.startScheduledBuilds();

        ArgumentCaptor<Runnable> run = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(run.capture());
        verify(buildService, never()).tryStart(anyLong());
        run.getValue().run();
        ArgumentCaptor<ZonedDateTime> deadline = ArgumentCaptor.forClass(ZonedDateTime.class);
        verify(buildService).buildAndRelease(eq(1L), isNull(), eq(10L), eq("lease"), deadline.capture());
        assertThat(deadline.getValue()).isBetween(before.plusHours(7), ZonedDateTime.now().plusHours(7));
    }

    private static Course course(long id, ZonedDateTime endDate) {
        Course course = new Course();
        course.setId(id);
        course.setEndDate(endDate);
        return course;
    }

    private static HyperionCourseSettingsEntity settings(long courseId, boolean enabled) {
        HyperionCourseSettingsEntity entity = new HyperionCourseSettingsEntity();
        entity.setCourseId(courseId);
        entity.setSettings(new HyperionCourseSettings(enabled, 12, false, 12, 1, 4, 20, 50, 30));
        return entity;
    }
}
