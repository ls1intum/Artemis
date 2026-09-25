package de.tum.cit.aet.artemis.admin.service.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

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
