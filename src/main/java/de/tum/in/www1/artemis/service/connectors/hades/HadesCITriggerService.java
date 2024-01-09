package de.tum.in.www1.artemis.service.connectors.hades;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;

import de.tum.in.www1.artemis.config.ProgrammingLanguageConfiguration;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationException;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationTriggerService;
import de.tum.in.www1.artemis.service.connectors.hades.dto.HadesBuildJobDTO;
import de.tum.in.www1.artemis.service.connectors.hades.dto.HadesBuildStepDTO;

@Service
@Profile("hades")
public class HadesCITriggerService implements ContinuousIntegrationTriggerService {

    @Value("${artemis.user-management.internal-admin.username}")
    private String gitUsername;

    @Value("${artemis.user-management.internal-admin.password}")
    private String gitPassword;

    @Value("${artemis.hades.images.clone-image}")
    private String cloneDockerIamge;

    @Value("${artemis.hades.images.result-image}")
    private String resultDockerIamge;

    private final Logger log = LoggerFactory.getLogger(HadesCIService.class);

    private final RestTemplate restTemplate;

    private final ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    @Value("${artemis.hades.url}")
    private String hadesServerUrl;

    public HadesCITriggerService(RestTemplate restTemplate, ProgrammingLanguageConfiguration programmingLanguageConfiguration) {
        this.restTemplate = restTemplate;
        this.programmingLanguageConfiguration = programmingLanguageConfiguration;
    }

    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation) throws ContinuousIntegrationException {
        log.debug("Triggering build for participation {} in Hades", participation.getId());
        var job = createJob(participation);
        postJob(job);
    }

    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation, String commitHash) throws ContinuousIntegrationException {
        log.warn("Triggering builds with a commit hash is not supported for Hades. Triggering build without commit hash.");
        triggerBuild(participation);
    }

    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation, String commitHash, boolean isTestPush) throws ContinuousIntegrationException {
        log.warn("Triggering with a test push is not supported for Hades. Triggering build without test push.");
        triggerBuild(participation, commitHash);
    }

    private void postJob(HadesBuildJobDTO job) {
        try {
            var response = restTemplate.postForEntity(hadesServerUrl + "/build", job, JsonNode.class);
            log.debug("Hades response: {}", response);
        }
        catch (Exception e) {
            log.error("Error triggering the build for " + job, e);
        }
    }

    private HadesBuildJobDTO createJob(ProgrammingExerciseParticipation participation) {

        // Build Job Metadata
        var metadata = new HashMap<String, String>();
        metadata.put("PLAN_KEY", participation.getBuildPlanId());

        var steps = new ArrayList<HadesBuildStepDTO>();

        // Create Clone Step
        // TODO: We need a solution to clone with less critical credentials - This requires changes in the localvc implementation
        var cloneMetadata = new HashMap<String, String>();
        cloneMetadata.put("REPOSITORY_DIR", "/shared");
        cloneMetadata.put("HADES_TEST_USERNAME", gitUsername);
        cloneMetadata.put("HADES_TEST_PASSWORD", gitPassword);
        cloneMetadata.put("HADES_TEST_URL", participation.getProgrammingExercise().getTestRepositoryUrl());
        cloneMetadata.put("HADES_TEST_PATH", "./example");
        cloneMetadata.put("HADES_ASSIGNMENT_USERNAME", gitUsername);
        cloneMetadata.put("HADES_ASSIGNMENT_PASSWORD", gitPassword);
        cloneMetadata.put("HADES_ASSIGNMENT_URL", participation.getRepositoryUrl());
        cloneMetadata.put("HADES_ASSIGNMENT_PATH", "./example/assignment");

        steps.add(new HadesBuildStepDTO(1, "Clone", cloneDockerIamge, cloneMetadata));

        // Create Execute Step
        var image = programmingLanguageConfiguration.getImage(participation.getProgrammingExercise().getProgrammingLanguage(),
                Optional.ofNullable(participation.getProgrammingExercise().getProjectType()));
        // TODO: Get script from template
        var script = "cd ./example && ls -lah && ./gradlew clean test";
        steps.add(new HadesBuildStepDTO(2, "Execute", image, script));

        // TODO: Create Results Step
        steps.add(new HadesBuildStepDTO(3, "Result", resultDockerIamge, script));

        // Create Hades Job
        var timestamp = java.time.Instant.now().toString();
        return new HadesBuildJobDTO("Test", metadata, timestamp, 1, steps);
    }

}
