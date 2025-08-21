package de.tum.cit.aet.artemis.programming.service.jenkinsstateless;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_STATELESS_JENKINS;

import java.util.ArrayList;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.repository.BuildPlanRepository;
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
@Profile(PROFILE_STATELESS_JENKINS)
public class StatelessJenkinsCITriggerService implements ContinuousIntegrationTriggerService {

    private static final Logger log = LoggerFactory.getLogger(StatelessJenkinsCITriggerService.class);

    private final StatelessJenkinsCIService statelessJenkinsCIService;

    private final String vscAccessToken = "TODO";

    public StatelessJenkinsCITriggerService(StatelessJenkinsCIService statelessJenkinsCIService, BuildPlanRepository buildPlanRepository) {
        this.statelessJenkinsCIService = statelessJenkinsCIService;
    }

    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation, boolean triggerAll) throws ContinuousIntegrationException {
        triggerBuild(participation, null, null);
    }

    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation, String commitHash, RepositoryType triggeredByPushTo) throws ContinuousIntegrationException {
        try {
            log.debug("Triggering build for participation {} via external CI connector", participation.getId());

            // Generate build script using existing Artemis templates
            String buildScript = getBuildScriptFor(participation);

            // Prepare the build trigger request DTO
            Long exerciseID = participation.getProgrammingExercise().getId();
            Long participationID = participation.getId();

            // Create the submission repository DTO
            var exerciseRepository = new RepositoryDTO(participation.getUserIndependentRepositoryUri(), commitHash, null, vscAccessToken);

            // Create the test repository DTO based on the corresponding exercise
            var testRepository = new RepositoryDTO(participation.getProgrammingExercise().getVcsTemplateRepositoryUri().toString(), null, null, vscAccessToken);

            // Choose if script is bash or groovy
            String scriptType = BuildTriggerRequestDTO.ScriptType.SHELL.getValue();

            var auxiliaryRepository = new ArrayList<RepositoryDTO>();
            var additionalProperties = new HashMap<String, String>();

            // Create the build trigger request DTO
            BuildTriggerRequestDTO buildTriggerRequest = new BuildTriggerRequestDTO(exerciseID, participationID, exerciseRepository, testRepository, auxiliaryRepository,
                    buildScript, scriptType, participation.getProgrammingExercise().getProgrammingLanguage().toString(), additionalProperties);

            // Delegate to connector service
            statelessJenkinsCIService.build(buildTriggerRequest);

        }
        catch (Exception e) {
            log.error("Failed to trigger build for participation {}", participation.getId(), e);
            throw new ContinuousIntegrationException("Failed to trigger build via external CI connector", e);
        }
    }

    private String getBuildScriptFor(ProgrammingExerciseParticipation participation) {
        // Note: Not sure if this is the correct way to retrieve the build script.
        var buildConfig = participation.getProgrammingExercise().getBuildConfig().getBuildScript();

        return buildConfig != null ? buildConfig : "";

    }
}
