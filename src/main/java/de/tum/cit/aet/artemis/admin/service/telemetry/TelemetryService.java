package de.tum.cit.aet.artemis.admin.service.telemetry;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.ProfileService;

@Lazy
@Service
@Profile(PROFILE_CORE_AND_SCHEDULING)
public class TelemetryService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);

    private static final Duration STARTUP_DELAY = Duration.ofMinutes(10);

    private final ProfileService profileService;

    // Resolves the lazy sender when the report runs instead of injecting it, so that scheduling on ApplicationReadyEvent does not pull the sender and
    // its dependencies into the startup bean graph; the deferred eager initialization creates them after startup.
    private final ApplicationContext applicationContext;

    private final TaskScheduler taskScheduler;

    private final boolean useTelemetry;

    private final boolean sendAdminDetails;

    private final boolean testServer;

    // Set once the single report is scheduled or the service shuts down; either way no further report may be scheduled.
    private boolean closedForScheduling;

    private ScheduledFuture<?> pendingReport;

    public TelemetryService(ProfileService profileService, ApplicationContext applicationContext, @Qualifier("taskScheduler") TaskScheduler taskScheduler,
            @Value("${artemis.telemetry.enabled:false}") boolean useTelemetry, @Value("${artemis.telemetry.sendAdminDetails:false}") boolean sendAdminDetails,
            @Value("${info.testServer:false}") boolean testServer) {
        this.profileService = profileService;
        this.applicationContext = applicationContext;
        this.taskScheduler = taskScheduler;
        this.useTelemetry = useTelemetry;
        this.sendAdminDetails = sendAdminDetails;
        this.testServer = testServer;
    }

    /**
     * Schedules one report after cluster discovery has had time to settle. The payload is collected when the task runs.
     *
     * @param startedAt the scheduling application's start time
     * @param readyAt   the time the application became ready
     */
    public synchronized void scheduleTelemetry(Instant startedAt, Instant readyAt) {
        if (closedForScheduling || !useTelemetry || profileService.isDevActive() || testServer) {
            return;
        }
        closedForScheduling = true;
        String startupId = UUID.randomUUID().toString();
        Instant sendAt = readyAt.plus(STARTUP_DELAY);
        pendingReport = taskScheduler.schedule(() -> applicationContext.getBean(TelemetrySendingService.class).sendTelemetryByPostRequest(sendAdminDetails, startupId, startedAt),
                sendAt);
        log.info("Scheduled startup telemetry for {}", sendAt);
    }

    @PreDestroy
    public synchronized void cancelTelemetry() {
        closedForScheduling = true;
        if (pendingReport != null) {
            pendingReport.cancel(false);
        }
    }
}
