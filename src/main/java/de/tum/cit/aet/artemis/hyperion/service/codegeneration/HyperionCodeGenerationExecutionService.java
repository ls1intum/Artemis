package de.tum.cit.aet.artemis.hyperion.service.codegeneration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.hibernate.LazyInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ArtifactLocationDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyCheckResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyIssueDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedFileDTO;
import de.tum.cit.aet.artemis.hyperion.dto.HyperionCodeGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.service.HyperionConsistencyCheckService;
import de.tum.cit.aet.artemis.hyperion.service.HyperionProgrammingExerciseContextRendererService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingSubmissionService;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;

/**
 * Service responsible for orchestrating the iterative code generation and compilation process.
 * Manages the complete lifecycle of AI-powered code generation including Git operations,
 * build triggering, and feedback loop integration for iterative improvement.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionCodeGenerationExecutionService {

    private static final Logger log = LoggerFactory.getLogger(HyperionCodeGenerationExecutionService.class);

    private static final int MAX_ITERATIONS = 2;

    private static final long TIMEOUT = 180_000; // 3 minutes

    private static final long POLL_INTERVAL = 3_000; // 3 seconds

    /**
     * Holds repository setup results
     */
    private record RepositorySetupResult(Repository repository, String originalCommitHash, boolean success) {
    }

    private enum BuildResultState {
        SUCCESS, FAILED, TIMED_OUT, PARTICIPATION_NOT_FOUND, CI_TRIGGER_FAILED,
    }

    private record BuildResultOutcome(Result result, BuildResultState state) {
    }

    private record GenerationExecutionResult(Result result, BuildResultOutcome buildResultOutcome, String lastCommitHash, int attemptsUsed, boolean generatedFilesCommitted) {
    }

    private record GenerationAttemptResult(String commitHash, BuildResultOutcome buildResultOutcome) {
    }

    private record CommitTriggerResult(String commitHash, boolean buildTriggered) {
    }

    private record CompletionDetails(String message, HyperionCodeGenerationEventDTO.CompletionReason reason, Map<String, String> reasonParams) {
    }

    private static final class GenerationExecutionProgress {

        private Result result;

        private BuildResultOutcome buildResultOutcome = new BuildResultOutcome(null, BuildResultState.TIMED_OUT);

        private String lastCommitHash;

        private int attemptsUsed;

        private boolean generatedFilesCommitted;

        private GenerationExecutionResult snapshot() {
            return new GenerationExecutionResult(result, buildResultOutcome, lastCommitHash, attemptsUsed, generatedFilesCommitted);
        }
    }

    private final String defaultBranch;

    private final GitService gitService;

    private final RepositoryService repositoryService;

    private final HyperionSolutionRepositoryService solutionStrategy;

    private final HyperionTemplateRepositoryService templateStrategy;

    private final HyperionTestRepositoryService testStrategy;

    private final HyperionConsistencyCheckService consistencyCheckService;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final ResultRepository resultRepository;

    private final ContinuousIntegrationTriggerService continuousIntegrationTriggerService;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final HyperionProgrammingExerciseContextRendererService repositoryStructureService;

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final ExerciseVersionService exerciseVersionService;

    public HyperionCodeGenerationExecutionService(@Value("${artemis.version-control.default-branch:main}") String defaultBranch, GitService gitService,
            RepositoryService repositoryService, SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository, ProgrammingSubmissionRepository programmingSubmissionRepository,
            ResultRepository resultRepository, ContinuousIntegrationTriggerService continuousIntegrationTriggerService,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService, HyperionProgrammingExerciseContextRendererService repositoryStructureService,
            HyperionSolutionRepositoryService solutionStrategy, HyperionTemplateRepositoryService templateStrategy, HyperionTestRepositoryService testStrategy,
            ProgrammingSubmissionService programmingSubmissionService, HyperionConsistencyCheckService consistencyCheckService, ExerciseVersionService exerciseVersionService) {
        this.defaultBranch = defaultBranch;
        this.gitService = gitService;
        this.repositoryService = repositoryService;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.resultRepository = resultRepository;
        this.continuousIntegrationTriggerService = continuousIntegrationTriggerService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.repositoryStructureService = repositoryStructureService;
        this.solutionStrategy = solutionStrategy;
        this.templateStrategy = templateStrategy;
        this.testStrategy = testStrategy;
        this.programmingSubmissionService = programmingSubmissionService;
        this.consistencyCheckService = consistencyCheckService;
        this.exerciseVersionService = exerciseVersionService;
    }

    /**
     * Resolves the appropriate code generation strategy based on repository type.
     *
     * @param repositoryType the type of repository (SOLUTION, TEMPLATE, TESTS)
     * @return the corresponding code generation strategy
     * @throws IllegalArgumentException if repository type is not supported
     */
    private HyperionCodeGenerationService resolveStrategy(RepositoryType repositoryType) {
        return switch (repositoryType) {
            case SOLUTION -> solutionStrategy;
            case TEMPLATE -> templateStrategy;
            case TESTS -> testStrategy;
            default -> throw new IllegalArgumentException("Unsupported repository type for code generation: " + repositoryType);
        };
    }

    /**
     * Sets up repository for code generation by validating URI and checking out repository.
     *
     * @param exercise       the programming exercise
     * @param repositoryType the type of repository to set up (TEMPLATE, SOLUTION, or TESTS)
     * @return repository setup result containing repository and commit hash
     */
    private RepositorySetupResult setupRepository(ProgrammingExercise exercise, RepositoryType repositoryType) {
        LocalVCRepositoryUri repositoryUri = exercise.getRepositoryURI(repositoryType);
        if (repositoryUri == null) {
            log.error("Could not get {} repository URI for exercise {}", repositoryType, exercise.getId());
            return new RepositorySetupResult(null, null, false);
        }

        try {
            Repository repository = gitService.getOrCheckoutRepository(repositoryUri, true, defaultBranch, false);

            if (repository == null) {
                log.error("Failed to checkout repository for exercise {}", exercise.getId());
                return new RepositorySetupResult(null, null, false);
            }

            String originalCommitHash = gitService.getLastCommitHash(repositoryUri);
            return new RepositorySetupResult(repository, originalCommitHash, true);

        }
        catch (GitAPIException e) {
            log.error("Failed to setup repository for exercise {}: {}", exercise.getId(), e.getMessage(), e);
            return new RepositorySetupResult(null, null, false);
        }
    }

    /**
     * Updates a single file in the repository, handling deletion of existing file and creation of new one.
     *
     * @param repository the repository
     * @param file       the generated file to update
     * @param exercise   the programming exercise (for logging)
     * @throws IOException if file operations fail
     */
    private void updateSingleFile(Repository repository, GeneratedFileDTO file, ProgrammingExercise exercise) throws IOException {
        // Check if file exists before attempting to delete
        if (gitService.getFileByName(repository, file.path()).isPresent()) {
            try {
                repositoryService.deleteFile(repository, file.path());
                log.debug("Deleted existing file: {}", file.path());
            }
            catch (Exception e) {
                log.warn("Failed to delete existing file {}: {}", file.path(), e.getMessage());
            }
        }
        else {
            log.debug("File {} does not exist, will create new", file.path());
        }

        try {
            InputStream inputStream = new ByteArrayInputStream(file.content().getBytes(StandardCharsets.UTF_8));
            repositoryService.createFile(repository, file.path(), inputStream);
            log.debug("Created/updated file: {}", file.path());
        }
        catch (IOException e) {
            log.error("Failed to create file {} for exercise {}: {}", file.path(), exercise.getId(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Commits changes, triggers CI build, and returns the new commit hash together with trigger status.
     *
     * @param repository    the repository
     * @param user          the user making the commit
     * @param repositoryUri the repository URI
     * @param exercise      the programming exercise (needed for triggering CI build)
     * @return commit metadata including the new commit hash and whether CI accepted the build trigger
     * @throws GitAPIException if git operations fail
     */
    private CommitTriggerResult commitAndGetHash(Repository repository, User user, LocalVCRepositoryUri repositoryUri, ProgrammingExercise exercise, RepositoryType repositoryType)
            throws GitAPIException {
        repositoryService.commitChanges(repository, user);
        String newCommitHash = gitService.getLastCommitHash(repositoryUri);
        boolean buildTriggered = true;
        ProgrammingExerciseParticipation exerciseParticipation = switch (repositoryType) {
            case TEMPLATE -> programmingExerciseParticipationService.findTemplateParticipationByProgrammingExerciseId(exercise.getId());
            case SOLUTION -> programmingExerciseParticipationService.retrieveSolutionParticipation(exercise);
            case TESTS -> programmingExerciseParticipationService.retrieveSolutionParticipation(exercise); // tests use solution participation
            default -> throw new IllegalArgumentException("Unsupported repository type: " + repositoryType);
        };
        try {
            if (repositoryType == RepositoryType.TESTS) {
                // Create TEST submission so polling can find the result
                programmingSubmissionService.createSolutionParticipationSubmissionWithTypeTest(exercise.getId(), newCommitHash);
                // Make clear this was triggered by a tests change
                continuousIntegrationTriggerService.triggerBuild(exerciseParticipation, newCommitHash, RepositoryType.TESTS);
            }
            else {
                continuousIntegrationTriggerService.triggerBuild(exerciseParticipation, newCommitHash, repositoryType);
            }
            log.debug("Triggered CI build for commit {} (repoType={}) in exercise {}", newCommitHash, repositoryType, exercise.getId());
        }
        catch (ContinuousIntegrationException e) {
            log.warn("Failed to trigger CI build for commit {} in exercise {}: {}", newCommitHash, exercise.getId(), e.getMessage());
            buildTriggered = false;
        }
        return new CommitTriggerResult(newCommitHash, buildTriggered);
    }

    /**
     * Extracts build logs from a result.
     *
     * @param result the build result
     * @return extracted build logs or default message
     */
    private String extractBuildLogs(Result result) {
        if (result != null && result.getSubmission() instanceof ProgrammingSubmission programmingSubmission) {
            try {
                return programmingSubmission.getBuildLogEntries().stream().map(BuildLogEntry::getLog).collect(Collectors.joining("\n"));
            }
            catch (LazyInitializationException e) {
                log.warn("Could not load build log entries for submission {}: {}. Using fallback message.", programmingSubmission.getId(), e.getMessage());
                return "Build logs could not be retrieved due to session constraints. Build failed with errors.";
            }
        }
        return "Build failed to produce a result.";
    }

    /**
     * Cleans up repository by resetting to original state.
     *
     * @param repository         the repository to clean up
     * @param originalCommitHash the original commit hash to reset to
     */
    private void cleanupRepository(Repository repository, String originalCommitHash) {
        if (repository != null && originalCommitHash != null) {
            gitService.resetToOriginHead(repository);
        }
    }

    /**
     * Generates and compiles code with websocket publisher callbacks.
     *
     * @param exercise       the programming exercise
     * @param user           the initiating user
     * @param courseId       the resolved course id for telemetry attribution
     * @param repositoryType repository type to generate
     * @param publisher      event publisher for websocket updates
     * @return the latest build result or null
     */
    public Result generateAndCompileCode(ProgrammingExercise exercise, User user, Long courseId, RepositoryType repositoryType, HyperionCodeGenerationEventPublisher publisher) {
        RepositorySetupResult setupResult = setupRepository(exercise, repositoryType);
        if (!setupResult.success()) {
            publisher.error("Repository setup failed");
            return null;
        }
        log.info("Setup Repo success");

        LocalVCRepositoryUri repositoryUri = exercise.getRepositoryURI(repositoryType);
        GenerationExecutionProgress executionProgress = new GenerationExecutionProgress();
        GenerationExecutionResult executionResult;

        try {
            executionResult = executeGenerationAttempts(exercise, user, courseId, repositoryType, publisher, setupResult.repository(), repositoryUri, executionProgress);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            publisher.error(e.getMessage());
            executionResult = executionProgress.snapshot();
        }
        catch (Exception e) {
            publisher.error(e.getMessage());
            executionResult = executionProgress.snapshot();
        }
        finally {
            cleanupRepository(setupResult.repository(), setupResult.originalCommitHash());
        }

        // Ensure the remote reflects the last pushed commit before signaling DONE
        try {
            if (executionResult.lastCommitHash != null) {
                waitUntilRemoteHasCommit(repositoryUri, executionResult.lastCommitHash, 3000);
            }
        }
        catch (InterruptedException ignored) {
        }

        if (executionResult.lastCommitHash != null && exerciseVersionService.isRepositoryTypeVersionable(repositoryType)) {
            exerciseVersionService.createExerciseVersion(exercise, user);
        }

        HyperionCodeGenerationEventDTO.CompletionStatus completionStatus = determineCompletionStatus(executionResult.generatedFilesCommitted, executionResult.buildResultOutcome);
        CompletionDetails completionDetails = buildCompletionDetails(repositoryType, executionResult.generatedFilesCommitted, executionResult.buildResultOutcome);
        int reportedAttempts = executionResult.attemptsUsed;
        publisher.done(completionStatus, completionDetails.reason(), completionDetails.reasonParams(), reportedAttempts, completionDetails.message());

        return executionResult.result;
    }

    private GenerationExecutionResult executeGenerationAttempts(ProgrammingExercise exercise, User user, Long courseId, RepositoryType repositoryType,
            HyperionCodeGenerationEventPublisher publisher, Repository repository, LocalVCRepositoryUri repositoryUri, GenerationExecutionProgress executionProgress)
            throws Exception {
        HyperionCodeGenerationService strategy = resolveStrategy(repositoryType);
        String consistencyIssues = buildConsistencyIssuesPrompt(exercise);
        String lastBuildLogs = null;

        for (int attempt = 0; attempt < MAX_ITERATIONS; attempt++) {
            executionProgress.attemptsUsed = attempt + 1;
            GenerationAttemptResult attemptResult = executeGenerationAttempt(strategy, exercise, user, courseId, repositoryType, publisher, repository, repositoryUri,
                    lastBuildLogs, consistencyIssues, executionProgress);
            if (attemptResult != null) {
                executionProgress.buildResultOutcome = attemptResult.buildResultOutcome();
                executionProgress.result = executionProgress.buildResultOutcome.result();
            }
            publisher.progress(attempt + 1);

            if (executionProgress.buildResultOutcome.state() != BuildResultState.FAILED) {
                break;
            }

            lastBuildLogs = extractBuildLogs(executionProgress.result);
        }

        return executionProgress.snapshot();
    }

    private GenerationAttemptResult executeGenerationAttempt(HyperionCodeGenerationService strategy, ProgrammingExercise exercise, User user, Long courseId,
            RepositoryType repositoryType, HyperionCodeGenerationEventPublisher publisher, Repository repository, LocalVCRepositoryUri repositoryUri, String lastBuildLogs,
            String consistencyIssues, GenerationExecutionProgress executionProgress) throws Exception {
        String repositoryStructure = repositoryStructureService.getRepositoryStructure(repository);
        List<GeneratedFileDTO> generatedFiles = strategy.generateCode(user, exercise, courseId, lastBuildLogs, repositoryStructure, consistencyIssues);
        if (generatedFiles == null || generatedFiles.isEmpty()) {
            return null;
        }

        publishGeneratedFiles(repository, generatedFiles, exercise, repositoryType, publisher, executionProgress.attemptsUsed);
        CommitTriggerResult commitTriggerResult = commitAndGetHash(repository, user, repositoryUri, exercise, repositoryType);
        String commitHash = commitTriggerResult.commitHash();
        executionProgress.lastCommitHash = commitHash;
        executionProgress.generatedFilesCommitted = true;
        if (!commitTriggerResult.buildTriggered()) {
            return new GenerationAttemptResult(commitHash, new BuildResultOutcome(null, BuildResultState.CI_TRIGGER_FAILED));
        }
        return new GenerationAttemptResult(commitHash, waitForBuildResult(exercise, commitHash, repositoryType));
    }

    private void publishGeneratedFiles(Repository repository, List<GeneratedFileDTO> generatedFiles, ProgrammingExercise exercise, RepositoryType repositoryType,
            HyperionCodeGenerationEventPublisher publisher, int iteration) throws IOException {
        for (GeneratedFileDTO file : generatedFiles) {
            boolean existed = gitService.getFileByName(repository, file.path()).isPresent();
            updateSingleFile(repository, file, exercise);
            if (existed) {
                publisher.fileUpdated(file.path(), repositoryType, iteration);
            }
            else {
                publisher.newFile(file.path(), repositoryType, iteration);
            }
        }
    }

    private CompletionDetails buildCompletionDetails(RepositoryType repositoryType, boolean generatedFilesCommitted, BuildResultOutcome buildResultOutcome) {
        if (!generatedFilesCommitted) {
            return new CompletionDetails(repositoryGenerationLabel(repositoryType) + " did not produce any committed files.",
                    HyperionCodeGenerationEventDTO.CompletionReason.NO_COMMITTED_FILES, Map.of());
        }

        HyperionCodeGenerationEventDTO.CompletionReason completionReason = buildCompletionReason(buildResultOutcome.state());
        return new CompletionDetails(committedFilesMessagePrefix(repositoryType) + buildResultMessageSuffix(completionReason), completionReason, Map.of());
    }

    private HyperionCodeGenerationEventDTO.CompletionStatus determineCompletionStatus(boolean generatedFilesCommitted, BuildResultOutcome buildResultOutcome) {
        if (!generatedFilesCommitted) {
            return HyperionCodeGenerationEventDTO.CompletionStatus.ERROR;
        }

        return buildResultOutcome.state() == BuildResultState.SUCCESS ? HyperionCodeGenerationEventDTO.CompletionStatus.SUCCESS
                : HyperionCodeGenerationEventDTO.CompletionStatus.PARTIAL;
    }

    private String repositoryGenerationLabel(RepositoryType repositoryType) {
        return switch (repositoryType) {
            case TEMPLATE -> "Template generation";
            case SOLUTION -> "Solution generation";
            case TESTS -> "Test generation";
            default -> "Code generation";
        };
    }

    private String committedFilesMessagePrefix(RepositoryType repositoryType) {
        return switch (repositoryType) {
            case TEMPLATE -> "Template files were generated and committed to the template repository";
            case SOLUTION -> "Solution files were generated and committed to the solution repository";
            case TESTS -> "Test files were generated and committed to the test repository";
            default -> "Files were generated and committed";
        };
    }

    private HyperionCodeGenerationEventDTO.CompletionReason buildCompletionReason(BuildResultState buildResultState) {
        return switch (buildResultState) {
            case SUCCESS -> HyperionCodeGenerationEventDTO.CompletionReason.BUILD_SUCCEEDED;
            case FAILED -> HyperionCodeGenerationEventDTO.CompletionReason.BUILD_FAILED;
            case TIMED_OUT -> HyperionCodeGenerationEventDTO.CompletionReason.BUILD_TIMED_OUT;
            case PARTICIPATION_NOT_FOUND -> HyperionCodeGenerationEventDTO.CompletionReason.PARTICIPATION_NOT_FOUND;
            case CI_TRIGGER_FAILED -> HyperionCodeGenerationEventDTO.CompletionReason.CI_TRIGGER_FAILED;
        };
    }

    private String buildResultMessageSuffix(HyperionCodeGenerationEventDTO.CompletionReason completionReason) {
        return switch (completionReason) {
            case BUILD_SUCCEEDED -> ".";
            case BUILD_FAILED -> ", but the build failed.";
            case BUILD_TIMED_OUT -> ", but the build result is not available yet because polling timed out.";
            case PARTICIPATION_NOT_FOUND -> ", but Hyperion could not resolve the participation needed to read the build result.";
            case CI_TRIGGER_FAILED -> ", but Hyperion could not trigger the CI build.";
            case NO_COMMITTED_FILES -> ".";
        };
    }

    /**
     * Builds a prompt-friendly summary of consistency check issues for the given exercise.
     *
     * @param exercise the programming exercise to analyze
     * @return formatted consistency issues, {@code "None"} when no issues are found, or a failure marker if the check fails
     */
    private String buildConsistencyIssuesPrompt(ProgrammingExercise exercise) {
        try {
            ConsistencyCheckResponseDTO response = consistencyCheckService.checkConsistency(exercise.getId());
            if (response == null || response.issues() == null || response.issues().isEmpty()) {
                return "None";
            }

            StringBuilder builder = new StringBuilder();
            int consistencyIssueCounter = 1;
            for (ConsistencyIssueDTO issue : response.issues()) {
                if (issue == null) {
                    continue;
                }
                builder.append(consistencyIssueCounter++).append(". [").append(issue.severity()).append("] ").append(issue.category()).append(": ").append(issue.description())
                        .append('\n');
                if (issue.suggestedFix() != null && !issue.suggestedFix().isBlank()) {
                    builder.append("   Suggested fix: ").append(issue.suggestedFix()).append('\n');
                }
                if (issue.relatedLocations() != null && !issue.relatedLocations().isEmpty()) {
                    String locations = issue.relatedLocations().stream().filter(Objects::nonNull).map(this::formatLocation).filter(l -> !l.isBlank())
                            .collect(Collectors.joining("; "));
                    if (!locations.isBlank()) {
                        builder.append("   Locations: ").append(locations).append('\n');
                    }
                }
            }
            if (builder.length() == 0) {
                return "None";
            }
            return builder.toString().trim();
        }
        catch (RuntimeException e) {
            log.warn("Consistency check failed for exercise {}: {}", exercise.getId(), e.getMessage(), e);
            // Best-effort context for code generation only; don't surface this to the client (not a consistency check flow).
            return "Unavailable (consistency check failed)";
        }
    }

    /**
     * Formats an artifact location for prompt inclusion.
     *
     * @param location the artifact location data
     * @return formatted location string
     */
    private String formatLocation(ArtifactLocationDTO location) {
        if (location == null) {
            return "";
        }
        String safeType = location.type() != null ? location.type().toString() : "";
        // Default to problem_statement.md when the location doesn't specify a concrete file path.
        String path = location.filePath() != null && !location.filePath().isBlank() ? location.filePath() : "problem_statement.md";
        String lineSuffix = "";
        if (location.startLine() != null && location.endLine() != null) {
            lineSuffix = ":" + location.startLine() + "-" + location.endLine();
        }
        else if (location.startLine() != null) {
            lineSuffix = ":" + location.startLine();
        }
        String typePrefix = safeType.isBlank() ? "" : safeType + ":";
        return typePrefix + path + lineSuffix;
    }

    /**
     * Waits until the remote head matches the expected commit hash or timeout.
     *
     * @param repositoryUri target repository
     * @param expectedHash  commit hash to wait for
     * @param timeoutMs     max wait time in milliseconds
     */
    private void waitUntilRemoteHasCommit(LocalVCRepositoryUri repositoryUri, String expectedHash, long timeoutMs) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            try {
                String head = gitService.getLastCommitHash(repositoryUri);
                if (expectedHash != null && expectedHash.equals(head)) {
                    return;
                }
            }
            catch (Exception ignored) {
            }
            Thread.sleep(300);
        }
    }

    /**
     * Waits for the build result from the CI system for a specific commit.
     * Polls the database for submission and result data with configurable timeout and interval.
     *
     * @param exercise   the programming exercise being built
     * @param commitHash the specific commit hash to wait for results
     * @return the build result if found within timeout, null if timed out
     * @throws InterruptedException if the waiting thread is interrupted
     */
    private BuildResultOutcome waitForBuildResult(ProgrammingExercise exercise, String commitHash, RepositoryType repositoryType) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        TemplateProgrammingExerciseParticipation templateParticipation = templateProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(exercise.getId())
                .orElse(null);
        SolutionProgrammingExerciseParticipation solutionParticipation = solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(exercise.getId())
                .orElse(null);
        ProgrammingExerciseParticipation participation = switch (repositoryType) {
            case TEMPLATE -> templateParticipation;
            case SOLUTION -> solutionParticipation;
            case TESTS -> solutionParticipation; // tests also use solution participation
            default -> null;
        };
        if (participation == null) {
            log.warn("Could not find participation for repoType {} in exercise {}", repositoryType, exercise.getId());
            return new BuildResultOutcome(null, BuildResultState.PARTICIPATION_NOT_FOUND);
        }

        int pollCount = 0;
        while (System.currentTimeMillis() - startTime < TIMEOUT) {
            try {
                ProgrammingSubmission submission = programmingSubmissionRepository
                        .findFirstByParticipationIdAndCommitHashOrderByIdDescWithFeedbacksAndTeamStudents(participation.getId(), commitHash);

                if (submission != null) {
                    Optional<Result> result = resultRepository.findLatestResultWithFeedbacksAndTestcasesForSubmission(submission.getId());

                    if (result.isPresent()) {
                        log.debug("Found build result for commit {} after {} polls ({}ms)", commitHash, pollCount, System.currentTimeMillis() - startTime);
                        return new BuildResultOutcome(result.get(), result.get().isSuccessful() ? BuildResultState.SUCCESS : BuildResultState.FAILED);
                    }
                }

                pollCount++;

                Thread.sleep(POLL_INTERVAL);
            }
            catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw ie;
            }
            catch (Exception e) {
                log.warn("Exception while polling for build result for commit {}: {}. Continuing...", commitHash, e.getMessage());
                Thread.sleep(POLL_INTERVAL);
            }
        }
        log.warn("Timed out waiting for build result for commit {} in exercise {} after {} polls ({}ms)", commitHash, exercise.getId(), pollCount, TIMEOUT);
        return new BuildResultOutcome(null, BuildResultState.TIMED_OUT);
    }
}
