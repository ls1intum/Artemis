package de.tum.cit.aet.artemis.hyperion.service.codegeneration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
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

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ArtifactLocationDTO;
import de.tum.cit.aet.artemis.hyperion.dto.CodeGenerationResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyCheckResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyIssueDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedFileDTO;
import de.tum.cit.aet.artemis.hyperion.dto.HyperionCodeGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.service.HyperionConsistencyCheckService;
import de.tum.cit.aet.artemis.hyperion.service.HyperionProgrammingExerciseContextRendererService;
import de.tum.cit.aet.artemis.hyperion.service.HyperionReviewCommentContextRendererService;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.File;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;
import de.tum.cit.aet.artemis.programming.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingSubmissionService;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;

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

    private static final int DEFAULT_MAX_ITERATIONS = 2;

    private static final long TIMEOUT = 180_000; // 3 minutes

    private static final long POLL_INTERVAL = 3_000; // 3 seconds

    // Truncation limits for build/test feedback fed back into the next retry prompt: enough signal to debug failures, small enough to keep the LLM context cheap.
    private static final int MAX_FEEDBACK_SUMMARY_ITEMS = 20;

    private static final int MAX_FEEDBACK_SUMMARY_TEXT_LENGTH = 500;

    private static final String DELETABLE_SOURCE_PATH_PREFIX = "src/";

    private static final String DELETABLE_TEST_PATH_PREFIX = "test/";

    private record RepositorySetupResult(Repository repository, String originalCommitHash, boolean success) {
    }

    private enum BuildResultState {
        SUCCESS, FAILED, TIMED_OUT, PARTICIPATION_NOT_FOUND, CI_TRIGGER_FAILED,
    }

    private record BuildResultOutcome(Result result, BuildResultState state) {
    }

    private record GenerationExecutionResult(Result result, BuildResultOutcome buildResultOutcome, String lastCommitHash, int attemptsUsed, boolean generatedFilesCommitted,
            boolean deletionOnly) {
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

        private boolean deletionOnly;

        private GenerationExecutionResult snapshot() {
            return new GenerationExecutionResult(result, buildResultOutcome, lastCommitHash, attemptsUsed, generatedFilesCommitted, deletionOnly);
        }
    }

    private final String defaultBranch;

    private final GitService gitService;

    private final RepositoryService repositoryService;

    private final HyperionSolutionRepositoryService solutionStrategy;

    private final HyperionTemplateRepositoryService templateStrategy;

    private final HyperionTestRepositoryService testStrategy;

    private final HyperionConsistencyCheckService consistencyCheckService;

    private final HyperionReviewCommentContextRendererService reviewCommentContextRendererService;

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
            ProgrammingSubmissionService programmingSubmissionService, HyperionConsistencyCheckService consistencyCheckService,
            HyperionReviewCommentContextRendererService reviewCommentContextRendererService, ExerciseVersionService exerciseVersionService) {
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
        this.reviewCommentContextRendererService = reviewCommentContextRendererService;
        this.exerciseVersionService = exerciseVersionService;
    }

    private HyperionCodeGenerationService resolveStrategy(RepositoryType repositoryType) {
        return switch (repositoryType) {
            case SOLUTION -> solutionStrategy;
            case TEMPLATE -> templateStrategy;
            case TESTS -> testStrategy;
            default -> throw new IllegalArgumentException("Unsupported repository type for code generation: " + repositoryType);
        };
    }

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

    private void updateSingleFile(Repository repository, GeneratedFileDTO file, ProgrammingExercise exercise) throws IOException {
        if (gitService.getFileByName(repository, file.path()).isPresent()) {
            try {
                repositoryService.deleteFile(repository, file.path());
            }
            catch (Exception e) {
                log.warn("Failed to delete existing file {}: {}", file.path(), e.getMessage());
            }
        }
        try {
            InputStream inputStream = new ByteArrayInputStream(file.content().getBytes(StandardCharsets.UTF_8));
            repositoryService.createFile(repository, file.path(), inputStream);
        }
        catch (IOException e) {
            log.error("Failed to create file {} for exercise {}: {}", file.path(), exercise.getId(), e.getMessage(), e);
            throw e;
        }
    }

    private boolean deleteObsoleteFiles(Repository repository, List<String> deletedFiles, ProgrammingExercise exercise, RepositoryType repositoryType,
            HyperionCodeGenerationEventPublisher publisher, int iteration) throws IOException {
        boolean deletedAnyFile = false;
        for (String deletedFile : deletedFiles) {
            Optional<String> safePath = normalizeDeletablePath(deletedFile, repositoryType);
            if (safePath.isEmpty()) {
                log.warn("Ignoring unsafe obsolete file path '{}' for exercise {}", deletedFile, exercise.getId());
                continue;
            }
            Optional<File> obsoleteFile = gitService.getFileByName(repository, safePath.get());
            if (obsoleteFile.isEmpty()) {
                log.debug("Obsolete file {} does not exist in exercise {}, skipping deletion", safePath.get(), exercise.getId());
                continue;
            }
            if (!obsoleteFile.get().isFile()) {
                log.warn("Ignoring obsolete path '{}' for exercise {} because it is not a regular file", safePath.get(), exercise.getId());
                continue;
            }
            repositoryService.deleteFile(repository, safePath.get());
            publisher.fileDeleted(safePath.get(), repositoryType, iteration);
            deletedAnyFile = true;
            log.debug("Deleted obsolete file {} for exercise {}", safePath.get(), exercise.getId());
        }
        return deletedAnyFile;
    }

    private Optional<String> normalizeDeletablePath(String rawPath, RepositoryType repositoryType) {
        if (rawPath == null || rawPath.isBlank()) {
            return Optional.empty();
        }
        String sanitizedPath = rawPath.replace('\\', '/').trim();
        try {
            if (containsParentTraversal(sanitizedPath)) {
                return Optional.empty();
            }
            Path normalizedPath = Path.of(sanitizedPath).normalize();
            if (normalizedPath.isAbsolute()) {
                return Optional.empty();
            }
            String normalized = normalizedPath.toString().replace('\\', '/');
            // Reject hidden paths (`src/.env`, `test/.gitignore`, `src/.config/foo.java`, …); `.gitkeep` is the only carveout because empty-dir placeholders are part of the
            // cleanup flow.
            String[] segments = normalized.split("/");
            for (int i = 0; i < segments.length; i++) {
                if (segments[i].startsWith(".") && !(i == segments.length - 1 && segments[i].equals(".gitkeep"))) {
                    return Optional.empty();
                }
            }
            return isDeletablePathForRepositoryType(normalized, repositoryType) ? Optional.of(normalized) : Optional.empty();
        }
        catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    private boolean containsParentTraversal(String path) {
        return List.of(path.split("/")).contains("..");
    }

    private boolean isDeletablePathForRepositoryType(String normalizedPath, RepositoryType repositoryType) {
        // Whitelist by repo type: SOLUTION/TEMPLATE may only delete under src/; TESTS only under test/. Excludes the bare prefix itself.
        String prefix = switch (repositoryType) {
            case TEMPLATE, SOLUTION -> DELETABLE_SOURCE_PATH_PREFIX;
            case TESTS -> DELETABLE_TEST_PATH_PREFIX;
            default -> null;
        };
        return prefix != null && normalizedPath.startsWith(prefix) && normalizedPath.length() > prefix.length();
    }

    private CommitTriggerResult commitAndGetHash(Repository repository, User user, LocalVCRepositoryUri repositoryUri, ProgrammingExercise exercise, RepositoryType repositoryType)
            throws GitAPIException {
        repositoryService.commitChanges(repository, user);
        String newCommitHash = gitService.getLastCommitHash(repositoryUri);
        boolean buildTriggered = true;
        // TESTS reuses the solution participation: tests-repo builds run against the solution checkout.
        ProgrammingExerciseParticipation exerciseParticipation = switch (repositoryType) {
            case TEMPLATE -> programmingExerciseParticipationService.findTemplateParticipationByProgrammingExerciseId(exercise.getId());
            case SOLUTION, TESTS -> programmingExerciseParticipationService.retrieveSolutionParticipation(exercise);
            default -> throw new IllegalArgumentException("Unsupported repository type: " + repositoryType);
        };
        try {
            if (repositoryType == RepositoryType.TESTS) {
                programmingSubmissionService.createSolutionParticipationSubmissionWithTypeTest(exercise.getId(), newCommitHash);
            }
            continuousIntegrationTriggerService.triggerBuild(exerciseParticipation, newCommitHash, repositoryType);
        }
        catch (ContinuousIntegrationException e) {
            log.warn("Failed to trigger CI build for commit {} in exercise {}: {}", newCommitHash, exercise.getId(), e.getMessage());
            buildTriggered = false;
        }
        return new CommitTriggerResult(newCommitHash, buildTriggered);
    }

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

    private String extractBuildFeedback(Result result) {
        String buildLogs = extractBuildLogs(result);
        String testFeedback = extractTestFeedbackSummary(result);
        if (testFeedback.isBlank()) {
            return buildLogs;
        }
        return buildLogs + "\n\nTest feedback summary:\n" + testFeedback;
    }

    private String extractTestFeedbackSummary(Result result) {
        if (result == null) {
            return "";
        }
        try {
            return result.getFeedbacks().stream().filter(Objects::nonNull).filter(feedback -> feedback.getTestCase() != null)
                    .sorted((left, right) -> Boolean.compare(Boolean.TRUE.equals(left.isPositive()), Boolean.TRUE.equals(right.isPositive()))).limit(MAX_FEEDBACK_SUMMARY_ITEMS)
                    .map(this::formatFeedbackSummary).filter(summary -> !summary.isBlank()).collect(Collectors.joining("\n"));
        }
        catch (LazyInitializationException e) {
            log.warn("Could not load feedback entries for result {}: {}. Continuing with build logs only.", result.getId(), e.getMessage());
            return "";
        }
    }

    private String formatFeedbackSummary(Feedback feedback) {
        String testName = Optional.ofNullable(feedback.getTestCase().getTestName()).filter(name -> !name.isBlank()).orElse("Unnamed test");
        String status = Boolean.TRUE.equals(feedback.isPositive()) ? "PASSED" : "FAILED";
        String text = Optional.ofNullable(feedback.getDetailText()).filter(detail -> !detail.isBlank()).orElse(feedback.getText());
        if (text == null || text.isBlank()) {
            return "- " + testName + ": " + status;
        }
        String summaryText = text.lines().findFirst().orElse("").trim();
        if (summaryText.length() > MAX_FEEDBACK_SUMMARY_TEXT_LENGTH) {
            summaryText = summaryText.substring(0, MAX_FEEDBACK_SUMMARY_TEXT_LENGTH) + "...";
        }
        return "- " + testName + ": " + status + " - " + summaryText;
    }

    private void cleanupRepository(Repository repository, String originalCommitHash) {
        if (repository != null && originalCommitHash != null) {
            gitService.resetToOriginHead(repository);
        }
    }

    /**
     * Runs the iterative AI generation against the given repository: commit, trigger CI, poll, retry until the repository-specific build target is reached.
     *
     * @param exercise                  the exercise to generate against
     * @param user                      the initiating user (used for commits + telemetry)
     * @param courseId                  resolved course id for telemetry attribution
     * @param repositoryType            repository to target (SOLUTION / TEMPLATE / TESTS)
     * @param initialAutoGeneration     {@code true} for the automatic first-attempt flow, which skips retries
     * @param selectedFeedbackThreadIds review-thread ids forwarded to the prompt context, or {@code null}
     * @param publisher                 event publisher receiving websocket progress + completion updates
     * @return the final build result, or {@code null} on early failure (e.g. repo setup)
     */
    public Result generateAndCompileCode(ProgrammingExercise exercise, User user, Long courseId, RepositoryType repositoryType, boolean initialAutoGeneration,
            List<Long> selectedFeedbackThreadIds, HyperionCodeGenerationEventPublisher publisher) {
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
            executionResult = executeGenerationAttempts(exercise, user, courseId, repositoryType, initialAutoGeneration, selectedFeedbackThreadIds, publisher,
                    setupResult.repository(), repositoryUri, executionProgress);
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
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (executionResult.lastCommitHash != null && exerciseVersionService.isRepositoryTypeVersionable(repositoryType)) {
            exerciseVersionService.createExerciseVersion(exercise, user);
        }

        HyperionCodeGenerationEventDTO.CompletionStatus completionStatus = determineCompletionStatus(executionResult.generatedFilesCommitted, executionResult.buildResultOutcome);
        CompletionDetails completionDetails = buildCompletionDetails(repositoryType, executionResult.generatedFilesCommitted, executionResult.deletionOnly,
                executionResult.buildResultOutcome);
        publisher.done(completionStatus, completionDetails.reason(), completionDetails.reasonParams(), executionResult.attemptsUsed, completionDetails.message());

        return executionResult.result;
    }

    public Result generateAndCompileCode(ProgrammingExercise exercise, User user, Long courseId, RepositoryType repositoryType, boolean initialAutoGeneration,
            HyperionCodeGenerationEventPublisher publisher) {
        return generateAndCompileCode(exercise, user, courseId, repositoryType, initialAutoGeneration, null, publisher);
    }

    private GenerationExecutionResult executeGenerationAttempts(ProgrammingExercise exercise, User user, Long courseId, RepositoryType repositoryType,
            boolean initialAutoGeneration, List<Long> selectedFeedbackThreadIds, HyperionCodeGenerationEventPublisher publisher, Repository repository,
            LocalVCRepositoryUri repositoryUri, GenerationExecutionProgress executionProgress) throws Exception {
        HyperionCodeGenerationService strategy = resolveStrategy(repositoryType);
        String consistencyIssues = buildConsistencyIssuesPrompt(exercise);
        String buildEnvironmentContext = repositoryStructureService.getBuildEnvironmentContext(repository);
        String selectedFeedbackThreads = buildSelectedFeedbackPrompt(exercise, repositoryType, selectedFeedbackThreadIds);
        boolean useSelectedFeedback = selectedFeedbackThreadIds != null;
        String lastBuildLogs = null;
        int maxIterations = resolveMaxIterations(repositoryType, initialAutoGeneration);

        for (int attempt = 0; attempt < maxIterations; attempt++) {
            executionProgress.attemptsUsed = attempt + 1;
            GenerationAttemptResult attemptResult = executeGenerationAttempt(strategy, exercise, user, courseId, repositoryType, publisher, repository, repositoryUri,
                    lastBuildLogs, buildEnvironmentContext, consistencyIssues, selectedFeedbackThreads, useSelectedFeedback, executionProgress);
            if (attemptResult != null) {
                executionProgress.buildResultOutcome = attemptResult.buildResultOutcome();
                executionProgress.result = executionProgress.buildResultOutcome.result();
            }
            publisher.progress(attempt + 1);

            if (executionProgress.buildResultOutcome.state() != BuildResultState.FAILED) {
                break;
            }

            lastBuildLogs = extractBuildFeedback(executionProgress.result);
        }

        return executionProgress.snapshot();
    }

    private int resolveMaxIterations(RepositoryType repositoryType, boolean initialAutoGeneration) {
        if (initialAutoGeneration && (repositoryType == RepositoryType.SOLUTION || repositoryType == RepositoryType.TEMPLATE)) {
            return 1;
        }
        return DEFAULT_MAX_ITERATIONS;
    }

    private GenerationAttemptResult executeGenerationAttempt(HyperionCodeGenerationService strategy, ProgrammingExercise exercise, User user, Long courseId,
            RepositoryType repositoryType, HyperionCodeGenerationEventPublisher publisher, Repository repository, LocalVCRepositoryUri repositoryUri, String lastBuildLogs,
            String buildEnvironmentContext, String consistencyIssues, String selectedFeedbackThreads, boolean useSelectedFeedback, GenerationExecutionProgress executionProgress)
            throws Exception {
        String repositoryStructure = repositoryStructureService.getRepositoryStructure(repository);

        CodeGenerationResponseDTO generationResponse = useSelectedFeedback
                ? strategy.generateCode(user, exercise, courseId, lastBuildLogs, repositoryStructure, buildEnvironmentContext, consistencyIssues, selectedFeedbackThreads)
                : strategy.generateCode(user, exercise, courseId, lastBuildLogs, repositoryStructure, buildEnvironmentContext, consistencyIssues);

        List<GeneratedFileDTO> generatedFiles = generationResponse != null ? generationResponse.getFiles() : List.of();
        List<String> deletedFiles = generationResponse != null ? generationResponse.getDeletedFiles() : List.of();

        if (generatedFiles.isEmpty() && deletedFiles.isEmpty()) {
            return null;
        }

        boolean deletedAnyFile = deleteObsoleteFiles(repository, deletedFiles, exercise, repositoryType, publisher, executionProgress.attemptsUsed);
        boolean publishedAnyFile = publishGeneratedFiles(repository, generatedFiles, exercise, repositoryType, publisher, executionProgress.attemptsUsed);
        if (!deletedAnyFile && !publishedAnyFile) {
            return null;
        }
        CommitTriggerResult commitTriggerResult = commitAndGetHash(repository, user, repositoryUri, exercise, repositoryType);
        String commitHash = commitTriggerResult.commitHash();
        executionProgress.lastCommitHash = commitHash;
        // True iff every committed attempt so far has been deletion-only (no published files). Drives the "files removed" vs "files generated" wording.
        executionProgress.deletionOnly = (!executionProgress.generatedFilesCommitted || executionProgress.deletionOnly) && !publishedAnyFile;
        executionProgress.generatedFilesCommitted = true;
        if (!commitTriggerResult.buildTriggered()) {
            return new GenerationAttemptResult(commitHash, new BuildResultOutcome(null, BuildResultState.CI_TRIGGER_FAILED));
        }
        return new GenerationAttemptResult(commitHash, waitForBuildResult(exercise, commitHash, repositoryType));
    }

    private boolean publishGeneratedFiles(Repository repository, List<GeneratedFileDTO> generatedFiles, ProgrammingExercise exercise, RepositoryType repositoryType,
            HyperionCodeGenerationEventPublisher publisher, int iteration) throws IOException {
        boolean publishedAnyFile = false;
        for (GeneratedFileDTO file : generatedFiles) {
            Optional<GeneratedFileDTO> safeFile = normalizeGeneratedFile(file);
            if (safeFile.isEmpty()) {
                log.warn("Ignoring generated file '{}' for {} repository in exercise {}", file != null ? file.path() : null, repositoryType, exercise.getId());
                continue;
            }
            GeneratedFileDTO normalizedFile = safeFile.get();
            boolean existed = gitService.getFileByName(repository, normalizedFile.path()).isPresent();
            updateSingleFile(repository, normalizedFile, exercise);
            publishedAnyFile = true;
            if (existed) {
                publisher.fileUpdated(normalizedFile.path(), repositoryType, iteration);
            }
            else {
                publisher.newFile(normalizedFile.path(), repositoryType, iteration);
            }
        }
        return publishedAnyFile;
    }

    private Optional<GeneratedFileDTO> normalizeGeneratedFile(GeneratedFileDTO file) {
        if (file == null || file.path() == null || file.path().isBlank()) {
            return Optional.empty();
        }
        String sanitizedPath = file.path().replace('\\', '/').trim();
        try {
            if (containsParentTraversal(sanitizedPath)) {
                return Optional.empty();
            }
            Path normalizedPath = Path.of(sanitizedPath).normalize();
            if (normalizedPath.isAbsolute()) {
                return Optional.empty();
            }
            String normalized = normalizedPath.toString().replace('\\', '/');
            return Optional.of(new GeneratedFileDTO(normalized, file.content()));
        }
        catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    private CompletionDetails buildCompletionDetails(RepositoryType repositoryType, boolean generatedFilesCommitted, boolean deletionOnly, BuildResultOutcome buildResultOutcome) {
        if (!generatedFilesCommitted) {
            return new CompletionDetails(repositoryGenerationLabel(repositoryType) + " did not produce any committed files.",
                    HyperionCodeGenerationEventDTO.CompletionReason.NO_COMMITTED_FILES, Map.of());
        }

        HyperionCodeGenerationEventDTO.CompletionReason completionReason = buildCompletionReason(buildResultOutcome.state());
        return new CompletionDetails(committedFilesMessagePrefix(repositoryType, deletionOnly) + buildResultMessageSuffix(completionReason), completionReason, Map.of());
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

    private String committedFilesMessagePrefix(RepositoryType repositoryType, boolean deletionOnly) {
        if (deletionOnly) {
            return switch (repositoryType) {
                case TEMPLATE -> "Obsolete template files were removed from the template repository";
                case SOLUTION -> "Obsolete solution files were removed from the solution repository";
                case TESTS -> "Obsolete test files were removed from the test repository";
                default -> "Obsolete files were removed from the repository";
            };
        }
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

    private String buildSelectedFeedbackPrompt(ProgrammingExercise exercise, RepositoryType repositoryType, List<Long> selectedFeedbackThreadIds) {
        if (reviewCommentContextRendererService == null) {
            return "{\"repositoryType\":\"" + repositoryType.name() + "\",\"threads\":[]}";
        }
        try {
            return reviewCommentContextRendererService.renderCodeGenerationSelectedFeedback(exercise.getId(), repositoryType, selectedFeedbackThreadIds);
        }
        catch (RuntimeException e) {
            log.warn("Selected feedback thread rendering failed for exercise {}: {}", exercise.getId(), e.getMessage(), e);
            return "{\"repositoryType\":\"" + repositoryType.name() + "\",\"threads\":[]}";
        }
    }

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
                        return new BuildResultOutcome(result.get(), hasReachedTargetResult(repositoryType, result.get()) ? BuildResultState.SUCCESS : BuildResultState.FAILED);
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

    private boolean hasReachedTargetResult(RepositoryType repositoryType, Result result) {
        if (result == null) {
            return false;
        }
        return switch (repositoryType) {
            case TEMPLATE -> isExactScore(result, 0.0) && result.getTestCaseCount() != null && result.getTestCaseCount() > 0;
            case SOLUTION -> isExactScore(result, 100.0);
            case TESTS -> Boolean.TRUE.equals(result.isSuccessful());
            default -> false;
        };
    }

    private boolean isExactScore(Result result, double targetScore) {
        Double score = result.getScore();
        return score != null && Double.compare(score, targetScore) == 0;
    }
}
