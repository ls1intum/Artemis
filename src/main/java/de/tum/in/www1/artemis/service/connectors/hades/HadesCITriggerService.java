package de.tum.in.www1.artemis.service.connectors.hades;

import java.util.ArrayList;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;

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

    private final Logger log = LoggerFactory.getLogger(HadesCIService.class);

    private final RestTemplate restTemplate;

    @Value("${artemis.hades.url}")
    private String hadesServerUrl;

    public HadesCITriggerService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation) throws ContinuousIntegrationException {
        log.debug("Triggering build for participation {} in Hades", participation.getId());
        var job = createJob(participation);
        postJob(job);
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

        var steps = new ArrayList<HadesBuildStepDTO>();
        steps.add(new HadesBuildStepDTO(1, "Clone", "ghcr.io/mtze/hades/hades-clone-container:pr-28", cloneMetadata));
        steps.add(new HadesBuildStepDTO(2, "Execute", "ls1tum/artemis-maven-template:java17-18", "cd ./example && ls -lah && ./gradlew clean test"));

        return new HadesBuildJobDTO("Test", null, "2021-01-01T00:00:00.000Z", 1, steps);
    }

}
