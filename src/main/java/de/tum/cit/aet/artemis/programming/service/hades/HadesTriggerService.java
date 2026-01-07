package de.tum.cit.aet.artemis.programming.service.hades;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HADES;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.programming.service.jenkinsstateless.dto.BuildTriggerRequestDTO;
import de.tum.cit.aet.artemis.programming.service.jenkinsstateless.dto.RepositoryDTO;

/**
 * Implementation of ContinuousIntegrationTriggerService for external CI
 * connectors.
 * This service generates build scripts using existing templates and sends them
 * to the external connector.
 */

@Service
@Profile(PROFILE_HADES)
public class HadesTriggerService implements ContinuousIntegrationTriggerService {

    // @Value("${artemis.continuous-integration.hades.images.result-image}")
    // private String resultDockerImage;
    //
    // // TODO: The value has a very bad naming schema - since this is a breaking change we need to coordinate with artemis users
    // @Value("${artemis.continuous-integration.artemis-authentication-token-value}")
    // private String resultToken;
    //
    // @Value("${server.url}")
    // private URL artemisServerUrl;
    //
    // @Value("${server.port}")
    // private String artemisServerPort;

    private static final Logger log = LoggerFactory.getLogger(HadesTriggerService.class);

    private final HadesService hadesService;

    @Autowired
    private final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    private final String vscAccessToken = "TODO";

    public HadesTriggerService(HadesService hadesService, ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository) {
        this.hadesService = hadesService;
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public void triggerBuild(ProgrammingExerciseParticipation participation) throws ContinuousIntegrationException {
        try {
            log.debug("Triggering build for participation {} via external CI connector", participation.getId());

            // Prepare the build trigger request DTO
            Long exerciseID = participation.getProgrammingExercise().getId();
            Long participationID = participation.getId();

            ProgrammingExerciseBuildConfig buildConfig = programmingExerciseBuildConfigRepository.findByIdElseThrow(exerciseID);
            String buildScript = buildConfig.getBuildScript();

            // Create the submission repository DTO
            var exerciseRepository = new RepositoryDTO(participation.getUserIndependentRepositoryUri(), null, null, vscAccessToken);

            // Create the test repository DTO based on the corresponding exercise
            var testRepository = new RepositoryDTO(participation.getProgrammingExercise().getTestRepositoryUri(), null, null, vscAccessToken);

            // Choose if script is bash or groovy: Hades should use a Bash script
            String scriptType = BuildTriggerRequestDTO.ScriptType.SHELL.getValue();

            var auxiliaryRepository = new ArrayList<RepositoryDTO>();
            var additionalProperties = new HashMap<String, String>();
            additionalProperties.put("projectType", Objects.requireNonNull(participation.getProgrammingExercise().getProjectType()).toString());

            // Create the build trigger request DTO
            BuildTriggerRequestDTO buildTriggerRequest = new BuildTriggerRequestDTO(exerciseID, participationID, exerciseRepository, testRepository, auxiliaryRepository,
                    buildScript, scriptType, participation.getProgrammingExercise().getProgrammingLanguage().toString(), additionalProperties);

            // Delegate to Hades service
            hadesService.build(buildTriggerRequest);

        }
        catch (Exception e) {
            log.error("Failed to trigger build for participation {}", participation.getId(), e);
            throw new ContinuousIntegrationException("Failed to trigger build via Hades", e);
        }
    }

    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation, boolean triggerAll) throws ContinuousIntegrationException {
        log.warn("Triggering builds with a trigger all option is not supported for Hades. Triggering build while ignoring option.");
        triggerBuild(participation);
    }

    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation, String commitHash, RepositoryType triggeredByPushTo) throws ContinuousIntegrationException {
        log.warn("Triggering with of a specific commitHash is not supported. Triggering build while ignoring option.");
        triggerBuild(participation);
    }

    // private HadesBuildJobDTO createJob(ProgrammingExerciseParticipation participation) throws JsonProcessingException {
    // // Build Job Metadata
    // var metadata = new HashMap<String, String>();
    // var steps = new ArrayList<HadesBuildStepDTO>();
    //
    // // Create Clone Step
    // // TODO: Add support for ssh authentication
    // var cloneMetadata = new HashMap<String, String>();
    // cloneMetadata.put("REPOSITORY_DIR", "/shared");
    // cloneMetadata.put("HADES_TEST_USERNAME", hadesUsername);
    // cloneMetadata.put("HADES_TEST_PASSWORD", hadesPassword);
    // cloneMetadata.put("HADES_TEST_URL", participation.getProgrammingExercise().getTestRepositoryUri());
    // cloneMetadata.put("HADES_TEST_PATH", "./");
    // cloneMetadata.put("HADES_TEST_ORDER", "1");
    // cloneMetadata.put("HADES_ASSIGNMENT_USERNAME", hadesUsername);
    // cloneMetadata.put("HADES_ASSIGNMENT_PASSWORD", hadesPassword);
    // cloneMetadata.put("HADES_ASSIGNMENT_URL", participation.getVcsRepositoryUri().toString());
    // cloneMetadata.put("HADES_ASSIGNMENT_PATH", "./assignment");
    // cloneMetadata.put("HADES_ASSIGNMENT_ORDER", "2");
    // // TODO: Auxiliary Repository clone is not supported yet
    //
    // steps.add(new HadesBuildStepDTO(1, "Clone", cloneDockerImage, cloneMetadata));
    //
    // // Create Execute Step
    // var image = programmingLanguageConfiguration.getImage(participation.getProgrammingExercise().getProgrammingLanguage(),
    // Optional.ofNullable(participation.getProgrammingExercise().getProjectType()));
    // var script = buildScriptGenerationService.getScript(participation.getProgrammingExercise());
    // steps.add(new HadesBuildStepDTO(2, "Execute", image, script));
    //
    // var resultMetadata = getResultMetadata(participation);
    // steps.add(new HadesBuildStepDTO(3, "Result", resultDockerImage, resultMetadata));
    //
    // // Create Hades Job
    // var timestamp = java.time.Instant.now().toString();
    // // Job name set as participationId for now
    // return new HadesBuildJobDTO(participation.getId().toString(), metadata, timestamp, 1, steps);
    // }
    //
    // private HashMap<String, String> getResultMetadata(ProgrammingExerciseParticipation participation) {
    // var resultMetadata = new HashMap<String, String>();
    // resultMetadata.put("API_TOKEN", resultToken);
    // resultMetadata.put("INGEST_DIR", "/shared/build/test-results/test");
    // resultMetadata.put("API_ENDPOINT", artemisServerUrl.toString() + ":" + artemisServerPort + "/api/public/programming-exercises/new-result");
    // resultMetadata.put("JOB_NAME", participation.getBuildPlanId());
    // resultMetadata.put("HADES_TEST_PATH", "./");
    // resultMetadata.put("HADES_ASSIGNMENT_PATH", "./assignment");
    // resultMetadata.put("DEBUG", "true"); // TODO: Remove
    // return resultMetadata;
    // }
}
