package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jakarta.annotation.PostConstruct;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.BuildLogDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;
import de.tum.cit.aet.artemis.buildagent.dto.LocalCIJobDTO;
import de.tum.cit.aet.artemis.buildagent.dto.LocalCITestJobDTO;
import de.tum.cit.aet.artemis.buildagent.service.parser.CustomFeedbackParser;
import de.tum.cit.aet.artemis.buildagent.service.parser.TestResultXmlParser;
import de.tum.cit.aet.artemis.buildagent.service.runner.BuildJobRunner;
import de.tum.cit.aet.artemis.buildagent.service.runner.BuildJobRunnerResult;
import de.tum.cit.aet.artemis.buildagent.service.runner.PreparedBuildJob;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.localci.service.scaparser.ReportParser;
import de.tum.cit.aet.artemis.localci.service.scaparser.exception.UnsupportedToolException;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisTool;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisReportDTO;
import de.tum.cit.aet.artemis.programming.exception.GitException;

/**
 * This service contains the logic to execute a build job for a programming exercise participation in the local CI system.
 * submitted to the executor service.
 */
@Lazy(false)
@Service
@Profile(PROFILE_BUILDAGENT)
public class BuildJobExecutionService {

    private static final Logger log = LoggerFactory.getLogger(BuildJobExecutionService.class);

    private final BuildJobGitService buildJobGitService;

    private final BuildJobRunner buildJobRunner;

    private final BuildLogsMap buildLogsMap;

    private static final int MAX_CLONE_RETRIES = 3;

    @Value("${artemis.version-control.default-branch:main}")
    private String defaultBranch;

    @Value("${artemis.checked-out-repos-path}")
    private String checkedOutReposPath;

    /**
     * Upper bound for feedback text of a single test case / assertion before truncation.
     * <p>
     * In practice, feedback is short (typically &lt; 1k chars). Very large feedback texts
     * are almost always caused by runaway output (e.g. infinite loops, excessive logging,
     * repeated stack traces) and do not provide additional value to students.
     * <p>
     * This limit prevents excessive network traffic when transmitting build results
     * from the build agent to the main server, and protects the database from
     * accidental storage explosions.
     */
    @Value("${artemis.feedback.max-feedback-length:20000}")
    private int maxFeedbackLength;

    private static final Duration TEMP_DIR_RETENTION_PERIOD = Duration.ofMinutes(5);

    public BuildJobExecutionService(BuildJobGitService buildJobGitService, BuildJobRunner buildJobRunner, BuildLogsMap buildLogsMap) {
        this.buildJobGitService = buildJobGitService;
        this.buildJobRunner = buildJobRunner;
        this.buildLogsMap = buildLogsMap;
    }

    @PostConstruct
    void initParsers() {
        TestResultXmlParser.setMaxFeedbackLength(maxFeedbackLength);
        CustomFeedbackParser.setMaxFeedbackLength(maxFeedbackLength);
    }

    /**
     * This method is responsible for cleaning up temporary directories that were used for checking out repositories.
     * It is triggered when the application is ready and runs asynchronously.
     * EventListener cannot be used here, as the bean is lazy
     * <a href="https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events-annotation">Spring Docs</a>
     */
    @PostConstruct
    @Async
    public void initAsync() {
        final ZonedDateTime currentTime = ZonedDateTime.now();
        cleanUpTempDirectoriesAsync(currentTime);
    }

