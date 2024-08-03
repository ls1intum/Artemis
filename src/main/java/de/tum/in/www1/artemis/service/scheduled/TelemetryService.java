package de.tum.in.www1.artemis.service.scheduled;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_SCHEDULING;

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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

@Service
@Profile(PROFILE_SCHEDULING)
public class TelemetryService {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TelemetryData(String version, String serverUrl, String universityName, String contact, List<String> profiles, String mainAdminName) {
    };

    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);

    private final Environment env;

    @Value("${artemis.telemetry.enabled}")
    private boolean useTelemetry;

    @Value("${artemis.telemetry.destination}")
    private String destination;

    @Value("${artemis.version}")
    private String version;

    @Value("${server.url}")
    private String serverUrl;

    @Value("${info.contact}")
    private String contact;

    public TelemetryService(Environment env) {
        this.env = env;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void sendTelemetry() {
        if (!useTelemetry) {
            return;
        }

        log.info("Sending telemetry information");
        List<String> activeProfiles = Arrays.asList(env.getActiveProfiles());

        TelemetryData telemetryData = new TelemetryData(version, serverUrl, "??", contact, activeProfiles, "??");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        RestTemplate restTemplate = new RestTemplate();
        ObjectWriter objectWriter = new ObjectMapper().writer().withDefaultPrettyPrinter();
        try {
            var telemetryJson = objectWriter.writeValueAsString(telemetryData);
            HttpEntity<String> requestEntity = new HttpEntity<>(telemetryJson, headers);
            ResponseEntity<String> response = restTemplate.exchange(destination + "/telemetry", HttpMethod.POST, requestEntity, String.class);
            log.info("TELEMETRY: {}", response.getBody());
        }
        catch (JsonProcessingException e) {
            log.warn("JsonProcessingException in sendTelemetry: {}", e.getMessage());
        }
        catch (Exception e) {
            log.warn("Exception in sendTelemetry: {}", e.getMessage());
        }
    }
}
