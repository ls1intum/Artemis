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
    private String contact;

    @Value("${artemis.telemetry.sendAdminDetails}")
    private boolean sendAdminDetails;

    @Value("${artemis.telemetry.destination}")
    private String destination;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${eureka.client.enabled}")
    private boolean eurekaEnabled;

    @Value("${artemis.continuous-integration.concurrent-build-size}")
    private long buildAgentCount;

    /**
     * Assembles the telemetry data, and sends it to the external telemetry server.
     *
     * @throws Exception if the writing the telemetry data to a json format fails, or the connection to the telemetry server fails
     */
    @Async
    public void sendTelemetryByPostRequest() throws Exception {
        List<String> activeProfiles = Arrays.asList(env.getActiveProfiles());
        TelemetryData telemetryData;
        var dataSource = datasourceUrl.startsWith("jdbc:mysql") ? "mysql" : "postgresql";
        long numberOfInstances = 1;
        if (eurekaEnabled) {
            // Wait one minute to give other nodes time to connect to the Eureka registry
            Thread.sleep(60000);
            numberOfInstances = eurekaClientService.getNumberOfReplicas();
        }

        if (sendAdminDetails) {
            telemetryData = new TelemetryData(version, serverUrl, operator, activeProfiles, profileService.isProductionActive(), dataSource, numberOfInstances, buildAgentCount,
                    contact, operatorAdminName);
        }
        else {
            telemetryData = new TelemetryData(version, serverUrl, operator, activeProfiles, profileService.isProductionActive(), dataSource, numberOfInstances, buildAgentCount,
                    null, null);
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
