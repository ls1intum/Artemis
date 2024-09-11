package de.tum.cit.aet.artemis.service.connectors.localci.buildagent;

import static de.tum.cit.aet.artemis.core.config.Constants.LOCALCI_WORKING_DIRECTORY;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;

import de.tum.cit.aet.artemis.core.exception.LocalCIException;
import de.tum.cit.aet.artemis.programming.domain.BuildLogEntry;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.service.connectors.ci.ContinuousIntegrationService.RepositoryCheckoutPath;

/**
 * This service contains methods that are used to interact with the Docker containers when executing build jobs in the local CI system.
 * It is closely related to the {@link BuildJobExecutionService} which contains the methods that are used to execute the build jobs.
 */
@Service
@Profile(PROFILE_BUILDAGENT)
public class BuildJobContainerService {

    private static final Logger log = LoggerFactory.getLogger(BuildJobContainerService.class);

    private final DockerClient dockerClient;

    private final HostConfig hostConfig;

    private final BuildLogsMap buildLogsMap;

    @Value("${artemis.continuous-integration.proxies.use-system-proxy:false}")
    private boolean useSystemProxy;

    @Value("${artemis.continuous-integration.proxies.default.http-proxy:}")
    private String httpProxy;

    @Value("${artemis.continuous-integration.proxies.default.https-proxy:}")
    private String httpsProxy;

    @Value("${artemis.continuous-integration.proxies.default.no-proxy:}")
    private String noProxy;

    public BuildJobContainerService(DockerClient dockerClient, HostConfig hostConfig, BuildLogsMap buildLogsMap) {
        this.dockerClient = dockerClient;
        this.hostConfig = hostConfig;
        this.buildLogsMap = buildLogsMap;
    }

    /**
     * Configure a container with the Docker image, the container name, optional proxy config variables, and set the command that runs when the container starts.
     *
     * @param containerName the name of the container to be created
     * @param image         the Docker image to use for the container
     * @param buildScript   the build script to be executed in the container
     * @return {@link CreateContainerResponse} that can be used to start the container
     */
    public CreateContainerResponse configureContainer(String containerName, String image, String buildScript) {
        List<String> envVars = new ArrayList<>();
        if (useSystemProxy) {
            envVars.add("HTTP_PROXY=" + httpProxy);
            envVars.add("HTTPS_PROXY=" + httpsProxy);
            envVars.add("NO_PROXY=" + noProxy);
        }
        envVars.add("SCRIPT=" + buildScript);
        return dockerClient.createContainerCmd(image).withName(containerName).withHostConfig(hostConfig).withEnv(envVars)
                // Command to run when the container starts. This is the command that will be executed in the container's main process, which runs in the foreground and blocks the
                // container from exiting until it finishes.
                // It waits until the script that is running the tests (see below execCreateCmdResponse) is completed, and until the result files are extracted which is indicated
                // by the creation of a file "stop_container.txt" in the container's root directory.
                .withCmd("sh", "-c", "while [ ! -f " + LOCALCI_WORKING_DIRECTORY + "/stop_container.txt ]; do sleep 0.5; done")
                // .withCmd("tail", "-f", "/dev/null") // Activate for debugging purposes instead of the above command to get a running container that you can peek into using
                // "docker exec -it <container-id> /bin/bash".
                .exec();
    }

    /**
     * Start the container with the given ID.
     *
     * @param containerId the ID of the container to be started
     */
    public void startContainer(String containerId) {
        dockerClient.startContainerCmd(containerId).exec();
    }

    /**
     * Run the script in the container and wait for it to finish before returning.
     *
     * @param containerId the id of the container in which the script should be run
     * @param buildJobId  the id of the build job that is currently being executed
     */

    public void runScriptInContainer(String containerId, String buildJobId) {
        log.info("Started running the build script for build job in container with id {}", containerId);
        // The "sh script.sh" execution command specified here is run inside the container as an additional process. This command runs in the background, independent of the
        // container's
        // main process. The execution command can run concurrently with the main process. This setup with the ExecCreateCmdResponse gives us the ability to wait in code until the
        // command has finished before trying to extract the results.
        executeDockerCommand(containerId, buildJobId, true, true, false, "bash", LOCALCI_WORKING_DIRECTORY + "/script.sh");
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
        return new TarArchiveInputStream(dockerClient.copyArchiveFromContainerCmd(containerId, path).exec());
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

        // Create a file "stop_container.txt" in the root directory of the container to indicate that the test results have been extracted or that the container should be stopped
        // for some other reason.
        // The container's main process is waiting for this file to appear and then stops the main process, thus stopping and removing the container.
        executeDockerCommandWithoutAwaitingResponse(containerId, "touch", LOCALCI_WORKING_DIRECTORY + "/stop_container.txt");
    }

