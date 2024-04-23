package de.tum.in.www1.artemis.service.connectors.localci.buildagent;

import static de.tum.in.www1.artemis.config.Constants.CHECKED_OUT_REPOS_TEMP_DIR;
import static de.tum.in.www1.artemis.config.Constants.LOCALCI_RESULTS_DIRECTORY;
import static de.tum.in.www1.artemis.config.Constants.LOCALCI_WORKING_DIRECTORY;
import static de.tum.in.www1.artemis.config.Constants.PROFILE_BUILDAGENT;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;

import de.tum.in.www1.artemis.config.localvcci.LocalCIConfiguration;
import de.tum.in.www1.artemis.domain.BuildLogEntry;
import de.tum.in.www1.artemis.domain.Repository;
import de.tum.in.www1.artemis.domain.VcsRepositoryUri;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.enumeration.StaticCodeAnalysisTool;
import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildJobQueueItem;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildResult;
import de.tum.in.www1.artemis.service.connectors.localci.scaparser.exception.UnsupportedToolException;
import de.tum.in.www1.artemis.service.connectors.localci.scaparser.strategy.ParserPolicy;
import de.tum.in.www1.artemis.service.connectors.localci.scaparser.strategy.ParserStrategy;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCRepositoryUri;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * This service contains the logic to execute a build job for a programming exercise participation in the local CI system.
 * submitted to the executor service.
 */
@Service
@Profile(PROFILE_BUILDAGENT)
public class BuildJobExecutionService {

    private static final Logger log = LoggerFactory.getLogger(BuildJobExecutionService.class);

    private final BuildJobContainerService buildJobContainerService;

    private final GitService gitService;

    private final LocalCIDockerService localCIDockerService;

    /**
     * Instead of creating a new XmlMapper for every build job, it is created once and provided as a Bean (see {@link LocalCIConfiguration#localCiXmlMapper()}).
     */
    private final XmlMapper localCiXmlMapper;

    @Value("${artemis.version-control.url}")
    private URL localVCBaseUrl;

    @Value("${artemis.version-control.default-branch:main}")
    private String defaultBranch;

    public BuildJobExecutionService(BuildJobContainerService buildJobContainerService, GitService gitService, LocalCIDockerService localCIDockerService,
            XmlMapper localCiXmlMapper) {
        this.buildJobContainerService = buildJobContainerService;
        this.gitService = gitService;
        this.localCIDockerService = localCIDockerService;
        this.localCiXmlMapper = localCiXmlMapper;
    }

