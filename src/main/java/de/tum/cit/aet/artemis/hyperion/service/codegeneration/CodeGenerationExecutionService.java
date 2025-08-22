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

    private final GitService gitService;

    private final CodeGenerationStrategy codeGenerationStrategy;

    private final RepositoryService repositoryService;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final ResultRepository resultRepository;

    public CodeGenerationExecutionService(GitService gitService, CodeGenerationStrategy codeGenerationStrategy, RepositoryService repositoryService,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository, ProgrammingSubmissionRepository programmingSubmissionRepository,
            ResultRepository resultRepository) {
        this.gitService = gitService;
        this.codeGenerationStrategy = codeGenerationStrategy;
        this.repositoryService = repositoryService;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.resultRepository = resultRepository;
    }

    /**
     * Gets the default branch name for the given repository.
     *
     * @param repositoryUri the URI of the repository to check
     * @return the default branch name
     * @throws GitAPIException if Git operations fail
     * @throws IOException     if I/O operations fail
     */
    private String getDefaultBranch(VcsRepositoryUri repositoryUri) throws GitAPIException, IOException {
        Repository tempRepo = null;
        try {
            tempRepo = gitService.getOrCheckoutRepository(repositoryUri, true, null, true);
            return tempRepo.getBranch();
        }
        finally {
            if (tempRepo != null) {
                gitService.deleteLocalRepository(tempRepo);
            }
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
        var repositoryUri = exercise.getVcsSolutionRepositoryUri();
        if (repositoryUri == null) {
            log.error("Could not get repository URI for exercise {}", exercise.getId());
            return null;
        }

        Repository repository = null;
        String originalCommitHash = null;
        String lastBuildLogs = null;
        Result result = null;

        try {
            String defaultBranch = getDefaultBranch(repositoryUri);
            repository = gitService.getOrCheckoutRepository(repositoryUri, true, defaultBranch, false);
            originalCommitHash = gitService.getLastCommitHash(repositoryUri).getName();

            for (int i = 0; i < MAX_ITERATIONS; i++) {
                List<GeneratedFile> generatedFiles = codeGenerationStrategy.generateCode(user, exercise, lastBuildLogs);

                repositoryService.deleteAllContentInRepository(repository);
                for (GeneratedFile file : generatedFiles) {
                    InputStream inputStream = new ByteArrayInputStream(file.content().getBytes(StandardCharsets.UTF_8));
                    repositoryService.createFile(repository, file.path(), inputStream);
                }

                repositoryService.commitChanges(repository, user);
                String newCommitHash = gitService.getLastCommitHash(repositoryUri).getName();

                result = waitForBuildResult(exercise, newCommitHash);

                if (result != null && result.isSuccessful()) {
                    log.info("Code generation and compilation successful for exercise {} on attempt {}", exercise.getId(), i + 1);
                    return result;
                }

                if (result != null && result.getSubmission() instanceof ProgrammingSubmission programmingSubmission) {
                    lastBuildLogs = programmingSubmission.getBuildLogEntries().stream().map(BuildLogEntry::getLog).collect(Collectors.joining("\n"));
                }
                else {
                    lastBuildLogs = "Build failed to produce a result.";
                }
                log.info("Code generation and compilation failed for exercise {} on attempt {}. Retrying...", exercise.getId(), i + 1);
            }
        }
        catch (GitAPIException | IOException | InterruptedException | NetworkingException e) {
            log.error("Error during code generation and compilation loop for exercise {}", exercise.getId(), e);
        }
        finally {
            if (repository != null && originalCommitHash != null) {
                gitService.resetToOriginHead(repository);
            }
        }

        log.warn("Code generation and compilation failed for exercise {} after {} attempts.", exercise.getId(), MAX_ITERATIONS);
        return result;
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
