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

    private static final Logger log = LoggerFactory.getLogger(HadesTriggerService.class);

    private final HadesService hadesService;

    @Autowired
    private final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

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
            var exerciseRepository = new RepositoryDTO(participation.getUserIndependentRepositoryUri().replace("localhost", "192.168.0.112"), null, null, null);

            // Create the test repository DTO based on the corresponding exercise
            var testRepository = new RepositoryDTO(participation.getProgrammingExercise().getTestRepositoryUri().replace("localhost", "192.168.0.112"), null, null, null);

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
}
