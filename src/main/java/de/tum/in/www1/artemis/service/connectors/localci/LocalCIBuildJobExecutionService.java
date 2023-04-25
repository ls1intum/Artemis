package de.tum.in.www1.artemis.service.connectors.localci;

import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

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
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;

import de.tum.in.www1.artemis.config.localvcci.LocalCIConfiguration;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.exception.localvc.LocalVCInternalException;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildResult;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCRepositoryUrl;
import de.tum.in.www1.artemis.service.connectors.vcs.VersionControlService;
import de.tum.in.www1.artemis.service.programming.ProgrammingMessagingService;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;
import de.tum.in.www1.artemis.web.websocket.programmingSubmission.BuildTriggerWebsocketError;

/**
 * This service is responsible for executing build jobs on the local CI server.
 */
@Service
@Profile("localci")
public class LocalCIBuildJobExecutionService {

    private final Logger log = LoggerFactory.getLogger(LocalCIBuildJobExecutionService.class);

    private final Optional<VersionControlService> versionControlService;

    private final ExecutorService localCIBuildExecutorService;

    private final DockerClient dockerClient;

    private final ProgrammingMessagingService programmingMessagingService;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    /**
     * The Path to the script file located in the resources folder. The script file contains the steps that run the tests on the Docker container.
     * This path is provided as a Bean, because the retrieval is quite costly in the production environment (see {@link LocalCIConfiguration#buildScriptFilePath()}).
     */
    private final Path buildScriptFilePath;

    /**
     * Instead of creating a new XMLInputFactory for every build job, it is created once and provided as a Bean (see {@link LocalCIConfiguration#localCIXMLInputFactory()}).
     */
    private final XMLInputFactory localCIXMLInputFactory;

    @Value("${artemis.version-control.url}")
    private URL localVCBaseUrl;

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private String localVCBasePath;

    @Value("${artemis.continuous-integration.build.images.java.default}")
    String dockerImage;

    @Value("${artemis.continuous-integration.timeout-seconds:120}")
    private int timeoutSeconds;

    @Value("${artemis.continuous-integration.asynchronous:true}")
    private boolean runBuildJobsAsynchronously;

    public LocalCIBuildJobExecutionService(Optional<VersionControlService> versionControlService, ExecutorService localCIBuildExecutorService, DockerClient dockerClient,
            ProgrammingMessagingService programmingMessagingService, TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, Path buildScriptFilePath, XMLInputFactory localCIXMLInputFactory) {
        this.versionControlService = versionControlService;
        this.localCIBuildExecutorService = localCIBuildExecutorService;
        this.dockerClient = dockerClient;
        this.programmingMessagingService = programmingMessagingService;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.buildScriptFilePath = buildScriptFilePath;
        this.localCIXMLInputFactory = localCIXMLInputFactory;
    }

