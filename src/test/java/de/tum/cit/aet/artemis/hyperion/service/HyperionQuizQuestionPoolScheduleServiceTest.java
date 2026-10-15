package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.hyperion.domain.HyperionCourseSettings;
import de.tum.cit.aet.artemis.hyperion.domain.HyperionCourseSettingsEntity;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolBuild;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolBuildTrigger;
import de.tum.cit.aet.artemis.hyperion.repository.HyperionCourseSettingsRepository;

class HyperionQuizQuestionPoolScheduleServiceTest {

    @Mock
    private HyperionCourseSettingsRepository settingsRepository;

    @Mock
    private CourseTestRepository courseRepository;

    @Mock
    private HyperionQuizQuestionPoolBuildService buildService;

    private HyperionQuizQuestionPoolScheduleService scheduler;

    private AutoCloseable mocks;

    private final ZonedDateTime now = ZonedDateTime.parse("2026-10-16T02:00:00+02:00");

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        scheduler = new HyperionQuizQuestionPoolScheduleService(settingsRepository, courseRepository, buildService);
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

        int built = scheduler.buildEnabledPools(now);

        assertThat(built).isEqualTo(2);
        verify(buildService).recordStart(running, QuizQuestionPoolBuildTrigger.SCHEDULED, 12);
        verify(buildService).buildAndRelease(1L, null, 10L, "lease");
        verify(buildService).buildAndRelease(4L, null, 40L, "lease");
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

        int built = scheduler.buildEnabledPools(now);

        assertThat(built).isZero();
        verify(buildService, never()).enqueue(first);
        verify(buildService).release(2L, "lease");
        verify(buildService, never()).buildAndRelease(anyLong(), any(), anyLong(), any());
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
