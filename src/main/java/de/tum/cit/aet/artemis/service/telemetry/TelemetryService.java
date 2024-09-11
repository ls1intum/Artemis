package de.tum.cit.aet.artemis.service.telemetry;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_SCHEDULING;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import de.tum.cit.aet.artemis.service.ProfileService;

@Service
@Profile(PROFILE_SCHEDULING)
public class TelemetryService {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TelemetryData(String version, String serverUrl, String operator, String contact, List<String> profiles, String adminName) {
    }

    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);

    private final Environment env;

    private final RestTemplate restTemplate;

    private final ProfileService profileService;

    @Value("${artemis.telemetry.enabled}")
    public boolean useTelemetry;

    @Value("${artemis.telemetry.sendAdminDetails}")
    private boolean sendAdminDetails;

    @Value("${artemis.telemetry.destination}")
    private String destination;

    @Value("${artemis.version}")
    private String version;

    @Value("${server.url}")
    private String serverUrl;

    @Value("${info.operatorName}")
    private String operator;

    @Value("${info.operatorAdminName}")
    private String operatorAdminName;

    @Value("${info.contact}")
    private String contact;

    public TelemetryService(Environment env, RestTemplate restTemplate, ProfileService profileService) {
        this.env = env;
        this.restTemplate = restTemplate;
        this.profileService = profileService;
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
            sendTelemetryByPostRequest();
        }
        catch (JsonProcessingException e) {
            log.warn("JsonProcessingException in sendTelemetry.", e);
        }
        catch (Exception e) {
            log.warn("Exception in sendTelemetry, with dst URI: {}", destination, e);
        }

    }

    /**
     * Assembles the telemetry data, and sends it to the external telemetry server.
     *
     * @throws Exception if the writing the telemetry data to a json format fails, or the connection to the telemetry server fails
     */
    public void sendTelemetryByPostRequest() throws Exception {
        List<String> activeProfiles = Arrays.asList(env.getActiveProfiles());
        TelemetryData telemetryData;
        if (sendAdminDetails) {
            telemetryData = new TelemetryData(version, serverUrl, operator, contact, activeProfiles, operatorAdminName);
        }
        else {
            telemetryData = new TelemetryData(version, serverUrl, operator, null, activeProfiles, null);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ObjectWriter objectWriter = new ObjectMapper().writer().withDefaultPrettyPrinter();

        var telemetryJson = objectWriter.writeValueAsString(telemetryData);
        HttpEntity<String> requestEntity = new HttpEntity<>(telemetryJson, headers);
        var response = restTemplate.postForEntity(destination + "/api/telemetry", requestEntity, String.class);
        log.info("Successfully sent telemetry data. {}", response.getBody());
    }
}
