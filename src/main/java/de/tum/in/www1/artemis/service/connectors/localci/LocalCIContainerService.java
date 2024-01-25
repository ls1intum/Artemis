package de.tum.in.www1.artemis.service.connectors.localci;

import static de.tum.in.www1.artemis.config.Constants.LOCALCI_WORKING_DIRECTORY;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;

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
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;

import de.tum.in.www1.artemis.domain.BuildLogEntry;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.service.connectors.BuildScriptProvider;
import de.tum.in.www1.artemis.service.connectors.aeolus.AeolusTemplateService;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationService.RepositoryCheckoutPath;

/**
 * This service contains methods that are used to interact with the Docker containers when executing build jobs in the local CI system.
 * It is closely related to the {@link LocalCIBuildJobExecutionService} which contains the methods that are used to execute the build jobs.
 */
@Service
@Profile("localci")
public class LocalCIContainerService {

    private static final Logger log = LoggerFactory.getLogger(LocalCIContainerService.class);

    private final DockerClient dockerClient;

    private final HostConfig hostConfig;

    @Value("${artemis.continuous-integration.proxies.use-system-proxy:false}")
    private boolean useSystemProxy;

    @Value("${artemis.continuous-integration.proxies.default.http-proxy:}")
    private String httpProxy;

    @Value("${artemis.continuous-integration.proxies.default.https-proxy:}")
    private String httpsProxy;

    @Value("${artemis.continuous-integration.proxies.default.no-proxy:}")
    private String noProxy;

    AeolusTemplateService aeolusTemplateService;

    BuildScriptProvider buildScriptProvider;

    public LocalCIContainerService(DockerClient dockerClient, HostConfig hostConfig, AeolusTemplateService aeolusTemplateService, BuildScriptProvider buildScriptProvider) {
        this.dockerClient = dockerClient;
        this.hostConfig = hostConfig;
        this.aeolusTemplateService = aeolusTemplateService;
        this.buildScriptProvider = buildScriptProvider;
    }

