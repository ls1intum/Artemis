package de.tum.cit.aet.artemis.admin.service.telemetry;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING;

import java.time.Instant;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Registers the readiness listener eagerly without pulling telemetry dependencies into the startup bean graph. */
@Component
@Lazy(false)
@Profile(PROFILE_CORE_AND_SCHEDULING)
public class TelemetryStartupListener {

    private final ApplicationContext applicationContext;

    public TelemetryStartupListener(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        if (event.getApplicationContext() == applicationContext) {
            applicationContext.getBean(TelemetryService.class).scheduleTelemetry(Instant.ofEpochMilli(applicationContext.getStartupDate()),
                    Instant.ofEpochMilli(event.getTimestamp()));
        }
    }
}
