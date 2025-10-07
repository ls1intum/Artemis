package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY;
import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL_CI_RESULTS_DIRECTORY;
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
import java.util.UUID;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;

import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.BuildLogDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;
import de.tum.cit.aet.artemis.buildagent.dto.LocalCIJobDTO;
import de.tum.cit.aet.artemis.buildagent.dto.LocalCITestJobDTO;
import de.tum.cit.aet.artemis.buildagent.service.parser.CustomFeedbackParser;
import de.tum.cit.aet.artemis.buildagent.service.parser.TestResultXmlParser;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.exception.GitException;
import de.tum.cit.aet.artemis.core.exception.LocalCIException;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisTool;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisReportDTO;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.ReportParser;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.exception.UnsupportedToolException;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;

/**
 * This service contains the logic to execute a build job for a programming exercise participation in the local CI system.
 * submitted to the executor service.
 */
@Lazy(false)
@Service
@Profile(PROFILE_BUILDAGENT)
public class BuildJobExecutionService {

    private static final Logger log = LoggerFactory.getLogger(BuildJobExecutionService.class);

    private final BuildJobContainerService buildJobContainerService;

    private final BuildJobGitService buildJobGitService;

    private final BuildAgentDockerService buildAgentDockerService;

    private final BuildLogsMap buildLogsMap;

    private static final int MAX_CLONE_RETRIES = 3;

    @Value("${artemis.version-control.default-branch:main}")
    private String defaultBranch;

    @Value("${artemis.checked-out-repos-path}")
    private String checkedOutReposPath;

    private static final Duration TEMP_DIR_RETENTION_PERIOD = Duration.ofMinutes(5);

