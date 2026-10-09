package de.tum.cit.aet.artemis.admin.service.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.support.TestPropertySourceUtils;

import de.tum.cit.aet.artemis.core.service.ProfileService;

class TelemetryStartupListenerTest {

    @Test
    void listensForReadinessOnSchedulingCoreNode() {
        try (var context = new AnnotationConfigApplicationContext()) {
            var service = mock(TelemetryService.class);
            context.getEnvironment().setActiveProfiles("core", "scheduling");
            context.registerBean(TelemetryService.class, () -> service);
            context.register(TelemetryStartupListener.class);
            context.refresh();
            context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0], context, Duration.ofSeconds(1)));
            verify(service).scheduleTelemetry(any(), any());
        }
    }

    @Test
    void excludedInstancesNeverInitializeSenderAtReadiness() {
        for (String exclusion : new String[] { "disabled", "development", "testServer" }) {
            try (var context = new AnnotationConfigApplicationContext()) {
                context.getEnvironment().setActiveProfiles("core", "scheduling");
                TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, "artemis.telemetry.enabled=" + !exclusion.equals("disabled"),
                        "info.testServer=" + exclusion.equals("testServer"));
                var profiles = mock(ProfileService.class);
                when(profiles.isDevActive()).thenReturn(exclusion.equals("development"));
                var scheduler = mock(TaskScheduler.class);
                context.registerBean(ProfileService.class, () -> profiles);
                context.registerBean(TaskScheduler.class, () -> scheduler);
                context.registerBean(TelemetrySendingService.class, () -> {
                    throw new IllegalStateException("Excluded telemetry must not initialize the sender");
                }, definition -> definition.setLazyInit(true));
                context.register(TelemetryService.class, TelemetryStartupListener.class);
                context.refresh();
                context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0], context, Duration.ofSeconds(1)));
                verifyNoInteractions(scheduler);
                assertThat(context.getBeanFactory().containsSingleton("telemetrySendingService")).isFalse();
            }
        }
    }

    @Test
    void doesNotRegisterOnWorkerOrDedicatedBuildAgent() {
        for (String profile : new String[] { "core", "buildagent", "scheduling" }) {
            try (var context = new AnnotationConfigApplicationContext()) {
                context.getEnvironment().setActiveProfiles(profile);
                context.register(TelemetryStartupListener.class);
                context.refresh();
                assertThat(context.getBeansOfType(TelemetryStartupListener.class)).isEmpty();
            }
        }
    }
}
