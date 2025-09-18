package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.buildagent.dto.BuildLogDTO;
import de.tum.cit.aet.artemis.core.exception.LocalCIException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.service.RepositoryCheckoutService.RepositoryCheckoutPath;

/**
 * This service contains methods that are used to interact with the Docker containers when executing build jobs in the local CI system.
 * It is closely related to the {@link BuildJobExecutionService} which contains the methods that are used to execute the build jobs.
 */
@Lazy(false)
@Service
@Profile(PROFILE_BUILDAGENT)
public class BuildJobContainerService {

    private static final Logger log = LoggerFactory.getLogger(BuildJobContainerService.class);

    private final BuildAgentConfiguration buildAgentConfiguration;

    private final BuildLogsMap buildLogsMap;

    @Value("${artemis.continuous-integration.proxies.use-system-proxy:false}")
    private boolean useSystemProxy;

    @Value("${artemis.continuous-integration.proxies.default.http-proxy:}")
    private String httpProxy;

    @Value("${artemis.continuous-integration.proxies.default.https-proxy:}")
    private String httpsProxy;

    @Value("${artemis.continuous-integration.proxies.default.no-proxy:}")
    private String noProxy;

    @Value("${artemis.continuous-integration.container-flags-limit.max-cpu-count:0}")
    private int maxCpuCount;

    @Value("${artemis.continuous-integration.container-flags-limit.max-memory:0}")
    private int maxMemory;

    @Value("${artemis.continuous-integration.container-flags-limit.max-memory-swap:0}")
    private int maxMemorySwap;

    public BuildJobContainerService(BuildAgentConfiguration buildAgentConfiguration, BuildLogsMap buildLogsMap) {
        this.buildAgentConfiguration = buildAgentConfiguration;
        this.buildLogsMap = buildLogsMap;
    }

    /**
     * Configure a container with the Docker image, the container name, optional proxy config variables, and set the command that runs when the container starts.
     *
     * @param containerName   the name of the container to be created
     * @param image           the Docker image to use for the container
     * @param buildScript     the build script to be executed in the container
     * @param exerciseEnvVars the environment variables provided by the instructor
     * @param cpuCount        the number of CPUs to allocate to the container
     * @param memory          the memory limit in MB to allocate to the container
     * @param memorySwap      the memory swap limit in MB to allocate to the container
     * @return {@link CreateContainerResponse} that can be used to start the container
     */
    public CreateContainerResponse configureContainer(String containerName, String image, String buildScript, List<String> exerciseEnvVars, int cpuCount, int memory,
            int memorySwap) {
        List<String> envVars = new ArrayList<>();
        if (useSystemProxy) {
            envVars.add("HTTP_PROXY=" + httpProxy);
            envVars.add("HTTPS_PROXY=" + httpsProxy);
            envVars.add("NO_PROXY=" + noProxy);
        }
        envVars.add("SCRIPT=" + buildScript);
        if (exerciseEnvVars != null && !exerciseEnvVars.isEmpty()) {
            envVars.addAll(exerciseEnvVars);
        }
        HostConfig defaultHostConfig = buildAgentConfiguration.hostConfig();
        HostConfig customHostConfig;
        if (cpuCount > 0 || memory > 0 || memorySwap > 0) {
            // Use provided values if they are greater than 0 and less than the maximum values, otherwise use either the maximum values or the default values from the host config.
            long adjustedCpuCount = (cpuCount > 0) ? ((maxCpuCount > 0) ? Math.min(cpuCount, maxCpuCount) : cpuCount)
                    : (defaultHostConfig.getCpuQuota() / defaultHostConfig.getCpuPeriod());

            long adjustedMemory = (memory > 0)
                    ? ((maxMemory > 0) ? Math.min(convertMemoryFromMBToBytes(memory), convertMemoryFromMBToBytes(maxMemory)) : convertMemoryFromMBToBytes(memory))
                    : defaultHostConfig.getMemory();

            long adjustedMemorySwap = (memorySwap > 0)
                    ? ((maxMemorySwap > 0) ? Math.min(convertMemoryFromMBToBytes(memorySwap), convertMemoryFromMBToBytes(maxMemorySwap)) : convertMemoryFromMBToBytes(memorySwap))
                    : defaultHostConfig.getMemorySwap();

            customHostConfig = copyAndAdjustHostConfig(defaultHostConfig, adjustedCpuCount, adjustedMemory, adjustedMemorySwap);
        }
        else {
            customHostConfig = defaultHostConfig;
        }
        try (final var createCommand = buildAgentConfiguration.getDockerClient().createContainerCmd(image)) {
            return createCommand.withName(containerName).withHostConfig(customHostConfig).withEnv(envVars)
                    // Command to run when the container starts. This is the command that will be executed in the container's main process, which runs in the foreground and blocks
                    // the
                    // container from exiting until it finishes.
                    // It waits until the script that is running the tests (see below execCreateCmdResponse) is completed, and until the result files are extracted which is
                    // indicated
                    // by the creation of a file "stop_container.txt" in the container's root directory.
                    .withEntrypoint().withCmd("sh", "-c", "while [ ! -f " + LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/stop_container.txt ]; do sleep 0.5; done")
                    // .withCmd("tail", "-f", "/dev/null") // Activate for debugging purposes instead of the above command to get a running container that you can peek into using
                    // "docker exec -it <container-id> /bin/bash".
                    .exec();
        }
    }

