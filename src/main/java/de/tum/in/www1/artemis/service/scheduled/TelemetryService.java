package de.tum.in.www1.artemis.service.scheduled;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

@Service
public class TelemetryService {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TelemetryData(String version, String serverUrl, String universityName, String contact, List<String> profiles, String mainAdminName) {
    }

    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);

    private final Environment env;

    private final RestTemplate restTemplate;

    @Value("${artemis.telemetry.enabled}")
    public boolean useTelemetry;

    @Value("${artemis.telemetry.destination}")
    private String destination;

    @Value("${artemis.version}")
    private String version;

    @Value("${server.url}")
    private String serverUrl;

    @Value("${info.universityAdminName}")
    private String universityName;

    @Value("${info.universityAdminName}")
    private String universityAdminName;

    @Value("${info.contact}")
    private String contact;

    public TelemetryService(Environment env, RestTemplate restTemplate) {
        this.env = env;
        this.restTemplate = restTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void sendTelemetry() {
        if (!useTelemetry && !Arrays.asList(env.getActiveProfiles()).contains("dev") && false) {
            return;
        }

        log.info("Sending telemetry information");
        List<String> activeProfiles = Arrays.asList(env.getActiveProfiles());

        TelemetryData telemetryData = new TelemetryData(version, serverUrl, universityName, contact, activeProfiles, universityAdminName);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ObjectWriter objectWriter = new ObjectMapper().writer().withDefaultPrettyPrinter();
        try {
            var telemetryJson = objectWriter.writeValueAsString(telemetryData);
            HttpEntity<String> requestEntity = new HttpEntity<>(telemetryJson, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(destination + "/telemetry", requestEntity, String.class);
            // HttpMethod.POST, requestEntity, String.class);
            log.info("Successfully sent telemetry data: {}", response.getBody());
        }
        catch (JsonProcessingException e) {
            log.warn("JsonProcessingException in sendTelemetry.", e);
        }
        catch (Exception e) {
            log.warn("Exception in sendTelemetry.", e);
        }
    }
}
