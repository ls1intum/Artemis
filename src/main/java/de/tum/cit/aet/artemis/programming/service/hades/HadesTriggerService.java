package de.tum.cit.aet.artemis.programming.service.hades;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HADES;

import java.util.ArrayList;
import java.util.Arrays;
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
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhasesDTO;
import de.tum.cit.aet.artemis.programming.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.service.RepositoryCheckoutService;
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

    private static final String DEFAULT_ASSIGNMENT_CHECKOUT_PATH = "assignment";

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
            // Honor the exercise's configured checkout paths so the clone step writes into the same directories the build script
            // references. Imported exercises keep custom checkout paths, so falling back to the language default would clone the
            // repositories into the wrong directories. Language defaults are only used when no checkout path is configured.
            String assignmentCheckoutPath = buildConfig.getAssignmentCheckoutPath();
            if (assignmentCheckoutPath == null || assignmentCheckoutPath.isBlank()) {
                assignmentCheckoutPath = DEFAULT_ASSIGNMENT_CHECKOUT_PATH;
            }
            String testCheckoutPath = buildConfig.getTestCheckoutPath();
            if (testCheckoutPath == null || testCheckoutPath.isBlank()) {
                testCheckoutPath = RepositoryCheckoutService.RepositoryCheckoutPath.TEST.forProgrammingLanguage(participation.getProgrammingExercise().getProgrammingLanguage());
            }
            var exerciseRepository = new RepositoryDTO(participation.getVcsRepositoryUri().getURI().toString(), assignmentHash, assignmentCheckoutPath, null);
            var testRepository = new RepositoryDTO(participation.getProgrammingExercise().getVcsTestRepositoryUri().getURI().toString(), testHash, testCheckoutPath, null);

            // Hades should use a Bash script
            ScriptType scriptType = ScriptType.SHELL;

            var auxiliaryRepository = new ArrayList<RepositoryDTO>();
            var additionalProperties = new HashMap<String, String>();

            ProjectType projectType = participation.getProgrammingExercise().getProjectType();
            if (projectType != null) {
                additionalProperties.put("projectType", projectType.toString());
            }

            additionalProperties.put("resultIngestDirectory", resolveResultIngestDirectory(activePhases, buildConfig, projectType));

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
        String result = computeBuildScript(activePhases);
        return buildScriptProviderService.replacePlaceholders(result, buildConfig.getAssignmentCheckoutPath(), buildConfig.getSolutionCheckoutPath(),
                buildConfig.getTestCheckoutPath());
    }

    private static String computeBuildScript(List<BuildPhaseDTO> activePhases) {
        List<BuildPhaseDTO> nonForceRunPhases = new ArrayList<>();
        List<BuildPhaseDTO> forceRunPhases = new ArrayList<>();
        for (BuildPhaseDTO phase : activePhases) {
            if (phase.script() == null || phase.script().isBlank()) {
                continue;
            }
            (phase.forceRun() ? forceRunPhases : nonForceRunPhases).add(phase);
        }

        // Each phase is rendered as a Bash function whose body first resets the working directory to /shared and then runs the
        // phase script. Wrapping the scripts in functions keeps `local` (used by the MAVEN_BLACKBOX templates) legal, which would
        // fail at Bash top level. The functions are invoked afterwards so `set -e` fail-fast semantics still apply per phase.
        StringBuilder functionDefinitions = new StringBuilder();
        List<String> nonForceRunCalls = new ArrayList<>();
        List<String> forceRunCalls = new ArrayList<>();
        int phaseIndex = 0;
        for (BuildPhaseDTO phase : nonForceRunPhases) {
            String functionName = "phase_" + phaseIndex++;
            functionDefinitions.append(renderPhaseFunction(functionName, phase));
            nonForceRunCalls.add(functionName);
        }
        for (BuildPhaseDTO phase : forceRunPhases) {
            String functionName = "phase_" + phaseIndex++;
            functionDefinitions.append(renderPhaseFunction(functionName, phase));
            forceRunCalls.add(functionName);
        }

        String nonForceRunBody = nonForceRunCalls.isEmpty() ? "cd " + HADES_WORKING_DIRECTORY : String.join("\n", nonForceRunCalls);

        if (forceRunPhases.isEmpty()) {
            // No force-run phases: run the non-force-run phases directly under `set -e` so the first failing phase aborts the build.
            return "set -e\n" + functionDefinitions + nonForceRunBody;
        }

        // The non-force-run phases run inside a `set -e` subshell so the build fails fast, but its exit code is captured so the
        // force-run phases always run afterwards and the script still exits with the original build result.
        return functionDefinitions + "(\nset -e\n" + nonForceRunBody + "\n)\nbuild_exit_code=$?\n" + String.join("\n", forceRunCalls) + "\nexit ${build_exit_code}";
    }

    private static String renderPhaseFunction(String functionName, BuildPhaseDTO phase) {
        return functionName + "() {\ncd " + HADES_WORKING_DIRECTORY + "\n" + phase.script().strip() + "\n}\n";
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

    private String resolveResultIngestDirectory(List<BuildPhaseDTO> activePhases, ProgrammingExerciseBuildConfig buildConfig, ProjectType projectType) {
        List<String> rawResultPaths = activePhases.stream().map(BuildPhaseDTO::resultPaths).filter(Objects::nonNull).flatMap(List::stream)
                .filter(path -> path != null && !path.isBlank()).distinct().toList();

        // Only fall back to a language default when the active phases declare no result paths at all. Otherwise the parser scans a
        // single INGEST_DIR, so it must point at a directory that contains every declared suite.
        if (rawResultPaths.isEmpty()) {
            boolean isMaven = projectType != null && projectType.toString().contains("MAVEN");
            return isMaven ? HADES_WORKING_DIRECTORY + "/target/surefire-reports" : DEFAULT_INGEST_DIRECTORY;
        }

        List<String> resolvedResultPaths = buildScriptProviderService.replaceResultPathsPlaceholders(rawResultPaths, buildConfig);
        List<String> resultDirectories = resolvedResultPaths.stream().map(HadesTriggerService::resultPathToDirectory).distinct().toList();
        String commonAncestor = longestCommonDirectory(resultDirectories);
        return commonAncestor.isBlank() ? HADES_WORKING_DIRECTORY : HADES_WORKING_DIRECTORY + "/" + commonAncestor;
    }

    /**
     * Strips glob wildcards and the trailing file pattern from a resolved result path, leaving the directory that contains the
     * result files (relative to {@link #HADES_WORKING_DIRECTORY}).
     */
    private static String resultPathToDirectory(String resultPathGlob) {
        // The directory is the literal path prefix up to (but excluding) the first segment that contains a wildcard. The
        // parser scans INGEST_DIR recursively, so a leading "**" (results at an unknown depth) resolves to the working-directory
        // root and the recursive walk locates them. Anchoring the stripped remainder instead would point at a directory that
        // never exists (e.g. "**/target/surefire-reports" -> "target/surefire-reports", while the reports live under
        // "structural/target/..." and "behavior/target/..."). The final segment is always the file pattern, never a directory.
        String[] segments = resultPathGlob.strip().split("/");
        StringBuilder directory = new StringBuilder();
        for (int i = 0; i < segments.length - 1; i++) {
            String segment = segments[i];
            if (segment.isEmpty() || segment.contains("*") || segment.contains("?")) {
                break;
            }
            if (directory.length() > 0) {
                directory.append('/');
            }
            directory.append(segment);
        }
        return directory.toString();
    }

    /**
     * Computes the longest common parent directory (by path segment) that is an ancestor of all given directories. Returns an
     * empty string when the directories share no common ancestor, meaning the ingest directory becomes the working directory root.
     */
    private static String longestCommonDirectory(List<String> directories) {
        if (directories.isEmpty()) {
            return "";
        }
        String[] commonSegments = directories.get(0).split("/");
        int commonLength = commonSegments.length;
        for (String directory : directories) {
            String[] segments = directory.split("/");
            int index = 0;
            while (index < commonLength && index < segments.length && commonSegments[index].equals(segments[index])) {
                index++;
            }
            commonLength = index;
        }
        return String.join("/", Arrays.copyOfRange(commonSegments, 0, commonLength));
    }
}
