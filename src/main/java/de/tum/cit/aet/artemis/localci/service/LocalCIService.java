package de.tum.cit.aet.artemis.localci.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.core.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhasesDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.service.ci.StatelessCIService;
import de.tum.cit.aet.artemis.programming.service.jenkinsstateless.dto.BuildTriggerRequestDTO;

/**
 * Implementation of StatelessCIService for local CI. Contains methods for communication with the local CI system.
 * Note: Because the ContinuousIntegrationSystem was designed with Jenkins integration in mind, some methods here are not
 * needed and thus contain an empty implementation.
 */
@Lazy
@Service
@Profile(PROFILE_LOCALCI)
public class LocalCIService implements StatelessCIService {

    private static final Logger log = LoggerFactory.getLogger(LocalCIService.class);

    private final BuildPhasesTemplateService buildPhasesTemplateService;

    private final DistributedDataAccessService distributedDataAccessService;

    private final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    public LocalCIService(BuildPhasesTemplateService buildPhasesTemplateService, DistributedDataAccessService distributedDataAccessService,
            ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository) {
        this.buildPhasesTemplateService = buildPhasesTemplateService;
        this.distributedDataAccessService = distributedDataAccessService;
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
    }

    /**
     * Fetches the default build plan configuration for the given localci exercise
     *
     * @param exercise for which the build plans should be recreated
     */
    @Override
    public void recreateBuildPlansForExercise(ProgrammingExercise exercise) throws JsonProcessingException {
        if (exercise == null) {
            return;
        }
        log.debug("Recreating build plans for exercise {}", exercise.getTitle());
        List<BuildPhaseDTO> phases = buildPhasesTemplateService.getDefaultBuildPlanPhasesFor(exercise);
        String image = buildPhasesTemplateService.getDefaultDockerImageFor(exercise);
        ProgrammingExerciseBuildConfig buildConfig = exercise.getBuildConfig();
        buildConfig.setBuildScript(null);
        buildConfig.setBuildPlanConfiguration(new BuildPlanPhasesDTO(phases, image).toBuildPlanConfiguration());
        // recreating the build plans for the exercise means we need to store the updated build config in the database
        programmingExerciseBuildConfigRepository.save(buildConfig);
    }

    /**
     * Get the current status of the build for the given participation, i.e. INACTIVE, QUEUED, or BUILDING.
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
        // Return a simplified view of build agents for health check
        // This excludes sensitive/large data like build scripts, repository URIs, SSH keys
        var buildAgentsSummary = distributedDataAccessService.getBuildAgentInformation().stream().map(agent -> {
            var buildAgent = agent.buildAgent();
            var name = buildAgent.name() != null ? buildAgent.name() : "Unknown";
            var displayName = buildAgent.displayName() != null ? buildAgent.displayName() : name;
            var memberAddress = buildAgent.memberAddress() != null ? buildAgent.memberAddress() : "";
            var status = agent.status() != null ? agent.status().name() : "UNKNOWN";
            var runningJobs = agent.runningBuildJobs().stream().map(job -> job.name() != null ? job.name() : job.id()).map(String::valueOf).toList();
            return Map.of("name", name, "displayName", displayName, "memberAddress", memberAddress, "status", status, "maxJobs", agent.maxNumberOfConcurrentBuildJobs(),
                    "currentJobs", agent.numberOfCurrentBuildJobs(), "runningJobs", runningJobs);
        }).toList();
        return new ConnectorHealth(true, Map.of("buildAgents", buildAgentsSummary));
    }

    // This method is temporary, for an adaptation to the programming-exercises/new-result endpoint
    // TODO: remove after endpoint handling is refactored.
    @Override
    public String getPlanKey(Object requestBody) throws ContinuousIntegrationException {
        return "";
    }

    @Override
    public UUID build(BuildTriggerRequestDTO buildTriggerRequestDTO) throws ContinuousIntegrationException {
        throw new UnsupportedOperationException("Unimplemented method 'build'");
    }
}
