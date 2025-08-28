package de.tum.cit.aet.artemis.hyperion.service.codegeneration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedFile;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;

/**
 * Service responsible for orchestrating the iterative code generation and compilation process.
 * Manages the complete lifecycle of AI-powered code generation including Git operations,
 * build triggering, and feedback loop integration for iterative improvement.
 */
@Service
public class CodeGenerationExecutionService {

    private static final Logger log = LoggerFactory.getLogger(CodeGenerationExecutionService.class);

    private static final int MAX_ITERATIONS = 3;

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

    private final CodeGenerationStrategy codeGenerationStrategy;

    private final RepositoryService repositoryService;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final ResultRepository resultRepository;

    public CodeGenerationExecutionService(GitService gitService, @Qualifier("solutionRepositoryStrategy") CodeGenerationStrategy codeGenerationStrategy,
            RepositoryService repositoryService, SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            ProgrammingSubmissionRepository programmingSubmissionRepository, ResultRepository resultRepository) {
        this.gitService = gitService;
        this.codeGenerationStrategy = codeGenerationStrategy;
        this.repositoryService = repositoryService;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.resultRepository = resultRepository;
    }

    /**
     * Gets the default branch name for the given repository.
     * Uses the configured default branch from GitService.
     *
     * @param repositoryUri the URI of the repository to check
     * @return the default branch name
     */
    private String getDefaultBranch(VcsRepositoryUri repositoryUri) {
        return gitService.getDefaultBranch();
    }

