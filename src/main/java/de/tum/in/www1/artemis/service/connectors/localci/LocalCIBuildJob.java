package de.tum.in.www1.artemis.service.connectors.localci;

import static de.tum.in.www1.artemis.config.Constants.ASSIGNMENT_REPO_NAME;
import static de.tum.in.www1.artemis.config.Constants.TEST_REPO_NAME;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.connectors.LtiNewResultService;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseGradingService;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;

public class LocalCIBuildJob implements Runnable {

    private final Logger log = LoggerFactory.getLogger(LocalCIBuildJob.class);

    private final Path assignmentRepositoryPath;

    private final Path testRepositoryPath;

    private final Path scriptPath;

    private final String dockerConnectionUri;

    private final ProgrammingExerciseGradingService programmingExerciseGradingService;

    private final WebsocketMessagingService messagingService;

    private final LtiNewResultService ltiNewResultService;

    private final ProgrammingExerciseParticipation participation;

    private static int id = 0;

    public LocalCIBuildJob(ProgrammingExerciseParticipation participation, Path assignmentRepositoryPath, Path testRepositoryPath, Path scriptPath,
            ProgrammingExerciseGradingService programmingExerciseGradingService, WebsocketMessagingService messagingService, LtiNewResultService ltiNewResultService) {
        this.participation = participation;
        this.assignmentRepositoryPath = assignmentRepositoryPath;
        this.testRepositoryPath = testRepositoryPath;
        this.scriptPath = scriptPath;
        id++;

        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            this.dockerConnectionUri = "tcp://localhost:2375";
        }
        else {
            this.dockerConnectionUri = "unix:///var/run/docker.sock";
        }

