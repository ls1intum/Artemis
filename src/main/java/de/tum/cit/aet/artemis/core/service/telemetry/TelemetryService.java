package de.tum.cit.aet.artemis.core.service.telemetry;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.ProfileService;

@Service
@Profile(PROFILE_SCHEDULING)
public class TelemetryService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);

    private final ProfileService profileService;

    private final TelemetrySendingService telemetrySendingService;

    private final boolean useTelemetry;

    private final boolean sendAdminDetails;

    public TelemetryService(ProfileService profileService, TelemetrySendingService telemetrySendingService, @Value("${artemis.telemetry.enabled}") boolean useTelemetry,
            @Value("${artemis.telemetry.sendAdminDetails}") boolean sendAdminDetails) {
        this.profileService = profileService;
        this.telemetrySendingService = telemetrySendingService;
        this.useTelemetry = useTelemetry;
        this.sendAdminDetails = sendAdminDetails;
    }

    /**
     * Sends telemetry data to the server after the application is ready.
     * This method is triggered automatically when the application context is fully initialized.
     * <p>
     * If telemetry is disabled (as specified by the {@code useTelemetry} flag), the task will not be executed.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void sendTelemetry() {
        if (!useTelemetry || profileService.isDevActive()) {
            return;
        }
        log.info("Start sending telemetry data asynchronously");
        telemetrySendingService.sendTelemetryByPostRequest(sendAdminDetails);
    }
}