    public BuildJobExecutionService(BuildJobContainerService buildJobContainerService, BuildJobGitService buildJobGitService, BuildAgentDockerService buildAgentDockerService,
            BuildLogsMap buildLogsMap) {
        this.buildJobContainerService = buildJobContainerService;
        this.buildJobGitService = buildJobGitService;
        this.buildAgentDockerService = buildAgentDockerService;
        this.buildLogsMap = buildLogsMap;
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
        log.debug("Cleaning up temporary directories in {}", checkedOutReposPath);
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(Path.of(checkedOutReposPath))) {
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
     * Orchestrates the execution of a build job in a Docker container. This method handles the preparation and configuration of the container,
     * including cloning the necessary repositories, checking out the appropriate branches, and preparing the environment for the build.
     * The method concludes by executing the build script within the Docker environment and parsing the results.
     * <p>
     * Key Steps:
     * 1. Pulls the required Docker image if not already available.
     * 2. Retrieves commit hashes for assignment and test repositories.
     * 3. Clones the repositories for assignment, tests, solution (if applicable), and any auxiliary repositories into the container.
     * 4. Configures the Docker container with the necessary environment and volume settings.
     * 5. Delegates to the 'runScriptAndParseResults' method to execute the build script and process the results.
     * <p>
     * If any step fails, an exception is thrown and the container cleanup is initiated.
     *
     * @param buildJob      The build job object containing details necessary for executing the build.
     * @param containerName The name of the Docker container that will be prepared and used for the build job.
     * @return The result of the build job as a {@link BuildResult}.
     * @throws LocalCIException If any error occurs during the preparation or execution of the build job.
     */
    public BuildResult runBuildJob(BuildJobQueueItem buildJob, String containerName) {

        String msg = "~~~~~~~~~~~~~~~~~~~~ Start Build Job " + buildJob.id() + " ~~~~~~~~~~~~~~~~~~~~";
        log.debug(msg);
        buildLogsMap.appendBuildLogEntry(buildJob.id(), msg);

        // Check if the Docker image is available. If not, pull it.
        try {
            buildAgentDockerService.pullDockerImage(buildJob, buildLogsMap);
        }
        catch (LocalCIException e) {
            msg = "Could not pull Docker image " + buildJob.buildConfig().dockerImage();
            buildLogsMap.appendBuildLogEntry(buildJob.id(), msg);
            throw new LocalCIException(msg, e);
        }

        boolean isPushToTestOrAuxRepository = buildJob.repositoryInfo().triggeredByPushTo() == RepositoryType.TESTS
                || buildJob.repositoryInfo().triggeredByPushTo() == RepositoryType.AUXILIARY;

        // get the local repository paths for assignment, tests, auxiliary and solution
        LocalVCRepositoryUri assignmentRepoUri = new LocalVCRepositoryUri(buildJob.repositoryInfo().assignmentRepositoryUri());
        LocalVCRepositoryUri testsRepoUri = new LocalVCRepositoryUri(buildJob.repositoryInfo().testRepositoryUri());

        // retrieve last commit hash from repositories
        String assignmentCommitHash = buildJob.buildConfig().assignmentCommitHash();
        if (assignmentCommitHash == null) {
            try {
                var commitObjectId = buildJobGitService.getLastCommitHash(assignmentRepoUri);
                if (commitObjectId != null) {
                    assignmentCommitHash = commitObjectId.getName();
                }
            }
            catch (EntityNotFoundException e) {
                msg = "Could not find last commit hash for assignment repository " + assignmentRepoUri.repositorySlug();
                buildLogsMap.appendBuildLogEntry(buildJob.id(), msg);
                throw new LocalCIException(msg, e);
            }
        }
        String testCommitHash = buildJob.buildConfig().testCommitHash();
        if (testCommitHash == null) {
            try {
                var commitObjectId = buildJobGitService.getLastCommitHash(testsRepoUri);
                if (commitObjectId != null) {
                    testCommitHash = commitObjectId.getName();
                }
            }
            catch (EntityNotFoundException e) {
                msg = "Could not find last commit hash for test repository " + testsRepoUri.repositorySlug();
                buildLogsMap.appendBuildLogEntry(buildJob.id(), msg);
                throw new LocalCIException(msg, e);
            }
        }

        Path assignmentRepositoryPath;
        /*
         * If the commit hash is null, this means that the latest commit of the default branch should be built.
         * If this build job is triggered by a push to the test repository, the commit hash reflects changes to the test repository.
         * Thus, we do not checkout the commit hash of the test repository in the assignment repository.
         */
        if (buildJob.buildConfig().assignmentCommitHash() != null && !isPushToTestOrAuxRepository) {
            // Clone the assignment repository into a temporary directory with the name of the commit hash and then checkout the commit hash.
            assignmentRepositoryPath = cloneRepository(assignmentRepoUri, assignmentCommitHash, true, buildJob.id());
        }
        else {
            // Clone the assignment to use the latest commit of the default branch
            assignmentRepositoryPath = cloneRepository(assignmentRepoUri, assignmentCommitHash, false, buildJob.id());
        }

        Path testsRepositoryPath = cloneRepository(testsRepoUri, assignmentCommitHash, false, buildJob.id());

        LocalVCRepositoryUri solutionRepoUri = null;
        Path solutionRepositoryPath = null;
        if (buildJob.repositoryInfo().solutionRepositoryUri() != null) {
            solutionRepoUri = new LocalVCRepositoryUri(buildJob.repositoryInfo().solutionRepositoryUri());
            // In case we have the same repository for assignment and solution, we can use the same path
            if (Objects.equals(solutionRepoUri.repositorySlug(), assignmentRepoUri.repositorySlug())) {
                solutionRepositoryPath = assignmentRepositoryPath;
            }
            else {
                solutionRepositoryPath = cloneRepository(solutionRepoUri, assignmentCommitHash, false, buildJob.id());
            }
        }

        String[] auxiliaryRepositoryUriList = buildJob.repositoryInfo().auxiliaryRepositoryUris();
        Path[] auxiliaryRepositoriesPaths = new Path[auxiliaryRepositoryUriList.length];
        LocalVCRepositoryUri[] auxiliaryRepositoriesUris = new LocalVCRepositoryUri[auxiliaryRepositoryUriList.length];

        int index = 0;
        for (String auxiliaryRepositoryUri : auxiliaryRepositoryUriList) {
            auxiliaryRepositoriesUris[index] = new LocalVCRepositoryUri(auxiliaryRepositoryUri);
            auxiliaryRepositoriesPaths[index] = cloneRepository(auxiliaryRepositoriesUris[index], assignmentCommitHash, false, buildJob.id());
            index++;
        }

        List<String> envVars = null;
        boolean isNetworkDisabled = false;
        int cpuCount = 0;
        int memory = 0;
        int memorySwap = 0;
        if (buildJob.buildConfig().dockerRunConfig() != null) {
            envVars = buildJob.buildConfig().dockerRunConfig().env();
            isNetworkDisabled = buildJob.buildConfig().dockerRunConfig().isNetworkDisabled();
            cpuCount = buildJob.buildConfig().dockerRunConfig().cpuCount();
            memory = buildJob.buildConfig().dockerRunConfig().memory();
            memorySwap = buildJob.buildConfig().dockerRunConfig().memorySwap();
        }

        CreateContainerResponse container = buildJobContainerService.configureContainer(containerName, buildJob.buildConfig().dockerImage(), buildJob.buildConfig().buildScript(),
                envVars, cpuCount, memory, memorySwap);

        return runScriptAndParseResults(buildJob, containerName, container.getId(), assignmentRepoUri, testsRepoUri, solutionRepoUri, auxiliaryRepositoriesUris,
                assignmentRepositoryPath, testsRepositoryPath, solutionRepositoryPath, auxiliaryRepositoriesPaths, assignmentCommitHash, testCommitHash, isNetworkDisabled);
    }

    /**
     * Executes a build job within a Docker container by running a designated build script and processing the results.
     * The method manages the entire lifecycle of the container used for the build, from starting it, populating it with necessary repositories,
     * running the build script, to finally stopping the container and cleaning up resources.
     * <p>
     * The method handles:
     * - Container preparation and initialization.
     * - Repository setup within the container for assignment, tests, solutions, and auxiliary content.
     * - Execution of the build script and capturing its results.
     * - Retrieval and parsing of the build results stored in a specified format (e.g., tar files).
     * - Handling exceptions that occur during the build process, including not finding expected results, and managing filesystem cleanups.
     *
     * @param buildJob                   The build job queue item containing details needed for the build process.
     * @param containerName              The name of the Docker container, used for logging and management purposes.
     * @param containerId                The identifier of the Docker container used for the build job.
     * @param assignmentRepositoryUri    URI for the assignment repository.
     * @param testRepositoryUri          URI for the test repository.
     * @param solutionRepositoryUri      Optional URI for the solution repository.
     * @param auxiliaryRepositoriesUris  Array of URIs for any auxiliary repositories needed for the build.
     * @param assignmentRepositoryPath   Local file system path to the assignment repository.
     * @param testsRepositoryPath        Local file system path to the test repository.
     * @param solutionRepositoryPath     Optional local file system path to the solution repository.
     * @param auxiliaryRepositoriesPaths Array of paths for the auxiliary repositories.
     * @param assignmentRepoCommitHash   Commit hash for the assignment repository used to fetch the specific state of the repository.
     * @param testRepoCommitHash         Commit hash for the test repository used similarly.
     * @return A {@link BuildResult} object representing the outcome of the build job.
     * @throws LocalCIException If errors occur during the build process or if the test results cannot be parsed successfully.
     */
    // TODO: This method has too many params, we should reduce the number an rather pass an object (record)
    private BuildResult runScriptAndParseResults(BuildJobQueueItem buildJob, String containerName, String containerId, LocalVCRepositoryUri assignmentRepositoryUri,
            LocalVCRepositoryUri testRepositoryUri, @Nullable LocalVCRepositoryUri solutionRepositoryUri, LocalVCRepositoryUri[] auxiliaryRepositoriesUris,
            Path assignmentRepositoryPath, Path testsRepositoryPath, Path solutionRepositoryPath, Path[] auxiliaryRepositoriesPaths, @Nullable String assignmentRepoCommitHash,
            @Nullable String testRepoCommitHash, boolean isNetworkDisabled) {

        long timeNanoStart = System.nanoTime();

        buildJobContainerService.startContainer(containerId);

        String msg = "~~~~~~~~~~~~~~~~~~~~ Started container " + containerName + " for build job " + buildJob.id() + " ~~~~~~~~~~~~~~~~~~~~";
        buildLogsMap.appendBuildLogEntry(buildJob.id(), msg);

        log.info(msg, containerName);

        msg = "~~~~~~~~~~~~~~~~~~~~ Populating build job container with repositories and build script ~~~~~~~~~~~~~~~~~~~~";
        buildLogsMap.appendBuildLogEntry(buildJob.id(), msg);
        log.debug(msg);
        buildJobContainerService.populateBuildJobContainer(containerId, assignmentRepositoryPath, testsRepositoryPath, solutionRepositoryPath, auxiliaryRepositoriesPaths,
                buildJob.repositoryInfo().auxiliaryRepositoryCheckoutDirectories(), buildJob.buildConfig().programmingLanguage(), buildJob.buildConfig().assignmentCheckoutPath(),
                buildJob.buildConfig().testCheckoutPath(), buildJob.buildConfig().solutionCheckoutPath());

        msg = "~~~~~~~~~~~~~~~~~~~~ Executing Build Script for Build job " + buildJob.id() + " ~~~~~~~~~~~~~~~~~~~~";
        buildLogsMap.appendBuildLogEntry(buildJob.id(), msg);
        log.debug(msg);

        buildJobContainerService.runScriptInContainer(containerId, buildJob.id(), isNetworkDisabled);

        msg = "~~~~~~~~~~~~~~~~~~~~ Finished Executing Build Script for Build job " + buildJob.id() + " ~~~~~~~~~~~~~~~~~~~~";
        buildLogsMap.appendBuildLogEntry(buildJob.id(), msg);
        log.info(msg);

        ZonedDateTime buildCompletedDate = ZonedDateTime.now();

        msg = "~~~~~~~~~~~~~~~~~~~~ Moving test results to specified directory for build job " + buildJob.id() + " ~~~~~~~~~~~~~~~~~~~~";
        buildLogsMap.appendBuildLogEntry(buildJob.id(), msg);
        log.debug(msg);

        buildJobContainerService.moveResultsToSpecifiedDirectory(containerId, buildJob.buildConfig().resultPaths(),
                LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + LOCAL_CI_RESULTS_DIRECTORY);

        // Get an input stream of the test result files.

        msg = "~~~~~~~~~~~~~~~~~~~~ Collecting test results from container " + containerId + " for build job " + buildJob.id() + " ~~~~~~~~~~~~~~~~~~~~";
        buildLogsMap.appendBuildLogEntry(buildJob.id(), msg);
        log.info(msg);

        TarArchiveInputStream testResultsTarInputStream = null;

        BuildResult buildResult;

        try {
            testResultsTarInputStream = buildJobContainerService.getArchiveFromContainer(containerId, LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + LOCAL_CI_RESULTS_DIRECTORY);

            var buildLogs = buildLogsMap.getAndTruncateBuildLogs(buildJob.id());
            buildResult = parseTestResults(testResultsTarInputStream, buildJob.buildConfig().branch(), assignmentRepoCommitHash, testRepoCommitHash, buildCompletedDate,
                    buildJob.id(), buildLogs);
        }
        catch (NotFoundException e) {
            msg = "Could not find test results in container " + containerName;
            buildLogsMap.appendBuildLogEntry(buildJob.id(), msg);
            log.error(msg, e);
            // If the test results are not found, this means that something went wrong during the build and testing of the submission.
            return constructFailedBuildResult(buildJob.buildConfig().branch(), assignmentRepoCommitHash, testRepoCommitHash, buildCompletedDate);
        }
        catch (IOException | IllegalStateException e) {
            msg = "Error while parsing test results";
            buildLogsMap.appendBuildLogEntry(buildJob.id(), msg);
            throw new LocalCIException(msg, e);
        }
        finally {
            try {
                if (testResultsTarInputStream != null) {
                    testResultsTarInputStream.close();
                }
            }
            catch (IOException e) {
                msg = "Could not close test results tar input stream";
                buildLogsMap.appendBuildLogEntry(buildJob.id(), msg);
                log.error(msg, e);
            }
            buildJobContainerService.stopContainer(containerName);

            // Delete the cloned repositories
            deleteCloneRepo(assignmentRepositoryUri, assignmentRepoCommitHash, buildJob.id(), assignmentRepositoryPath);
            deleteCloneRepo(testRepositoryUri, assignmentRepoCommitHash, buildJob.id(), testsRepositoryPath);
            // do not try to delete the temp repository if it does not exist or is the same as the assignment reposity
            if (solutionRepositoryUri != null && !Objects.equals(assignmentRepositoryUri.repositorySlug(), solutionRepositoryUri.repositorySlug())) {
                deleteCloneRepo(solutionRepositoryUri, assignmentRepoCommitHash, buildJob.id(), solutionRepositoryPath);
            }

            for (int i = 0; i < auxiliaryRepositoriesUris.length; i++) {
                deleteCloneRepo(auxiliaryRepositoriesUris[i], assignmentRepoCommitHash, buildJob.id(), auxiliaryRepositoriesPaths[i]);
            }

            try {
                deleteRepoParentFolder(assignmentRepoCommitHash, assignmentRepositoryPath, testRepoCommitHash, testsRepositoryPath);
            }
            catch (IOException e) {
                msg = "Could not delete " + checkedOutReposPath + " directory";
                buildLogsMap.appendBuildLogEntry(buildJob.id(), msg);
                log.error(msg, e);
            }
        }

        msg = "Building and testing submission for repository " + assignmentRepositoryUri.repositorySlug() + " and commit hash " + assignmentRepoCommitHash + " took "
                + TimeLogUtil.formatDurationFrom(timeNanoStart) + " for build job " + buildJob.id();
        buildLogsMap.appendBuildLogEntry(buildJob.id(), msg);
        log.info(msg);

        return buildResult;
    }

    // --- Helper methods ----

    private BuildResult parseTestResults(TarArchiveInputStream testResultsTarInputStream, String assignmentRepoBranchName, String assignmentRepoCommitHash,
            String testsRepoCommitHash, ZonedDateTime buildCompletedDate, String buildJobId, List<BuildLogDTO> buildLogs) throws IOException {

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
                            TestResultXmlParser.processTestResultFile(testResultFileString, failedTests, successfulTests);
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
                buildCompletedDate, staticCodeAnalysisReports, buildLogs);
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
        return IOUtils.toString(tarArchiveInputStream, StandardCharsets.UTF_8);
    }