        this.programmingExerciseGradingService = programmingExerciseGradingService;
        this.messagingService = messagingService;
        this.ltiNewResultService = ltiNewResultService;
    }

    /**
     * Runs the build job. This includes creating and starting a Docker container, executing the build script, and processing the build result.
     */
    @Override
    public void run() {
        long timeNanoStart = System.nanoTime();

        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerHost(dockerConnectionUri).build();
        DockerClient dockerClient = DockerClientBuilder.getInstance(config).build();

        HostConfig hostConfig = HostConfig.newHostConfig().withAutoRemove(true) // Automatically remove the container when it exits.
                .withBinds(new Bind(assignmentRepositoryPath.toString(), new Volume("/assignment-repository")),
                        new Bind(testRepositoryPath.toString(), new Volume("/test-repository")), new Bind(scriptPath.toString(), new Volume("/script.sh")));

        ProjectType projectType = participation.getProgrammingExercise().getProjectType();
        if (projectType == null || (!projectType.isMaven() && !projectType.isGradle())) {
            throw new LocalCIException("Project type must be either Maven or Gradle.");
        }

        // Create the container from the "ls1tum/artemis-maven-template:java17-13" image with the local paths to the Git repositories and the shell script bound to it.
        CreateContainerResponse container = dockerClient.createContainerCmd("ls1tum/artemis-maven-template:java17-13").withHostConfig(hostConfig)
                // TODO: Replace with default branch for participation.
                .withEnv("ARTEMIS_BUILD_TOOL=" + (projectType.isMaven() ? "maven" : "gradle"), "ARTEMIS_DEFAULT_BRANCH=main")
                // Command to run when the container starts. This is the command that will be executed in the container's main process, which runs in the foreground and blocks the
                // container from exiting until it finishes.
                // It waits until the script that is running the tests (see below execCreateCmdResponse) is completed, and until the result files are extracted which is indicated
                // by
                // the creation of a file "results_extracted.txt" in the container's root directory.
                .withCmd("sh", "-c", "while [ ! -f /results_extracted.txt ]; do sleep 0.5; done")
                // .withCmd("tail", "-f", "/dev/null") // Activate for debugging purposes instead of the above command to get a running container that you can peek into using
                // "docker exec -it <container-id> /bin/bash".
                .exec();

        ZonedDateTime buildStartedDate = ZonedDateTime.now();

        // Start the container.
        dockerClient.startContainerCmd(container.getId()).exec();

        // The "sh script.sh" command specified here is run inside the container as an additional process. This command runs in the background, independent of the container's
        // main process. The execution command can run concurrently with the main process.
        ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(container.getId()).withAttachStdout(true).withAttachStderr(true).withCmd("sh", "script.sh").exec();

        // Start the command and wait for it to complete.
        final CountDownLatch latch = new CountDownLatch(1);

        dockerClient.execStartCmd(execCreateCmdResponse.getId()).exec(new ResultCallback.Adapter<>() {

            @Override
            public void onComplete() {
                latch.countDown();
            }
        });

        try {
            // Block until the latch reaches 0 or until the thread is interrupted.
            latch.await();
        }
        catch (InterruptedException e) {
            throw new IllegalStateException("Interrupted while waiting for command to complete", e);
        }

        ZonedDateTime buildCompletedDate = ZonedDateTime.now();

        LocalCIBuildResultNotificationDTO.LocalCIVCSDTO assignmentVC;
        LocalCIBuildResultNotificationDTO.LocalCIVCSDTO testVC;

        try {
            // Get an input stream of the file in .git folder of the assignment repository and the test repository that contains the current commit hash of branch main.
            TarArchiveInputStream assignmentRepoTarInputStream = new TarArchiveInputStream(
                    dockerClient.copyArchiveFromContainerCmd(container.getId(), "/repositories/assignment-repository/.git/refs/heads/main").exec());
            assignmentRepoTarInputStream.getNextTarEntry();
            String assignmentRepoCommitHash = IOUtils.toString(assignmentRepoTarInputStream, StandardCharsets.UTF_8).replace("\n", "");
            assignmentRepoTarInputStream.close();

            TarArchiveInputStream testRepoTarInputStream = new TarArchiveInputStream(
                    dockerClient.copyArchiveFromContainerCmd(container.getId(), "/repositories/test-repository/.git/refs/heads/main").exec());
            testRepoTarInputStream.getNextTarEntry();
            String testRepoCommitHash = IOUtils.toString(testRepoTarInputStream, StandardCharsets.UTF_8).replace("\n", "");
            testRepoTarInputStream.close();

            // TODO: Take default branch name from the participation.
            assignmentVC = new LocalCIBuildResultNotificationDTO.LocalCIVCSDTO(assignmentRepoCommitHash, ASSIGNMENT_REPO_NAME, "main", List.of());
            testVC = new LocalCIBuildResultNotificationDTO.LocalCIVCSDTO(testRepoCommitHash, TEST_REPO_NAME, "main", List.of());
        }
        catch (IOException e) {
            throw new LocalCIException("Could not read commit hash from .git folder", e);
        }

        // When Gradle is used as the build tool, the test results are located in /repositories/test-repository/build/test-resuls/test/TEST-*.xml.
        // When Maven is used as the build tool, the test results are located in /repositories/test-repository/target/surefire-reports/TEST-*.xml.
        String testResultsPath;
        if (projectType.isGradle()) {
            testResultsPath = "/repositories/test-repository/build/test-results/test";
        }
        else if (projectType.isMaven()) {
            testResultsPath = "/repositories/test-repository/target/surefire-reports";
        }
        else {
            throw new LocalCIException("Unknown build tool: " + projectType);
        }

        // Get an input stream of the test result files.
        TarArchiveInputStream testResultsTarInputStream = new TarArchiveInputStream(dockerClient.copyArchiveFromContainerCmd(container.getId(), testResultsPath).exec());

        // Create a file "results_extracted.txt" in the root directory of the container to indicate that the test results have been extracted. The container's main process is
        // waiting for this file to appear and then stops the main process, thus stopping and removing the container.
        ExecCreateCmdResponse createResultsExtractedFileCmdResponse = dockerClient.execCreateCmd(container.getId()).withCmd("touch", "results_extracted.txt").exec();
        dockerClient.execStartCmd(createResultsExtractedFileCmdResponse.getId()).exec(new ResultCallback.Adapter<>());

        LocalCIBuildResultNotificationDTO buildResult;
        try {
            buildResult = parseTestResults(testResultsTarInputStream, projectType, buildStartedDate, buildCompletedDate, assignmentVC, testVC);
        }
        catch (IOException | XMLStreamException e) {
            throw new LocalCIException("Error while parsing test results", e);
        }

        processResult(buildResult);

        log.info("Building and testing submission for repository {} took {}", participation.getRepositoryUrl(), TimeLogUtil.formatDurationFrom(timeNanoStart));
    }

    private LocalCIBuildResultNotificationDTO parseTestResults(TarArchiveInputStream testResultsTarInputStream, ProjectType projectType, ZonedDateTime buildStartedDate,
            ZonedDateTime buildCompletedDate, LocalCIBuildResultNotificationDTO.LocalCIVCSDTO assignmentVC, LocalCIBuildResultNotificationDTO.LocalCIVCSDTO testVC)
            throws IOException, XMLStreamException {
        // List<String> timestamps = new ArrayList<>();
        boolean isBuildSuccessful = true;

        List<LocalCIBuildResultNotificationDTO.LocalCITestJobDTO> failedTests = new ArrayList<>();
        List<LocalCIBuildResultNotificationDTO.LocalCITestJobDTO> successfulTests = new ArrayList<>();

        XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
        TarArchiveEntry tarEntry;
        while ((tarEntry = testResultsTarInputStream.getNextTarEntry()) != null) {
            if (!tarEntry.isDirectory() && tarEntry.getName().startsWith(projectType.isGradle() ? "test" : "surefire-reports" + "/TEST-") && tarEntry.getName().endsWith(".xml")) {
                // Read the contents of the tar entry as a string.
                String xmlString = IOUtils.toString(testResultsTarInputStream, StandardCharsets.UTF_8);

                // Create an XML stream reader for the string.
                XMLStreamReader xmlStreamReader = xmlInputFactory.createXMLStreamReader(new StringReader(xmlString));

                // Move to the first start element.
                while (xmlStreamReader.hasNext() && !xmlStreamReader.isStartElement()) {
                    xmlStreamReader.next();
                }

                // Check if the start element is the "testsuite" node.
                if (!xmlStreamReader.getLocalName().equals("testsuite")) {
                    throw new IllegalStateException("Expected testsuite element, but got " + xmlStreamReader.getLocalName());
                }

                // Extract the timestamp attribute from the "testsuite" node.
                // TODO: Extract timestamp for maven (if even necessary).
                // String timestamp = xmlStreamReader.getAttributeValue(null, "timestamp");
                // timestamps.add(timestamp);

                // Go through all testcase nodes.
                while (xmlStreamReader.hasNext()) {
                    xmlStreamReader.next();

                    if (xmlStreamReader.isStartElement() && xmlStreamReader.getLocalName().equals("testcase")) {
                        // Extract the name attribute from the "testcase" node.
                        String name = xmlStreamReader.getAttributeValue(null, "name");

                        String methodName = ""; // TODO
                        String className = ""; // TODO

                        // Check if there is a failure node inside the testcase node.
                        // Call next() until there is an end element (no failure node exists inside the testcase node) or a start element (failure node exists inside the
                        // testcase node).
                        xmlStreamReader.next();
                        while (!(xmlStreamReader.isEndElement() || xmlStreamReader.isStartElement())) {
                            xmlStreamReader.next();
                        }
                        if (xmlStreamReader.isStartElement() && xmlStreamReader.getLocalName().equals("failure")) {
                            // Extract the message attribute from the "failure" node.
                            // TODO: Extract message for maven.
                            String error = xmlStreamReader.getAttributeValue(null, "message");

                            // Add the failed test to the list of failed tests.
                            failedTests.add(new LocalCIBuildResultNotificationDTO.LocalCITestJobDTO(name, methodName, className, error != null ? List.of(error) : List.of()));

                            // If there is at least one test case with a failure node, the build is not successful.
                            isBuildSuccessful = false;
                        }
                        else {
                            // Add the successful test to the list of successful tests.
                            successfulTests.add(new LocalCIBuildResultNotificationDTO.LocalCITestJobDTO(name, methodName, className, List.of()));
                        }
                    }
                }
                // Close the XML stream reader.
                xmlStreamReader.close();
            }
        }

        LocalCIBuildResultNotificationDTO.LocalCIJobDTO job = new LocalCIBuildResultNotificationDTO.LocalCIJobDTO(id, failedTests, successfulTests, List.of(), List.of(),
                List.of());

        LocalCIBuildResultNotificationDTO.LocalCITestSummaryDTO testSummary = new LocalCIBuildResultNotificationDTO.LocalCITestSummaryDTO(
                (int) ChronoUnit.SECONDS.between(buildStartedDate, buildCompletedDate), 0, failedTests.size(), 0, 0, successfulTests.size(), "some description", 0, 0,
                failedTests.size() + successfulTests.size(), 0);

        LocalCIBuildResultNotificationDTO.LocalCIBuildDTO build = new LocalCIBuildResultNotificationDTO.LocalCIBuildDTO(false, 0, "Some reason for this build", buildCompletedDate,
                isBuildSuccessful, testSummary, List.of(assignmentVC, testVC), List.of(job));

        return new LocalCIBuildResultNotificationDTO(null, null, null, build);
    }

    private void processResult(LocalCIBuildResultNotificationDTO buildResult) {

        // The 'user' is not properly logged into Artemis, this leads to an issue when accessing custom repository methods.
        // Therefore, a mock auth object has to be created.
        SecurityUtils.setAuthorizationObject();
        Optional<Result> optResult = programmingExerciseGradingService.processNewProgrammingExerciseResult(participation, buildResult);

        // Only notify the user about the new result if the result was created successfully.
        if (optResult.isPresent()) {
            Result result = optResult.get();
            log.debug("Send result to client over websocket. Result: {}, Submission: {}, Participation: {}", result, result.getSubmission(), result.getParticipation());
            // notify user via websocket
            messagingService.broadcastNewResult((Participation) participation, result);
            if (participation instanceof StudentParticipation) {
                // do not try to report results for template or solution participations
                ltiNewResultService.onNewResult((ProgrammingExerciseStudentParticipation) participation);
            }
        }
    }
}