    /**
     * Prepare the paths to the assignment and test repositories, the branch to check out, the volume configuration for the Docker container, and the container configuration,
     * and then call to
     * execute the
     * job.
     *
     * @param buildJob      The build job object containing necessary information to execute the build job.
     * @param containerName The name of the Docker container that will be used to run the build job.
     *                          It needs to be prepared beforehand to stop and remove the container if something goes wrong here.
     * @return The build result.
     * @throws LocalCIException If some error occurs while preparing or running the build job.
     */
    public LocalCIBuildResult runBuildJob(LocalCIBuildJobQueueItem buildJob, String containerName) {

        // Check if the Docker image is available. If not, pull it.
        try {
            localCIDockerService.pullDockerImage(buildJob.buildConfig().dockerImage());
        }
        catch (LocalCIException e) {
            throw new LocalCIException("Could not pull Docker image " + buildJob.buildConfig().dockerImage(), e);
        }

        boolean isPushToTestOrAuxRepository = buildJob.repositoryInfo().triggeredByPushTo() == RepositoryType.TESTS
                || buildJob.repositoryInfo().triggeredByPushTo() == RepositoryType.AUXILIARY;

        // get the local repository paths for assignment, tests, auxiliary and solution
        LocalVCRepositoryUri assignmentRepoUri = new LocalVCRepositoryUri(buildJob.repositoryInfo().assignmentRepositoryUri(), localVCBaseUrl);
        LocalVCRepositoryUri testsRepoUri = new LocalVCRepositoryUri(buildJob.repositoryInfo().testRepositoryUri(), localVCBaseUrl);

        // retrieve last commit hash from repositories
        String assignmentCommitHash = buildJob.buildConfig().commitHash();
        if (assignmentCommitHash == null) {
            try {
                assignmentCommitHash = gitService.getLastCommitHash(assignmentRepoUri).getName();
            }
            catch (EntityNotFoundException e) {
                throw new LocalCIException("Could not find last commit hash for assignment repository " + assignmentRepoUri.repositorySlug(), e);
            }
        }
        String testCommitHash;
        try {
            testCommitHash = gitService.getLastCommitHash(testsRepoUri).getName();
        }
        catch (EntityNotFoundException e) {
            throw new LocalCIException("Could not find last commit hash for test repository " + testsRepoUri.repositorySlug(), e);
        }

        Path assignmentRepositoryPath;
        /*
         * If the commit hash is null, this means that the latest commit of the default branch should be built.
         * If this build job is triggered by a push to the test repository, the commit hash reflects changes to the test repository.
         * Thus, we do not checkout the commit hash of the test repository in the assignment repository.
         */
        if (buildJob.buildConfig().commitHash() != null && !isPushToTestOrAuxRepository) {
            // Clone the assignment repository into a temporary directory with the name of the commit hash and then checkout the commit hash.
            assignmentRepositoryPath = cloneRepository(assignmentRepoUri, assignmentCommitHash, true);
        }
        else {
            // Clone the assignment to use the latest commit of the default branch
            assignmentRepositoryPath = cloneRepository(assignmentRepoUri, assignmentCommitHash, false);
        }

        Path testsRepositoryPath = cloneRepository(testsRepoUri, assignmentCommitHash, false);

        LocalVCRepositoryUri solutionRepoUri = null;
        Path solutionRepositoryPath = null;
        if (buildJob.repositoryInfo().solutionRepositoryUri() != null) {
            solutionRepoUri = new LocalVCRepositoryUri(buildJob.repositoryInfo().solutionRepositoryUri(), localVCBaseUrl);
            // In case we have the same repository for assignment and solution, we can use the same path
            if (Objects.equals(solutionRepoUri.repositorySlug(), assignmentRepoUri.repositorySlug())) {
                solutionRepositoryPath = assignmentRepositoryPath;
            }
            else {
                solutionRepositoryPath = cloneRepository(solutionRepoUri, assignmentCommitHash, false);
            }
        }

        String[] auxiliaryRepositoryUriList = buildJob.repositoryInfo().auxiliaryRepositoryUris();
        Path[] auxiliaryRepositoriesPaths = new Path[auxiliaryRepositoryUriList.length];
        LocalVCRepositoryUri[] auxiliaryRepositoriesUris = new LocalVCRepositoryUri[auxiliaryRepositoryUriList.length];

        int index = 0;
        for (String auxiliaryRepositoryUri : auxiliaryRepositoryUriList) {
            auxiliaryRepositoriesUris[index] = new LocalVCRepositoryUri(auxiliaryRepositoryUri, localVCBaseUrl);
            auxiliaryRepositoriesPaths[index] = cloneRepository(auxiliaryRepositoriesUris[index], assignmentCommitHash, false);
            index++;
        }

        CreateContainerResponse container = buildJobContainerService.configureContainer(containerName, buildJob.buildConfig().dockerImage(), buildJob.buildConfig().buildScript());

        return runScriptAndParseResults(buildJob, containerName, container.getId(), assignmentRepoUri, testsRepoUri, solutionRepoUri, auxiliaryRepositoriesUris,
                assignmentRepositoryPath, testsRepositoryPath, solutionRepositoryPath, auxiliaryRepositoriesPaths, assignmentCommitHash, testCommitHash);
    }

