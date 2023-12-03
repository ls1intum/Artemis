package de.tum.in.www1.artemis.service.connectors.localci;

import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.IOUtils;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;

import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;

import de.tum.in.www1.artemis.config.localvcci.LocalCIConfiguration;
import de.tum.in.www1.artemis.domain.AuxiliaryRepository;
import de.tum.in.www1.artemis.domain.BuildLogEntry;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.domain.enumeration.StaticCodeAnalysisTool;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.exception.localvc.LocalVCInternalException;
import de.tum.in.www1.artemis.repository.AuxiliaryRepositoryRepository;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildResult;
import de.tum.in.www1.artemis.service.connectors.localci.scaparser.strategy.ParserPolicy;
import de.tum.in.www1.artemis.service.connectors.localci.scaparser.strategy.ParserStrategy;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCRepositoryUrl;
import de.tum.in.www1.artemis.service.connectors.vcs.VersionControlService;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;
import de.tum.in.www1.artemis.service.util.XmlFileUtils;

/**
 * This service contains the logic to execute a build job for a programming exercise participation in the local CI system.
 * The {@link #runBuildJob(ProgrammingExerciseParticipation, String, String, String)} method is wrapped into a Callable by the {@link LocalCIBuildJobManagementService} and
 * submitted to the executor service.
 */
@Service
@Profile("localci")
public class LocalCIBuildJobExecutionService {

    private final Logger log = LoggerFactory.getLogger(LocalCIBuildJobExecutionService.class);

    private final LocalCIBuildPlanService localCIBuildPlanService;

    private final Optional<VersionControlService> versionControlService;

    private final LocalCIContainerService localCIContainerService;

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    /**
     * Instead of creating a new XMLInputFactory for every build job, it is created once and provided as a Bean (see {@link LocalCIConfiguration#localCIXMLInputFactory()}).
     */
    private final XMLInputFactory localCIXMLInputFactory;

    @Value("${artemis.version-control.url}")
    private URL localVCBaseUrl;

    @Value("${artemis.repo-clone-path}")
    private String repoClonePath;

    public LocalCIBuildJobExecutionService(LocalCIBuildPlanService localCIBuildPlanService, Optional<VersionControlService> versionControlService,
            LocalCIContainerService localCIContainerService, AuxiliaryRepositoryRepository auxiliaryRepositoryRepository, XMLInputFactory localCIXMLInputFactory) {
        this.localCIBuildPlanService = localCIBuildPlanService;
        this.versionControlService = versionControlService;
        this.localCIContainerService = localCIContainerService;
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
        this.localCIXMLInputFactory = localCIXMLInputFactory;
    }

    public enum LocalCIBuildJobRepositoryType {

        ASSIGNMENT("assignment"), TEST("test"), AUXILIARY("auxiliary");

        private final String name;

