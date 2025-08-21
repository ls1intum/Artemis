package de.tum.cit.aet.artemis.programming.service.localci;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.service.BuildScriptProviderService;
import de.tum.cit.aet.artemis.programming.service.aeolus.AeolusTemplateService;
import de.tum.cit.aet.artemis.programming.service.ci.StatelessCIService;
import de.tum.cit.aet.artemis.programming.service.jenkinsstateless.dto.BuildTriggerRequestDTO;

/**
 * Implementation of ContinuousIntegrationService for local CI. Contains methods
 * for communication with the local CI system.
 * Note: Because the ContinuousIntegrationSystem was designed with Jenkins
 * integration in mind, some methods here are not
 * needed and thus contain an empty implementation.
 */
@Lazy
@Service
@Profile(PROFILE_LOCALCI)
public class LocalCIService implements StatelessCIService {

    private static final Logger log = LoggerFactory.getLogger(LocalCIService.class);

    private final BuildScriptProviderService buildScriptProviderService;

    private final AeolusTemplateService aeolusTemplateService;

    private final DistributedDataAccessService distributedDataAccessService;

    private final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    public LocalCIService(BuildScriptProviderService buildScriptProviderService, AeolusTemplateService aeolusTemplateService,
            DistributedDataAccessService distributedDataAccessService, ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository) {
        this.buildScriptProviderService = buildScriptProviderService;
        this.aeolusTemplateService = aeolusTemplateService;
        this.distributedDataAccessService = distributedDataAccessService;
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
    }

    // /**
    // * Fetches the default build plan configuration for the given exercise and the
    // windfile for its metadata (docker image etc.).
    // *
    // * @param exercise for which the build plans should be recreated
    // */
    // public void recreateBuildPlansForExercise(ProgrammingExercise exercise)
    // throws JsonProcessingException {
    // // TODO: implement this differently for LocalCI in the future
    // if (exercise == null) {
    // return;
    // }
    // log.debug("Recreating build plans for exercise {}", exercise.getTitle());
    // String script = buildScriptProviderService.getScriptFor(exercise);
    // Windfile windfile = aeolusTemplateService.getDefaultWindfileFor(exercise);
    // ProgrammingExerciseBuildConfig buildConfig = exercise.getBuildConfig();
    // buildConfig.setBuildScript(script);
    // buildConfig.setBuildPlanConfiguration(new
    // ObjectMapper().writeValueAsString(windfile));
    // // recreating the build plans for the exercise means we need to store the
    // updated build config in the database
    // programmingExerciseBuildConfigRepository.save(buildConfig);
    // }

    /**
     * Get the current status of the build for the given participation, i.e.
     * INACTIVE, QUEUED, or BUILDING.
     *
     * @param participation participation for which to get status
     * @return build status
     */
    @Override
    public BuildStatus getBuildStatus(ProgrammingExerciseParticipation participation) {
        if (!distributedDataAccessService.getQueuedJobsForParticipation(participation.getId()).isEmpty()) {
            return BuildStatus.QUEUED;
        }
        else if (!distributedDataAccessService.getProcessingJobsForParticipation(participation.getId()).isEmpty()) {
            return BuildStatus.BUILDING;
        }
        else {
            return BuildStatus.INACTIVE;
        }
    }

    @Override
    public ConnectorHealth health() {
        return new ConnectorHealth(true, Map.of("buildAgents", distributedDataAccessService.getBuildAgentInformation()));
    }

    @Override
    public UUID build(BuildTriggerRequestDTO buildTriggerRequestDTO) throws ContinuousIntegrationException {
        // Not necessary for LocalCI as the tirgger is handled by the trigger service
        // directly
        throw new UnsupportedOperationException("Unimplemented method 'build'");
    }
}