    /**
     * Configure a container with the Docker image, the container name, optional proxy config variables, and set the command that runs when the container starts.
     *
     * @param containerName the name of the container to be created
     * @param image         the Docker image to use for the container
     * @return {@link CreateContainerResponse} that can be used to start the container
     */
    public CreateContainerResponse configureContainer(String containerName, String image) {
        List<String> envVars = new ArrayList<>();
        if (useSystemProxy) {
            envVars.add("HTTP_PROXY=" + httpProxy);
            envVars.add("HTTPS_PROXY=" + httpsProxy);
            envVars.add("NO_PROXY=" + noProxy);
        }
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
     * @return a list of {@link BuildLogEntry} that contains the logs of the script execution
     */

    public List<BuildLogEntry> runScriptInContainer(String containerId) {
        log.info("Started running the build script for build job in container with id {}", containerId);
        // The "sh script.sh" execution command specified here is run inside the container as an additional process. This command runs in the background, independent of the
        // container's
        // main process. The execution command can run concurrently with the main process. This setup with the ExecCreateCmdResponse gives us the ability to wait in code until the
        // command has finished before trying to extract the results.
        return executeDockerCommand(containerId, true, true, false, "bash", LOCALCI_WORKING_DIRECTORY + "/script.sh");
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
        executeDockerCommand(containerId, true, true, true, "bash", "-c", command);

        for (String sourcePath : sourcePaths) {
            checkPath(sourcePath);
            command = "shopt -s globstar && mv " + sourcePath + " " + destinationPath;
            executeDockerCommand(containerId, true, true, true, "bash", "-c", command);
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
     * You could also use {@link DockerClient#stopContainerCmd(String)} to stop the container, but this takes significantly longer than using the approach with the file because of
     * increased overhead for the stopContainerCmd() method.
     *
     * @param containerName The name of the container to stop. Cannot use the container ID, because this method might have to be called from the main thread (not the thread started
     *                          for the build job) where the container ID is not available.
     */
    public void stopContainer(String containerName) {
        // List all containers, including the non-running ones.
        List<Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();

        // Check if there's a container with the given name.
        Optional<Container> containerOptional = containers.stream().filter(container -> container.getNames()[0].equals("/" + containerName)).findFirst();
        if (containerOptional.isEmpty()) {
            return;
        }

        // Check if the container is running. Return if it's not.
        boolean isContainerRunning = "running".equals(containerOptional.get().getState());
        if (!isContainerRunning) {
            return;
        }

        // Get the container ID.
        String containerId = containerOptional.get().getId();

        // Create a file "stop_container.txt" in the root directory of the container to indicate that the test results have been extracted or that the container should be stopped
        // for some other reason.
        // The container's main process is waiting for this file to appear and then stops the main process, thus stopping and removing the container.
        executeDockerCommandWithoutAwaitingResponse(containerId, "touch", LOCALCI_WORKING_DIRECTORY + "/stop_container.txt");
    }

    /**
     * Copy the repositories and build script from the Artemis container to the build job container.
     *
     * @param buildJobContainerId                    the id of the build job container
     * @param assignmentRepositoryPath               the path to the assignment repository
     * @param testRepositoryPath                     the path to the test repository
     * @param solutionRepositoryPath                 the path to the solution repository
     * @param auxiliaryRepositoriesPaths             the paths to the auxiliary repositories
     * @param auxiliaryRepositoryCheckoutDirectories the names of the auxiliary repositories
     * @param buildScriptPath                        the path to the build script
     * @param programmingLanguage                    the programming language of the exercise
     */
    public void populateBuildJobContainer(String buildJobContainerId, Path assignmentRepositoryPath, Path testRepositoryPath, Path solutionRepositoryPath,
            Path[] auxiliaryRepositoriesPaths, String[] auxiliaryRepositoryCheckoutDirectories, Path buildScriptPath, ProgrammingLanguage programmingLanguage) {
        String testCheckoutPath = RepositoryCheckoutPath.TEST.forProgrammingLanguage(programmingLanguage);
        String assignmentCheckoutPath = RepositoryCheckoutPath.ASSIGNMENT.forProgrammingLanguage(programmingLanguage);

        if (!Objects.equals(testCheckoutPath, "")) {
            addDirectory(buildJobContainerId, LOCALCI_WORKING_DIRECTORY + "/testing-dir", true);
            executeDockerCommand(buildJobContainerId, false, false, true, "chmod", "-R", "777", LOCALCI_WORKING_DIRECTORY + "/testing-dir");
        }
        addAndPrepareDirectory(buildJobContainerId, testRepositoryPath, LOCALCI_WORKING_DIRECTORY + "/testing-dir/" + testCheckoutPath);
        addAndPrepareDirectory(buildJobContainerId, assignmentRepositoryPath, LOCALCI_WORKING_DIRECTORY + "/testing-dir/" + assignmentCheckoutPath);
        if (solutionRepositoryPath != null) {
            String solutionCheckoutPath = RepositoryCheckoutPath.SOLUTION.forProgrammingLanguage(programmingLanguage);
            addAndPrepareDirectory(buildJobContainerId, solutionRepositoryPath, LOCALCI_WORKING_DIRECTORY + "/testing-dir/" + solutionCheckoutPath);
        }
        for (int i = 0; i < auxiliaryRepositoriesPaths.length; i++) {
            addAndPrepareDirectory(buildJobContainerId, auxiliaryRepositoriesPaths[i], LOCALCI_WORKING_DIRECTORY + "/testing-dir/" + auxiliaryRepositoryCheckoutDirectories[i]);
        }
        convertDosFilesToUnix(LOCALCI_WORKING_DIRECTORY + "/testing-dir/", buildJobContainerId);

        addAndPrepareDirectory(buildJobContainerId, buildScriptPath, LOCALCI_WORKING_DIRECTORY + "/script.sh");
        convertDosFilesToUnix(LOCALCI_WORKING_DIRECTORY + "/script.sh", buildJobContainerId);
    }

    private void addAndPrepareDirectory(String containerId, Path repositoryPath, String newDirectoryName) {
        copyToContainer(repositoryPath.toString(), containerId);
        renameDirectoryOrFile(containerId, LOCALCI_WORKING_DIRECTORY + "/" + repositoryPath.getFileName().toString(), newDirectoryName);
    }

    private void renameDirectoryOrFile(String containerId, String oldName, String newName) {
        executeDockerCommand(containerId, false, false, true, "mv", oldName, newName);
    }

    private void addDirectory(String containerId, String directoryName, boolean createParentsIfNecessary) {
        String[] command = createParentsIfNecessary ? new String[] { "mkdir", "-p", directoryName } : new String[] { "mkdir", directoryName };
        executeDockerCommand(containerId, false, false, true, command);
    }

    private void convertDosFilesToUnix(String path, String containerId) {
        executeDockerCommand(containerId, false, false, true, "sh", "-c", "find " + path + " -type f ! -path '*/.git/*' -exec sed -i 's/\\r$//' {} \\;");
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

    private List<BuildLogEntry> executeDockerCommand(String containerId, boolean attachStdout, boolean attachStderr, boolean forceRoot, String... command) {
        boolean detach = !attachStdout && !attachStderr;

        ExecCreateCmd execCreateCmd = dockerClient.execCreateCmd(containerId).withAttachStdout(attachStdout).withAttachStderr(attachStderr).withCmd(command);
        if (forceRoot) {
            execCreateCmd = execCreateCmd.withUser("root");
        }
        ExecCreateCmdResponse execCreateCmdResponse = execCreateCmd.exec();
        List<BuildLogEntry> buildLogEntries = new ArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);
        dockerClient.execStartCmd(execCreateCmdResponse.getId()).withDetach(detach).exec(new ResultCallback.Adapter<>() {

            @Override
            public void onNext(Frame item) {
                String text = new String(item.getPayload());
                buildLogEntries.add(new BuildLogEntry(ZonedDateTime.now(), text));
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }
        });

        try {
            latch.await();
        }
        catch (InterruptedException e) {
            throw new LocalCIException("Interrupted while executing Docker command: " + String.join(" ", command), e);
        }
        return buildLogEntries;
    }

    private void checkPath(String path) {
        if (path == null || path.contains("..") || !path.matches("[a-zA-Z0-9_*./-]+")) {
            throw new LocalCIException("Invalid path: " + path);
        }
    }
}