    /**
     * Prepare paths to the assignment and test repositories and the build script and then submit the build job to the executor service.
     *
     * @param participation The participation of the repository for which the build job should be executed.
     * @return A future that will be completed with the build result.
     * @throws LocalCIException If the build job could not be submitted to the executor service.
     */
    public CompletableFuture<LocalCIBuildResult> addBuildJobToQueue(ProgrammingExerciseParticipation participation) {

        ProjectType projectType = participation.getProgrammingExercise().getProjectType();
        if (projectType == null || !projectType.isGradle()) {
            throw new LocalCIException("Project type must be Gradle.");
        }

        // Prepare the Docker container name before submitting the build job to the executor service, so we can remove the container if something goes wrong.
        String containerName = "artemis-local-ci-" + participation.getId() + "-" + ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));

        // Prepare a Callable that will later be called. It contains the actual steps needed to execute the build job.
        Callable<LocalCIBuildResult> buildJob = () -> prepareAndRunBuildJob(participation, containerName);

        // Wrap the buildJob Callable in a BuildJobTimeoutCallable, so that the build job is cancelled if it takes too long.
        BuildJobTimeoutCallable<LocalCIBuildResult> timedBuildJob = new BuildJobTimeoutCallable<>(buildJob, timeoutSeconds);

        /*
         * Submit the build job to the executor service. This runs in a separate thread, so it does not block the main thread.
         * createCompletableFuture() is only used to provide a way to run build jobs synchronously for testing and debugging purposes and depends on the
         * artemis.continuous-integration.asynchronous environment variable.
         * Usually, when using asynchronous build jobs, it will just resolve to "CompletableFuture.supplyAsync".
         */
        CompletableFuture<LocalCIBuildResult> futureResult = createCompletableFuture(() -> {
            try {
                return localCIBuildExecutorService.submit(timedBuildJob).get();
            }
            catch (InterruptedException | ExecutionException | CompletionException e) {
                finishBuildJobExceptionally(participation, containerName, e);
                // Wrap the exception in a CompletionException so that the future is completed exceptionally and the thenAccept block is not run.
                // This CompletionException will not resurface anywhere else as it is thrown in this completable future's separate thread.
                throw new CompletionException(e);
            }
        });

        // Add "_QUEUED" to the build plan id to indicate that the build job is queued.
        updateBuildPlanStatus(participation, ContinuousIntegrationService.BuildStatus.QUEUED);

        return futureResult;
    }

    /**
     * Prepare the paths to the assignment and test repositories and then execute the build job.
     *
     * @param participation The participation of the repository for which the build job should be executed.
     * @param containerName The name of the Docker container that will be used to run the build job.
     * @return The build result.
     * @throws LocalCIException If some error occurs while preparing or running the build job.
     */
    private LocalCIBuildResult prepareAndRunBuildJob(ProgrammingExerciseParticipation participation, String containerName) {
        // Add "_BUILDING" to the build plan id to indicate that the build plan is currently building.
        updateBuildPlanStatus(participation, ContinuousIntegrationService.BuildStatus.BUILDING);

        log.info("Updated build plan status for build job " + containerName);

        // Retrieve the paths to the repositories that the build jobs needs.
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
        HostConfig volumeConfig = createVolumeConfig(assignmentRepositoryPath, testsRepositoryPath);

        // Create the container from the "ls1tum/artemis-maven-template" image with the local paths to the Git repositories and the shell script bound to it. This does not
        // start the container yet.
        CreateContainerResponse container = createContainer(containerName, volumeConfig, branch);

        log.info("Created container for build job " + containerName);

        return runBuildJob(participation, containerName, container.getId(), branch);
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
    private LocalCIBuildResult runBuildJob(ProgrammingExerciseParticipation participation, String containerName, String containerId, String branch) {

        long timeNanoStart = System.nanoTime();

        dockerClient.startContainerCmd(containerId).exec();

        log.info("Started container for build job " + containerName);

        runScriptInContainer(containerId);

        log.info("Ran script in container for build job " + containerName);

        ZonedDateTime buildCompletedDate = ZonedDateTime.now();

        String assignmentRepoCommitHash = "";
        String testsRepoCommitHash = "";

        try {
            assignmentRepoCommitHash = getCommitHashOfBranch(containerId, "assignment-repository", branch);
            testsRepoCommitHash = getCommitHashOfBranch(containerId, "test-repository", branch);
        }
        catch (NotFoundException | IOException e) {
            // Could not read commit hash from .git folder. Stop the container and return a build result that indicates that the build failed (empty list for failed tests and
            // empty list for successful tests).
            stopContainer(containerName);
            return constructFailedBuildResult(branch, assignmentRepoCommitHash, testsRepoCommitHash, buildCompletedDate);
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
            stopContainer(containerName);
            return constructFailedBuildResult(branch, assignmentRepoCommitHash, testsRepoCommitHash, buildCompletedDate);
        }

        log.info("Retrieved test results for build job " + containerName);

        stopContainer(containerName);

        log.info("Stopped container for build job " + containerName);

        LocalCIBuildResult buildResult;
        try {
            buildResult = parseTestResults(testResultsTarInputStream, branch, assignmentRepoCommitHash, testsRepoCommitHash, buildCompletedDate);
        }
        catch (IOException | XMLStreamException | IllegalStateException e) {
            throw new LocalCIException("Error while parsing test results", e);
        }

        log.info("Parsed test results for build job " + containerName);

        // Set the build status to "INACTIVE" to indicate that the build is not running anymore.
        updateBuildPlanStatus(participation, ContinuousIntegrationService.BuildStatus.INACTIVE);

        log.info("Updated build plan status to INACTIVE for build job " + containerName);

        log.info("Building and testing submission for repository {} took {}", participation.getRepositoryUrl(), TimeLogUtil.formatDurationFrom(timeNanoStart));

        return buildResult;
    }

    /**
     * Create an asynchronous or a synchronous CompletableFuture depending on the runBuildJobsAsynchronously flag.
     *
     * @param supplier the supplier of the Future, i.e. the function that submits the build job
     * @return the CompletableFuture
     */
    private CompletableFuture<LocalCIBuildResult> createCompletableFuture(Supplier<LocalCIBuildResult> supplier) {
        if (runBuildJobsAsynchronously) {
            // Just use the normal supplyAsync.
            return CompletableFuture.supplyAsync(supplier);
        }
        else {
            // Use a synchronous CompletableFuture, e.g. in the test environment.
            // Otherwise, tests will not wait for the CompletableFuture to complete before asserting on the database.
            CompletableFuture<LocalCIBuildResult> future = new CompletableFuture<>();
            try {
                LocalCIBuildResult result = supplier.get();
                future.complete(result);
            }
            catch (Exception e) {
                future.completeExceptionally(e);
            }
            return future;
        }
    }

    /**
     * Finish the build job if an exception occurred while building and testing the repository.
     *
     * @param participation The participation of the repository for which the build job was executed.
     * @param containerName The name of the Docker container that was used to execute the build job.
     * @param exception     The exception that occurred while building and testing the repository.
     */
    private void finishBuildJobExceptionally(ProgrammingExerciseParticipation participation, String containerName, Exception exception) {
        log.error("Error while building and testing repository " + participation.getRepositoryUrl(), exception);

        // Set the build status to "INACTIVE" to indicate that the build is not running anymore.
        updateBuildPlanStatus(participation, ContinuousIntegrationService.BuildStatus.INACTIVE);

        // Notify the user, that the build job produced an exception.
        BuildTriggerWebsocketError error = new BuildTriggerWebsocketError(exception.getMessage(), participation.getId());
        // This cast to Participation is safe as the participation is either a ProgrammingExerciseStudentParticipation, a TemplateProgrammingExerciseParticipation, or a
        // SolutionProgrammingExerciseParticipation, which all extend Participation.
        programmingMessagingService.notifyUserAboutSubmissionError((Participation) participation, error);

        stopContainer(containerName);
    }

    /**
     * Wrapper for the buildJob Callable that adds a timeout when the build job is called.
     *
     * @param buildJobCallable The build job that should be called.
     * @param timeoutSeconds   The number of seconds after which the build job is cancelled.
     */
    private record BuildJobTimeoutCallable<LocalCIBuildResult> (Callable<LocalCIBuildResult> buildJobCallable, long timeoutSeconds) implements Callable<LocalCIBuildResult> {

        /**
         * Calls the buildJobCallable and waits for the result or for the timeout to pass.
         *
         * @return the LocalCIBuildResult
         * @throws ExecutionException   if there was an error when calling the buildJobCallable or during the execution of the buildJobCallable, i.e. a LocalCIException.
         * @throws InterruptedException if the thread was interrupted while waiting for the buildJobCallable to finish, e.g. if the ExecutorService was terminated from somewhere
         *                                  else.
         * @throws TimeoutException     if the timeout passed before the buildJobCallable finished.
         */
        @Override
        public LocalCIBuildResult call() throws ExecutionException, InterruptedException, TimeoutException {
            Future<LocalCIBuildResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return buildJobCallable.call();
                }
                catch (Exception e) {
                    // Something went wrong while trying to call the build job.
                    // The exception is stored in the Future and will resurface as an ExecutionException when running "future.get()" below.
                    throw new RuntimeException(e);
                }
            });

            try {
                // When the build job is called, wait for the result or for the timeout to pass.
                return future.get(timeoutSeconds, TimeUnit.SECONDS);
            }
            catch (ExecutionException | InterruptedException | TimeoutException e) {
                // Cancel the future if it is not completed or cancelled yet.
                future.cancel(true);
                // This exception will resurface in the catch block of "localCIBuildExecutorService.submit(timedBuildJob).get()" where the container is stopped and the user is
                // notified.
                throw e;
            }
        }
    }

    private String getCommitHashOfBranch(String containerId, String repositoryName, String branchName) throws IOException {
        // Get an input stream of the file in .git folder of the repository that contains the current commit hash of the branch.
        TarArchiveInputStream repositoryTarInputStream = new TarArchiveInputStream(
                dockerClient.copyArchiveFromContainerCmd(containerId, "/repositories/" + repositoryName + "/.git/refs/heads/" + branchName).exec());
        repositoryTarInputStream.getNextTarEntry();
        String commitHash = IOUtils.toString(repositoryTarInputStream, StandardCharsets.UTF_8).replace("\n", "");
        repositoryTarInputStream.close();
        return commitHash;
    }

    private void runScriptInContainer(String containerId) {
        // The "sh script.sh" command specified here is run inside the container as an additional process. This command runs in the background, independent of the container's
        // main process. The execution command can run concurrently with the main process. This setup with the ExecCreateCmdResponse gives us the ability to wait in code until the
        // command has finished before trying to extract the results.
        ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId).withAttachStdout(true).withAttachStderr(true).withCmd("sh", "script.sh").exec();

        log.info("Created CMD for build job " + containerId);

        // Start the command and wait for it to complete.
        final CountDownLatch latch = new CountDownLatch(1);
        dockerClient.execStartCmd(execCreateCmdResponse.getId()).exec(new ResultCallback.Adapter<>() {

            @Override
            public void onComplete() {
                latch.countDown();
                log.info("Completed waiting for CMD build job " + containerId);
            }
        });

        try {
            log.info("Started CMD for build job " + containerId);
            // Block until the latch reaches 0 or until the thread is interrupted.
            latch.await();
        }
        catch (InterruptedException e) {
            throw new LocalCIException("Interrupted while waiting for command to complete", e);
        }
    }

    private CreateContainerResponse createContainer(String containerName, HostConfig volumeConfig, String branch) {
        return dockerClient.createContainerCmd(dockerImage).withName(containerName).withHostConfig(volumeConfig)
                .withEnv("ARTEMIS_BUILD_TOOL=gradle", "ARTEMIS_DEFAULT_BRANCH=" + branch)
                // Command to run when the container starts. This is the command that will be executed in the container's main process, which runs in the foreground and blocks the
                // container from exiting until it finishes.
                // It waits until the script that is running the tests (see below execCreateCmdResponse) is completed, and until the result files are extracted which is indicated
                // by the creation of a file "stop_container.txt" in the container's root directory.
                .withCmd("sh", "-c", "while [ ! -f /stop_container.txt ]; do sleep 0.5; done")
                // .withCmd("tail", "-f", "/dev/null") // Activate for debugging purposes instead of the above command to get a running container that you can peek into using
                // "docker exec -it <container-id> /bin/bash".
                .exec();
    }

    private HostConfig createVolumeConfig(Path assignmentRepositoryPath, Path testRepositoryPath) {
        // Configure the volumes of the container such that it can access the assignment repository, the test repository, and the build script.
        return HostConfig.newHostConfig().withAutoRemove(true) // Automatically remove the container when it exits.
                .withBinds(new Bind(assignmentRepositoryPath.toString(), new Volume("/assignment-repository")),
                        new Bind(testRepositoryPath.toString(), new Volume("/test-repository")), new Bind(buildScriptFilePath.toString(), new Volume("/script.sh")));
    }

    /**
     * Stops the container with the given id by creating a file "stop_container.txt" in its root directory.
     * The container was created in such a way that it waits for this file to appear and then stops running, causing it to be removed at the same time.
     *
     * @param containerName The name of the container to stop. Cannot use the container id, because this method might have to be called from the main thread (not the thread started
     *                          for the build job) where the container ID is not available.
     */
    private void stopContainer(String containerName) {
        // List all containers, including the non-running ones.
        List<Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();

        // Check if there's a container with the given name.
        Optional<Container> containerOptional = containers.stream().filter(container -> container.getNames()[0].equals("/" + containerName)).findFirst();
        if (containerOptional.isEmpty()) {
            return;
        }

        // Check if the container is running. Return if it's not.
        boolean isContainerRunning = containerOptional.get().getState().equals("running");
        if (!isContainerRunning) {
            return;
        }

        // Get the container ID.
        String containerId = containerOptional.get().getId();

        // Create a file "stop_container.txt" in the root directory of the container to indicate that the test results have been extracted or that the container should be stopped
        // for some other reason.
        // The container's main process is waiting for this file to appear and then stops the main process, thus stopping and removing the container.
        ExecCreateCmdResponse createStopContainerFileCmdResponse = dockerClient.execCreateCmd(containerId).withCmd("touch", "stop_container.txt").exec();
        dockerClient.execStartCmd(createStopContainerFileCmdResponse.getId()).exec(new ResultCallback.Adapter<>());
    }

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

    private LocalCIBuildResult constructFailedBuildResult(String assignmentRepoBranchName, String assignmentRepoCommitHash, String testsRepoCommitHash,
            ZonedDateTime buildRunDate) {
        return constructBuildResult(List.of(), List.of(), assignmentRepoBranchName, assignmentRepoCommitHash, testsRepoCommitHash, false, buildRunDate);
    }

    private LocalCIBuildResult constructBuildResult(List<LocalCIBuildResult.LocalCITestJobDTO> failedTests, List<LocalCIBuildResult.LocalCITestJobDTO> successfulTests,
            String assignmentRepoBranchName, String assignmentRepoCommitHash, String testsRepoCommitHash, boolean isBuildSuccessful, ZonedDateTime buildRunDate) {
        LocalCIBuildResult.LocalCIJobDTO job = new LocalCIBuildResult.LocalCIJobDTO(failedTests, successfulTests);

        return new LocalCIBuildResult(assignmentRepoBranchName, assignmentRepoCommitHash, testsRepoCommitHash, isBuildSuccessful, buildRunDate, List.of(job));
    }

    /**
     * Updates the build plan status of the given participation to the given status.
     * This method attaches the new status to the build plan id and saves it in the database. This way no new database table must be added just for this purpose.
     * Inactive build plan id: "TESTCOURSE1TESTEX2-USER1"
     * Queued build plan id: "TESTCOURSE1TESTEX2-USER1_QUEUED"
     * Building build plan id: "TESTCOURSE1TESTEX2-USER1_BUILDING"
     *
     * @param participation  the participation for which the build plan status should be updated.
     * @param newBuildStatus the new build plan status.
     * @throws LocalCIException if the build plan id is null.
     */
    private void updateBuildPlanStatus(ProgrammingExerciseParticipation participation, ContinuousIntegrationService.BuildStatus newBuildStatus) {
        String buildPlanId = participation.getBuildPlanId();
        if (buildPlanId == null) {
            throw new LocalCIException("Build plan id is null.");
        }
        buildPlanId = buildPlanId.replace("_" + ContinuousIntegrationService.BuildStatus.QUEUED.name(), "").replace("_" + ContinuousIntegrationService.BuildStatus.BUILDING.name(),
                "");

        if (!newBuildStatus.equals(ContinuousIntegrationService.BuildStatus.INACTIVE)) {
            buildPlanId += "_" + newBuildStatus.name();
        }

        participation.setBuildPlanId(buildPlanId);

        if (participation instanceof TemplateProgrammingExerciseParticipation templateParticipation) {
            templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        }
        else if (participation instanceof SolutionProgrammingExerciseParticipation solutionParticipation) {
            solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);
        }
        else {
            programmingExerciseStudentParticipationRepository.save((ProgrammingExerciseStudentParticipation) participation);
        }
    }
}
