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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import de.tum.cit.aet.artemis.core.service.ProfileService;

@Service
@Profile(PROFILE_SCHEDULING)
public class TelemetrySendingService {

    private static final Logger log = LoggerFactory.getLogger(TelemetrySendingService.class);

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TelemetryData(String version, String serverUrl, String operator, List<String> profiles, boolean isProductionInstance, String dataSource, long numberOfNodes,
            long buildAgentCount, String contact, String adminName) {
    }

    private final Environment env;

    private final RestTemplate restTemplate;

    private final EurekaClientService eurekaClientService;

    private final ProfileService profileService;

    public TelemetrySendingService(Environment env, RestTemplate restTemplate, EurekaClientService eurekaClientService, ProfileService profileService) {
        this.env = env;
        this.restTemplate = restTemplate;
        this.eurekaClientService = eurekaClientService;
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

    @Value("${artemis.continuous-integration.concurrent-build-size:0}")
    private long buildAgentCount;

    /**
     * Sends telemetry data to a specified destination via an HTTP POST request asynchronously.
     * The telemetry includes information about the application version, environment, data source,
     * and optionally, administrator details. If Eureka is enabled, the number of registered
     * instances is also included.
     *
     * <p>
     * The method constructs the telemetry data, converts it to JSON, and sends it to a
     * telemetry server. The request is sent asynchronously due to the {@code @Async} annotation.
     *
     * @param eurekaEnabled    a flag indicating whether Eureka is enabled. If {@code true},
     *                             the method retrieves the number of instances registered with Eureka.
     * @param sendAdminDetails a flag indicating whether to include administrator details in the
     *                             telemetry data (such as contact information and admin name).
     * @throws Exception if an error occurs while sending the telemetry data or constructing the request.
     */
    @Async
    public void sendTelemetryByPostRequest(boolean eurekaEnabled, boolean sendAdminDetails, long waitInSeconds) throws Exception {

        long numberOfInstances = 1;

        if (eurekaEnabled) {
            try {
                log.info("Wait {} seconds before querying Eureka.", waitInSeconds);
                Thread.sleep(waitInSeconds * 1000);
            }
            catch (InterruptedException e) {
                log.warn("Waiting for other instances to spin up was interrupted.");
            }

            log.info("Querying other instances from Eureka...");
            numberOfInstances = eurekaClientService.getNumberOfReplicas();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ObjectWriter objectWriter = new ObjectMapper().writer().withDefaultPrettyPrinter();

        var telemetryJson = objectWriter.writeValueAsString(buildTelemetryData(sendAdminDetails, numberOfInstances));
        HttpEntity<String> requestEntity = new HttpEntity<>(telemetryJson, headers);
        var response = restTemplate.postForEntity(destination + "/api/telemetry", requestEntity, String.class);
        log.info("Successfully sent telemetry data. {}", response.getBody());
    }

    /**
     * Retrieves telemetry data for the current system configuration, including details
     * about the active profiles, data source type, and optionally admin contact details.
     *
     * @param sendAdminDetails  whether to include admin contact information in the telemetry data
     * @param numberOfInstances the number of instances to include in the telemetry data
     * @return an instance of {@link TelemetryData} containing the gathered telemetry information
     */
    private TelemetryData buildTelemetryData(boolean sendAdminDetails, long numberOfInstances) {

        TelemetryData telemetryData;
        var dataSource = datasourceUrl.startsWith("jdbc:mysql") ? "mysql" : "postgresql";
        List<String> activeProfiles = Arrays.asList(env.getActiveProfiles());

        String contact = null;
        String adminName = null;
        if (sendAdminDetails) {
            contact = operatorContact;
            adminName = operatorAdminName;
        }
        telemetryData = new TelemetryData(version, serverUrl, operator, activeProfiles, profileService.isProductionActive(), dataSource, numberOfInstances, buildAgentCount,
                contact, adminName);
        return telemetryData;
    }
}
