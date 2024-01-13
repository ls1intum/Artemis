package de.tum.in.www1.artemis.service.connectors.localci;

import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import org.eclipse.jgit.api.errors.GitAPIException;
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
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.StaticCodeAnalysisTool;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.exception.localvc.LocalVCInternalException;
import de.tum.in.www1.artemis.repository.AuxiliaryRepositoryRepository;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.aeolus.AeolusResult;
import de.tum.in.www1.artemis.service.connectors.aeolus.AeolusTemplateService;
import de.tum.in.www1.artemis.service.connectors.aeolus.Windfile;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildResult;
import de.tum.in.www1.artemis.service.connectors.localci.scaparser.exception.UnsupportedToolException;
import de.tum.in.www1.artemis.service.connectors.localci.scaparser.strategy.ParserPolicy;
import de.tum.in.www1.artemis.service.connectors.localci.scaparser.strategy.ParserStrategy;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCRepositoryUri;
import de.tum.in.www1.artemis.service.connectors.vcs.VersionControlService;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeature;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeatureService;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;
import de.tum.in.www1.artemis.service.util.XmlFileUtils;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * This service contains the logic to execute a build job for a programming exercise participation in the local CI system.
 * The {@link #runBuildJob(ProgrammingExerciseParticipation, String, boolean, String, String)} method is wrapped into a Callable by the {@link LocalCIBuildJobManagementService} and
 * submitted to the executor service.
 */
@Service
@Profile("localci")
public class LocalCIBuildJobExecutionService {

    private static final Logger log = LoggerFactory.getLogger(LocalCIBuildJobExecutionService.class);

    private final LocalCIBuildPlanService localCIBuildPlanService;

    private final Optional<VersionControlService> versionControlService;

    private final LocalCIContainerService localCIContainerService;

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    private final ParticipationRepository participationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final Optional<ProgrammingLanguageFeatureService> programmingLanguageFeatureService;

    /**
     * Instead of creating a new XMLInputFactory for every build job, it is created once and provided as a Bean (see {@link LocalCIConfiguration#localCIXMLInputFactory()}).
     */
    private final XMLInputFactory localCIXMLInputFactory;

    private final GitService gitService;

    @Value("${artemis.version-control.url}")
    private URL localVCBaseUrl;

    @Value("${artemis.repo-clone-path}")
    private String repoClonePath;

    @Value("${artemis.version-control.default-branch:main}")
    private String defaultBranch;

    private final AeolusTemplateService aeolusTemplateService;

    public LocalCIBuildJobExecutionService(LocalCIBuildPlanService localCIBuildPlanService, Optional<VersionControlService> versionControlService,
            LocalCIContainerService localCIContainerService, AuxiliaryRepositoryRepository auxiliaryRepositoryRepository, XMLInputFactory localCIXMLInputFactory,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            Optional<ProgrammingLanguageFeatureService> programmingLanguageFeatureService, GitService gitService, AeolusTemplateService aeolusTemplateService,
            ParticipationRepository participationRepository) {
        this.localCIBuildPlanService = localCIBuildPlanService;
        this.versionControlService = versionControlService;
        this.localCIContainerService = localCIContainerService;
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
        this.participationRepository = participationRepository;
        this.localCIXMLInputFactory = localCIXMLInputFactory;
        this.aeolusTemplateService = aeolusTemplateService;
        this.gitService = gitService;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.programmingLanguageFeatureService = programmingLanguageFeatureService;
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
     * Prepare the paths to the assignment and test repositories, the branch to check out, the volume configuration for the Docker container, and the container configuration,
     * and then call {@link #runScriptAndParseResults(ProgrammingExerciseParticipation, String, String, String, String, Path, Path, Path, Path[], String[], Path, boolean)} to
     * execute the
     * job.
     *
     * @param participation          The participation of the repository for which the build job should be executed.
     * @param commitHash             The commit hash of the commit that should be built. If it is null, the latest commit of the default branch will be built.
     * @param isPushToTestRepository Defines if the build job is triggered by a push to a test repository
     * @param containerName          The name of the Docker container that will be used to run the build job.
     *                                   It needs to be prepared beforehand to stop and remove the container if something goes wrong here.
     * @param dockerImage            The Docker image that will be used to run the build job.
     * @return The build result.
     * @throws LocalCIException If some error occurs while preparing or running the build job.
     */
    public LocalCIBuildResult runBuildJob(ProgrammingExerciseParticipation participation, String commitHash, boolean isPushToTestRepository, String containerName,
            String dockerImage) {
        // Check if participation has been deleted to cancel the job
        try {
            participation = (ProgrammingExerciseParticipation) participationRepository.findByIdElseThrow(participation.getId());
        }
        catch (EntityNotFoundException e) {
            throw new LocalCIException("Participation has been deleted", e);
        }

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
        Path buildScriptPath = localCIContainerService.createBuildScript(participation, containerName);

        // Retrieve the paths to the repositories that the build job needs.
        // This includes the assignment repository (the one to be tested, e.g. the student's repository, or the template repository), and the tests repository which includes
        // the tests to be executed.
        LocalVCRepositoryUri assignmentRepositoryUri;
        LocalVCRepositoryUri testsRepositoryUri;
        LocalVCRepositoryUri[] auxiliaryRepositoriesUris;
        Path[] auxiliaryRepositoriesPaths;
        String[] auxiliaryRepositoryCheckoutDirectories;

        try {
            assignmentRepositoryUri = new LocalVCRepositoryUri(participation.getRepositoryUri(), localVCBaseUrl);
            testsRepositoryUri = new LocalVCRepositoryUri(participation.getProgrammingExercise().getTestRepositoryUri(), localVCBaseUrl);

            if (!auxiliaryRepositories.isEmpty()) {
                auxiliaryRepositoriesUris = new LocalVCRepositoryUri[auxiliaryRepositories.size()];
                auxiliaryRepositoriesPaths = new Path[auxiliaryRepositories.size()];
                auxiliaryRepositoryCheckoutDirectories = new String[auxiliaryRepositories.size()];

                for (int i = 0; i < auxiliaryRepositories.size(); i++) {
                    auxiliaryRepositoriesUris[i] = new LocalVCRepositoryUri(auxiliaryRepositories.get(i).getRepositoryUri(), localVCBaseUrl);
                    auxiliaryRepositoriesPaths[i] = auxiliaryRepositoriesUris[i].getRepoClonePath(repoClonePath).toAbsolutePath();
                    auxiliaryRepositoryCheckoutDirectories[i] = auxiliaryRepositories.get(i).getCheckoutDirectory();
                }
            }
            else {
                auxiliaryRepositoriesPaths = new Path[0];
                auxiliaryRepositoryCheckoutDirectories = new String[0];
            }
        }
        catch (LocalVCInternalException e) {
            throw new LocalCIException("Error while creating LocalVCRepositoryUri", e);
        }

        Path assignmentRepositoryPath = assignmentRepositoryUri.getRepoClonePath(repoClonePath).toAbsolutePath();
        Path testsRepositoryPath = testsRepositoryUri.getRepoClonePath(repoClonePath).toAbsolutePath();
        Path solutionRepositoryPath = null;
        if (participation.getProgrammingExercise().getCheckoutSolutionRepository()) {
            try {
                if (programmingLanguageFeatureService.isPresent()) {
                    ProgrammingLanguageFeature programmingLanguageFeature = programmingLanguageFeatureService.get()
                            .getProgrammingLanguageFeatures(participation.getProgrammingExercise().getProgrammingLanguage());
                    if (programmingLanguageFeature.checkoutSolutionRepositoryAllowed()) {
                        var solutionParticipation = solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(participation.getProgrammingExercise().getId());
                        if (solutionParticipation.isPresent()) {
                            solutionRepositoryPath = new LocalVCRepositoryUri(solutionParticipation.get().getRepositoryUri(), localVCBaseUrl).getRepoClonePath(repoClonePath)
                                    .toAbsolutePath();
                        }
                    }
                }
            }
            catch (Exception e) {
                throw new LocalCIException("Error while creating solution LocalVCRepositoryUri", e);
            }
        }

        String branch;
        try {
            branch = versionControlService.orElseThrow().getOrRetrieveBranchOfParticipation(participation);
        }
        catch (LocalVCInternalException e) {
            throw new LocalCIException("Error while getting branch of participation", e);
        }

        /*
         * If the commit hash is null, this means that the latest commit of the default branch should be built.
         * If this build job is triggered by a push to the test repository, the commit hash reflects changes to the test repository.
         * Thus, we do not checkout the commit hash of the test repository in the assignment repository.
         */
        if (commitHash != null && !isPushToTestRepository) {
            // Clone the assignment repository into a temporary directory with the name of the commit hash and then checkout the commit hash.
            assignmentRepositoryPath = cloneAndCheckoutRepository(participation, commitHash);
        }

        // Create the container from the "ls1tum/artemis-maven-template" image with the local paths to the Git repositories and the shell script bound to it.
        // This does not start the container yet.
        CreateContainerResponse container = localCIContainerService.configureContainer(containerName, dockerImage);

        return runScriptAndParseResults(participation, containerName, container.getId(), branch, commitHash, assignmentRepositoryPath, testsRepositoryPath, solutionRepositoryPath,
                auxiliaryRepositoriesPaths, auxiliaryRepositoryCheckoutDirectories, buildScriptPath, isPushToTestRepository);
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
            Path assignmentRepositoryPath, Path testsRepositoryPath, Path solutionRepositoryPath, Path[] auxiliaryRepositoriesPaths,
            String[] auxiliaryRepositoryCheckoutDirectories, Path buildScriptPath, boolean isPushToTestRepository) {

        ProgrammingLanguage programmingLanguage = participation.getProgrammingExercise().getProgrammingLanguage();

        List<String> resultPaths;

        try {
            resultPaths = getTestResultPaths(participation.getProgrammingExercise());
        }
        catch (IllegalArgumentException e) {
            localCIContainerService.deleteScriptFile(containerName);
            throw new LocalCIException("Error while getting test result paths", e);
        }

        long timeNanoStart = System.nanoTime();

        localCIContainerService.startContainer(containerId);

        log.info("Started container for build job {}", containerName);

        localCIContainerService.populateBuildJobContainer(containerId, assignmentRepositoryPath, testsRepositoryPath, solutionRepositoryPath, auxiliaryRepositoriesPaths,
                auxiliaryRepositoryCheckoutDirectories, buildScriptPath, programmingLanguage);

        List<BuildLogEntry> buildLogEntries = localCIContainerService.runScriptInContainer(containerId);

        log.info("Finished running the build script in container {}", containerName);

        ZonedDateTime buildCompletedDate = ZonedDateTime.now();

        localCIContainerService.moveResultsToSpecifiedDirectory(containerId, resultPaths, LocalCIContainerService.WORKING_DIRECTORY + LocalCIContainerService.RESULTS_DIRECTORY);

        // Wait for copying results to finish
        try {
            Thread.sleep(1000);
        }
        catch (InterruptedException e) {
            throw new LocalCIException("Waiting for copying results got interrupted");
        }

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
            localCIContainerService.deleteScriptFile(containerName);
            return constructFailedBuildResult(branch, assignmentRepoCommitHash, testRepoCommitHash, buildCompletedDate);
        }

        // Get an input stream of the test result files.

        TarArchiveInputStream testResultsTarInputStream;

        try {
            testResultsTarInputStream = localCIContainerService.getArchiveFromContainer(containerId,
                    LocalCIContainerService.WORKING_DIRECTORY + LocalCIContainerService.RESULTS_DIRECTORY);
        }
        catch (NotFoundException e) {
            // If the test results are not found, this means that something went wrong during the build and testing of the submission.
            return constructFailedBuildResult(branch, assignmentRepoCommitHash, testRepoCommitHash, buildCompletedDate);
        }
        finally {
            localCIContainerService.stopContainer(containerName);

            // Delete script file from host system
            localCIContainerService.deleteScriptFile(containerName);

            // Delete cloned repository
            if (commitHash != null && !isPushToTestRepository) {
                deleteCloneRepo(participation, commitHash);
            }
        }

        LocalCIBuildResult buildResult;
        try {
            buildResult = parseTestResults(testResultsTarInputStream, branch, assignmentRepoCommitHash, testRepoCommitHash, buildCompletedDate);
            buildResult.setBuildLogEntries(buildLogEntries);
        }
        catch (IOException | XMLStreamException | IllegalStateException e) {
            throw new LocalCIException("Error while parsing test results", e);
        }

        // Set the build status to "INACTIVE" to indicate that the build is not running anymore.
        localCIBuildPlanService.updateBuildPlanStatus(participation, ContinuousIntegrationService.BuildStatus.INACTIVE);

        log.info("Building and testing submission for repository {} and commit hash {} took {}", participation.getRepositoryUri(), commitHash,
                TimeLogUtil.formatDurationFrom(timeNanoStart));

        return buildResult;
    }

    // --- Helper methods ----

    private List<String> getTestResultPaths(ProgrammingExercise programmingExercise) throws IllegalArgumentException {
        List<String> testResultPaths = new ArrayList<>();
        Windfile windfile = programmingExercise.getWindfile();
        if (windfile == null) {
            windfile = aeolusTemplateService.getDefaultWindfileFor(programmingExercise);
        }
        if (windfile == null) {
            throw new IllegalArgumentException("No windfile found for programming exercise " + programmingExercise.getId());
        }
        for (AeolusResult testResultPath : windfile.getResults()) {
            testResultPaths.add(LocalCIContainerService.WORKING_DIRECTORY + "/testing-dir/" + testResultPath.getPath());
        }
        return testResultPaths;
    }

    private LocalCIBuildResult parseTestResults(TarArchiveInputStream testResultsTarInputStream, String assignmentRepoBranchName, String assignmentRepoCommitHash,
            String testsRepoCommitHash, ZonedDateTime buildCompletedDate) throws IOException, XMLStreamException {

        List<LocalCIBuildResult.LocalCITestJobDTO> failedTests = new ArrayList<>();
        List<LocalCIBuildResult.LocalCITestJobDTO> successfulTests = new ArrayList<>();
        List<StaticCodeAnalysisReportDTO> staticCodeAnalysisReports = new ArrayList<>();

        TarArchiveEntry tarEntry;
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
                // ugly workaround because in swift result files \n\t breaks the parsing
                processTestResultFile(xmlString.replace("\n\t", ""), failedTests, successfulTests);
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
        Document document = XmlFileUtils.readFromString(xmlString);
        document.setDocumentURI(fileName);
        try {
            ParserPolicy parserPolicy = new ParserPolicy();
            ParserStrategy parserStrategy = parserPolicy.configure(document);
            staticCodeAnalysisReports.add(parserStrategy.parse(document));
        }
        catch (UnsupportedToolException e) {
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
        else if (!"skipped".equals(xmlStreamReader.getLocalName())) {
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

    private Path cloneAndCheckoutRepository(ProgrammingExerciseParticipation participation, String commitHash) {
        try {
            // Clone the assignment repository into a temporary directory with the name of the commit hash and then checkout the commit hash.
            Repository repository = gitService.getOrCheckoutRepository(participation.getVcsRepositoryUri(), Paths.get("checked-out-repos", commitHash), false);
            gitService.checkoutRepositoryAtCommit(repository, commitHash);
            return repository.getLocalPath();
        }
        catch (GitAPIException e) {
            throw new LocalCIException("Error while cloning repository", e);
        }
    }

    private void deleteCloneRepo(ProgrammingExerciseParticipation participation, String commitHash) {
        try {
            Repository repository = gitService.getExistingCheckedOutRepositoryByLocalPath(Paths.get("checked-out-repos", commitHash), participation.getVcsRepositoryUri(),
                    defaultBranch);
            if (repository == null) {
                throw new EntityNotFoundException("Repository with commit hash " + commitHash + " not found");
            }
            gitService.deleteLocalRepository(repository);
        }
        catch (EntityNotFoundException e) {
            throw new LocalCIException("Error while checking out repository", e);
        }
        catch (IOException e) {
            throw new RuntimeException("Error while deleting repository", e);
        }
    }
}