    /**
     * Sets up repository for code generation by validating URI and checking out repository.
     *
     * @param exercise the programming exercise
     * @return repository setup result containing repository and commit hash
     */
    private RepositorySetupResult setupRepository(ProgrammingExercise exercise) {
        var repositoryUri = exercise.getVcsSolutionRepositoryUri();
        if (repositoryUri == null) {
            log.error("Could not get repository URI for exercise {}", exercise.getId());
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
    private void updateSingleFile(Repository repository, GeneratedFile file, ProgrammingExercise exercise) throws IOException {
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
    private void processGeneratedFiles(Repository repository, List<GeneratedFile> generatedFiles, ProgrammingExercise exercise) throws IOException {
        for (GeneratedFile file : generatedFiles) {
            updateSingleFile(repository, file, exercise);
        }
    }

    /**
     * Commits changes and returns the new commit hash.
     *
     * @param repository    the repository
     * @param user          the user making the commit
     * @param repositoryUri the repository URI
     * @return the new commit hash
     * @throws GitAPIException if git operations fail
     */
    private String commitAndGetHash(Repository repository, User user, VcsRepositoryUri repositoryUri) throws GitAPIException {
        repositoryService.commitChanges(repository, user);
        return gitService.getLastCommitHash(repositoryUri).getName();
    }

    /**
     * Extracts build logs from a result.
     *
     * @param result the build result
     * @return extracted build logs or default message
     */
    private String extractBuildLogs(Result result) {
        if (result != null && result.getSubmission() instanceof ProgrammingSubmission programmingSubmission) {
            return programmingSubmission.getBuildLogEntries().stream().map(BuildLogEntry::getLog).collect(Collectors.joining("\n"));
        }
        return "Build failed to produce a result.";
    }

    /**
     * Executes a single iteration of code generation, compilation, and build checking.
     *
     * @param exercise      the programming exercise
     * @param user          the user
     * @param repository    the repository
     * @param repositoryUri the repository URI
     * @param lastBuildLogs previous build logs for context
     * @param iteration     current iteration number (for logging)
     * @return iteration result with success status and build logs
     */
    private IterationResult executeGenerationIteration(ProgrammingExercise exercise, User user, Repository repository, VcsRepositoryUri repositoryUri, String lastBuildLogs,
            int iteration) {
        try {
            List<GeneratedFile> generatedFiles = codeGenerationStrategy.generateCode(user, exercise, lastBuildLogs);

            if (generatedFiles == null || generatedFiles.isEmpty()) {
                log.warn("No files generated for exercise {} on attempt {}. Skipping repository update.", exercise.getId(), iteration);
                return new IterationResult(null, lastBuildLogs, false);
            }

            log.debug("Generated {} files for exercise {} on attempt {}", generatedFiles.size(), exercise.getId(), iteration);

            processGeneratedFiles(repository, generatedFiles, exercise);
            String newCommitHash = commitAndGetHash(repository, user, repositoryUri);
            Result result = waitForBuildResult(exercise, newCommitHash);

            if (result != null && result.isSuccessful()) {
                log.info("Code generation and compilation successful for exercise {} on attempt {}", exercise.getId(), iteration);
                return new IterationResult(result, null, true);
            }

            String buildLogs = extractBuildLogs(result);
            log.info("Code generation and compilation failed for exercise {} on attempt {}. Retrying...", exercise.getId(), iteration);
            return new IterationResult(result, buildLogs, false);

        }
        catch (IOException | GitAPIException | InterruptedException | NetworkingException e) {
            log.error("Error during iteration {} for exercise {}: {}", iteration, exercise.getId(), e.getMessage(), e);
            return new IterationResult(null, lastBuildLogs, false);
        }
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
     * Generates and compiles code for a programming exercise using iterative LLM-powered approach.
     * Runs up to MAX_ITERATIONS attempts, using build failure feedback to improve code generation.
     * Manages Git repository operations, code generation, compilation, and cleanup.
     *
     * @param exercise the programming exercise to generate code for
     * @param user     the user initiating the code generation
     * @return the final build result, or null if all attempts failed
     */
    public Result generateAndCompileCode(ProgrammingExercise exercise, User user) {
        log.warn("Starting code generation and compilation for exercise {}\n\n\n", exercise.getId());

        RepositorySetupResult setupResult = setupRepository(exercise);
        if (!setupResult.success()) {
            return null;
        }

        var repositoryUri = exercise.getVcsSolutionRepositoryUri();
        try {
            String lastBuildLogs = null;
            Result result = null;

            for (int i = 0; i < MAX_ITERATIONS; i++) {
                IterationResult iterationResult = executeGenerationIteration(exercise, user, setupResult.repository(), repositoryUri, lastBuildLogs, i + 1);

                result = iterationResult.result();
                if (iterationResult.successful()) {
                    return result;
                }

                lastBuildLogs = iterationResult.buildLogs();
            }

            log.warn("Code generation and compilation failed for exercise {} after {} attempts.", exercise.getId(), MAX_ITERATIONS);
            return result;

        }
        finally {
            cleanupRepository(setupResult.repository(), setupResult.originalCommitHash());
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
    private Result waitForBuildResult(ProgrammingExercise exercise, String commitHash) throws InterruptedException {
        long timeout = 120_000; // 2 minutes
        long pollInterval = 5_000; // 5 seconds
        long startTime = System.currentTimeMillis();

        var solutionParticipation = solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(exercise.getId()).orElse(null);
        if (solutionParticipation == null) {
            log.warn("Could not find solution participation for exercise {}", exercise.getId());
            return null;
        }

        while (System.currentTimeMillis() - startTime < timeout) {
            ProgrammingSubmission submission = programmingSubmissionRepository
                    .findFirstByParticipationIdAndCommitHashOrderByIdDescWithFeedbacksAndTeamStudents(solutionParticipation.getId(), commitHash);
            if (submission != null) {
                Optional<Result> result = resultRepository.findLatestResultWithFeedbacksAndTestcasesForSubmission(submission.getId());
                if (result.isPresent()) {
                    return result.get();
                }
            }
            Thread.sleep(pollInterval);
        }
        log.warn("Timed out waiting for build result for commit {} in exercise {}", commitHash, exercise.getId());
        return null; // Timeout
    }
}
