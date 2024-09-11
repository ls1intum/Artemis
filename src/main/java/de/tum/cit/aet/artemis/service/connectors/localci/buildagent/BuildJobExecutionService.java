package de.tum.cit.aet.artemis.service.connectors.localci.buildagent;

import static de.tum.cit.aet.artemis.config.Constants.CHECKED_OUT_REPOS_TEMP_DIR;
import static de.tum.cit.aet.artemis.config.Constants.LOCALCI_RESULTS_DIRECTORY;
import static de.tum.cit.aet.artemis.config.Constants.LOCALCI_WORKING_DIRECTORY;
import static de.tum.cit.aet.artemis.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.service.connectors.localci.buildagent.TestResultXmlParser.processTestResultFile;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import jakarta.annotation.Nullable;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;

import de.tum.cit.aet.artemis.domain.Repository;
import de.tum.cit.aet.artemis.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.domain.enumeration.RepositoryType;
import de.tum.cit.aet.artemis.domain.enumeration.StaticCodeAnalysisTool;
import de.tum.cit.aet.artemis.exception.GitException;
import de.tum.cit.aet.artemis.exception.LocalCIException;
import de.tum.cit.aet.artemis.service.connectors.localci.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.service.connectors.localci.dto.BuildResult;
import de.tum.cit.aet.artemis.service.connectors.localci.scaparser.ReportParser;
import de.tum.cit.aet.artemis.service.connectors.localci.scaparser.exception.UnsupportedToolException;
import de.tum.cit.aet.artemis.service.connectors.localvc.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.service.dto.StaticCodeAnalysisReportDTO;
import de.tum.cit.aet.artemis.service.util.TimeLogUtil;
import de.tum.cit.aet.artemis.web.rest.errors.EntityNotFoundException;