    private HostConfig copyAndAdjustHostConfig(HostConfig defaultHostConfig, long cpuCount, long memory, long memorySwap) {
        long cpuPeriod = defaultHostConfig.getCpuPeriod();
        return HostConfig.newHostConfig().withCpuQuota(cpuCount * cpuPeriod).withCpuPeriod(cpuPeriod).withMemory(memory).withMemorySwap(memorySwap)
                .withPidsLimit(defaultHostConfig.getPidsLimit()).withAutoRemove(true);
    }

    private long convertMemoryFromMBToBytes(long memory) {
        return memory * 1024L * 1024L;
    }

    /**
     * Start the container with the given ID.
     *
     * @param containerId the ID of the container to be started
     */
    public void startContainer(String containerId) {
        try (final var startCommand = buildAgentConfiguration.getDockerClient().startContainerCmd(containerId)) {
            startCommand.exec();
        }
    }

    /**
     * Run the script in the container and wait for it to finish before returning.
     *
     * @param containerId       the id of the container in which the script should be run
     * @param buildJobId        the id of the build job that is currently being executed
     * @param isNetworkDisabled whether the network should be disabled for the container
     */
    public void runScriptInContainer(String containerId, String buildJobId, boolean isNetworkDisabled) {
        if (isNetworkDisabled) {
            log.info("disconnecting container with id {} from network", containerId);
            try (final var disconnectCommand = buildAgentConfiguration.getDockerClient().disconnectFromNetworkCmd()) {
                disconnectCommand.withContainerId(containerId).withNetworkId("bridge").exec();
            }
            catch (Exception e) {
                log.error("Failed to disconnect container with id {} from network: {}", containerId, e.getMessage());
                buildLogsMap.appendBuildLogEntry(buildJobId, "Failed to disconnect container from default network 'bridge': " + e.getMessage());
                throw new LocalCIException("Failed to disconnect container from default network 'bridge': " + e.getMessage());
            }
        }

        log.info("Started running the build script for build job in container with id {}", containerId);
        // The "sh script.sh" execution command specified here is run inside the container as an additional process. This command runs in the background, independent of the
        // container's
        // main process. The execution command can run concurrently with the main process. This setup with the ExecCreateCmdResponse gives us the ability to wait in code until the
        // command has finished before trying to extract the results.
        executeDockerCommand(containerId, buildJobId, true, true, false, "bash", LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/script.sh");
    }

    /**
     * Moves the generated result files to a specified directory so it can easily be retrieved
     *
     * @param containerId     the id of the container which generated the files
     * @param sourcePaths     the list of paths in the container where the generated files can be found
     * @param destinationPath the path of the directory where the files shall be moved
     */
    public void moveResultsToSpecifiedDirectory(String containerId, List<String> sourcePaths, String destinationPath) {
        String command = "shopt -s globstar && mkdir -p " + destinationPath;
        executeDockerCommand(containerId, null, true, true, true, "bash", "-c", command);

        for (String sourcePath : sourcePaths) {
            checkPath(sourcePath);
            command = "shopt -s globstar && mv " + sourcePath + " " + destinationPath;
            executeDockerCommand(containerId, null, true, true, true, "bash", "-c", command);
        }
    }

    /**
     * Retrieve an archive from a running Docker container.
     *
     * @param containerId the id of the container.
     * @param path        the path to the file or directory to be retrieved.
     * @return a {@link TarArchiveInputStream} that can be used to read the archive.
     */
    public TarArchiveInputStream getArchiveFromContainer(String containerId, String path) throws NotFoundException {
        try (final var copyArchiveCommand = buildAgentConfiguration.getDockerClient().copyArchiveFromContainerCmd(containerId, path)) {
            return new TarArchiveInputStream(copyArchiveCommand.exec());
        }
    }

    /**
     * Stops the container with the given name by creating a file "stop_container.txt" in its root directory.
     * The container must be created in such a way that it waits for this file to appear and then stops running, causing it to be removed at the same time.
     * In case the container is not responding, we can force remove it using {@link DockerClient#removeContainerCmd(String)}.
     * This takes significantly longer than using the approach with the file because of increased overhead for the removeContainerCmd() method.
     *
     * @param containerName The name of the container to stop. Cannot use the container ID, because this method might have to be called from the main thread (not the thread started
     *                          for the build job) where the container ID is not available.
     */
    public void stopContainer(String containerName) {
        // List all containers, including the non-running ones.
        Container container = getContainerForName(containerName);

        // Check if the container exists. Return if it does not.
        if (container == null) {
            return;
        }

        // Check if the container is running. Return if it's not.
        boolean isContainerRunning = "running".equals(container.getState());
        if (!isContainerRunning) {
            return;
        }

        // Get the container ID.
        String containerId = container.getId();

        log.info("Stopping container with id {}", containerId);

        // Create a file "stop_container.txt" in the root directory of the container to indicate that the test results have been extracted or that the container should be stopped
        // for some other reason.
        // The container's main process is waiting for this file to appear and then stops the main process, thus stopping and removing the container.
        executeDockerCommandWithoutAwaitingResponse(containerId, "touch", LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/stop_container.txt");
    }

    /**
     * Stops, kills or removes a container in case a build job has failed or the container is unresponsive.
     * Adding a file "stop_container.txt" like in {@link #stopContainer(String)} might not work for unresponsive containers, thus we use
     * {@link DockerClient#stopContainerCmd(String)}, {@link DockerClient#killContainerCmd(String)} and {@link DockerClient#removeContainerCmd(String)} to stop, kill or remove the
     * container.
     *
     * @param containerId The ID of the container to stop or kill.
     */
    public void stopUnresponsiveContainer(String containerId) {
        try (final var executor = Executors.newSingleThreadExecutor()) {
            try {
                // Attempt to stop the container. It should stop the container and auto-remove it.
                // {@link DockerClient#stopContainerCmd(String)} first sends a SIGTERM command to the container to gracefully stop it,
                // and if it does not stop within the timeout, it sends a SIGKILL command to kill the container.
                log.warn("Stopping unresponsive container with id {}", containerId);

                // Submit Docker stop command to executor service
                Future<Void> future = executor.submit(() -> {
                    try (final var stopCommand = buildAgentConfiguration.getDockerClient().stopContainerCmd(containerId)) {
                        stopCommand.withTimeout(15).exec();
                        return null;  // Return type to match Future<Void>
                    }
                });

                // Await the future with a timeout
                future.get(20, TimeUnit.SECONDS);  // Wait for the stop command to complete with a timeout
            }
            catch (Exception e) {
                Throwable cause = e.getCause();
                // e will be ExecutionException if thrown in executor service by submitted task
                // We are interested in the underlying cause in this case
                if (e instanceof ExecutionException && (cause instanceof NotFoundException || cause instanceof NotModifiedException)) {
                    log.warn("Container with id {} is already stopped. Attempting to remove container.", containerId, cause);
                    // this can also happen for containers that are stuck in "Ready" state so they can not be stopped or killed
                    // try to remove the container so it won't show up in the next cleanup again
                    removeContainer(containerId, executor);
                }
                else {
                    log.error("Failed to stop container with id {}. Attempting to kill container.", containerId, e);

                    // Attempt to kill the container if stop fails
                    killContainer(containerId, executor);
                }
            }
            finally {
                executor.shutdown();
            }
        }
    }

    /**
     * Kills a Docker container asynchronously using the given container ID and executor.
     * Waits up to 10 seconds for completion. Logs an error if the operation fails.
     *
     * @param containerId The ID of the container to kill.
     * @param executor    The ExecutorService for running the command.
     */
    private void killContainer(String containerId, ExecutorService executor) {
        try (final var killCommand = buildAgentConfiguration.getDockerClient().killContainerCmd(containerId)) {
            Future<Void> killFuture = executor.submit(() -> {
                killCommand.exec();
                return null;
            });

            killFuture.get(10, TimeUnit.SECONDS);  // Wait for the kill command to complete with a timeout
        }
        catch (Exception e) {
            log.error("Failed to kill container with id {}.", containerId, e);
        }
    }

    /**
     * Removes a Docker container asynchronously using the given container ID and executor.
     * Waits up to 10 seconds for completion. Logs an error if the operation fails.
     *
     * @param containerId The ID of the container to remove.
     * @param executor    The ExecutorService for running the command.
     */
    private void removeContainer(String containerId, ExecutorService executor) {
        try (final var removeCommand = buildAgentConfiguration.getDockerClient().removeContainerCmd(containerId)) {
            Future<Void> removeFuture = executor.submit(() -> {
                removeCommand.exec();
                return null;
            });
            removeFuture.get(10, TimeUnit.SECONDS); // Wait for the remove command to complete with a timeout
        }
        catch (Exception e) {
            log.error("Failed to remove container with id {}", containerId, e);
        }
    }

    /**
     * Get the ID of a running container by its name.
     *
     * @param containerName The name of the container.
     * @return The ID of the running container or null if no running container with the given name was found.
     */
    public String getIDOfRunningContainer(String containerName) {
        Container container = getContainerForName(containerName);
        // Return id if container not null
        return Optional.ofNullable(container).map(Container::getId).orElse(null);
    }

    /**
     * Prepares a Docker container for a build job by setting up the required directories and repositories within the container.
     * This includes setting up directories for assignment, tests, solutions, and any auxiliary repositories provided.
     * Directories are created and permissions are set to ensure they are fully accessible.
     * <p>
     * Steps involved:
     * 1. Ensures the existence of a working directory within the container.
     * 2. Sets directory permissions to be fully accessible (chmod 777).
     * 3. Adds and prepares directories for the test, assignment, and optionally the solution repositories based on their respective paths.
     * 4. Processes auxiliary repositories, if any, by setting up their specified directories.
     * 5. Converts DOS-style line endings to Unix style to ensure file compatibility within the container.
     * 6. Creates a script file for further processing or setup within the container.
     *
     * @param buildJobContainerId                    The identifier for the Docker container being prepared.
     * @param assignmentRepositoryPath               The filesystem path to the assignment repository.
     * @param testRepositoryPath                     The filesystem path to the test repository.
     * @param solutionRepositoryPath                 The optional filesystem path to the solution repository; can be null if not applicable.
     * @param auxiliaryRepositoriesPaths             An array of paths for auxiliary repositories to be included in the build process.
     * @param auxiliaryRepositoryCheckoutDirectories An array of directory names within the container where each auxiliary repository should be checked out.
     * @param programmingLanguage                    The programming language of the repositories, which influences directory naming conventions.
     * @param assignmentCheckoutPath                 The directory within the container where the assignment repository should be checked out; can be null if not applicable,
     *                                                   default would be used.
     * @param testCheckoutPath                       The directory within the container where the test repository should be checked out; can be null if not applicable, default
     *                                                   would be used.
     * @param solutionCheckoutPath                   The directory within the container where the solution repository should be checked out; can be null if not applicable, default
     *                                                   would be used.
     */
    public void populateBuildJobContainer(String buildJobContainerId, Path assignmentRepositoryPath, Path testRepositoryPath, Path solutionRepositoryPath,
            Path[] auxiliaryRepositoriesPaths, String[] auxiliaryRepositoryCheckoutDirectories, ProgrammingLanguage programmingLanguage, String assignmentCheckoutPath,
            String testCheckoutPath, String solutionCheckoutPath) {

        assignmentCheckoutPath = (!StringUtils.isBlank(assignmentCheckoutPath)) ? assignmentCheckoutPath
                : RepositoryCheckoutPath.ASSIGNMENT.forProgrammingLanguage(programmingLanguage);

        String defaultTestCheckoutPath = RepositoryCheckoutPath.TEST.forProgrammingLanguage(programmingLanguage);
        testCheckoutPath = (!StringUtils.isBlank(defaultTestCheckoutPath) && !StringUtils.isBlank(testCheckoutPath)) ? testCheckoutPath : defaultTestCheckoutPath;

        // Make sure to create the working directory in case it does not exist.
        // In case the test checkout path is the working directory, we only create up to the parent, as the working directory is created below.
        addDirectory(buildJobContainerId, LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + (testCheckoutPath.isEmpty() ? "" : "/testing-dir"), true);
        // Make sure the working directory and all subdirectories are accessible
        executeDockerCommand(buildJobContainerId, null, false, false, true, "chmod", "-R", "777", LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/testing-dir");

        // Copy the test repository to the container and move it to the test checkout path (may be the working directory)
        addAndPrepareDirectoryAndReplaceContent(buildJobContainerId, testRepositoryPath, LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/testing-dir/" + testCheckoutPath);
        // Copy the assignment repository to the container and move it to the assignment checkout path
        addAndPrepareDirectoryAndReplaceContent(buildJobContainerId, assignmentRepositoryPath,
                LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/testing-dir/" + assignmentCheckoutPath);
        if (solutionRepositoryPath != null) {
            solutionCheckoutPath = (!StringUtils.isBlank(solutionCheckoutPath)) ? solutionCheckoutPath
                    : RepositoryCheckoutPath.SOLUTION.forProgrammingLanguage(programmingLanguage);
            addAndPrepareDirectoryAndReplaceContent(buildJobContainerId, solutionRepositoryPath,
                    LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/testing-dir/" + solutionCheckoutPath);
        }
        for (int i = 0; i < auxiliaryRepositoriesPaths.length; i++) {
            addAndPrepareDirectoryAndReplaceContent(buildJobContainerId, auxiliaryRepositoriesPaths[i],
                    LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/testing-dir/" + auxiliaryRepositoryCheckoutDirectories[i]);
        }

        createScriptFile(buildJobContainerId);
    }

    private void createScriptFile(String buildJobContainerId) {
        executeDockerCommand(buildJobContainerId, null, false, false, true, "bash", "-c", "echo \"$SCRIPT\" > " + LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/script.sh");
        executeDockerCommand(buildJobContainerId, null, false, false, true, "bash", "-c", "chmod +x " + LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/script.sh");
    }

    private void addAndPrepareDirectoryAndReplaceContent(String containerId, Path repositoryPath, String newDirectoryName) {
        copyToContainer(repositoryPath, containerId);
        addDirectory(containerId, newDirectoryName, true);
        insertRepositoryFiles(containerId, LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/" + repositoryPath.getFileName().toString(), newDirectoryName);
    }

    private void insertRepositoryFiles(String containerId, String oldName, String newName) {
        executeDockerCommand(containerId, null, false, false, true, "cp", "-r", oldName + (oldName.endsWith("/") ? "." : "/."), newName);
    }

    private void addDirectory(String containerId, String directoryName, boolean createParentsIfNecessary) {
        String[] command = createParentsIfNecessary ? new String[] { "mkdir", "-p", directoryName } : new String[] { "mkdir", directoryName };
        executeDockerCommand(containerId, null, false, false, true, command);
    }

    private void copyToContainer(Path sourcePath, String containerId) {
        try (final var uploadStream = new ByteArrayInputStream(createTarArchive(sourcePath).toByteArray());
                final var copyToContainerCommand = buildAgentConfiguration.getDockerClient().copyArchiveToContainerCmd(containerId)
                        .withRemotePath(LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY).withTarInputStream(uploadStream)) {
            copyToContainerCommand.exec();
        }
        catch (IOException e) {
            throw new LocalCIException("Could not copy to container " + containerId, e);
        }
    }

    private ByteArrayOutputStream createTarArchive(Path sourcePath) {

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        TarArchiveOutputStream tarArchiveOutputStream = new TarArchiveOutputStream(byteArrayOutputStream);

        // This needs to be done in case the files have a long name
        tarArchiveOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);

        try {
            addFileToTar(tarArchiveOutputStream, sourcePath, "");
        }
        catch (IOException e) {
            throw new LocalCIException("Could not create tar archive", e);
        }
        return byteArrayOutputStream;
    }

    private void addFileToTar(TarArchiveOutputStream tarArchiveOutputStream, Path path, String parent) throws IOException {
        TarArchiveEntry tarEntry = new TarArchiveEntry(path, parent + path.getFileName());
        tarArchiveOutputStream.putArchiveEntry(tarEntry);

        if (Files.isRegularFile(path)) {
            try (InputStream is = Files.newInputStream(path)) {
                byte[] buffer = new byte[1024];
                int count;
                while ((count = is.read(buffer)) != -1) {
                    tarArchiveOutputStream.write(buffer, 0, count);
                }
            }
            tarArchiveOutputStream.closeArchiveEntry();
        }
        else {
            tarArchiveOutputStream.closeArchiveEntry();
            try (Stream<Path> children = Files.list(path)) {
                for (Path child : children.toList()) {
                    addFileToTar(tarArchiveOutputStream, child, parent + path.getFileName() + "/");
                }
            }
        }

    }

    private void executeDockerCommandWithoutAwaitingResponse(String containerId, String... command) {
        DockerClient dockerClient = buildAgentConfiguration.getDockerClient();
        try (final var createCommand = dockerClient.execCreateCmd(containerId).withCmd(command)) {
            final var createCommandResponse = createCommand.exec();
            dockerClient.execStartCmd(createCommandResponse.getId()).withDetach(true).exec(new ResultCallback.Adapter<>());
        }
    }

    private void executeDockerCommand(String containerId, String buildJobId, boolean attachStdout, boolean attachStderr, boolean forceRoot, String... command) {
        DockerClient dockerClient = buildAgentConfiguration.getDockerClient();
        boolean detach = !attachStdout && !attachStderr;

        try (var execCreateCommandTemp = dockerClient.execCreateCmd(containerId).withAttachStdout(attachStdout).withAttachStderr(attachStderr).withCmd(command)) {
            final var execCreateCommand = forceRoot ? execCreateCommandTemp.withUser("root") : execCreateCommandTemp;
            ExecCreateCmdResponse execCreateCmdResponse = execCreateCommand.exec();
            final CountDownLatch latch = new CountDownLatch(1);
            try {
                dockerClient.execStartCmd(execCreateCmdResponse.getId()).withDetach(detach).exec(new ResultCallback.Adapter<>() {

                    @Override
                    public void onNext(Frame item) {
                        String payload = new String(item.getPayload());
                        String[] logLines = splitBehindNewLines(payload);
                        ZonedDateTime now = ZonedDateTime.now();

                        if (buildJobId == null) {
                            return;
                        }
                        for (String line : logLines) {
                            if (line.isEmpty()) {
                                continue;
                            }
                            BuildLogDTO buildLogEntry = new BuildLogDTO(now, line);
                            buildLogsMap.appendBuildLogEntry(buildJobId, buildLogEntry);
                        }
                    }

                    @Override
                    public void onComplete() {
                        latch.countDown();
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        log.error("Error while executing Docker command: {} on container {}", String.join(" ", command), containerId, throwable);
                        latch.countDown();
                    }
                });
            }
            catch (ConflictException e) {
                throw new LocalCIException("Could not execute Docker command: " + String.join(" ", command), e);
            }

            try {
                latch.await();
            }
            catch (InterruptedException e) {
                throw new LocalCIException("Interrupted while executing Docker command: " + String.join(" ", command), e);
            }
        }
    }

    private void checkPath(String path) {
        if (path == null || path.contains("..") || !path.matches("[a-zA-Z0-9_*./-]+")) {
            throw new LocalCIException("Invalid path: " + path);
        }
    }

    private Container getContainerForName(String containerName) {
        try (final var listContainerCommand = buildAgentConfiguration.getDockerClient().listContainersCmd().withShowAll(true)) {
            List<Container> containers = listContainerCommand.exec();
            return containers.stream().filter(container -> container.getNames()[0].equals("/" + containerName)).findFirst().orElse(null);
        }
        catch (Exception ex) {
            if (DockerUtil.isDockerNotAvailable(ex)) {
                log.error("Docker is not available: {}", ex.getMessage());
            }
            else {
                log.error("Failed to get container for name {}: {}", containerName, ex.getMessage());
            }
            return null;
        }
    }

    private String[] splitBehindNewLines(String input) {
        String newlineLookBehindPattern = "(?<=\\R)";
        return input.split(newlineLookBehindPattern);
    }
}