    private void cleanUpTempDirectoriesAsync(ZonedDateTime currentTime) {
        Path reposPath = Path.of(checkedOutReposPath);
        if (!Files.exists(reposPath)) {
            log.info("Checked-out repos directory {} does not exist (yet), skipping cleanup", checkedOutReposPath);
            return;
        }
        log.debug("Cleaning up temporary directories in {}", checkedOutReposPath);
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(reposPath)) {
            for (Path path : directoryStream) {
                try {
                    ZonedDateTime lastModifiedTime = ZonedDateTime.ofInstant(Files.getLastModifiedTime(path).toInstant(), currentTime.getZone());
                    if (Files.isDirectory(path) && lastModifiedTime.isBefore(currentTime.minus(TEMP_DIR_RETENTION_PERIOD))) {
                        FileUtils.deleteDirectory(path.toFile());
                    }
                }
                catch (IOException e) {
                    log.error("Could not delete temporary directory {}", path, e);
                }
            }
        }
        catch (IOException e) {
            log.error("Could not delete temporary directories", e);
        }
        log.debug("Clean up of temporary directories in {} completed.", checkedOutReposPath);
    }

    /**
     * Clones the required repositories, delegates isolated execution to the configured runner, and parses the runner-neutral result archive.
     * Queue ordering and time estimation happen before this method and therefore remain independent of the selected runner.
     *
     * @param buildJob the build job to execute
     * @return the parsed LocalCI result
     */
    public BuildResult runBuildJob(BuildJobQueueItem buildJob) {
        // Bind this job's clone token to the executing thread for the whole job. Every git operation below reaches
        // the credential through BuildJobGitService, including the ones inherited from AbstractGitService that carry
        // no build job context. The finally is essential: executor threads are reused, and a token left behind would
        // be presented for the next job, whose repositories it does not cover.
        buildJobGitService.setCloneTokenForCurrentThread(buildJob.cloneToken());
        try {
            return runBuildJobWithBoundCloneToken(buildJob);
        }
        finally {
            buildJobGitService.clearCloneTokenForCurrentThread();
        }
    }

    private BuildResult runBuildJobWithBoundCloneToken(BuildJobQueueItem buildJob) {
        long timeNanoStart = System.nanoTime();
        String startMessage = "~~~~~~~~~~~~~~~~~~~~ Start Build Job " + buildJob.id() + " using " + buildJobRunner.type().displayName() + " ~~~~~~~~~~~~~~~~~~~~";
        buildLogsMap.appendBuildLogEntry(buildJob.id(), startMessage);
        log.debug(startMessage);

        LocalVCRepositoryUri assignmentRepositoryUri = new LocalVCRepositoryUri(buildJob.repositoryInfo().assignmentRepositoryUri());
        LocalVCRepositoryUri testRepositoryUri = new LocalVCRepositoryUri(buildJob.repositoryInfo().testRepositoryUri());
        LocalVCRepositoryUri solutionRepositoryUri = null;
        LocalVCRepositoryUri[] auxiliaryRepositoryUris = new LocalVCRepositoryUri[buildJob.repositoryInfo().auxiliaryRepositoryUris().length];
        Path assignmentRepositoryPath = null;
        Path testRepositoryPath = null;
        Path solutionRepositoryPath = null;
        Path[] auxiliaryRepositoryPaths = new Path[auxiliaryRepositoryUris.length];
        String assignmentCommitHash = resolveCommitHash(buildJob, assignmentRepositoryUri, buildJob.buildConfig().assignmentCommitHash(), "assignment");
        String testCommitHash = resolveCommitHash(buildJob, testRepositoryUri, buildJob.buildConfig().testCommitHash(), "test");
        BuildResult buildResult;

        try {
            boolean useSpecificAssignmentCommit = buildJob.buildConfig().assignmentCommitHash() != null && buildJob.repositoryInfo().triggeredByPushTo() != RepositoryType.TESTS
                    && buildJob.repositoryInfo().triggeredByPushTo() != RepositoryType.AUXILIARY;
            assignmentRepositoryPath = cloneRepository(assignmentRepositoryUri, useSpecificAssignmentCommit ? assignmentCommitHash : null, useSpecificAssignmentCommit,
                    buildJob.id());
            testRepositoryPath = cloneRepository(testRepositoryUri, null, false, buildJob.id());

            if (buildJob.repositoryInfo().solutionRepositoryUri() != null) {
                solutionRepositoryUri = new LocalVCRepositoryUri(buildJob.repositoryInfo().solutionRepositoryUri());
                solutionRepositoryPath = Objects.equals(solutionRepositoryUri.repositorySlug(), assignmentRepositoryUri.repositorySlug()) ? assignmentRepositoryPath
                        : cloneRepository(solutionRepositoryUri, null, false, buildJob.id());
            }

            for (int i = 0; i < auxiliaryRepositoryUris.length; i++) {
                auxiliaryRepositoryUris[i] = new LocalVCRepositoryUri(buildJob.repositoryInfo().auxiliaryRepositoryUris()[i]);
                auxiliaryRepositoryPaths[i] = cloneRepository(auxiliaryRepositoryUris[i], null, false, buildJob.id());
            }

            PreparedBuildJob preparedBuildJob = new PreparedBuildJob(assignmentRepositoryPath, testRepositoryPath, solutionRepositoryPath, List.of(auxiliaryRepositoryPaths));
            BuildJobRunnerResult runnerResult = buildJobRunner.execute(buildJob, preparedBuildJob);
            try {
                if (runnerResult.resultArchive() == null) {
                    String message = "The build execution did not produce a result archive for build job " + buildJob.id();
                    buildLogsMap.appendBuildLogEntry(buildJob.id(), message);
                    log.warn(message);
                    buildResult = constructFailedBuildResult(buildJob.buildConfig().branch(), assignmentCommitHash, testCommitHash, runnerResult.completedAt(),
                            runnerResult.exitCode());
                }
                else {
                    TarArchiveInputStream resultArchive = runnerResult.resultArchive() instanceof TarArchiveInputStream tarArchive ? tarArchive
                            : new TarArchiveInputStream(runnerResult.resultArchive());
                    var buildLogs = buildLogsMap.getAndTruncateBuildLogs(buildJob.id());
                    buildResult = parseTestResults(resultArchive, buildJob.buildConfig().branch(), assignmentCommitHash, testCommitHash, runnerResult.completedAt(), buildJob.id(),
                            buildLogs, runnerResult.exitCode());
                }
            }
            catch (IOException | IllegalStateException e) {
                String message = "Error while parsing build results";
                buildLogsMap.appendBuildLogEntry(buildJob.id(), message);
                throw new LocalCIException(message, e);
            }
            finally {
                // Closing the runner result also runs the runner cleanup (for Docker: stopping the container). A cleanup failure must not discard an already parsed
                // build result, and it must not be reported as a result parsing error.
                try {
                    runnerResult.close();
                }
                catch (IOException | RuntimeException e) {
                    log.warn("Could not release the execution resources for build job {}", buildJob.id(), e);
                }
            }
        }
        finally {
            cleanupRepositories(buildJob.id(), assignmentRepositoryUri, testRepositoryUri, solutionRepositoryUri, auxiliaryRepositoryUris, assignmentRepositoryPath,
                    testRepositoryPath, solutionRepositoryPath, auxiliaryRepositoryPaths);
        }

        String finishMessage = "Building and testing submission for repository " + assignmentRepositoryUri.repositorySlug() + " and commit hash " + assignmentCommitHash + " took "
                + TimeLogUtil.formatDurationFrom(timeNanoStart) + " for build job " + buildJob.id();
        buildLogsMap.appendBuildLogEntry(buildJob.id(), finishMessage);
        log.info(finishMessage);
        return buildResult;
    }

    private String resolveCommitHash(BuildJobQueueItem buildJob, LocalVCRepositoryUri repositoryUri, @Nullable String configuredCommitHash, String repositoryKind) {
        if (configuredCommitHash != null) {
            return configuredCommitHash;
        }
        try {
            return buildJobGitService.getLastCommitHash(repositoryUri);
        }
        catch (EntityNotFoundException e) {
            String message = "Could not find last commit hash for " + repositoryKind + " repository " + repositoryUri.repositorySlug();
            buildLogsMap.appendBuildLogEntry(buildJob.id(), message);
            throw new LocalCIException(message, e);
        }
    }

    private void cleanupRepositories(String buildJobId, LocalVCRepositoryUri assignmentRepositoryUri, LocalVCRepositoryUri testRepositoryUri,
            @Nullable LocalVCRepositoryUri solutionRepositoryUri, LocalVCRepositoryUri[] auxiliaryRepositoryUris, @Nullable Path assignmentRepositoryPath,
            @Nullable Path testRepositoryPath, @Nullable Path solutionRepositoryPath, Path[] auxiliaryRepositoryPaths) {
        if (assignmentRepositoryPath != null) {
            deleteCloneRepo(assignmentRepositoryUri, buildJobId, assignmentRepositoryPath);
        }
        if (testRepositoryPath != null) {
            deleteCloneRepo(testRepositoryUri, buildJobId, testRepositoryPath);
        }
        if (solutionRepositoryUri != null && solutionRepositoryPath != null && !Objects.equals(assignmentRepositoryUri.repositorySlug(), solutionRepositoryUri.repositorySlug())) {
            deleteCloneRepo(solutionRepositoryUri, buildJobId, solutionRepositoryPath);
        }
        for (int i = 0; i < auxiliaryRepositoryUris.length; i++) {
            if (auxiliaryRepositoryUris[i] != null && auxiliaryRepositoryPaths[i] != null) {
                deleteCloneRepo(auxiliaryRepositoryUris[i], buildJobId, auxiliaryRepositoryPaths[i]);
            }
        }
        try {
            deleteBuildJobRepositoryFolder(buildJobId);
        }
        catch (IOException e) {
            String message = "Could not delete " + checkedOutReposPath + "/" + buildJobId + " directory";
            buildLogsMap.appendBuildLogEntry(buildJobId, message);
            log.error(message, e);
        }
    }

    // --- Helper methods ----

    private BuildResult parseTestResults(TarArchiveInputStream testResultsTarInputStream, String assignmentRepoBranchName, String assignmentRepoCommitHash,
            String testsRepoCommitHash, ZonedDateTime buildCompletedDate, String buildJobId, List<BuildLogDTO> buildLogs, int buildScriptExitCode) throws IOException {

        List<LocalCITestJobDTO> failedTests = new ArrayList<>();
        List<LocalCITestJobDTO> successfulTests = new ArrayList<>();
        List<StaticCodeAnalysisReportDTO> staticCodeAnalysisReports = new ArrayList<>();

        TarArchiveEntry tarEntry;
        while ((tarEntry = testResultsTarInputStream.getNextEntry()) != null) {
            // Go through all tar entries that are test result files.
            if (!isValidTestResultFile(tarEntry)) {
                continue;
            }

            // Read the contents of the tar entry as a string.
            String fileContent = readTarEntryContent(testResultsTarInputStream);
            // Get the file name of the tar entry.
            String fileName = getFileName(tarEntry);

            try {
                // Check if the file is a static code analysis report file
                if (StaticCodeAnalysisTool.getToolByFilePattern(fileName).isPresent()) {
                    processStaticCodeAnalysisReportFile(fileName, fileContent, staticCodeAnalysisReports, buildJobId);
                }
                else {
                    // ugly workaround because in swift result files \n\t breaks the parsing
                    var testResultFileString = fileContent.replace("\n\t", "");
                    if (!testResultFileString.isBlank()) {
                        if (fileName.endsWith(".xml")) {
                            log.debug("Parsing test result XML file '{}' for build job {} ({} bytes)", fileName, buildJobId, testResultFileString.length());
                            TestResultXmlParser.processTestResultFile(testResultFileString, failedTests, successfulTests);
                            log.debug("After parsing '{}' for build job {}: {} failed tests, {} successful tests", fileName, buildJobId, failedTests.size(),
                                    successfulTests.size());
                        }
                        else if (fileName.endsWith(".json")) {
                            CustomFeedbackParser.processTestResultFile(fileName, testResultFileString, failedTests, successfulTests);
                        }
                    }
                    else {
                        String msg = "The file " + fileName + " does not contain any testcases.";
                        buildLogsMap.appendBuildLogEntry(buildJobId, msg);
                        log.warn(msg);
                    }
                }
            }
            catch (Exception e) {
                // Exceptions due to one invalid file should not lead to the whole build to fail.
                String msg = "Error while parsing report file " + fileName + ", ignoring.";
                buildLogsMap.appendBuildLogEntry(buildJobId, msg);
                log.warn(msg, e);
            }
        }

        return constructBuildResult(failedTests, successfulTests, assignmentRepoBranchName, assignmentRepoCommitHash, testsRepoCommitHash, !failedTests.isEmpty(),
                buildCompletedDate, staticCodeAnalysisReports, buildLogs, buildScriptExitCode);
    }

    private boolean isValidTestResultFile(TarArchiveEntry tarArchiveEntry) {
        String name = tarArchiveEntry.getName();
        int lastIndexOfSlash = name.lastIndexOf('/');
        String result = (lastIndexOfSlash != -1 && lastIndexOfSlash + 1 < name.length()) ? name.substring(lastIndexOfSlash + 1) : name;

        // Java test result files are named "TEST-*.xml", Python test result files are named "*results.xml".
        return !tarArchiveEntry.isDirectory() && (result.endsWith(".xml") && !result.equals("pom.xml") || result.endsWith(".json") || result.endsWith(".sarif"));
    }

    /**
     * Get the file name of the tar entry.
     *
     * @param tarEntry the tar entry
     * @return the file name of the tar entry
     */
    private String getFileName(TarArchiveEntry tarEntry) {
        String filePath = tarEntry.getName();
        // Find the index of the last '/'
        int lastIndex = filePath.lastIndexOf('/');
        // If '/' is found, extract the substring after it; otherwise, keep the original string
        if (lastIndex != -1) {
            return filePath.substring(lastIndex + 1);
        }
        else {
            return filePath;
        }
    }

    /**
     * Processes a static code analysis report file and adds the report to the corresponding list.
     *
     * @param fileName                  the file name of the static code analysis report file
     * @param reportContent             the content of the static code analysis report file
     * @param staticCodeAnalysisReports the list of static code analysis reports
     */
    private void processStaticCodeAnalysisReportFile(String fileName, String reportContent, List<StaticCodeAnalysisReportDTO> staticCodeAnalysisReports, String buildJobId) {
        try {
            staticCodeAnalysisReports.add(ReportParser.getReport(reportContent, fileName));
        }
        catch (UnsupportedToolException e) {
            String msg = "Failed to parse static code analysis report for " + fileName;
            buildLogsMap.appendBuildLogEntry(buildJobId, msg);
            throw new IllegalStateException("Failed to parse static code analysis report for " + fileName, e);
        }
    }

    private String readTarEntryContent(TarArchiveInputStream tarArchiveInputStream) throws IOException {
        return new String(tarArchiveInputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    /**
     * Constructs a {@link BuildResult} that indicates a failed build from the given parameters. The lists of failed and successful tests are both empty which will be
     * interpreted as a failed build by Artemis.
     *
     * @param assignmentRepoBranchName The name of the branch of the assignment repository that was checked out for the build.
     * @param assignmentRepoCommitHash The commit hash of the assignment repository that was checked out for the build.
     * @param testsRepoCommitHash      The commit hash of the tests repository that was checked out for the build.
     * @param buildRunDate             The date when the build was completed.
     * @param buildScriptExitCode      The exit code of the build script (0 = success, non-zero = failure).
     * @return a {@link BuildResult} that indicates a failed build
     */
    private BuildResult constructFailedBuildResult(String assignmentRepoBranchName, @Nullable String assignmentRepoCommitHash, @Nullable String testsRepoCommitHash,
            ZonedDateTime buildRunDate, int buildScriptExitCode) {
        return constructBuildResult(List.of(), List.of(), assignmentRepoBranchName, assignmentRepoCommitHash, testsRepoCommitHash, false, buildRunDate, List.of(), null,
                buildScriptExitCode);
    }

    /**
     * Constructs a {@link BuildResult} from the given parameters.
     *
     * @param failedTests               The list of failed tests.
     * @param successfulTests           The list of successful tests.
     * @param assignmentRepoBranchName  The name of the branch of the assignment repository that was checked out for the build.
     * @param assignmentRepoCommitHash  The commit hash of the assignment repository that was checked out for the build.
     * @param testsRepoCommitHash       The commit hash of the tests repository that was checked out for the build.
     * @param isBuildSuccessful         Whether the build was successful or not.
     * @param buildRunDate              The date when the build was completed.
     * @param staticCodeAnalysisReports The static code analysis reports
     * @param buildLogs                 the build logs
     * @param buildScriptExitCode       The exit code of the build script (0 = success, non-zero = failure).
     * @return a {@link BuildResult}
     */
    private BuildResult constructBuildResult(List<LocalCITestJobDTO> failedTests, List<LocalCITestJobDTO> successfulTests, String assignmentRepoBranchName,
            String assignmentRepoCommitHash, String testsRepoCommitHash, boolean isBuildSuccessful, ZonedDateTime buildRunDate,
            List<StaticCodeAnalysisReportDTO> staticCodeAnalysisReports, List<BuildLogDTO> buildLogs, int buildScriptExitCode) {
        LocalCIJobDTO job = new LocalCIJobDTO(failedTests, successfulTests);
        return new BuildResult(assignmentRepoBranchName, assignmentRepoCommitHash, testsRepoCommitHash, isBuildSuccessful, buildRunDate, List.of(job), buildLogs,
                staticCodeAnalysisReports, true, buildScriptExitCode);
    }

    /**
     * Clones a repository into a build-job-specific directory and optionally checks out a specific commit.
     * <p>
     * <b>Directory Isolation Strategy:</b>
     * Each build job gets its own isolated directory to prevent race conditions between concurrent builds.
     * The directory structure is: {@code {checkedOutReposPath}/{buildJobId}/{repositoryFolder}}
     * <p>
     * <b>IMPORTANT:</b> The {@code buildJobId} is used for directory isolation, NOT the commit hash.
     * This ensures that concurrent build jobs (even those processing the same commit) cannot interfere
     * with each other's files during the tar archive creation or cleanup phases.
     * <p>
     * <b>Commit Hash Usage:</b>
     * The {@code commitHash} parameter is ONLY used when {@code checkout=true} to checkout a specific
     * commit after cloning. For repositories that should use the default branch (test, solution, auxiliary),
     * pass {@code null} for commitHash and {@code false} for checkout.
     *
     * @param repositoryUri the URI of the repository to clone
     * @param commitHash    the commit hash to checkout after cloning (only used if checkout=true);
     *                          pass null if the default branch should be used
     * @param checkout      if true, checkout the specified commitHash after cloning;
     *                          if false, use the default branch (commitHash is ignored)
     * @param buildJobId    the unique identifier of the build job, used to create an isolated directory
     * @return the local path where the repository was cloned
     * @throws LocalCIException if cloning fails after all retry attempts or if checkout fails
     */
    private Path cloneRepository(LocalVCRepositoryUri repositoryUri, @Nullable String commitHash, boolean checkout, String buildJobId) {
        Repository repository = null;

        for (int attempt = 1; attempt <= MAX_CLONE_RETRIES; attempt++) {
            try {
                // Clone into a build-job-specific directory: {checkedOutReposPath}/{buildJobId}/{repoFolder}
                // Using buildJobId (not commitHash) ensures complete isolation between concurrent build jobs
                repository = buildJobGitService.cloneRepository(repositoryUri, Path.of(checkedOutReposPath, buildJobId, repositoryUri.folderNameForRepositoryUri()));
                break;
            }
            catch (GitAPIException | IOException | URISyntaxException e) {
                if (attempt >= MAX_CLONE_RETRIES) {
                    String msg = "Error while cloning repository " + repositoryUri.repositorySlug() + " with uri " + repositoryUri + " after " + MAX_CLONE_RETRIES + " attempts";
                    buildLogsMap.appendBuildLogEntry(buildJobId, msg);
                    throw new LocalCIException(msg, e);
                }
                buildLogsMap.appendBuildLogEntry(buildJobId,
                        "Attempt " + attempt + " to clone repository " + repositoryUri.repositorySlug() + " failed due to " + e.getMessage() + ". Retrying...");
            }
        }

        try {
            // Only checkout a specific commit if explicitly requested (checkout=true) and a commit hash is provided.
            // For test/solution/auxiliary repos, checkout=false so they use the default branch.
            if (checkout && commitHash != null) {
                buildJobGitService.checkoutRepositoryAtCommit(repository, commitHash);
            }

            // Close the repository to release file handles; prevents IO errors during later deletion
            // (e.g., "java.io.IOException: Unable to delete file: ...\.git\objects\pack\...")
            repository.closeBeforeDelete();
            return repository.getLocalPath();
        }
        catch (GitException e) {
            String msg = "Error while checking out commit " + commitHash + " in repository " + repositoryUri.repositorySlug();
            buildLogsMap.appendBuildLogEntry(buildJobId, msg);
            throw new LocalCIException(msg, e);
        }
    }

    /**
     * Deletes a single cloned repository for a specific build job.
     * <p>
     * This method properly closes the Git repository before deletion to release file handles,
     * which is necessary to avoid IO errors on some platforms (especially Windows).
     * <p>
     * The repository path is constructed using the buildJobId to ensure we only delete
     * repositories belonging to this specific build job, not repositories from other concurrent builds.
     * <p>
     * <b>Note:</b> Deletion failures are logged but do not throw exceptions. Any remaining files
     * will be cleaned up during the next server startup by {@link #cleanUpTempDirectoriesAsync}.
     *
     * @param repositoryUri  the URI of the repository to delete
     * @param buildJobId     the unique identifier of the build job that owns this repository
     * @param repositoryPath the original path (used only for error logging)
     */
    private void deleteCloneRepo(LocalVCRepositoryUri repositoryUri, String buildJobId, Path repositoryPath) {
        String msg;
        try {
            // Construct path using buildJobId to ensure we only affect this build job's files
            Path repositoryPathForDeletion = Path.of(checkedOutReposPath, buildJobId, repositoryUri.folderNameForRepositoryUri());
            Repository repository = buildJobGitService.getExistingCheckedOutRepositoryByLocalPath(repositoryPathForDeletion, repositoryUri, defaultBranch);
            if (repository == null) {
                msg = "Repository for build job " + buildJobId + " not found";
                buildLogsMap.appendBuildLogEntry(buildJobId, msg);
                throw new EntityNotFoundException(msg);
            }
            buildJobGitService.deleteLocalRepository(repository);
        }
        // Deletion failures are non-fatal; cleanup will happen on next server start via cleanUpTempDirectoriesAsync()
        catch (EntityNotFoundException e) {
            msg = "Error while checking out repository";
            buildLogsMap.appendBuildLogEntry(buildJobId, msg);
            log.error("Error while deleting repository with URI {} and Path {}", repositoryUri, repositoryPath, e);
        }
        catch (IOException e) {
            msg = "Error while deleting repository";
            buildLogsMap.appendBuildLogEntry(buildJobId, msg);
            log.error("Error while deleting repository with URI {} and Path {}", repositoryUri, repositoryPath, e);
        }
    }

    /**
     * Deletes the entire directory containing all repositories for a specific build job.
     * <p>
     * <b>Directory Structure:</b>
     * All repositories for a build job are stored under: {@code {checkedOutReposPath}/{buildJobId}/}
     * This method deletes that entire directory tree in one operation.
     * <p>
     * <b>Why buildJobId instead of commitHash?</b>
     * Using the unique buildJobId as the parent folder ensures complete isolation between concurrent
     * build jobs. Previously, using commitHash caused race conditions: if two build jobs processed
     * the same commit, one job's cleanup could delete the shared directory while the other job
     * was still reading files (causing NoSuchFileException during tar archive creation).
     * <p>
     * This method is called in the finally block of {@link #runBuildJob} to ensure
     * cleanup happens even if the build fails.
     *
     * @param buildJobId the unique identifier of the build job whose repository folder should be deleted
     * @throws IOException if the directory cannot be deleted
     * @see #cloneRepository for the directory creation logic
     */
    private void deleteBuildJobRepositoryFolder(String buildJobId) throws IOException {
        Path buildJobRepoFolder = Path.of(checkedOutReposPath, buildJobId);
        FileUtils.deleteDirectory(buildJobRepoFolder.toFile());
    }
}
