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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.HostConfig;

import de.tum.in.www1.artemis.config.localvcci.LocalCIConfiguration;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.exception.localvc.LocalVCInternalException;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildResult;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCRepositoryUrl;
import de.tum.in.www1.artemis.service.connectors.vcs.VersionControlService;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;

/**
 * This service contains the logic to execute a build job for a programming exercise participation in the local CI system.
 * The {@link #runBuildJob(ProgrammingExerciseParticipation, String)} method is wrapped into a Callable by the {@link LocalCIBuildJobManagementService} and submitted to the
 * executor service.
 */
@Service
@Profile("localci")
public class LocalCIBuildJobExecutionService {

    private final Logger log = LoggerFactory.getLogger(LocalCIBuildJobExecutionService.class);

    private final LocalCIBuildPlanService localCIBuildPlanService;

    private final Optional<VersionControlService> versionControlService;

    private final DockerClient dockerClient;

    private final LocalCIContainerService localCIContainerService;

    /**
     * Instead of creating a new XMLInputFactory for every build job, it is created once and provided as a Bean (see {@link LocalCIConfiguration#localCIXMLInputFactory()}).
     */
    private final XMLInputFactory localCIXMLInputFactory;

    @Value("${artemis.version-control.url}")
    private URL localVCBaseUrl;

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private String localVCBasePath;

    public LocalCIBuildJobExecutionService(LocalCIBuildPlanService localCIBuildPlanService, Optional<VersionControlService> versionControlService, DockerClient dockerClient,
            LocalCIContainerService localCIContainerService, XMLInputFactory localCIXMLInputFactory) {
        this.localCIBuildPlanService = localCIBuildPlanService;
        this.versionControlService = versionControlService;
        this.dockerClient = dockerClient;
        this.localCIContainerService = localCIContainerService;
        this.localCIXMLInputFactory = localCIXMLInputFactory;
    }

    public enum LocalCIBuildJobRepositoryType {