/**
 * This service contains the logic to execute a build job for a programming exercise participation in the local CI system.
 * submitted to the executor service.
 */
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

    public BuildJobExecutionService(BuildJobContainerService buildJobContainerService, BuildJobGitService buildJobGitService, BuildAgentDockerService buildAgentDockerService,
            BuildLogsMap buildLogsMap) {
        this.buildJobContainerService = buildJobContainerService;
        this.buildJobGitService = buildJobGitService;
        this.buildAgentDockerService = buildAgentDockerService;
        this.buildLogsMap = buildLogsMap;
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

        CreateContainerResponse container = buildJobContainerService.configureContainer(containerName, buildJob.buildConfig().dockerImage(), buildJob.buildConfig().buildScript());

        return runScriptAndParseResults(buildJob, containerName, container.getId(), assignmentRepoUri, testsRepoUri, solutionRepoUri, auxiliaryRepositoriesUris,
                assignmentRepositoryPath, testsRepositoryPath, solutionRepositoryPath, auxiliaryRepositoriesPaths, assignmentCommitHash, testCommitHash);
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
    private BuildResult runScriptAndParseResults(BuildJobQueueItem buildJob, String containerName, String containerId, VcsRepositoryUri assignmentRepositoryUri,
            VcsRepositoryUri testRepositoryUri, VcsRepositoryUri solutionRepositoryUri, VcsRepositoryUri[] auxiliaryRepositoriesUris, Path assignmentRepositoryPath,
            Path testsRepositoryPath, Path solutionRepositoryPath, Path[] auxiliaryRepositoriesPaths, @Nullable String assignmentRepoCommitHash,
            @Nullable String testRepoCommitHash) {

        long timeNanoStart = System.nanoTime();

        buildJobContainerService.startContainer(containerId);

        String msg = "~~~~~~~~~~~~~~~~~~~~ Started container " + containerName + " for build job " + buildJob.id() + " ~~~~~~~~~~~~~~~~~~~~";
        buildLogsMap.appendBuildLogEntry(buildJob.id(), msg);

        log.info(msg, containerName);

        msg = "~~~~~~~~~~~~~~~~~~~~ Populating build job container with repositories and build script ~~~~~~~~~~~~~~~~~~~~";
        buildLogsMap.appendBuildLogEntry(buildJob.id(), msg);
        log.debug(msg);
        buildJobContainerService.populateBuildJobContainer(containerId, assignmentRepositoryPath, testsRepositoryPath, solutionRepositoryPath, auxiliaryRepositoriesPaths,
                buildJob.repositoryInfo().auxiliaryRepositoryCheckoutDirectories(), buildJob.buildConfig().programmingLanguage());

        msg = "~~~~~~~~~~~~~~~~~~~~ Executing Build Script for Build job " + buildJob.id() + " ~~~~~~~~~~~~~~~~~~~~";
        buildLogsMap.appendBuildLogEntry(buildJob.id(), msg);
        log.debug(msg);

        buildJobContainerService.runScriptInContainer(containerId, buildJob.id());

        msg = "~~~~~~~~~~~~~~~~~~~~ Finished Executing Build Script for Build job " + buildJob.id() + " ~~~~~~~~~~~~~~~~~~~~";
        buildLogsMap.appendBuildLogEntry(buildJob.id(), msg);
        log.info(msg);

        ZonedDateTime buildCompletedDate = ZonedDateTime.now();

        buildJobContainerService.moveResultsToSpecifiedDirectory(containerId, buildJob.buildConfig().resultPaths(), LOCALCI_WORKING_DIRECTORY + LOCALCI_RESULTS_DIRECTORY);

        // Get an input stream of the test result files.

        TarArchiveInputStream testResultsTarInputStream;

        try {
            testResultsTarInputStream = buildJobContainerService.getArchiveFromContainer(containerId, LOCALCI_WORKING_DIRECTORY + LOCALCI_RESULTS_DIRECTORY);
        }
        catch (NotFoundException e) {
            msg = "Could not find test results in container " + containerName;
            buildLogsMap.appendBuildLogEntry(buildJob.id(), msg);
            log.error(msg, e);
            // If the test results are not found, this means that something went wrong during the build and testing of the submission.
            return constructFailedBuildResult(buildJob.buildConfig().branch(), assignmentRepoCommitHash, testRepoCommitHash, buildCompletedDate);
        }
        finally {
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
                msg = "Could not delete " + CHECKED_OUT_REPOS_TEMP_DIR + " directory";
                buildLogsMap.appendBuildLogEntry(buildJob.id(), msg);
                log.error(msg, e);
            }
        }

        BuildResult buildResult;
        try {
            buildResult = parseTestResults(testResultsTarInputStream, buildJob.buildConfig().branch(), assignmentRepoCommitHash, testRepoCommitHash, buildCompletedDate,
                    buildJob.id());
            buildResult.setBuildLogEntries(buildLogsMap.getBuildLogs(buildJob.id()));
        }
        catch (IOException | IllegalStateException e) {
            msg = "Error while parsing test results";
            buildLogsMap.appendBuildLogEntry(buildJob.id(), msg);
            throw new LocalCIException(msg, e);
        }

        msg = "Building and testing submission for repository " + assignmentRepositoryUri.repositorySlug() + " and commit hash " + assignmentRepoCommitHash + " took "
                + TimeLogUtil.formatDurationFrom(timeNanoStart);
        buildLogsMap.appendBuildLogEntry(buildJob.id(), msg);
        log.info(msg);

        return buildResult;
    }

    // --- Helper methods ----

    private BuildResult parseTestResults(TarArchiveInputStream testResultsTarInputStream, String assignmentRepoBranchName, String assignmentRepoCommitHash,
            String testsRepoCommitHash, ZonedDateTime buildCompletedDate, String buildJobId) throws IOException {

        List<BuildResult.LocalCITestJobDTO> failedTests = new ArrayList<>();
        List<BuildResult.LocalCITestJobDTO> successfulTests = new ArrayList<>();
        List<StaticCodeAnalysisReportDTO> staticCodeAnalysisReports = new ArrayList<>();

        TarArchiveEntry tarEntry;
        while ((tarEntry = testResultsTarInputStream.getNextEntry()) != null) {
            // Go through all tar entries that are test result files.
            if (!isValidTestResultFile(tarEntry)) {
                continue;
            }

            // Read the contents of the tar entry as a string.
            String xmlString = readTarEntryContent(testResultsTarInputStream);
            // Get the file name of the tar entry.
            String fileName = getFileName(tarEntry);

            try {
                // Check if the file is a static code analysis report file
                if (StaticCodeAnalysisTool.getToolByFilePattern(fileName).isPresent()) {
                    processStaticCodeAnalysisReportFile(fileName, xmlString, staticCodeAnalysisReports, buildJobId);
                }
                else {
                    // ugly workaround because in swift result files \n\t breaks the parsing
                    var testResultFileString = xmlString.replace("\n\t", "");
                    if (!testResultFileString.isBlank()) {
                        processTestResultFile(testResultFileString, failedTests, successfulTests);
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
                buildCompletedDate, staticCodeAnalysisReports);
    }

    private boolean isValidTestResultFile(TarArchiveEntry tarArchiveEntry) {
        String name = tarArchiveEntry.getName();
        int lastIndexOfSlash = name.lastIndexOf('/');
        String result = (lastIndexOfSlash != -1 && lastIndexOfSlash + 1 < name.length()) ? name.substring(lastIndexOfSlash + 1) : name;

        // Java test result files are named "TEST-*.xml", Python test result files are named "*results.xml".
        return !tarArchiveEntry.isDirectory() && result.endsWith(".xml") && !result.equals("pom.xml");
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
     * @param xmlString                 the content of the static code analysis report file
     * @param staticCodeAnalysisReports the list of static code analysis reports
     */
    private void processStaticCodeAnalysisReportFile(String fileName, String xmlString, List<StaticCodeAnalysisReportDTO> staticCodeAnalysisReports, String buildJobId) {
        try {
            staticCodeAnalysisReports.add(ReportParser.getReport(xmlString, fileName));
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
        return constructBuildResult(List.of(), List.of(), assignmentRepoBranchName, assignmentRepoCommitHash, testsRepoCommitHash, false, buildRunDate, List.of());
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
     * @return a {@link BuildResult}
     */
    private BuildResult constructBuildResult(List<BuildResult.LocalCITestJobDTO> failedTests, List<BuildResult.LocalCITestJobDTO> successfulTests, String assignmentRepoBranchName,
            String assignmentRepoCommitHash, String testsRepoCommitHash, boolean isBuildSuccessful, ZonedDateTime buildRunDate,
            List<StaticCodeAnalysisReportDTO> staticCodeAnalysisReports) {
        BuildResult.LocalCIJobDTO job = new BuildResult.LocalCIJobDTO(failedTests, successfulTests);

        return new BuildResult(assignmentRepoBranchName, assignmentRepoCommitHash, testsRepoCommitHash, isBuildSuccessful, buildRunDate, List.of(job), staticCodeAnalysisReports);
    }

    private Path cloneRepository(VcsRepositoryUri repositoryUri, @Nullable String commitHash, boolean checkout, String buildJobId) {
        Repository repository = null;

        for (int attempt = 1; attempt <= MAX_CLONE_RETRIES; attempt++) {
            try {
                // Generate a random folder name for the repository parent folder if the commit hash is null. This is to avoid conflicts when cloning multiple repositories.
                String repositoryParentFolder = commitHash != null ? commitHash : UUID.randomUUID().toString();
                // Clone the assignment repository into a temporary directory
                repository = buildJobGitService.cloneRepository(repositoryUri,
                        Path.of(CHECKED_OUT_REPOS_TEMP_DIR, repositoryParentFolder, repositoryUri.folderNameForRepositoryUri()));

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

    private void deleteCloneRepo(VcsRepositoryUri repositoryUri, @Nullable String commitHash, String buildJobId, Path repositoryPath) {
        String msg;
        try {
            Path repositoryPathForDeletion = commitHash != null ? Paths.get(CHECKED_OUT_REPOS_TEMP_DIR, commitHash, repositoryUri.folderNameForRepositoryUri()) : repositoryPath;
            Repository repository = buildJobGitService.getExistingCheckedOutRepositoryByLocalPath(repositoryPathForDeletion, repositoryUri, defaultBranch);
            if (repository == null) {
                msg = "Repository with commit hash " + commitHash + " not found";
                buildLogsMap.appendBuildLogEntry(buildJobId, msg);
                throw new EntityNotFoundException(msg);
            }
            buildJobGitService.deleteLocalRepository(repository);
        }
        catch (EntityNotFoundException e) {
            msg = "Error while checking out repository";
            buildLogsMap.appendBuildLogEntry(buildJobId, msg);
            throw new LocalCIException(msg, e);
        }
        catch (IOException e) {
            msg = "Error while deleting repository";
            buildLogsMap.appendBuildLogEntry(buildJobId, msg);
            throw new LocalCIException(msg, e);
        }
    }

    private void deleteRepoParentFolder(String assignmentRepoCommitHash, Path assignmentRepositoryPath, String testRepoCommitHash, Path testsRepositoryPath) throws IOException {
        Path assignmentRepo = assignmentRepoCommitHash != null ? Path.of(CHECKED_OUT_REPOS_TEMP_DIR, assignmentRepoCommitHash)
                : getRepositoryParentFolderPath(assignmentRepositoryPath);
        FileUtils.deleteDirectory(assignmentRepo.toFile());
        Path testRepo = testRepoCommitHash != null ? Path.of(CHECKED_OUT_REPOS_TEMP_DIR, testRepoCommitHash) : getRepositoryParentFolderPath(testsRepositoryPath);
        FileUtils.deleteDirectory(testRepo.toFile());
    }

    private Path getRepositoryParentFolderPath(Path repoPath) {
        return repoPath.getParent().getParent();
    }
}
