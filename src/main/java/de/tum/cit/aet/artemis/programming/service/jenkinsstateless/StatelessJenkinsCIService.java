package de.tum.cit.aet.artemis.programming.service.jenkinsstateless;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_STATELESS_JENKINS;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.core.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.service.ci.StatelessCIService;
import de.tum.cit.aet.artemis.programming.service.jenkinsstateless.dto.BuildTriggerRequestDTO;

/**
 * Implementation of ContinuousIntegrationConnectorService that communicates
 * with an external CI connector microservice.
 * This service handles all CI operations by delegating to the microservice via
 * REST API calls.
 */
@Service
@Profile(PROFILE_STATELESS_JENKINS)
public class StatelessJenkinsCIService implements StatelessCIService {

    private static final Logger log = LoggerFactory.getLogger(StatelessJenkinsCIService.class);

    private final RestTemplate restTemplate;

    @Value("${artemis.external-ci.url:http://localhost:8081}")
    private String connectorBaseUrl;

    public StatelessJenkinsCIService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public ConnectorHealth health() {
        try {
            String healthUrl = connectorBaseUrl + "/actuator/health";

            // Call the Jenkins connector health endpoint
            var healthResponse = restTemplate.getForObject(healthUrl, Map.class);

            if (healthResponse != null && "UP".equals(healthResponse.get("status"))) {
                return new ConnectorHealth(true, Map.of("status", "up", "connectorUrl", connectorBaseUrl, "service", "jenkins-connector"));
            }
            else {
                return new ConnectorHealth(false, Map.of("status", "down", "connectorUrl", connectorBaseUrl, "service", "jenkins-connector", "message", "Health check failed"));
            }
        }
        catch (Exception e) {
            log.error("Failed to check health of Jenkins connector at {}", connectorBaseUrl, e);
            return new ConnectorHealth(false, Map.of("status", "down", "connectorUrl", connectorBaseUrl, "service", "jenkins-connector", "error", e.getMessage()));
        }
    }

    @Override
    public BuildStatus getBuildStatus(ProgrammingExerciseParticipation participation) {
        // For stateless CI, we cannot track individual build statuses by participation
        // since builds are managed by the external Jenkins connector service.
        // This would require either:
        // 1. Storing build IDs mapped to participations
        // 2. Querying the connector service with participation info
        // 3. Using a different approach for build status tracking

        log.debug("getBuildStatus called for participation {}, but stateless CI doesn't track individual build statuses", participation.getId());

        // For now, return INACTIVE since we don't have a direct way to query build status by participation
        // In a full implementation, you might want to:
        // - Store build UUIDs returned from the build() method
        // - Query the connector service using those UUIDs
        // - Map the connector's build statuses to the CI service statuses
        return BuildStatus.INACTIVE;
    }

    @Override
    public UUID build(BuildTriggerRequestDTO buildTriggerRequestDTO) throws ContinuousIntegrationException {
        try {
            log.info("Triggering build via Jenkins connector for exercise {} and participation {}", buildTriggerRequestDTO.exerciseId(), buildTriggerRequestDTO.participationId());

            String buildApiUrl = connectorBaseUrl + "/api/v1/build";

            // POST the DTO to the Jenkins connector's build API
            restTemplate.postForObject(buildApiUrl, buildTriggerRequestDTO, Void.class);

            // Generate a UUID for this build request
            UUID buildId = UUID.randomUUID();
            log.debug("Build request submitted with ID: {}", buildId);

            return buildId;
        }
        catch (Exception e) {
            log.error("Failed to trigger build via Jenkins connector for exercise {} and participation {}", buildTriggerRequestDTO.exerciseId(),
                    buildTriggerRequestDTO.participationId(), e);
            throw new ContinuousIntegrationException("Failed to trigger build via Jenkins connector", e);
        }
    }
}
