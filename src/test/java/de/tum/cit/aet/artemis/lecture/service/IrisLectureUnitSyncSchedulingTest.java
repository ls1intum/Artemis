package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ScheduledFuture;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import de.tum.cit.aet.artemis.lecture.repository.IrisLectureUnitSyncStateRepository;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.AttachmentVideoUnitTestRepository;

class IrisLectureUnitSyncSchedulingTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

    private final AttachmentVideoUnitTestRepository units = mock(AttachmentVideoUnitTestRepository.class);

    private final IrisLectureUnitSyncStateRepository states = mock(IrisLectureUnitSyncStateRepository.class);

    private final IrisLectureUnitSyncDispatchService dispatch = mock(IrisLectureUnitSyncDispatchService.class);

    private final IrisLectureUnitSyncEventListener listener = new IrisLectureUnitSyncEventListener(units, states, dispatch, mock(SlideRepository.class),
            mock(IrisLectureUnitSyncService.class));

    @Test
    void testConfigurationPreventsBackgroundSyncDuringHttpMockSetupButAllowsExplicitCalls() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("config/application.yml"));
        var scheduled = new ArrayList<ScheduledWork>();
        try (var context = schedule(yaml.getObject(), scheduled)) {
            assertThat(context.getBean(IrisLectureUnitSyncEventListener.class)).isSameAs(listener);
            assertThat(scheduled).hasSize(2);
            // Advance well past the five-minute retry that interrupted Iris mock expectation registration in CI.
            runDue(scheduled, NOW.plus(Duration.ofHours(2)));
            verifyNoInteractions(units, states, dispatch);

            listener.retryDirtyStates();
            listener.backfillMissingSyncStates();
            verify(states).findTop50ByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(any(), any());
            verify(units).findUnitsMissingIrisSyncStateFromActiveCourses(any(), any());
        }
    }

    @Test
    void productionDefaultsStillStartBothJobsImmediatelyAndRepeatEveryFiveMinutes() {
        var scheduled = new ArrayList<ScheduledWork>();
        try (var context = schedule(new Properties(), scheduled)) {
            assertThat(context.getBean(IrisLectureUnitSyncEventListener.class)).isSameAs(listener);
            assertThat(scheduled).hasSize(2).allSatisfy(work -> assertThat(work.interval()).isEqualTo(Duration.ofMinutes(5)));
            runDue(scheduled, NOW);
            verify(states).findTop50ByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(any(), any());
            verify(units).findUnitsMissingIrisSyncStateFromActiveCourses(any(), any());
        }
    }

    private GenericApplicationContext schedule(Properties properties, List<ScheduledWork> scheduled) {
        // Use Spring's real annotation processor and a controlled clock, without sleeps, a database or a scheduler thread.
        var scheduler = mock(TaskScheduler.class, invocation -> {
            if (invocation.getMethod().getName().equals("getClock")) {
                return Clock.fixed(NOW, ZoneOffset.UTC);
            }
            if (invocation.getMethod().getName().equals("scheduleAtFixedRate")) {
                int lastArgument = invocation.getArguments().length - 1;
                scheduled.add(new ScheduledWork(invocation.getArgument(0), lastArgument == 2 ? invocation.getArgument(1) : NOW, invocation.getArgument(lastArgument)));
                return mock(ScheduledFuture.class);
            }
            return RETURNS_DEFAULTS.answer(invocation);
        });
        var context = new GenericApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new PropertiesPropertySource("configuration", properties));
        context.registerBean(TaskScheduler.class, () -> scheduler);
        context.registerBean(ScheduledAnnotationBeanPostProcessor.class);
        context.registerBean(IrisLectureUnitSyncEventListener.class, () -> listener);
        context.refresh();
        return context;
    }

    private static void runDue(List<ScheduledWork> scheduled, Instant time) {
        scheduled.stream().filter(work -> !work.start().isAfter(time)).forEach(work -> work.task().run());
    }

    private record ScheduledWork(Runnable task, Instant start, Duration interval) {
    }
}
