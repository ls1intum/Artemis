package de.tum.cit.aet.artemis.core.service.telemetry;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.core.service.ProfileService;

@Service
@Profile(PROFILE_SCHEDULING)
public class TelemetryService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);

    private final ProfileService profileService;

    private final TelemetrySendingService telemetrySendingService;

    @Value("${artemis.telemetry.destination}")
    private String destination;

    @Value("${artemis.telemetry.enabled}")
    public boolean useTelemetry;

    @Value("${artemis.telemetry.sendAdminDetails}")
    public boolean sendAdminDetails;

    @Value("${eureka.client.enabled:false}")
    public boolean eurekaEnabled;

    public TelemetryService(ProfileService profileService, TelemetrySendingService telemetrySendingService) {
        this.profileService = profileService;
        this.telemetrySendingService = telemetrySendingService;
    }

    /**
     * Sends telemetry to the server specified in artemis.telemetry.destination.
     * This function runs once, at the startup of the application.
     * If telemetry is disabled in artemis.telemetry.enabled, no data is sent.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void sendTelemetry() {
        if (!useTelemetry || profileService.isDevActive()) {
            return;
        }

        log.info("Sending telemetry information");
        try {
            telemetrySendingService.sendTelemetryByPostRequest(eurekaEnabled, sendAdminDetails);
        }
        catch (JsonProcessingException e) {
            log.warn("JsonProcessingException in sendTelemetry.", e);
        }
        catch (Exception e) {
            log.warn("Exception in sendTelemetry, with dst URI: {}", destination, e);
        }
    }
}
