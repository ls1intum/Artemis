package de.tum.cit.aet.artemis.programming.service.ci;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.core.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.dto.BuildTriggerRequestDTO;
import de.tum.cit.aet.artemis.programming.dto.CIBuildStatusDTO;
import de.tum.cit.aet.artemis.programming.dto.RepositoryInfoDTO;

/**
 * Implementation of ContinuousIntegrationConnectorService that communicates with an external CI connector microservice.
 * This service handles all CI operations by delegating to the microservice via REST API calls.
 */
@Service
@Profile("external-ci")
public class ExternalCIConnectorService implements ContinuousIntegrationConnectorService {

    private static final Logger log = LoggerFactory.getLogger(ExternalCIConnectorService.class);

    private final RestTemplate restTemplate;

    @Value("${artemis.external-ci.url:http://localhost:8081}")
    private String connectorBaseUrl;

    public ExternalCIConnectorService(@Qualifier("externalCIRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation, String commitHash, RepositoryType triggeredByPushTo, String buildScript)
            throws ContinuousIntegrationException {
        try {
            log.debug("Triggering build for participation {} via external CI connector", participation.getId());

            // Build the request DTO
            var request = createBuildTriggerRequest(participation, commitHash, triggeredByPushTo, buildScript);

            // Make API call to connector
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<BuildTriggerRequestDTO> entity = new HttpEntity<>(request, headers);

            ResponseEntity<UUID> response = restTemplate.postForEntity(connectorBaseUrl + "/api/builds/trigger", entity, UUID.class);

            UUID buildId = response.getBody();
            log.info("Successfully triggered build for participation {} -> build ID: {}", participation.getId(), buildId);

            // TODO: Store build ID in participation or build record for status tracking

        }
        catch (RestClientException e) {
            log.error("Failed to trigger build for participation {}", participation.getId(), e);
            throw new ContinuousIntegrationException("Failed to trigger build via external CI connector", e);
        }
    }

    @Override
    public Optional<CIBuildStatusDTO> getBuildStatus(ProgrammingExerciseParticipation participation) {
        try {
            // TODO: Get stored build ID for this participation
            UUID buildId = null; // Placeholder - need to implement build ID storage

            if (buildId == null) {
                return Optional.empty();
            }

            log.debug("Getting build status for build ID: {}", buildId);

            ResponseEntity<CIBuildStatusDTO> response = restTemplate.getForEntity(connectorBaseUrl + "/api/builds/status/" + buildId, CIBuildStatusDTO.class);

            return Optional.ofNullable(response.getBody());

        }
        catch (RestClientException e) {
            log.error("Failed to get build status for participation {}", participation.getId(), e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> getBuildLogs(ProgrammingExerciseParticipation participation, String buildId) {
        // TODO: Implement when logs API is added to connector
        log.warn("Build logs retrieval not yet implemented for external CI connector");
        return Optional.empty();
    }

    @Override
    public ConnectorHealth getHealth() {
        try {
            log.debug("Checking external CI connector health");

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(connectorBaseUrl + "/api/health", HttpMethod.GET, null,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            Map<String, Object> healthData = response.getBody();
            boolean isHealthy = "UP".equals(healthData.get("status"));

            return new ConnectorHealth(isHealthy, Map.of("connector-url", connectorBaseUrl, "connector-details", healthData));

        }
        catch (RestClientException e) {
            log.error("External CI connector health check failed", e);
            return new ConnectorHealth(false, Map.of("connector-url", connectorBaseUrl, "error", e.getMessage()));
        }
    }

    @Override
    public List<String> getSupportedProgrammingLanguages() {
        try {
            log.debug("Getting supported programming languages from external CI connector");

            ResponseEntity<List<String>> response = restTemplate.exchange(connectorBaseUrl + "/api/builds/supported-languages", HttpMethod.GET, null,
                    new ParameterizedTypeReference<List<String>>() {
                    });

            return response.getBody();

        }
        catch (RestClientException e) {
            log.error("Failed to get supported languages from external CI connector", e);
            return List.of(); // Return empty list on error
        }
    }

    @Override
    public Optional<String> getDefaultBuildScriptTemplate(String programmingLanguage, String exerciseType) {
        try {
            log.debug("Getting build script template for language: {}, type: {}", programmingLanguage, exerciseType);

            String url = String.format("%s/api/builds/template/%s?exerciseType=%s", connectorBaseUrl, programmingLanguage, exerciseType);

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return Optional.ofNullable(response.getBody());

        }
        catch (RestClientException e) {
            log.debug("No template available for language: {}, type: {} - {}", programmingLanguage, exerciseType, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Creates a build trigger request DTO from participation data.
     */
    private BuildTriggerRequestDTO createBuildTriggerRequest(ProgrammingExerciseParticipation participation, String commitHash, RepositoryType triggeredByPushTo,
            String buildScript) {
        var exercise = participation.getProgrammingExercise();

        // Main exercise repository
        var exerciseRepo = new RepositoryInfoDTO(participation.getRepositoryUri(), commitHash, null, // cloneLocation - handled by connector
                null, // accessToken - TODO: get from VCS service
                null  // branch - use default
        );

        // Test repository
        var testRepo = new RepositoryInfoDTO(exercise.getTestRepositoryUri(), null, // Use default/latest commit
                null, null, null);

        // Solution repository (if needed)
        RepositoryInfoDTO solutionRepo = null;
        if (exercise.getSolutionRepositoryUri() != null) {
            solutionRepo = new RepositoryInfoDTO(exercise.getSolutionRepositoryUri(), null, null, null, null);
        }

        return new BuildTriggerRequestDTO(exercise.getId(), participation.getId(), exerciseRepo, testRepo, solutionRepo, null, // auxiliaryRepositories - TODO: implement if needed
                buildScript, triggeredByPushTo != null ? triggeredByPushTo.name() : null, exercise.getProgrammingLanguage().name().toLowerCase(), null  // additionalProperties
        );
    }
}
