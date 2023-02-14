package de.tum.in.www1.artemis.service.connectors.localci;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.IOUtils;

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

import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildResultNotificationDTO;

public class LocalCIBuildJob {

    private final ProjectType projectType;

    private final Path assignmentRepositoryPath;

    private final Path testRepositoryPath;

    private final Path scriptPath;

    private final DockerClient dockerClient;

    private static int id = 0;

    public LocalCIBuildJob(ProjectType projectType, Path assignmentRepositoryPath, Path testRepositoryPath, Path scriptPath) {
        if (projectType == null || (!projectType.isMaven() && !projectType.isGradle())) {
            throw new IllegalArgumentException("Project type must be either Maven or Gradle.");
        }
        this.projectType = projectType;
        this.assignmentRepositoryPath = assignmentRepositoryPath;
        this.testRepositoryPath = testRepositoryPath;
        this.scriptPath = scriptPath;
        id++;

        String connectionUri;
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            connectionUri = "tcp://localhost:2375";
        }
        else {
            connectionUri = "unix:///var/run/docker.sock";
        }

        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerHost(connectionUri).build();
        dockerClient = DockerClientBuilder.getInstance(config).build();
    }

    public LocalCIBuildResultNotificationDTO runBuildJob() throws IOException {

        HostConfig hostConfig = HostConfig.newHostConfig().withAutoRemove(true) // Automatically remove the container when it exits.
                .withBinds(new Bind(assignmentRepositoryPath.toString(), new Volume("/assignment-repository")),
                        new Bind(testRepositoryPath.toString(), new Volume("/test-repository")), new Bind(scriptPath.toString(), new Volume("/script.sh")));

        // Create the container from the "ls1tum/artemis-maven-template:java17-13" image with the local paths to the Git repositories and the shell script bound to it.
        CreateContainerResponse container = dockerClient.createContainerCmd("ls1tum/artemis-maven-template:java17-13").withHostConfig(hostConfig)
                .withEnv("ARTEMIS_BUILD_TOOL=" + (projectType.isMaven() ? "maven" : "gradle"), "ARTEMIS_DEFAULT_BRANCH=main") // TODO: Replace with default branch for
                                                                                                                              // participation.
                // Command to run when the container starts. This is the command that will be executed in the container's main process, which runs in the foreground and blocks the
                // container from exiting until it finishes.
                // It waits until the script that is running the tests (see below execCreateCmdResponse) is completed, which is running in the background and indicates termination
                // by creating a file "script_completed.txt" in the root directory.
                .withCmd("sh", "-c", "while [ ! -f /script_completed.txt ]; do sleep 0.5; done")
                // .withCmd("tail", "-f", "/dev/null") // Activate for debugging purposes instead of the above command to get a running container that you can peek into using
                // "docker exec -it <container-id> /bin/bash".
                .exec();

        LocalCIBuildResultNotificationDTO buildResult = null;

        try {
            // Start the container.
            dockerClient.startContainerCmd(container.getId()).exec();

            // The "sh script.sh" command specified here is run inside the container as an additional process. This command runs in the background, independent of the container's
            // main process. The execution command can run concurrently with the main process.
            // Creates a script_completed file in the container's root directory when the script finishes. The main process is waiting for this file to appear and then stops the
            // main process, thus stopping the container.
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(container.getId()).withAttachStdout(true).withAttachStderr(true)
                    .withCmd("sh", "-c", "sh script.sh; touch /script_completed.txt").exec();

            // Start the command and wait for it to complete.
            final CountDownLatch latch = new CountDownLatch(1);

            ZonedDateTime buildStartedDate = ZonedDateTime.now();

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
                throw new IllegalStateException("Unknown build tool: " + projectType);
            }

            // Get an input stream of the test result files.
            TarArchiveInputStream tarInputStream = new TarArchiveInputStream(dockerClient.copyArchiveFromContainerCmd(container.getId(), testResultsPath).exec());

            List<LocalCIBuildResultNotificationDTO.LocalCITestJobDTO> failedTests = new ArrayList<>();
            List<LocalCIBuildResultNotificationDTO.LocalCITestJobDTO> successfulTests = new ArrayList<>();
            // List<String> timestamps = new ArrayList<>();
            boolean isBuildSuccessful = true;

            XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
            TarArchiveEntry tarEntry;
            while ((tarEntry = tarInputStream.getNextTarEntry()) != null) {
                if (!tarEntry.isDirectory() && tarEntry.getName().startsWith(projectType.isGradle() ? "test" : "surefire-reports" + "/TEST-")
                        && tarEntry.getName().endsWith(".xml")) {
                    // Read the contents of the tar entry as a string.
                    String xmlString = IOUtils.toString(tarInputStream, StandardCharsets.UTF_8);

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
                                failedTests.add(new LocalCIBuildResultNotificationDTO.LocalCITestJobDTO(name, methodName, className, error != null ? List.of(error) : null));

                                // If there is at least one test case with a failure node, the build is not successful.
                                isBuildSuccessful = false;
                            }
                            else {
                                // Add the successful test to the list of successful tests.
                                successfulTests.add(new LocalCIBuildResultNotificationDTO.LocalCITestJobDTO(name, methodName, className, null));
                            }
                        }
                    }
                    // Close the XML stream reader.
                    xmlStreamReader.close();
                }
            }

            // The commit hash of the assignment repository is located in /repositories/assignment-repository/.git/refs/heads/main.
            // String commitHashAssignmentRepository = null;

            // The commit hash of the test repository is located in /repositories/test-repository/.git/refs/heads/main.
            // String commitHashTestRepository = null;

            LocalCIBuildResultNotificationDTO.LocalCIJobDTO job = new LocalCIBuildResultNotificationDTO.LocalCIJobDTO(id, failedTests, successfulTests, null, null, null);

            LocalCIBuildResultNotificationDTO.LocalCITestSummaryDTO testSummary = new LocalCIBuildResultNotificationDTO.LocalCITestSummaryDTO(
                    (int) ChronoUnit.SECONDS.between(buildStartedDate, buildCompletedDate), 0, failedTests.size(), 0, 0, successfulTests.size(), "some description", 0, 0,
                    failedTests.size() + successfulTests.size(), 0);

            LocalCIBuildResultNotificationDTO.LocalCIBuildDTO build = new LocalCIBuildResultNotificationDTO.LocalCIBuildDTO(false, 0, "Some reason for this build",
                    buildCompletedDate, isBuildSuccessful, testSummary, List.of(), List.of(job));

            buildResult = new LocalCIBuildResultNotificationDTO(null, null, null, build);
        }
        catch (Exception e) {
            // TODO: Handle exception, i.e. notify Artemis that the build failed because of some internal issue.
            System.out.println(e.getMessage());
        }
        return buildResult;
    }
}