        ASSIGNMENT("assignment"), TEST("test");

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
     * and then call {@link #runScriptAndParseResults(ProgrammingExerciseParticipation, String, String, String)} to execute the job.
     *
     * @param participation The participation of the repository for which the build job should be executed.
     * @param containerName The name of the Docker container that will be used to run the build job.
     *                          It needs to be prepared beforehand to stop and remove the container if something goes wrong here.
     * @return The build result.
     * @throws LocalCIException If some error occurs while preparing or running the build job.
     */
    public LocalCIBuildResult runBuildJob(ProgrammingExerciseParticipation participation, String containerName) {
        // Update the build plan status to "BUILDING".
        localCIBuildPlanService.updateBuildPlanStatus(participation, ContinuousIntegrationService.BuildStatus.BUILDING);

        log.info("Updated build plan status for build job " + containerName);

        // Retrieve the paths to the repositories that the build job needs.
        // This includes the assignment repository (the one to be tested, e.g. the student's repository, or the template repository), and the tests repository which includes
        // the tests to be executed.
        LocalVCRepositoryUrl assignmentRepositoryUrl;
        LocalVCRepositoryUrl testsRepositoryUrl;
        try {
            assignmentRepositoryUrl = new LocalVCRepositoryUrl(participation.getRepositoryUrl(), localVCBaseUrl);
            testsRepositoryUrl = new LocalVCRepositoryUrl(participation.getProgrammingExercise().getTestRepositoryUrl(), localVCBaseUrl);
        }
        catch (LocalVCInternalException e) {
            throw new LocalCIException("Error while creating LocalVCRepositoryUrl", e);
        }

        log.info("Retrieved repository URLs for build job " + containerName);

        Path assignmentRepositoryPath = assignmentRepositoryUrl.getLocalRepositoryPath(localVCBasePath).toAbsolutePath();
        Path testsRepositoryPath = testsRepositoryUrl.getLocalRepositoryPath(localVCBasePath).toAbsolutePath();

        String branch;
        try {
            branch = versionControlService.orElseThrow().getOrRetrieveBranchOfParticipation(participation);
        }
        catch (LocalVCInternalException e) {
            throw new LocalCIException("Error while getting branch of participation", e);
        }

        log.info("Retrieved repository paths and branch for build job " + containerName);

        // Create the volume configuration for the container. The assignment repository, the tests repository, and the build script are bound into the container to be used by
        // the build job.
        HostConfig volumeConfig = localCIContainerService.createVolumeConfig(assignmentRepositoryPath, testsRepositoryPath);

        // Create the container from the "ls1tum/artemis-maven-template" image with the local paths to the Git repositories and the shell script bound to it. This does not
        // start the container yet.
        CreateContainerResponse container = localCIContainerService.configureContainer(containerName, volumeConfig, branch);

        log.info("Created container for build job " + containerName);

        return runScriptAndParseResults(participation, containerName, container.getId(), branch);
    }

    /**
     * Runs the build job. This includes creating and starting a Docker container, executing the build script, and processing the build result.
     *
     * @param participation The participation for which the build job should be run.
     * @param containerName The name of the container that should be used for the build job. This is used to remove the container and is also accessible from outside build job
     *                          running in its own thread.
     * @param containerId   The id of the container that should be used for the build job.
     * @param branch        The branch that should be built.
     * @return The build result.
     * @throws LocalCIException if something went wrong while running the build job.
     */
    private LocalCIBuildResult runScriptAndParseResults(ProgrammingExerciseParticipation participation, String containerName, String containerId, String branch) {

        long timeNanoStart = System.nanoTime();

        localCIContainerService.startContainer(containerId);

        log.info("Started container for build job " + containerName);

        localCIContainerService.runScriptInContainer(containerId);

        log.info("Ran script in container for build job " + containerName);

        ZonedDateTime buildCompletedDate = ZonedDateTime.now();

        String assignmentRepoCommitHash = "";
        String testRepoCommitHash = "";

        try {
            assignmentRepoCommitHash = localCIContainerService.getCommitHashOfBranch(containerId, LocalCIBuildJobRepositoryType.ASSIGNMENT, branch);
            testRepoCommitHash = localCIContainerService.getCommitHashOfBranch(containerId, LocalCIBuildJobRepositoryType.TEST, branch);
        }
        catch (NotFoundException | IOException e) {
            // Could not read commit hash from .git folder. Stop the container and return a build result that indicates that the build failed (empty list for failed tests and
            // empty list for successful tests).
            localCIContainerService.stopContainer(containerName);
            return constructFailedBuildResult(branch, assignmentRepoCommitHash, testRepoCommitHash, buildCompletedDate);
        }

        log.info("Retrieved commit hashes for build job " + containerName);

        // When Gradle is used as the build tool, the test results are located in /repositories/test-repository/build/test-results/test/TEST-*.xml.
        // When Maven is used as the build tool, the test results are located in /repositories/test-repository/target/surefire-reports/TEST-*.xml.
        String testResultsPath = "/repositories/test-repository/build/test-results/test";

        // Get an input stream of the test result files.
        TarArchiveInputStream testResultsTarInputStream;
        try {
            testResultsTarInputStream = new TarArchiveInputStream(dockerClient.copyArchiveFromContainerCmd(containerId, testResultsPath).exec());
        }
        catch (NotFoundException e) {
            // If the test results are not found, this means that something went wrong during the build and testing of the submission.
            // Stop the container and return a build results that indicates that the build failed.
            localCIContainerService.stopContainer(containerName);
            return constructFailedBuildResult(branch, assignmentRepoCommitHash, testRepoCommitHash, buildCompletedDate);
        }

        log.info("Retrieved test results for build job " + containerName);

        localCIContainerService.stopContainer(containerName);

        log.info("Stopped container for build job " + containerName);

        LocalCIBuildResult buildResult;
        try {
            buildResult = parseTestResults(testResultsTarInputStream, branch, assignmentRepoCommitHash, testRepoCommitHash, buildCompletedDate);
        }
        catch (IOException | XMLStreamException | IllegalStateException e) {
            throw new LocalCIException("Error while parsing test results", e);
        }

        log.info("Parsed test results for build job " + containerName);

        // Set the build status to "INACTIVE" to indicate that the build is not running anymore.
        localCIBuildPlanService.updateBuildPlanStatus(participation, ContinuousIntegrationService.BuildStatus.INACTIVE);

        log.info("Building and testing submission for repository {} took {}", participation.getRepositoryUrl(), TimeLogUtil.formatDurationFrom(timeNanoStart));

        return buildResult;
    }

    // --- Helper methods ----

    private LocalCIBuildResult parseTestResults(TarArchiveInputStream testResultsTarInputStream, String assignmentRepoBranchName, String assignmentRepoCommitHash,
            String testsRepoCommitHash, ZonedDateTime buildCompletedDate) throws IOException, XMLStreamException {

        boolean isBuildSuccessful = true;

        List<LocalCIBuildResult.LocalCITestJobDTO> failedTests = new ArrayList<>();
        List<LocalCIBuildResult.LocalCITestJobDTO> successfulTests = new ArrayList<>();

        TarArchiveEntry tarEntry;
        while ((tarEntry = testResultsTarInputStream.getNextTarEntry()) != null) {

            if (tarEntry.isDirectory() || !tarEntry.getName().endsWith(".xml") || !tarEntry.getName().startsWith("test/TEST-")) {
                continue;
            }

            // Read the contents of the tar entry as a string.
            String xmlString = IOUtils.toString(testResultsTarInputStream, StandardCharsets.UTF_8);

            // Create an XML stream reader for the string.
            XMLStreamReader xmlStreamReader = localCIXMLInputFactory.createXMLStreamReader(new StringReader(xmlString));

            // Move to the first start element.
            while (xmlStreamReader.hasNext() && !xmlStreamReader.isStartElement()) {
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

                // Extract the name attribute from the "testcase" node.
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

                    // If there is at least one test case with a failure node, the build is not successful.
                    isBuildSuccessful = false;
                }
                else {
                    // Add the successful test to the list of successful tests.
                    successfulTests.add(new LocalCIBuildResult.LocalCITestJobDTO(name, List.of()));
                }
            }
            // Close the XML stream reader.
            xmlStreamReader.close();
        }

        return constructBuildResult(failedTests, successfulTests, assignmentRepoBranchName, assignmentRepoCommitHash, testsRepoCommitHash, isBuildSuccessful, buildCompletedDate);
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
        return constructBuildResult(List.of(), List.of(), assignmentRepoBranchName, assignmentRepoCommitHash, testsRepoCommitHash, false, buildRunDate);
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
            String assignmentRepoBranchName, String assignmentRepoCommitHash, String testsRepoCommitHash, boolean isBuildSuccessful, ZonedDateTime buildRunDate) {
        LocalCIBuildResult.LocalCIJobDTO job = new LocalCIBuildResult.LocalCIJobDTO(failedTests, successfulTests);

        return new LocalCIBuildResult(assignmentRepoBranchName, assignmentRepoCommitHash, testsRepoCommitHash, isBuildSuccessful, buildRunDate, List.of(job));
    }
}