        LocalCIBuildJobRepositoryType(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Prepare the paths to the assignment and test repositories, the branch to checkout, the volume configuration for the Docker container, and the container configuration,
     * and then call {@link #runScriptAndParseResults(ProgrammingExerciseParticipation, String, String, String, String, Path, Path, Path[], String[], Path)} to execute the job.
     *
     * @param participation The participation of the repository for which the build job should be executed.
     * @param commitHash    The commit hash of the commit that should be built. If it is null, the latest commit of the default branch will be built.
     * @param containerName The name of the Docker container that will be used to run the build job.
     *                          It needs to be prepared beforehand to stop and remove the container if something goes wrong here.
     * @param dockerImage   The Docker image that will be used to run the build job.
     * @return The build result.
     * @throws LocalCIException If some error occurs while preparing or running the build job.
     */
    public LocalCIBuildResult runBuildJob(ProgrammingExerciseParticipation participation, String commitHash, String containerName, String dockerImage) {
        // Update the build plan status to "BUILDING".
        localCIBuildPlanService.updateBuildPlanStatus(participation, ContinuousIntegrationService.BuildStatus.BUILDING);

        List<AuxiliaryRepository> auxiliaryRepositories;

        // If the auxiliary repositories are not initialized, we need to fetch them from the database.
        if (Hibernate.isInitialized(participation.getProgrammingExercise().getAuxiliaryRepositories())) {
            auxiliaryRepositories = participation.getProgrammingExercise().getAuxiliaryRepositories();
        }
        else {
            auxiliaryRepositories = auxiliaryRepositoryRepository.findByExerciseId(participation.getProgrammingExercise().getId());
        }

        // Prepare script
        Path buildScriptPath = localCIContainerService.createBuildScript(participation);

        // Retrieve the paths to the repositories that the build job needs.
        // This includes the assignment repository (the one to be tested, e.g. the student's repository, or the template repository), and the tests repository which includes
        // the tests to be executed.
        LocalVCRepositoryUrl assignmentRepositoryUrl;
        LocalVCRepositoryUrl testsRepositoryUrl;
        LocalVCRepositoryUrl[] auxiliaryRepositoriesUrls;
        Path[] auxiliaryRepositoriesPaths;
        String[] auxiliaryRepositoryCheckoutDirectories;

        try {
            assignmentRepositoryUrl = new LocalVCRepositoryUrl(participation.getRepositoryUrl(), localVCBaseUrl);
            testsRepositoryUrl = new LocalVCRepositoryUrl(participation.getProgrammingExercise().getTestRepositoryUrl(), localVCBaseUrl);

            if (!auxiliaryRepositories.isEmpty()) {
                auxiliaryRepositoriesUrls = new LocalVCRepositoryUrl[auxiliaryRepositories.size()];
                auxiliaryRepositoriesPaths = new Path[auxiliaryRepositories.size()];
                auxiliaryRepositoryCheckoutDirectories = new String[auxiliaryRepositories.size()];

                for (int i = 0; i < auxiliaryRepositories.size(); i++) {
                    auxiliaryRepositoriesUrls[i] = new LocalVCRepositoryUrl(auxiliaryRepositories.get(i).getRepositoryUrl(), localVCBaseUrl);
                    auxiliaryRepositoriesPaths[i] = auxiliaryRepositoriesUrls[i].getRepoClonePath(repoClonePath).toAbsolutePath();
                    auxiliaryRepositoryCheckoutDirectories[i] = auxiliaryRepositories.get(i).getCheckoutDirectory();
                }
            }
            else {
                auxiliaryRepositoriesPaths = new Path[0];
                auxiliaryRepositoryCheckoutDirectories = new String[0];
            }
        }
        catch (LocalVCInternalException e) {
            throw new LocalCIException("Error while creating LocalVCRepositoryUrl", e);
        }

        Path assignmentRepositoryPath = assignmentRepositoryUrl.getRepoClonePath(repoClonePath).toAbsolutePath();
        Path testsRepositoryPath = testsRepositoryUrl.getRepoClonePath(repoClonePath).toAbsolutePath();

        String branch;
        try {
            branch = versionControlService.orElseThrow().getOrRetrieveBranchOfParticipation(participation);
        }
        catch (LocalVCInternalException e) {
            throw new LocalCIException("Error while getting branch of participation", e);
        }

        // Create the container from the "ls1tum/artemis-maven-template" image with the local paths to the Git repositories and the shell script bound to it. Also give the
        // container information about the branch and commit hash to be used.
        // This does not start the container yet.
        CreateContainerResponse container = localCIContainerService.configureContainer(containerName, branch, commitHash, dockerImage);

        return runScriptAndParseResults(participation, containerName, container.getId(), branch, commitHash, assignmentRepositoryPath, testsRepositoryPath,
                auxiliaryRepositoriesPaths, auxiliaryRepositoryCheckoutDirectories, buildScriptPath);
    }

    /**
     * Runs the build job. This includes creating and starting a Docker container, executing the build script, and processing the build result.
     *
     * @param participation The participation for which the build job should be run.
     * @param containerName The name of the container that should be used for the build job. This is used to remove the container and is also accessible from outside build job
     *                          running in its own thread.
     * @param containerId   The id of the container that should be used for the build job.
     * @param branch        The branch that should be built.
     * @param commitHash    The commit hash of the commit that should be built. If it is null, this method uses the latest commit of the repository.
     * @return The build result.
     * @throws LocalCIException if something went wrong while running the build job.
     */
    private LocalCIBuildResult runScriptAndParseResults(ProgrammingExerciseParticipation participation, String containerName, String containerId, String branch, String commitHash,
            Path assignmentRepositoryPath, Path testsRepositoryPath, Path[] auxiliaryRepositoriesPaths, String[] auxiliaryRepositoryCheckoutDirectories, Path buildScriptPath) {

        ProgrammingLanguage programmingLanguage = participation.getProgrammingExercise().getProgrammingLanguage();

        long timeNanoStart = System.nanoTime();

        localCIContainerService.startContainer(containerId);

        log.info("Started container for build job {}", containerName);

        localCIContainerService.populateBuildJobContainer(containerId, assignmentRepositoryPath, testsRepositoryPath, auxiliaryRepositoriesPaths,
                auxiliaryRepositoryCheckoutDirectories, buildScriptPath, programmingLanguage);

        List<BuildLogEntry> buildLogEntries = localCIContainerService.runScriptInContainer(containerId);

        log.info("Finished running the build script in container {}", containerName);

        ZonedDateTime buildCompletedDate = ZonedDateTime.now();

        String assignmentRepoCommitHash = commitHash;
        String testRepoCommitHash = "";

        try {
            if (commitHash == null) {
                // Retrieve the latest commit hash from the assignment repository.
                assignmentRepoCommitHash = localCIContainerService.getCommitHashOfBranch(containerId, LocalCIBuildJobRepositoryType.ASSIGNMENT, branch, programmingLanguage);
            }
            // Always use the latest commit from the test repository.
            testRepoCommitHash = localCIContainerService.getCommitHashOfBranch(containerId, LocalCIBuildJobRepositoryType.TEST, branch, programmingLanguage);
        }
        catch (NotFoundException | IOException e) {
            // Could not read commit hash from .git folder. Stop the container and return a build result that indicates that the build failed (empty list for failed tests and
            // empty list for successful tests).
            localCIContainerService.stopContainer(containerName);
            // Delete script file from host system
            localCIContainerService.deleteScriptFile(participation.getId().toString());
            return constructFailedBuildResult(branch, assignmentRepoCommitHash, testRepoCommitHash, buildCompletedDate);
        }

        List<String> testResultsPaths = getTestResultPaths(participation.getProgrammingExercise());

        // Get an input stream of the test result files.
        List<TarArchiveInputStream> testResultsTarInputStreams = new ArrayList<>();
        try {
            for (String testResultsPath : testResultsPaths) {
                testResultsTarInputStreams.add(localCIContainerService.getArchiveFromContainer(containerId, testResultsPath));
            }
        }
        catch (NotFoundException e) {
            // If the test results are not found, this means that something went wrong during the build and testing of the submission.
            // Stop the container and return a build results that indicates that the build failed.
            localCIContainerService.stopContainer(containerName);
            // Delete script file from host system
            localCIContainerService.deleteScriptFile(participation.getId().toString());
            return constructFailedBuildResult(branch, assignmentRepoCommitHash, testRepoCommitHash, buildCompletedDate);
        }

        localCIContainerService.stopContainer(containerName);

        // Delete script file from host system
        localCIContainerService.deleteScriptFile(participation.getId().toString());

        LocalCIBuildResult buildResult;
        try {
            buildResult = parseTestResults(testResultsTarInputStreams, branch, assignmentRepoCommitHash, testRepoCommitHash, buildCompletedDate);
            buildResult.setBuildLogEntries(buildLogEntries);
        }
        catch (IOException | XMLStreamException | IllegalStateException e) {
            throw new LocalCIException("Error while parsing test results", e);
        }

        // Set the build status to "INACTIVE" to indicate that the build is not running anymore.
        localCIBuildPlanService.updateBuildPlanStatus(participation, ContinuousIntegrationService.BuildStatus.INACTIVE);

        log.info("Building and testing submission for repository {} and commit hash {} took {}", participation.getRepositoryUrl(), commitHash,
                TimeLogUtil.formatDurationFrom(timeNanoStart));

        return buildResult;
    }

    // --- Helper methods ----

    private List<String> getTestResultPaths(ProgrammingExercise programmingExercise) {
        switch (programmingExercise.getProgrammingLanguage()) {
            case JAVA, KOTLIN -> {
                return getJavaKotlinTestResultPaths(programmingExercise);
            }
            case PYTHON -> {
                return getPythonTestResultPaths();
            }
            default -> throw new IllegalArgumentException("Programming language " + programmingExercise.getProgrammingLanguage() + " is not supported");
        }
    }

    private List<String> getJavaKotlinTestResultPaths(ProgrammingExercise programmingExercise) {
        List<String> testResultPaths = new ArrayList<>();
        if (ProjectType.isMavenProject(programmingExercise.getProjectType())) {
            if (programmingExercise.hasSequentialTestRuns()) {
                testResultPaths.add("/testing-dir/structural/target/surefire-reports");
                testResultPaths.add("/testing-dir/behavior/target/surefire-reports");
            }
            else {
                testResultPaths.add("/testing-dir/target/surefire-reports");
            }
        }
        else {
            if (programmingExercise.hasSequentialTestRuns()) {
                testResultPaths.add("/testing-dir/build/test-results/behaviorTests");
                testResultPaths.add("/testing-dir/build/test-results/structuralTests");
            }
            else {
                testResultPaths.add("/testing-dir/build/test-results/test");
            }
        }
        if (programmingExercise.isStaticCodeAnalysisEnabled()) {
            testResultPaths.add("/testing-dir/target");
        }
        return testResultPaths;
    }

    private List<String> getPythonTestResultPaths() {
        List<String> testResultPaths = new ArrayList<>();
        testResultPaths.add("/testing-dir/test-reports");
        return testResultPaths;
    }

    private LocalCIBuildResult parseTestResults(List<TarArchiveInputStream> testResultsTarInputStreams, String assignmentRepoBranchName, String assignmentRepoCommitHash,
            String testsRepoCommitHash, ZonedDateTime buildCompletedDate) throws IOException, XMLStreamException {

        List<LocalCIBuildResult.LocalCITestJobDTO> failedTests = new ArrayList<>();
        List<LocalCIBuildResult.LocalCITestJobDTO> successfulTests = new ArrayList<>();
        List<StaticCodeAnalysisReportDTO> staticCodeAnalysisReports = new ArrayList<>();

        TarArchiveEntry tarEntry;
        for (TarArchiveInputStream testResultsTarInputStream : testResultsTarInputStreams) {
            if (testResultsTarInputStream == null) {
                continue;
            }
            while ((tarEntry = testResultsTarInputStream.getNextTarEntry()) != null) {
                // Go through all tar entries that are test result files.
                if (!isValidTestResultFile(tarEntry)) {
                    continue;
                }
                // Read the contents of the tar entry as a string.
                String xmlString = readTarEntryContent(testResultsTarInputStream);
                // Get the file name of the tar entry.
                String fileName = getFileName(tarEntry);

                // Check if the file is a static code analysis report file
                if (StaticCodeAnalysisTool.getToolByFilePattern(fileName).isPresent()) {
                    processStaticCodeAnalysisReportFile(fileName, xmlString, staticCodeAnalysisReports);
                }
                else {
                    processTestResultFile(xmlString, failedTests, successfulTests);
                }
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
        return !tarArchiveEntry.isDirectory() && ((result.endsWith(".xml") && !result.equals("pom.xml")));
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
        return lastIndex != -1 ? filePath.substring(lastIndex + 1) : filePath;
    }

    /**
     * Processes a static code analysis report file and adds the report to the corresponding list.
     *
     * @param fileName                  the file name of the static code analysis report file
     * @param xmlString                 the content of the static code analysis report file
     * @param staticCodeAnalysisReports the list of static code analysis reports
     */
    private void processStaticCodeAnalysisReportFile(String fileName, String xmlString, List<StaticCodeAnalysisReportDTO> staticCodeAnalysisReports) {
        Document document = XmlFileUtils.readFromString(xmlString);
        document.setDocumentURI(fileName);
        try {
            ParserPolicy parserPolicy = new ParserPolicy();
            ParserStrategy parserStrategy = parserPolicy.configure(document);
            staticCodeAnalysisReports.add(parserStrategy.parse(document));
        }
        catch (Exception e) {
            throw new IllegalStateException("Failed to parse static code analysis report for " + fileName, e);
        }
    }

    private String readTarEntryContent(TarArchiveInputStream tarArchiveInputStream) throws IOException {
        return IOUtils.toString(tarArchiveInputStream, StandardCharsets.UTF_8);
    }

    /**
     * Processes a test result file and adds the failed and successful tests to the corresponding lists.
     *
     * @param testResultFileString The string that represents the test results XML file.
     * @param failedTests          The list of failed tests.
     * @param successfulTests      The list of successful tests.
     * @throws XMLStreamException    if the XML stream reader cannot be created or there is an error while parsing the XML file
     * @throws IllegalStateException if the first start element of the XML file is not a "testsuite" node
     */
    private void processTestResultFile(String testResultFileString, List<LocalCIBuildResult.LocalCITestJobDTO> failedTests,
            List<LocalCIBuildResult.LocalCITestJobDTO> successfulTests) throws XMLStreamException {
        // Create an XML stream reader for the string that represents the test results XML file.
        XMLStreamReader xmlStreamReader = localCIXMLInputFactory.createXMLStreamReader(new StringReader(testResultFileString));

        // Move to the first start element.
        while (xmlStreamReader.hasNext() && !xmlStreamReader.isStartElement()) {
            xmlStreamReader.next();
        }

        if ("testsuites".equals(xmlStreamReader.getLocalName())) {
            xmlStreamReader.next();
        }

        // Check if the start element is the "testsuite" node.
        if (!("testsuite".equals(xmlStreamReader.getLocalName()))) {
            throw new IllegalStateException("Expected testsuite element, but got " + xmlStreamReader.getLocalName());
        }

        // Go through all testcase nodes.
        while (xmlStreamReader.hasNext()) {
            xmlStreamReader.next();

            if (!xmlStreamReader.isStartElement() || !("testcase".equals(xmlStreamReader.getLocalName()))) {
                continue;
            }
            // Now we are at the start of a "testcase" node.
            processTestCaseNode(xmlStreamReader, failedTests, successfulTests);
        }

        // Close the XML stream reader.
        xmlStreamReader.close();
    }

    private void processTestCaseNode(XMLStreamReader xmlStreamReader, List<LocalCIBuildResult.LocalCITestJobDTO> failedTests,
            List<LocalCIBuildResult.LocalCITestJobDTO> successfulTests) throws XMLStreamException {
        // Extract the name attribute from the "testcase" node. This is the name of the test case.
        String name = xmlStreamReader.getAttributeValue(null, "name");

        // Check if there is a failure node inside the testcase node.
        // Call next() until there is an end element (no failure node exists inside the testcase node) or a start element (failure node exists inside the
        // testcase node).
        xmlStreamReader.next();
        while (!(xmlStreamReader.isEndElement() || xmlStreamReader.isStartElement())) {
            xmlStreamReader.next();
        }
        if (xmlStreamReader.isStartElement() && "failure".equals(xmlStreamReader.getLocalName())) {
            // Extract the message attribute from the "failure" node.
            String error = xmlStreamReader.getAttributeValue(null, "message");

            // Add the failed test to the list of failed tests.
            List<String> errors = error != null ? List.of(error) : List.of();
            failedTests.add(new LocalCIBuildResult.LocalCITestJobDTO(name, errors));
        }
        else {
            // Add the successful test to the list of successful tests.
            successfulTests.add(new LocalCIBuildResult.LocalCITestJobDTO(name, List.of()));
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
     * @param failedTests              The list of failed tests.
     * @param successfulTests          The list of successful tests.
     * @param assignmentRepoBranchName The name of the branch of the assignment repository that was checked out for the build.
     * @param assignmentRepoCommitHash The commit hash of the assignment repository that was checked out for the build.
     * @param testsRepoCommitHash      The commit hash of the tests repository that was checked out for the build.
     * @param isBuildSuccessful        Whether the build was successful or not.
     * @param buildRunDate             The date when the build was completed.
     * @return a {@link LocalCIBuildResult}
     */
    private LocalCIBuildResult constructBuildResult(List<LocalCIBuildResult.LocalCITestJobDTO> failedTests, List<LocalCIBuildResult.LocalCITestJobDTO> successfulTests,
            String assignmentRepoBranchName, String assignmentRepoCommitHash, String testsRepoCommitHash, boolean isBuildSuccessful, ZonedDateTime buildRunDate,
            List<StaticCodeAnalysisReportDTO> staticCodeAnalysisReports) {
        LocalCIBuildResult.LocalCIJobDTO job = new LocalCIBuildResult.LocalCIJobDTO(failedTests, successfulTests);

        return new LocalCIBuildResult(assignmentRepoBranchName, assignmentRepoCommitHash, testsRepoCommitHash, isBuildSuccessful, buildRunDate, List.of(job),
                staticCodeAnalysisReports);
    }
}
