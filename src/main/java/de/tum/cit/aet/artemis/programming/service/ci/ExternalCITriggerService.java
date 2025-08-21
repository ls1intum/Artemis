package de.tum.cit.aet.artemis.programming.service.ci;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.repository.BuildPlanRepository;

/**
 * Implementation of ContinuousIntegrationTriggerService for external CI connectors.
 * This service generates build scripts using existing templates and sends them to the external connector.
 */
@Service
@Profile("external-ci")
public class ExternalCITriggerService implements ContinuousIntegrationTriggerService {

    private static final Logger log = LoggerFactory.getLogger(ExternalCITriggerService.class);

    private final ExternalCIConnectorService connectorService;

    private final BuildPlanRepository buildPlanRepository;

    public ExternalCITriggerService(ExternalCIConnectorService connectorService, BuildPlanRepository buildPlanRepository) {
        this.connectorService = connectorService;
        this.buildPlanRepository = buildPlanRepository;
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
            String buildScript = generateBuildScript(participation);

            // Delegate to connector service
            connectorService.triggerBuild(participation, commitHash, triggeredByPushTo, buildScript);

        }
        catch (Exception e) {
            log.error("Failed to trigger build for participation {}", participation.getId(), e);
            throw new ContinuousIntegrationException("Failed to trigger build via external CI connector", e);
        }
    }

    /**
     * Retrieves the build script from the stored build plan for the exercise.
     * This uses the existing build plan that was generated when the exercise was created.
     */
    private String generateBuildScript(ProgrammingExerciseParticipation participation) {
        try {
            var exercise = participation.getProgrammingExercise();

            // Retrieve the stored build plan from the database
            var buildPlanOptional = buildPlanRepository.findByProgrammingExercises_IdWithProgrammingExercises(exercise.getId());

            if (buildPlanOptional.isPresent()) {
                String buildScript = buildPlanOptional.get().getBuildPlan();
                log.debug("Retrieved build script for exercise {} (length: {} chars)", exercise.getId(), buildScript.length());
                return buildScript;
            }
            else {
                log.warn("No build plan found for exercise {}, this should not happen for external CI", exercise.getId());
                throw new RuntimeException("No build plan found for exercise " + exercise.getId());
            }
        }
        catch (Exception e) {
            log.error("Failed to retrieve build script for participation {}", participation.getId(), e);
            throw new RuntimeException("Failed to retrieve build script", e);
        }
    }
}
