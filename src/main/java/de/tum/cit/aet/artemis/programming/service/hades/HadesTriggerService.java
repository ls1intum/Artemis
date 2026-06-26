package de.tum.cit.aet.artemis.programming.service.hades;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HADES;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.localci.service.BuildPhaseEvaluationService;
import de.tum.cit.aet.artemis.localci.service.BuildPhasesTemplateService;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhasesDTO;
import de.tum.cit.aet.artemis.programming.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.service.hades.dto.BuildTriggerRequestDTO;
import de.tum.cit.aet.artemis.programming.service.hades.dto.RepositoryDTO;

/**
 * Implementation of ContinuousIntegrationTriggerService for Hades.
 * This service converts ProgrammingExerciseParticipation to BuildTriggerRequestDTO and
 * sends the build request to HadesService.
 */

@Lazy
@Service
@Profile(PROFILE_HADES)
public class HadesTriggerService implements ContinuousIntegrationTriggerService {

    private static final Logger log = LoggerFactory.getLogger(HadesTriggerService.class);

    private final HadesService hadesService;

    private final BuildPhaseEvaluationService buildPhaseEvaluationService;

    private final BuildPhasesTemplateService buildPhasesTemplateService;

    private final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    private final GitService gitService;

    public HadesTriggerService(HadesService hadesService, BuildPhaseEvaluationService buildPhaseEvaluationService, BuildPhasesTemplateService buildPhasesTemplateService,
            ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository, GitService gitService) {
        this.hadesService = hadesService;
        this.buildPhaseEvaluationService = buildPhaseEvaluationService;
        this.buildPhasesTemplateService = buildPhasesTemplateService;
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
        this.gitService = gitService;
    }

    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation) throws ContinuousIntegrationException {
        triggerBuild(participation, null, null);
    }

    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation, boolean triggerAll) throws ContinuousIntegrationException {
        log.warn("Triggering builds with a trigger all option is not supported for Hades. Triggering build while ignoring option.");
        triggerBuild(participation, null, null);
    }

    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation, String commitHash, RepositoryType triggeredByPushTo) throws ContinuousIntegrationException {
        try {
            log.debug("Triggering build for participation {} via external CI connector", participation.getId());

            // Prepare the build trigger request DTO
            Long exerciseID = participation.getProgrammingExercise().getId();
            Long participationID = participation.getId();

            ProgrammingExerciseBuildConfig buildConfig = programmingExerciseBuildConfigRepository
                    .getProgrammingExerciseBuildConfigElseThrow(participation.getProgrammingExercise());
            String buildScript = getBuildScript(buildConfig, participation, participation.getProgrammingExercise());

            String assignmentHash = (triggeredByPushTo == null || triggeredByPushTo == RepositoryType.USER) && commitHash != null ? commitHash
                    : gitService.getLastCommitHash(participation.getVcsRepositoryUri());
            String testHash = triggeredByPushTo == RepositoryType.TESTS && commitHash != null ? commitHash
                    : gitService.getLastCommitHash(participation.getProgrammingExercise().getVcsTestRepositoryUri());
            var exerciseRepository = new RepositoryDTO(participation.getVcsRepositoryUri().getURI().toString(), assignmentHash, null, null);
            var testRepository = new RepositoryDTO(participation.getProgrammingExercise().getVcsTestRepositoryUri().getURI().toString(), testHash, null, null);

            // Hades should use a Bash script
            ScriptType scriptType = ScriptType.SHELL;

            var auxiliaryRepository = new ArrayList<RepositoryDTO>();
            var additionalProperties = new HashMap<String, String>();

            ProjectType projectType = participation.getProgrammingExercise().getProjectType();
            if (projectType != null) {
                additionalProperties.put("projectType", projectType.toString());
            }

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

    /**
     * Generates the build script for the given participation by resolving active build phases
     * and concatenating their scripts into a single shell command.
     *
     * @param buildConfig         the build configuration containing build phases
     * @param participation       the programming exercise participation
     * @param programmingExercise the programming exercise
     * @return the concatenated shell script for all active build phases
     */
    public String getBuildScript(ProgrammingExerciseBuildConfig buildConfig, ProgrammingExerciseParticipation participation, ProgrammingExercise programmingExercise) {
        programmingExercise.setBuildConfig(buildConfig);

        Optional<BuildPlanPhasesDTO> buildPlanPhasesDTO = buildConfig.getBuildPlanPhases();
        final boolean isMissingDefaultPhases = buildPlanPhasesDTO.isEmpty() || buildPlanPhasesDTO.orElseThrow().phases() == null;
        final List<BuildPhaseDTO> phases = isMissingDefaultPhases ? buildPhasesTemplateService.getDefaultBuildPlanPhasesFor(programmingExercise)
                : buildPlanPhasesDTO.orElseThrow().phases();

        final List<BuildPhaseDTO> activePhases = buildPhaseEvaluationService.determineActiveBuildPhases(phases, participation);

        StringBuilder script = new StringBuilder("set -e && cd /shared && ");
        for (BuildPhaseDTO phase : activePhases) {
            if (phase.script() != null && !phase.script().isBlank()) {
                script.append(phase.script().strip()).append(" && ");
            }
        }

        String result = script.toString();
        if (result.endsWith(" && ")) {
            result = result.substring(0, result.length() - 4);
        }
        return result;
    }
}
