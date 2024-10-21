package de.tum.cit.aet.artemis.core.service.telemetry;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.service.ProfileService;

@Service
@Profile(PROFILE_SCHEDULING)
public class TelemetrySendingService {

    private static final Logger log = LoggerFactory.getLogger(TelemetrySendingService.class);

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TelemetryData(String version, String serverUrl, String operator, List<String> profiles, boolean isProductionInstance, boolean isTestServer, String dataSource,
            String contact, String adminName) {
    }

    private final Environment env;

    private final RestTemplate restTemplate;

    private final ProfileService profileService;

    public TelemetrySendingService(Environment env, RestTemplate restTemplate, ProfileService profileService) {
        this.env = env;
        this.restTemplate = restTemplate;
        this.profileService = profileService;
    }

    @Value("${artemis.version}")
    private String version;

    @Value("${server.url}")
    private String serverUrl;

    @Value("${info.operatorName}")
    private String operator;

    @Value("${info.operatorAdminName}")
    private String operatorAdminName;

    @Value("${info.contact}")
    private String operatorContact;

    @Value("${artemis.telemetry.destination}")
    private String destination;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${info.test-server:false}")
    private boolean isTestServer;

    /**
     * Sends telemetry data to a specified destination via an HTTP POST request asynchronously.
     * The telemetry includes information about the application version, environment, data source,
     * and optionally, administrator details. If Eureka is enabled, the number of registered
     * instances is also included.
     *
     * <p>
     * The method constructs the telemetry data object, converts it to JSON, and sends it to a
     * telemetry collection server. The request is sent asynchronously due to the {@code @Async} annotation.
     *
     * @param sendAdminDetails a flag indicating whether to include administrator details in the
     *                             telemetry data (such as contact information and admin name).
     */
    @Async
    public void sendTelemetryByPostRequest(boolean sendAdminDetails) {

        try {
            String telemetryJson = new ObjectMapper().writer().withDefaultPrettyPrinter().writeValueAsString(buildTelemetryData(sendAdminDetails));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> requestEntity = new HttpEntity<>(telemetryJson, headers);

            log.info("Sending telemetry to {}", destination);
            var response = restTemplate.postForEntity(destination + "/api/telemetry", requestEntity, String.class);
            log.info("Successfully sent telemetry data. {}", response.getBody());
        }
        catch (JsonProcessingException e) {
            log.warn("JsonProcessingException in sendTelemetry.", e);
        }
        catch (Exception e) {
            log.warn("Exception in sendTelemetry, with dst URI: {}", destination, e);
        }
    }

    /**
     * Retrieves telemetry data for the current system configuration, including details
     * about the active profiles, data source type, and optionally admin contact details.
     *
     * @param sendAdminDetails whether to include admin contact information in the telemetry data
     * @return an instance of {@link TelemetryData} containing the gathered telemetry information
     */
    private TelemetryData buildTelemetryData(boolean sendAdminDetails) {
        TelemetryData telemetryData;
        var dataSource = datasourceUrl.startsWith("jdbc:mysql") ? "mysql" : "postgresql";
        List<String> activeProfiles = Arrays.asList(env.getActiveProfiles());

        String contact = null;
        String adminName = null;
        if (sendAdminDetails) {
            contact = operatorContact;
            adminName = operatorAdminName;
        }
        telemetryData = new TelemetryData(version, serverUrl, operator, activeProfiles, profileService.isProductionActive(), isTestServer, dataSource, contact, adminName);
        return telemetryData;
    }
}
