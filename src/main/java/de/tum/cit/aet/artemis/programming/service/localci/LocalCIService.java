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
 * Implementation of ContinuousIntegrationService for local CI. Contains methods for communication with the local CI system.
 * Note: Because the ContinuousIntegrationSystem was designed with Jenkins integration in mind, some methods here are not
 * needed and thus contain an empty implementation.
 */
@Lazy
@Service
@Profile(PROFILE_LOCALCI)
public class LocalCIService implements StatelessCIService {

    private static final Logger log = LoggerFactory.getLogger(LocalCIService.class);

    private final DistributedDataAccessService distributedDataAccessService;

    public LocalCIService(BuildScriptProviderService buildScriptProviderService, AeolusTemplateService aeolusTemplateService,
            DistributedDataAccessService distributedDataAccessService, ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository) {
        this.distributedDataAccessService = distributedDataAccessService;
    }

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
        // Not necessary for LocalCI as the trigger is handled by the trigger service directly
        throw new UnsupportedOperationException("Unimplemented method 'build'");
    }
}
