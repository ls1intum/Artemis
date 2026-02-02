package de.tum.cit.aet.artemis.programming.service.hades;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HADES;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;
import de.tum.cit.aet.artemis.core.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.service.ci.StatelessCIService;
import de.tum.cit.aet.artemis.programming.service.hades.dto.HadesBuildJobDTO;
import de.tum.cit.aet.artemis.programming.service.hades.dto.HadesBuildResponseDTO;
import de.tum.cit.aet.artemis.programming.service.hades.dto.HadesBuildStepDTO;
import de.tum.cit.aet.artemis.programming.service.jenkinsstateless.dto.BuildTriggerRequestDTO;

@Service
@Profile(PROFILE_HADES)
public class HadesService implements StatelessCIService {

    private static final Logger log = LoggerFactory.getLogger(HadesService.class);

    private final RestTemplate restTemplate;

    @Value("${artemis.continuous-integration.url}")
    private String hadesServerUrl;

    @Value("${artemis.continuous-integration.hades.images.clone-image}")
    private String cloneDockerImage;

    @Value("${artemis.version-control.build-agent-git-username}")
    private String username;

    @Value("${artemis.version-control.build-agent-git-password}")
    private String password;

    private String repositoryDir = "/shared";

    private String workingDir = "/shared";

    private String hadesTestPath = "./";

    private String assignmentPath = "./assignment";

    private List<HadesBuildStepDTO.VolumeMount> volumeMounts = List.of(new HadesBuildStepDTO.VolumeMount("shared", "/shared"));

    private String testOrder = "1";

    private String assignmentOrder = "2";

    @Value("${artemis.continuous-integration.hades.images.result-parser-image}")
    private String resultParserImage;

    @Value("${artemis.continuous-integration.hades.adapter.endpoint}")
    private String adapterEndPoint;

    private String ingestDir = "/shared/example/build/test-results/test";

    private final ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    public HadesService(RestTemplate restTemplate, ProgrammingLanguageConfiguration programmingLanguageConfiguration) {
        this.restTemplate = restTemplate;
        this.programmingLanguageConfiguration = programmingLanguageConfiguration;
    }

    @Override
    public BuildStatus getBuildStatus(ProgrammingExerciseParticipation participation) {
        // TODO: fetch from HadesLogManager
        return null;
    }

    @Override
    public UUID build(BuildTriggerRequestDTO buildTriggerRequestDTO) throws ContinuousIntegrationException {
        try {
            log.info("Triggering build via Hades for exercise {} and participation {}", buildTriggerRequestDTO.exerciseId(), buildTriggerRequestDTO.participationId());

            HadesBuildJobDTO hadesDTO = convertToHadesBuildJobDTO(buildTriggerRequestDTO);
            log.info("Stepping into build(HadesBuildJobDTO) with: {}", hadesDTO);
            return build(hadesDTO);
        }
        catch (Exception e) {
            log.error("Failed to trigger build via Hades for exercise {} and participation {}", buildTriggerRequestDTO.exerciseId(), buildTriggerRequestDTO.participationId(), e);
            throw new ContinuousIntegrationException("Failed to trigger build via Hades", e);
        }
    }

    private UUID build(HadesBuildJobDTO hadesBuildJobDTO) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<HadesBuildJobDTO> request = new HttpEntity<>(hadesBuildJobDTO, headers);
        ResponseEntity<HadesBuildResponseDTO> response = restTemplate.postForEntity(hadesServerUrl + "/build", request, HadesBuildResponseDTO.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new ContinuousIntegrationException("Build request failed with status: " + response.getStatusCode());
        }

        HadesBuildResponseDTO responseBody = response.getBody();
        if (responseBody == null) {
            throw new ContinuousIntegrationException("Received empty build ID from Hades");
        }

        return UUID.fromString(response.getBody().jobId());
    }

    @Override
    public ConnectorHealth health() {
        var additionalInfo = new HashMap<String, Object>();
        additionalInfo.put("url", hadesServerUrl);
        ConnectorHealth health;
        try {
            final var response = restTemplate.getForObject(hadesServerUrl + "/ping", JsonNode.class);
            final var hadesStatus = response != null ? response.get("message").asText() : null;
            health = new ConnectorHealth("pong".equals(hadesStatus), additionalInfo);
        }
        catch (Exception ex) {
            health = new ConnectorHealth(false, additionalInfo, ex);
        }
        return health;
    }

    private HadesBuildJobDTO convertToHadesBuildJobDTO(BuildTriggerRequestDTO buildTriggerRequestDTO) throws JsonProcessingException {
        var metadata = new HashMap<String, String>();
        var steps = new ArrayList<HadesBuildStepDTO>();

        // Create Clone Step
        var cloneMetadata = new HashMap<String, String>();
        cloneMetadata.put("REPOSITORY_DIR", repositoryDir);
        cloneMetadata.put("HADES_TEST_USERNAME", username);
        cloneMetadata.put("HADES_TEST_PASSWORD", password);
        cloneMetadata.put("HADES_TEST_URL", buildTriggerRequestDTO.testRepository().url());
        cloneMetadata.put("HADES_TEST_PATH", hadesTestPath);
        cloneMetadata.put("HADES_TEST_ORDER", testOrder);
        cloneMetadata.put("HADES_ASSIGNMENT_USERNAME", username);
        cloneMetadata.put("HADES_ASSIGNMENT_PASSWORD", password);
        cloneMetadata.put("HADES_ASSIGNMENT_URL", buildTriggerRequestDTO.exerciseRepository().url());
        cloneMetadata.put("HADES_ASSIGNMENT_PATH", assignmentPath);
        cloneMetadata.put("HADES_ASSIGNMENT_ORDER", assignmentOrder);

        steps.add(new HadesBuildStepDTO(1, "Clone", cloneDockerImage, volumeMounts, workingDir, cloneMetadata));

        // Create Execute Step
        ProjectType projectType = ProjectType.tryFromString(buildTriggerRequestDTO.additionalProperties().get("projectType"));
        var image = programmingLanguageConfiguration.getImage(ProgrammingLanguage.valueOf(buildTriggerRequestDTO.programmingLanguage()), Optional.ofNullable(projectType));
        var script = buildTriggerRequestDTO.buildScript();
        var fullScript = "set -e && cd /shared && " + script;
        steps.add(new HadesBuildStepDTO(2, "Execute", image, volumeMounts, fullScript));

        // Create Parse Result Step
        var parseResultMetadata = new HashMap<String, String>();
        parseResultMetadata.put("API_ENDPOINT", adapterEndPoint);
        parseResultMetadata.put("INGEST_DIR", ingestDir);
        steps.add(new HadesBuildStepDTO(3, "Parse Result", resultParserImage, volumeMounts, parseResultMetadata));

        // Create Hades Job
        var timestamp = java.time.Instant.now().toString();
        return new HadesBuildJobDTO(buildTriggerRequestDTO.participationId().toString(), metadata, timestamp, 1, steps);
    }
}
