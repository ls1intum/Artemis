package de.tum.cit.aet.artemis.admin.service.telemetry;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.core.service.distributed.NodeRegistryService;
import de.tum.cit.aet.artemis.localci.api.LocalCITelemetryApi;

@Lazy
@Service
@Profile(PROFILE_CORE_AND_SCHEDULING)
public class TelemetrySendingService {

    private static final Logger log = LoggerFactory.getLogger(TelemetrySendingService.class);

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TelemetryData(String version, String serverUrl, String operator, List<String> profiles, boolean isProductionInstance, boolean isTestServer, String dataSource,
            String contact, String adminName, boolean isLocalLLMDeploymentEnabled, String universityName, List<String> moduleFeatures, Integer numberOfNodes,
            Integer buildAgentCount, Boolean isMultiNode, String startupId, Instant startedAt) {
    }

    private final Environment env;

    private final RestTemplate restTemplate;

    private final ProfileService profileService;

    private final NodeRegistryService nodeRegistryService;

    private final Optional<LocalCITelemetryApi> localCITelemetryApi;

    public TelemetrySendingService(Environment env, RestTemplate restTemplate, ProfileService profileService, NodeRegistryService nodeRegistryService,
            Optional<LocalCITelemetryApi> localCITelemetryApi) {
        this.env = env;
        this.restTemplate = restTemplate;
        this.profileService = profileService;
        this.nodeRegistryService = nodeRegistryService;
        this.localCITelemetryApi = localCITelemetryApi;
    }

    @Value("${artemis.version}")
    private String version;

    @Value("${server.url}")
    private String serverUrl;

    @Value("${info.operatorName:}")
    private String operator;

    @Value("${info.universityName:}")
    private String universityName;

    @Value("${info.operatorAdminName:}")
    private String operatorAdminName;

    @Value("${info.contact}")
    private String operatorContact;

    @Value("${artemis.telemetry.destination}")
    private String destination;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${info.testServer:false}")
    private boolean isTestServer;

    @Value("${info.localLLMDeploymentEnabled:false}")
    private boolean isLocalLLMDeploymentEnabled;

    /**
     * Sends telemetry data to a specified destination via an HTTP POST request asynchronously.
     * The telemetry includes information about the application version, environment, data source,
     * enabled module features, connected nodes and build agents, and optionally administrator details.
     *
     * <p>
     * The method constructs the telemetry data object and posts it to a telemetry collection server, which receives it as JSON.
     * The request is sent asynchronously due to the {@code @Async} annotation.
     *
     * @param sendAdminDetails a flag indicating whether to include administrator details in the
     *                             telemetry data (such as contact information and admin name).
     * @param startupId        stable identifier of this scheduling-node startup
     * @param startedAt        time the scheduling application started
     */
    @Async
    public void sendTelemetryByPostRequest(boolean sendAdminDetails, String startupId, Instant startedAt) {

        try {
            HttpHeaders headers = new HttpHeaders();
            // Declared explicitly: the default message converters include XML, which could otherwise be chosen for the record.
            headers.setContentType(MediaType.APPLICATION_JSON);
            var requestEntity = new HttpEntity<>(buildTelemetryData(sendAdminDetails, startupId, startedAt), headers);

            log.info("Sending startup telemetry to {}", destination);
            // NOTE: there should be no module in the following URL
            var response = restTemplate.postForEntity(destination + "/api/telemetry", requestEntity, String.class);
            log.info("Successfully sent telemetry data: {}", response.getStatusCode());
        }
        catch (RestClientResponseException e) {
            // Neither the exception nor its message is logged: both carry the response body, in which a collector may echo the report and its administrator details.
            log.warn("The telemetry service at {} rejected the report with status {}", destination, e.getStatusCode());
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
     * @param startupId        stable startup identifier
     * @param startedAt        scheduling application start time
     * @return an instance of {@link TelemetryData} containing the gathered telemetry information
     */
    TelemetryData buildTelemetryData(boolean sendAdminDetails, String startupId, Instant startedAt) {
        var dataSource = datasourceUrl.startsWith("jdbc:mysql") ? "mysql" : "postgresql";
        List<String> activeProfiles = Arrays.asList(env.getActiveProfiles());

        String contact = null;
        String adminName = null;
        if (sendAdminDetails) {
            contact = operatorContact;
            adminName = operatorAdminName;
        }
        Integer nodeCount = null;
        Integer buildAgentCount = localCITelemetryApi.isEmpty() ? 0 : null;
        try {
            var liveNodes = nodeRegistryService.getLiveNodeIds();
            if (!liveNodes.isEmpty()) {
                nodeCount = liveNodes.size();
            }
        }
        catch (Exception ex) {
            log.warn("Could not determine the live core-node count for telemetry", ex);
        }
        try {
            if (localCITelemetryApi.isPresent()) {
                buildAgentCount = localCITelemetryApi.get().getConnectedBuildAgentCount();
            }
        }
        catch (Exception ex) {
            log.warn("Could not determine the connected build-agent count for telemetry", ex);
        }
        List<String> moduleFeatures = new ArtemisConfigHelper().getEnabledFeatures(env).stream().sorted().toList();
        return new TelemetryData(version, serverUrl, operator, activeProfiles, profileService.isProductionActive(), isTestServer, dataSource, contact, adminName,
                isLocalLLMDeploymentEnabled, universityName, moduleFeatures, nodeCount, buildAgentCount, nodeCount == null ? null : nodeCount > 1, startupId, startedAt);
    }
}