    /**
     * Stops or kills a container in case a build job has failed or the container is unresponsive.
     * Adding a file "stop_container.txt" like in {@link #stopContainer(String)} might not work for unresponsive containers, thus we use
     * {@link DockerClient#stopContainerCmd(String)} and {@link DockerClient#killContainerCmd(String)} to stop or kill the container.
     *
     * @param containerId The ID of the container to stop or kill.
     */
    public void stopUnresponsiveContainer(String containerId) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            // Attempt to stop the container. It should stop the container and auto-remove it.
            // {@link DockerClient#stopContainerCmd(String)} first sends a SIGTERM command to the container to gracefully stop it,
            // and if it does not stop within the timeout, it sends a SIGKILL command to kill the container.
            log.info("Stopping container with id {}", containerId);

            // Submit Docker stop command to executor service
            Future<Void> future = executor.submit(() -> {
                dockerClient.stopContainerCmd(containerId).withTimeout(5).exec();
                return null;  // Return type to match Future<Void>
            });

            // Await the future with a timeout
            future.get(10, TimeUnit.SECONDS);  // Wait for the stop command to complete with a timeout
        }
        catch (NotFoundException | NotModifiedException e) {
            log.debug("Container with id {} is already stopped: {}", containerId, e.getMessage());
        }
        catch (Exception e) {
            log.warn("Failed to stop container with id {}. Attempting to kill container: {}", containerId, e.getMessage());

            // Attempt to kill the container if stop fails
            try {
                Future<Void> killFuture = executor.submit(() -> {
                    dockerClient.killContainerCmd(containerId).exec();
                    return null;
                });

                killFuture.get(5, TimeUnit.SECONDS);  // Wait for the kill command to complete with a timeout
            }
            catch (Exception killException) {
                log.warn("Failed to kill container with id {}: {}", containerId, killException.getMessage());
            }
        }
        finally {
            executor.shutdown();
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
     */
    public void populateBuildJobContainer(String buildJobContainerId, Path assignmentRepositoryPath, Path testRepositoryPath, Path solutionRepositoryPath,
            Path[] auxiliaryRepositoriesPaths, String[] auxiliaryRepositoryCheckoutDirectories, ProgrammingLanguage programmingLanguage) {
        String testCheckoutPath = RepositoryCheckoutPath.TEST.forProgrammingLanguage(programmingLanguage);
        String assignmentCheckoutPath = RepositoryCheckoutPath.ASSIGNMENT.forProgrammingLanguage(programmingLanguage);

        // Make sure to create the working directory in case it does not exist.
        // In case the test checkout path is the working directory, we only create up to the parent, as the working directory is created below.
        addDirectory(buildJobContainerId, LOCALCI_WORKING_DIRECTORY + (testCheckoutPath.isEmpty() ? "" : "/testing-dir"), true);
        // Make sure the working directory and all subdirectories are accessible
        executeDockerCommand(buildJobContainerId, null, false, false, true, "chmod", "-R", "777", LOCALCI_WORKING_DIRECTORY + "/testing-dir");

        // Copy the test repository to the container and move it to the test checkout path (may be the working directory)
        addAndPrepareDirectory(buildJobContainerId, testRepositoryPath, LOCALCI_WORKING_DIRECTORY + "/testing-dir/" + testCheckoutPath);
        // Copy the assignment repository to the container and move it to the assignment checkout path
        addAndPrepareDirectory(buildJobContainerId, assignmentRepositoryPath, LOCALCI_WORKING_DIRECTORY + "/testing-dir/" + assignmentCheckoutPath);
        if (solutionRepositoryPath != null) {
            String solutionCheckoutPath = RepositoryCheckoutPath.SOLUTION.forProgrammingLanguage(programmingLanguage);
            addAndPrepareDirectory(buildJobContainerId, solutionRepositoryPath, LOCALCI_WORKING_DIRECTORY + "/testing-dir/" + solutionCheckoutPath);
        }
        for (int i = 0; i < auxiliaryRepositoriesPaths.length; i++) {
            addAndPrepareDirectory(buildJobContainerId, auxiliaryRepositoriesPaths[i], LOCALCI_WORKING_DIRECTORY + "/testing-dir/" + auxiliaryRepositoryCheckoutDirectories[i]);
        }

        createScriptFile(buildJobContainerId);
    }

    private void createScriptFile(String buildJobContainerId) {
        executeDockerCommand(buildJobContainerId, null, false, false, true, "bash", "-c", "echo \"$SCRIPT\" > " + LOCALCI_WORKING_DIRECTORY + "/script.sh");
        executeDockerCommand(buildJobContainerId, null, false, false, true, "bash", "-c", "chmod +x " + LOCALCI_WORKING_DIRECTORY + "/script.sh");
    }

    private void addAndPrepareDirectory(String containerId, Path repositoryPath, String newDirectoryName) {
        copyToContainer(repositoryPath.toString(), containerId);
        renameDirectoryOrFile(containerId, LOCALCI_WORKING_DIRECTORY + "/" + repositoryPath.getFileName().toString(), newDirectoryName);
    }

    private void renameDirectoryOrFile(String containerId, String oldName, String newName) {
        executeDockerCommand(containerId, null, false, false, true, "mv", oldName, newName);
    }

    private void addDirectory(String containerId, String directoryName, boolean createParentsIfNecessary) {
        String[] command = createParentsIfNecessary ? new String[] { "mkdir", "-p", directoryName } : new String[] { "mkdir", directoryName };
        executeDockerCommand(containerId, null, false, false, true, command);
    }

    private void copyToContainer(String sourcePath, String containerId) {
        try (InputStream uploadStream = new ByteArrayInputStream(createTarArchive(sourcePath).toByteArray())) {
            dockerClient.copyArchiveToContainerCmd(containerId).withRemotePath(LOCALCI_WORKING_DIRECTORY).withTarInputStream(uploadStream).exec();
        }
        catch (IOException e) {
            throw new LocalCIException("Could not copy to container " + containerId, e);
        }
    }

    private ByteArrayOutputStream createTarArchive(String sourcePath) {
        Path path = Paths.get(sourcePath);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        TarArchiveOutputStream tarArchiveOutputStream = new TarArchiveOutputStream(byteArrayOutputStream);

        // This needs to be done in case the files have a long name
        tarArchiveOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);

        try {
            addFileToTar(tarArchiveOutputStream, path.toFile(), "");
        }
        catch (IOException e) {
            throw new LocalCIException("Could not create tar archive", e);
        }
        return byteArrayOutputStream;
    }

    private void addFileToTar(TarArchiveOutputStream tarArchiveOutputStream, File file, String parent) throws IOException {
        TarArchiveEntry tarEntry = new TarArchiveEntry(file, parent + file.getName());
        tarArchiveOutputStream.putArchiveEntry(tarEntry);

        if (file.isFile()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[1024];
                int count;
                while ((count = fis.read(buffer)) != -1) {
                    tarArchiveOutputStream.write(buffer, 0, count);
                }
            }
            tarArchiveOutputStream.closeArchiveEntry();
        }
        else {
            tarArchiveOutputStream.closeArchiveEntry();
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    addFileToTar(tarArchiveOutputStream, child, parent + file.getName() + "/");
                }
            }
        }
    }

    private void executeDockerCommandWithoutAwaitingResponse(String containerId, String... command) {
        ExecCreateCmdResponse createCmdResponse = dockerClient.execCreateCmd(containerId).withCmd(command).exec();
        dockerClient.execStartCmd(createCmdResponse.getId()).withDetach(true).exec(new ResultCallback.Adapter<>());
    }

    private void executeDockerCommand(String containerId, String buildJobId, boolean attachStdout, boolean attachStderr, boolean forceRoot, String... command) {
        boolean detach = !attachStdout && !attachStderr;

        ExecCreateCmd execCreateCmd = dockerClient.execCreateCmd(containerId).withAttachStdout(attachStdout).withAttachStderr(attachStderr).withCmd(command);
        if (forceRoot) {
            execCreateCmd = execCreateCmd.withUser("root");
        }
        ExecCreateCmdResponse execCreateCmdResponse = execCreateCmd.exec();
        final CountDownLatch latch = new CountDownLatch(1);
        try {
            dockerClient.execStartCmd(execCreateCmdResponse.getId()).withDetach(detach).exec(new ResultCallback.Adapter<>() {

                @Override
                public void onNext(Frame item) {
                    String text = new String(item.getPayload());
                    BuildLogEntry buildLogEntry = new BuildLogEntry(ZonedDateTime.now(), text);
                    if (buildJobId != null) {
                        buildLogsMap.appendBuildLogEntry(buildJobId, buildLogEntry);
                    }
                }

                @Override
                public void onComplete() {
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

    private void checkPath(String path) {
        if (path == null || path.contains("..") || !path.matches("[a-zA-Z0-9_*./-]+")) {
            throw new LocalCIException("Invalid path: " + path);
        }
    }

    private Container getContainerForName(String containerName) {
        List<Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();
        return containers.stream().filter(container -> container.getNames()[0].equals("/" + containerName)).findFirst().orElse(null);
    }
}