    /**
     * Runs the build job. This includes creating and starting a Docker container, executing the build script, and processing the build result.
     *
     * @param containerName The name of the container that should be used for the build job. This is used to remove the container and is also accessible from outside build job
     *                          running in its own thread.
     * @param containerId   The id of the container that should be used for the build job.
     * @return The build result.
     * @throws LocalCIException if something went wrong while running the build job.
     */
    private LocalCIBuildResult runScriptAndParseResults(LocalCIBuildJobQueueItem buildJob, String containerName, String containerId, VcsRepositoryUri assignmentRepositoryUri,
            VcsRepositoryUri testRepositoryUri, VcsRepositoryUri solutionRepositoryUri, VcsRepositoryUri[] auxiliaryRepositoriesUris, Path assignmentRepositoryPath,
            Path testsRepositoryPath, Path solutionRepositoryPath, Path[] auxiliaryRepositoriesPaths, String assignmentRepoCommitHash, String testRepoCommitHash) {

        long timeNanoStart = System.nanoTime();

        buildJobContainerService.startContainer(containerId);

        log.info("Started container for build job {}", containerName);

        buildJobContainerService.populateBuildJobContainer(containerId, assignmentRepositoryPath, testsRepositoryPath, solutionRepositoryPath, auxiliaryRepositoriesPaths,
                buildJob.repositoryInfo().auxiliaryRepositoryCheckoutDirectories(), buildJob.buildConfig().programmingLanguage());

        List<BuildLogEntry> buildLogEntries = buildJobContainerService.runScriptInContainer(containerId);

        log.info("Finished running the build script in container {}", containerName);

        ZonedDateTime buildCompletedDate = ZonedDateTime.now();

        buildJobContainerService.moveResultsToSpecifiedDirectory(containerId, buildJob.buildConfig().resultPaths(), LOCALCI_WORKING_DIRECTORY + LOCALCI_RESULTS_DIRECTORY);

        // Get an input stream of the test result files.

        TarArchiveInputStream testResultsTarInputStream;

        try {
            testResultsTarInputStream = buildJobContainerService.getArchiveFromContainer(containerId, LOCALCI_WORKING_DIRECTORY + LOCALCI_RESULTS_DIRECTORY);
        }
        catch (NotFoundException e) {
            // If the test results are not found, this means that something went wrong during the build and testing of the submission.
            return constructFailedBuildResult(buildJob.buildConfig().branch(), assignmentRepoCommitHash, testRepoCommitHash, buildCompletedDate);
        }
        finally {
            buildJobContainerService.stopContainer(containerName);

            // Delete the cloned repositories
            deleteCloneRepo(assignmentRepositoryUri, assignmentRepoCommitHash);
            deleteCloneRepo(testRepositoryUri, assignmentRepoCommitHash);
            // do not try to delete the temp repository if it does not exist or is the same as the assignment reposity
            if (solutionRepositoryUri != null && !Objects.equals(assignmentRepositoryUri.repositorySlug(), solutionRepositoryUri.repositorySlug())) {
                deleteCloneRepo(solutionRepositoryUri, assignmentRepoCommitHash);
            }
            for (VcsRepositoryUri auxiliaryRepositoryUri : auxiliaryRepositoriesUris) {
                deleteCloneRepo(auxiliaryRepositoryUri, assignmentRepoCommitHash);
            }

            try {
                FileUtils.deleteDirectory(Path.of(CHECKED_OUT_REPOS_TEMP_DIR, assignmentRepoCommitHash).toFile());
            }
            catch (IOException e) {
                log.error("Could not delete " + CHECKED_OUT_REPOS_TEMP_DIR + " directory", e);
            }
        }

        LocalCIBuildResult buildResult;
        try {
            buildResult = parseTestResults(testResultsTarInputStream, buildJob.buildConfig().branch(), assignmentRepoCommitHash, testRepoCommitHash, buildCompletedDate);
            buildResult.setBuildLogEntries(buildLogEntries);
        }
        catch (IOException | IllegalStateException e) {
            throw new LocalCIException("Error while parsing test results", e);
        }

        log.info("Building and testing submission for repository {} and commit hash {} took {}", assignmentRepositoryUri.repositorySlug(), assignmentRepoCommitHash,
                TimeLogUtil.formatDurationFrom(timeNanoStart));

        return buildResult;
    }

    // --- Helper methods ----