    /**
     * Constructs a {@link BuildResult} that indicates a failed build from the given parameters. The lists of failed and successful tests are both empty which will be
     * interpreted as a failed build by Artemis.
     *
     * @param assignmentRepoBranchName The name of the branch of the assignment repository that was checked out for the build.
     * @param assignmentRepoCommitHash The commit hash of the assignment repository that was checked out for the build.
     * @param testsRepoCommitHash      The commit hash of the tests repository that was checked out for the build.
     * @param buildRunDate             The date when the build was completed.
     * @return a {@link BuildResult} that indicates a failed build
     */
    private BuildResult constructFailedBuildResult(String assignmentRepoBranchName, @Nullable String assignmentRepoCommitHash, @Nullable String testsRepoCommitHash,
            ZonedDateTime buildRunDate) {
        return constructBuildResult(List.of(), List.of(), assignmentRepoBranchName, assignmentRepoCommitHash, testsRepoCommitHash, false, buildRunDate, List.of(), null);
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
     * @return a {@link BuildResult}
     */
    private BuildResult constructBuildResult(List<LocalCITestJobDTO> failedTests, List<LocalCITestJobDTO> successfulTests, String assignmentRepoBranchName,
            String assignmentRepoCommitHash, String testsRepoCommitHash, boolean isBuildSuccessful, ZonedDateTime buildRunDate,
            List<StaticCodeAnalysisReportDTO> staticCodeAnalysisReports, List<BuildLogDTO> buildLogs) {
        LocalCIJobDTO job = new LocalCIJobDTO(failedTests, successfulTests);
        return new BuildResult(assignmentRepoBranchName, assignmentRepoCommitHash, testsRepoCommitHash, isBuildSuccessful, buildRunDate, List.of(job), buildLogs,
                staticCodeAnalysisReports, true);
    }

    private Path cloneRepository(LocalVCRepositoryUri repositoryUri, @Nullable String commitHash, boolean checkout, String buildJobId) {
        Repository repository = null;

        for (int attempt = 1; attempt <= MAX_CLONE_RETRIES; attempt++) {
            try {
                // Generate a random folder name for the repository parent folder if the commit hash is null. This is to avoid conflicts when cloning multiple repositories.
                String repositoryParentFolder = commitHash != null ? commitHash : UUID.randomUUID().toString();
                // Clone the assignment repository into a temporary directory
                repository = buildJobGitService.cloneRepository(repositoryUri, Path.of(checkedOutReposPath, repositoryParentFolder, repositoryUri.folderNameForRepositoryUri()));

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
            if (checkout && commitHash != null) {
                // Checkout the commit hash
                buildJobGitService.checkoutRepositoryAtCommit(repository, commitHash);
            }

            // if repository is not closed, it causes weird IO issues when trying to delete the repository later on
            // java.io.IOException: Unable to delete file: ...\.git\objects\pack\...
            repository.closeBeforeDelete();
            return repository.getLocalPath();
        }
        catch (GitException e) {
            String msg = "Error while checking out commit " + commitHash + " in repository " + repositoryUri.repositorySlug();
            buildLogsMap.appendBuildLogEntry(buildJobId, msg);
            throw new LocalCIException(msg, e);
        }
    }

    private void deleteCloneRepo(LocalVCRepositoryUri repositoryUri, @Nullable String commitHash, String buildJobId, Path repositoryPath) {
        String msg;
        try {
            Path repositoryPathForDeletion = commitHash != null ? Path.of(checkedOutReposPath, commitHash, repositoryUri.folderNameForRepositoryUri()) : repositoryPath;
            Repository repository = buildJobGitService.getExistingCheckedOutRepositoryByLocalPath(repositoryPathForDeletion, repositoryUri, defaultBranch);
            if (repository == null) {
                msg = "Repository with commit hash " + commitHash + " not found";
                buildLogsMap.appendBuildLogEntry(buildJobId, msg);
                throw new EntityNotFoundException(msg);
            }
            buildJobGitService.deleteLocalRepository(repository);
        }
        // Do not throw an exception if deletion fails. If an exception occurs, clean up will happen in the next server start.
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

    private void deleteRepoParentFolder(String assignmentRepoCommitHash, Path assignmentRepositoryPath, String testRepoCommitHash, Path testsRepositoryPath) throws IOException {
        Path assignmentRepo = assignmentRepoCommitHash != null ? Path.of(checkedOutReposPath, assignmentRepoCommitHash) : getRepositoryParentFolderPath(assignmentRepositoryPath);
        FileUtils.deleteDirectory(assignmentRepo.toFile());
        Path testRepo = testRepoCommitHash != null ? Path.of(checkedOutReposPath, testRepoCommitHash) : getRepositoryParentFolderPath(testsRepositoryPath);
        FileUtils.deleteDirectory(testRepo.toFile());
    }

    private Path getRepositoryParentFolderPath(Path repoPath) {
        return repoPath.getParent().getParent();
    }
}
