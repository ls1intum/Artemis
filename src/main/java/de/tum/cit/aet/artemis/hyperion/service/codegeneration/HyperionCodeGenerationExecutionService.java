package de.tum.cit.aet.artemis.hyperion.service.codegeneration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.hibernate.LazyInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedFileDTO;
import de.tum.cit.aet.artemis.hyperion.service.HyperionProgrammingExerciseContextRendererService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
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

    /**
     * Holds iteration execution results
     */
    private record IterationResult(Result result, String buildLogs, boolean successful) {
    }

    private final GitService gitService;

    private final RepositoryService repositoryService;

    private final HyperionSolutionRepositoryService solutionStrategy;

    private final HyperionTemplateRepositoryService templateStrategy;

    private final HyperionTestRepositoryService testStrategy;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final ResultRepository resultRepository;

    private final ContinuousIntegrationTriggerService continuousIntegrationTriggerService;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final HyperionProgrammingExerciseContextRendererService repositoryStructureService;

    private final ProgrammingSubmissionService programmingSubmissionService;

    public HyperionCodeGenerationExecutionService(GitService gitService, RepositoryService repositoryService,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository, ProgrammingSubmissionRepository programmingSubmissionRepository,
            ResultRepository resultRepository, ContinuousIntegrationTriggerService continuousIntegrationTriggerService,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService, HyperionProgrammingExerciseContextRendererService repositoryStructureService,
            HyperionSolutionRepositoryService solutionStrategy, HyperionTemplateRepositoryService templateStrategy, HyperionTestRepositoryService testStrategy,
            ProgrammingSubmissionService programmingSubmissionService) {
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
    }

    /**
     * Gets the default branch name for the given repository.
     * Uses the configured default branch from GitService.
     *
     * @param repositoryUri the URI of the repository to check
     * @return the default branch name
     */
    private String getDefaultBranch(LocalVCRepositoryUri repositoryUri) {
        return gitService.getDefaultBranch();
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
        var repositoryUri = exercise.getRepositoryURI(repositoryType);
        if (repositoryUri == null) {
            log.error("Could not get {} repository URI for exercise {}", repositoryType, exercise.getId());
            return new RepositorySetupResult(null, null, false);
        }

        try {
            String defaultBranch = getDefaultBranch(repositoryUri);
            Repository repository = gitService.getOrCheckoutRepository(repositoryUri, true, defaultBranch, false);

            if (repository == null) {
                log.error("Failed to checkout repository for exercise {}", exercise.getId());
                return new RepositorySetupResult(null, null, false);
            }

            String originalCommitHash = gitService.getLastCommitHash(repositoryUri).getName();
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
     * Processes all generated files by updating them in the repository.
     *
     * @param repository     the repository
     * @param generatedFiles list of generated files to process
     * @param exercise       the programming exercise (for logging)
     * @throws IOException if file operations fail
     */
    private void processGeneratedFiles(Repository repository, List<GeneratedFileDTO> generatedFiles, ProgrammingExercise exercise) throws IOException {
        for (GeneratedFileDTO file : generatedFiles) {
            updateSingleFile(repository, file, exercise);
        }
    }

    /**
     * Commits changes, triggers CI build, and returns the new commit hash.
     *
     * @param repository    the repository
     * @param user          the user making the commit
     * @param repositoryUri the repository URI
     * @param exercise      the programming exercise (needed for triggering CI build)
     * @return the new commit hash
     * @throws GitAPIException if git operations fail
     */
    private String commitAndGetHash(Repository repository, User user, LocalVCRepositoryUri repositoryUri, ProgrammingExercise exercise, RepositoryType repositoryType)
            throws GitAPIException {
        repositoryService.commitChanges(repository, user);
        String newCommitHash = gitService.getLastCommitHash(repositoryUri).getName();
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
        }
        return newCommitHash;
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
     * @param repositoryType repository type to generate
     * @param publisher      event publisher for websocket updates
     * @return the latest build result or null
     */
    public Result generateAndCompileCode(ProgrammingExercise exercise, User user, RepositoryType repositoryType, HyperionCodeGenerationEventPublisher publisher) {
        RepositorySetupResult setupResult = setupRepository(exercise, repositoryType);
        if (!setupResult.success()) {
            publisher.error("Repository setup failed");
            return null;
        }
        log.info("Setup Repo success");

        var repositoryUri = exercise.getRepositoryURI(repositoryType);
        String lastBuildLogs = null;
        Result result = null;
        String lastCommitHash = null;
        int attemptsUsed = 0;

        try {
            HyperionCodeGenerationService strategy = resolveStrategy(repositoryType);
            for (int i = 0; i < MAX_ITERATIONS; i++) {
                attemptsUsed = i + 1;
                String repositoryStructure = repositoryStructureService.getRepositoryStructure(setupResult.repository());
                List<GeneratedFileDTO> generatedFiles = strategy.generateCode(user, exercise, lastBuildLogs, repositoryStructure);

                if (generatedFiles != null && !generatedFiles.isEmpty()) {

                    for (GeneratedFileDTO file : generatedFiles) {
                        boolean existed = gitService.getFileByName(setupResult.repository(), file.path()).isPresent();
                        updateSingleFile(setupResult.repository(), file, exercise);
                        if (existed) {
                            publisher.fileUpdated(file.path(), repositoryType);
                        }
                        else {
                            publisher.newFile(file.path(), repositoryType);
                        }
                    }

                    String newCommitHash = commitAndGetHash(setupResult.repository(), user, repositoryUri, exercise, repositoryType);
                    lastCommitHash = newCommitHash;
                    result = waitForBuildResult(exercise, newCommitHash, repositoryType);
                }

                publisher.progress(i + 1);

                if (result != null && result.isSuccessful()) {
                    break;
                }

                lastBuildLogs = extractBuildLogs(result);
            }

        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            publisher.error(e.getMessage());
        }
        catch (Exception e) {
            publisher.error(e.getMessage());
        }
        finally {
            cleanupRepository(setupResult.repository(), setupResult.originalCommitHash());
        }

        // Ensure the remote reflects the last pushed commit before signaling DONE
        try {
            if (lastCommitHash != null) {
                waitUntilRemoteHasCommit(repositoryUri, lastCommitHash, 3000);
            }
        }
        catch (InterruptedException ignored) {
        }

        boolean success = result != null && result.isSuccessful();
        int reportedAttempts = attemptsUsed == 0 ? MAX_ITERATIONS : attemptsUsed;
        publisher.done(success, reportedAttempts, success ? "Succeeded" : "Failed");

        return result;
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
                String head = gitService.getLastCommitHash(repositoryUri).getName();
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
    private Result waitForBuildResult(ProgrammingExercise exercise, String commitHash, RepositoryType repositoryType) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        var templateParticipation = templateProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(exercise.getId()).orElse(null);
        var solutionParticipation = solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(exercise.getId()).orElse(null);
        ProgrammingExerciseParticipation participation = switch (repositoryType) {
            case TEMPLATE -> templateParticipation;
            case SOLUTION -> solutionParticipation;
            case TESTS -> solutionParticipation; // tests also use solution participation
            default -> null;
        };
        if (participation == null) {
            log.warn("Could not find participation for repoType {} in exercise {}", repositoryType, exercise.getId());
            return null;
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
                        return result.get();
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
        return null; // Timeout
    }
}
