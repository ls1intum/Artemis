package de.tum.cit.aet.artemis.core.service.telemetry;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.ProfileService;

@Service
@Profile(PROFILE_SCHEDULING)
public class TelemetryService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);

    private final ProfileService profileService;

    private final TaskScheduler taskScheduler;

    private final TelemetrySendingService telemetrySendingService;

    private final boolean useTelemetry;

    private final boolean sendAdminDetails;

    private final boolean eurekaEnabled;

    private final long sendingDelay;

    public TelemetryService(ProfileService profileService, TelemetrySendingService telemetrySendingService, @Value("${artemis.telemetry.enabled}") boolean useTelemetry,
            @Value("${artemis.telemetry.sendAdminDetails}") boolean sendAdminDetails, @Value("${eureka.client.enabled:false}") boolean eurekaEnabled,
            @Value("${artemis.telemetry.sendingDelay:180}") long sendingDelay) {

        this.profileService = profileService;
        this.telemetrySendingService = telemetrySendingService;
        this.taskScheduler = new ThreadPoolTaskScheduler();
        ((ThreadPoolTaskScheduler) this.taskScheduler).initialize();

        this.useTelemetry = useTelemetry;
        this.sendAdminDetails = sendAdminDetails;
        this.eurekaEnabled = eurekaEnabled;
        this.sendingDelay = sendingDelay;
    }

    /**
     * Schedules the sending of telemetry data to the server after the application is ready, after enough time for other instances to start has passed.
     * This method is triggered automatically when the application context is fully initialized.
     * <p>
     * The task will be scheduled to run after a delay, specified by the {@code sendingDelay} variable.
     * This is 180 seconds by default, but can be changed in the configs (Three minutes)
     * If telemetry is disabled (as specified by the {@code useTelemetry} flag), the task will not be scheduled.
     * <p>
     * It uses a {@link TaskScheduler} to schedule the {@link #sendTelemetry()} method to run asynchronously
     * after the delay.
     * <p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void scheduleTelemetryTask() {
        if (!useTelemetry || profileService.isDevActive()) {
            return;
        }

        log.info("Scheduling telemetry information to be sent after {} seconds delay", sendingDelay);
        Instant startTime = Instant.now().plus(Duration.ofSeconds(sendingDelay));
        taskScheduler.schedule(this::sendTelemetry, startTime);
    }

    /**
     * Sends telemetry to the server specified in artemis.telemetry.destination.
     * If telemetry is disabled in artemis.telemetry.enabled, no data is sent.
     */
    private void sendTelemetry() {
        log.info("Sending telemetry information asynchronously after {} seconds delay", sendingDelay);
        telemetrySendingService.sendTelemetryByPostRequest(eurekaEnabled, sendAdminDetails);
    }
}
