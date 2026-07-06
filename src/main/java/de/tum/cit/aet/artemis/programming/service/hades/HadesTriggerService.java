package de.tum.cit.aet.artemis.programming.service.hades;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HADES;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.localci.service.BuildPhaseEvaluationService;
import de.tum.cit.aet.artemis.localci.service.BuildPhasesTemplateService;
import de.tum.cit.aet.artemis.localci.service.BuildScriptProviderService;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
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

    private final BuildScriptProviderService buildScriptProviderService;

    private static final String HADES_WORKING_DIRECTORY = "/shared";

    private static final String DEFAULT_INGEST_DIRECTORY = HADES_WORKING_DIRECTORY + "/build/test-results/test";

    public HadesTriggerService(HadesService hadesService, BuildPhaseEvaluationService buildPhaseEvaluationService, BuildPhasesTemplateService buildPhasesTemplateService,
            ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository, GitService gitService, BuildScriptProviderService buildScriptProviderService) {
        this.hadesService = hadesService;
        this.buildPhaseEvaluationService = buildPhaseEvaluationService;
        this.buildPhasesTemplateService = buildPhasesTemplateService;
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
        this.gitService = gitService;
        this.buildScriptProviderService = buildScriptProviderService;
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
            List<BuildPhaseDTO> activePhases = resolveActivePhases(buildConfig, participation, participation.getProgrammingExercise());
            String buildScript = buildScript(buildConfig, activePhases);

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

            additionalProperties.put("resultIngestDirectory",
                    resolveResultIngestDirectory(activePhases, buildConfig, participation.getProgrammingExercise().getProgrammingLanguage(), projectType));

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

    public String getBuildScript(ProgrammingExerciseBuildConfig buildConfig, ProgrammingExerciseParticipation participation, ProgrammingExercise programmingExercise) {
        List<BuildPhaseDTO> activePhases = resolveActivePhases(buildConfig, participation, programmingExercise);
        return buildScript(buildConfig, activePhases);
    }

    private String buildScript(ProgrammingExerciseBuildConfig buildConfig, List<BuildPhaseDTO> activePhases) {
        StringBuilder script = new StringBuilder("set -e && cd ").append(HADES_WORKING_DIRECTORY).append(" && ");
        for (BuildPhaseDTO phase : activePhases) {
            if (phase.script() != null && !phase.script().isBlank()) {
                script.append(phase.script().strip()).append(" && ");
            }
        }

        String result = script.toString();
        if (result.endsWith(" && ")) {
            result = result.substring(0, result.length() - 4);
        }

        return buildScriptProviderService.replacePlaceholders(result, buildConfig.getAssignmentCheckoutPath(), buildConfig.getSolutionCheckoutPath(),
                buildConfig.getTestCheckoutPath());
    }

    private List<BuildPhaseDTO> resolveActivePhases(ProgrammingExerciseBuildConfig buildConfig, ProgrammingExerciseParticipation participation,
            ProgrammingExercise programmingExercise) {
        programmingExercise.setBuildConfig(buildConfig);
        BuildPlanPhasesDTO buildPlanPhasesDTO;
        try {
            buildPlanPhasesDTO = BuildPlanPhasesDTO.fromBuildPlanConfiguration(buildConfig.getBuildPlanConfiguration());
        }
        catch (JsonProcessingException e) {
            throw new LocalCIException("The build plan configuration is invalid for build config " + buildConfig.getId(), e);
        }

        final List<BuildPhaseDTO> phases = buildPlanPhasesDTO.phases() == null ? buildPhasesTemplateService.getDefaultBuildPlanPhasesFor(programmingExercise)
                : buildPlanPhasesDTO.phases();

        return buildPhaseEvaluationService.determineActiveBuildPhases(phases, participation);
    }

    private String resolveResultIngestDirectory(List<BuildPhaseDTO> activePhases, ProgrammingExerciseBuildConfig buildConfig, ProgrammingLanguage programmingLanguage,
            ProjectType projectType) {
        boolean isMaven = projectType != null && projectType.toString().contains("MAVEN");
        if (programmingLanguage == ProgrammingLanguage.JAVA) {
            return isMaven ? HADES_WORKING_DIRECTORY + "/target/surefire-reports" : DEFAULT_INGEST_DIRECTORY;
        }

        List<String> rawResultPaths = activePhases.stream().map(BuildPhaseDTO::resultPaths).filter(Objects::nonNull).flatMap(List::stream)
                .filter(path -> path != null && !path.isBlank()).distinct().toList();

        if (!rawResultPaths.isEmpty()) {
            List<String> resolvedResultPaths = buildScriptProviderService.replaceResultPathsPlaceholders(rawResultPaths, buildConfig);
            return toIngestDirectory(resolvedResultPaths.get(0));
        }
        return DEFAULT_INGEST_DIRECTORY;
    }

    private static String toIngestDirectory(String resultPathGlob) {
        String cleaned = resultPathGlob.strip().replace("**/", "");
        int lastSlash = cleaned.lastIndexOf('/');
        String directory = lastSlash >= 0 ? cleaned.substring(0, lastSlash) : "";
        return directory.isBlank() ? HADES_WORKING_DIRECTORY : HADES_WORKING_DIRECTORY + "/" + directory;
    }
}