    private LocalCIBuildResult parseTestResults(TarArchiveInputStream testResultsTarInputStream, String assignmentRepoBranchName, String assignmentRepoCommitHash,
            String testsRepoCommitHash, ZonedDateTime buildCompletedDate) throws IOException {

        List<LocalCIBuildResult.LocalCITestJobDTO> failedTests = new ArrayList<>();
        List<LocalCIBuildResult.LocalCITestJobDTO> successfulTests = new ArrayList<>();
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
                    processStaticCodeAnalysisReportFile(fileName, xmlString, staticCodeAnalysisReports);
                }
                else {
                    // ugly workaround because in swift result files \n\t breaks the parsing
                    processTestResultFile(xmlString.replace("\n\t", ""), failedTests, successfulTests);
                }
            }
            catch (IllegalStateException e) {
                // Exceptions due to one invalid sca file should not lead to the whole build to fail.
                log.warn("Invalid report format in file {}, ignoring.", fileName, e);
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
    private void processStaticCodeAnalysisReportFile(String fileName, String xmlString, List<StaticCodeAnalysisReportDTO> staticCodeAnalysisReports) {
        try {
            ParserPolicy parserPolicy = new ParserPolicy();
            ParserStrategy parserStrategy = parserPolicy.configure(fileName);
            staticCodeAnalysisReports.add(parserStrategy.parse(xmlString));
        }
        catch (UnsupportedToolException e) {
            throw new IllegalStateException("Failed to parse static code analysis report for " + fileName, e);
        }
    }

    private String readTarEntryContent(TarArchiveInputStream tarArchiveInputStream) throws IOException {
        return IOUtils.toString(tarArchiveInputStream, StandardCharsets.UTF_8);
    }

    private void processTestResultFile(String testResultFileString, List<LocalCIBuildResult.LocalCITestJobDTO> failedTests,
            List<LocalCIBuildResult.LocalCITestJobDTO> successfulTests) throws IOException {
        TestSuite testSuite = localCiXmlMapper.readValue(testResultFileString, TestSuite.class);

        for (TestCase testCase : testSuite.testCases()) {
            Failure failure = testCase.extractFailure();
            if (failure != null) {
                failedTests.add(new LocalCIBuildResult.LocalCITestJobDTO(testCase.name(), List.of(failure.extractMessage())));
            }
            else {
                successfulTests.add(new LocalCIBuildResult.LocalCITestJobDTO(testCase.name(), List.of()));
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TestSuite(@JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(localName = "testcase") List<TestCase> testCases) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TestCase(@JacksonXmlProperty(isAttribute = true, localName = "name") String name, @JacksonXmlProperty(localName = "failure") Failure failure,
            @JacksonXmlProperty(localName = "error") Failure error) {

        private Failure extractFailure() {
            return failure != null ? failure : error;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Failure(@JacksonXmlProperty(isAttribute = true, localName = "type") String type, @JacksonXmlProperty(isAttribute = true, localName = "message") String message,
            @JacksonXmlText String detailedMessage) {

        private String extractMessage() {
            return message != null ? message : detailedMessage;
        }
    }

    /**
     * Constructs a {@link LocalCIBuildResult} that indicates a failed build from the given parameters. The lists of failed and successful tests are both empty which will be
     * interpreted as a failed build by Artemis.
     *
     * @param assignmentRepoBranchName The name of the branch of the assignment repository that was checked out for the build.
     * @param assignmentRepoCommitHash The commit hash of the assignment repository that was checked out for the build.
     * @param testsRepoCommitHash      The commit hash of the tests repository that was checked out for the build.
     * @param buildRunDate             The date when the build was completed.
     * @return a {@link LocalCIBuildResult} that indicates a failed build
     */
    private LocalCIBuildResult constructFailedBuildResult(String assignmentRepoBranchName, String assignmentRepoCommitHash, String testsRepoCommitHash,
            ZonedDateTime buildRunDate) {
        return constructBuildResult(List.of(), List.of(), assignmentRepoBranchName, assignmentRepoCommitHash, testsRepoCommitHash, false, buildRunDate, List.of());
    }

    /**
     * Constructs a {@link LocalCIBuildResult} from the given parameters.
     *
     * @param failedTests               The list of failed tests.
     * @param successfulTests           The list of successful tests.
     * @param assignmentRepoBranchName  The name of the branch of the assignment repository that was checked out for the build.
     * @param assignmentRepoCommitHash  The commit hash of the assignment repository that was checked out for the build.
     * @param testsRepoCommitHash       The commit hash of the tests repository that was checked out for the build.
     * @param isBuildSuccessful         Whether the build was successful or not.
     * @param buildRunDate              The date when the build was completed.
     * @param staticCodeAnalysisReports The static code analysis reports
     * @return a {@link LocalCIBuildResult}
     */
    private LocalCIBuildResult constructBuildResult(List<LocalCIBuildResult.LocalCITestJobDTO> failedTests, List<LocalCIBuildResult.LocalCITestJobDTO> successfulTests,
            String assignmentRepoBranchName, String assignmentRepoCommitHash, String testsRepoCommitHash, boolean isBuildSuccessful, ZonedDateTime buildRunDate,
            List<StaticCodeAnalysisReportDTO> staticCodeAnalysisReports) {
        LocalCIBuildResult.LocalCIJobDTO job = new LocalCIBuildResult.LocalCIJobDTO(failedTests, successfulTests);

        return new LocalCIBuildResult(assignmentRepoBranchName, assignmentRepoCommitHash, testsRepoCommitHash, isBuildSuccessful, buildRunDate, List.of(job),
                staticCodeAnalysisReports);
    }

    private Path cloneRepository(VcsRepositoryUri repositoryUri, String commitHash, boolean checkout) {
        try {
            // Clone the assignment repository into a temporary directory
            Repository repository = gitService.getOrCheckoutRepository(repositoryUri, Paths.get(CHECKED_OUT_REPOS_TEMP_DIR, commitHash, repositoryUri.folderNameForRepositoryUri()),
                    false);
            if (checkout) {
                // Checkout the commit hash
                gitService.checkoutRepositoryAtCommit(repository, commitHash);
            }
            return repository.getLocalPath();
        }
        catch (GitAPIException e) {
            throw new LocalCIException("Error while cloning repository", e);
        }
    }

    private void deleteCloneRepo(VcsRepositoryUri repositoryUri, String commitHash) {
        try {
            Repository repository = gitService.getExistingCheckedOutRepositoryByLocalPath(
                    Paths.get(CHECKED_OUT_REPOS_TEMP_DIR, commitHash, repositoryUri.folderNameForRepositoryUri()), repositoryUri, defaultBranch);
            if (repository == null) {
                throw new EntityNotFoundException("Repository with commit hash " + commitHash + " not found");
            }
            gitService.deleteLocalRepository(repository);
        }
        catch (EntityNotFoundException e) {
            throw new LocalCIException("Error while checking out repository", e);
        }
        catch (IOException e) {
            throw new LocalCIException("Error while deleting repository", e);
        }
    }
}
