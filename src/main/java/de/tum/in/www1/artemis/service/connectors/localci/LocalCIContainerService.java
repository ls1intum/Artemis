package de.tum.in.www1.artemis.service.connectors.localci;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

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
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;

import de.tum.in.www1.artemis.config.localvcci.LocalCIConfiguration;
import de.tum.in.www1.artemis.exception.LocalCIException;

/**
 * This service contains methods that are used to interact with the Docker containers when executing build jobs in the local CI system.
 * It is closely related to the {@link LocalCIBuildJobExecutionService} which contains the methods that are used to execute the build jobs.
 */
@Service
@Profile("localci")
public class LocalCIContainerService {

    private final Logger log = LoggerFactory.getLogger(LocalCIContainerService.class);

    private final DockerClient dockerClient;

    /**
     * The Path to the script file located in the resources folder. The script file contains the steps that run the tests on the Docker container.
     * This path is provided as a Bean, because the retrieval is quite costly in the production environment (see {@link LocalCIConfiguration#buildScriptFilePath()}).
     */
    private final Path buildScriptFilePath;

    @Value("${artemis.continuous-integration.build.images.java.default}")
    String dockerImage;

    public LocalCIContainerService(DockerClient dockerClient, Path buildScriptFilePath) {
        this.dockerClient = dockerClient;
        this.buildScriptFilePath = buildScriptFilePath;
    }

    /**
     * Configure the volumes of the container such that it can access the assignment repository, the test repository, and the build script.
     *
     * @param assignmentRepositoryPath the path to the assignment repository in the file system
     * @param testRepositoryPath       the path to the test repository in the file system
     * @return the host configuration for the container containing the binds to the assignment repository, the test repository, and the build script
     */
    public HostConfig createVolumeConfig(Path assignmentRepositoryPath, Path testRepositoryPath) {
        return HostConfig.newHostConfig().withAutoRemove(true) // Automatically remove the container when it exits.
                .withBinds(
                        new Bind(assignmentRepositoryPath.toString(), new Volume("/" + LocalCIBuildJobExecutionService.LocalCIBuildJobRepositoryType.ASSIGNMENT + "-repository")),
                        new Bind(testRepositoryPath.toString(), new Volume("/" + LocalCIBuildJobExecutionService.LocalCIBuildJobRepositoryType.TEST + "-repository")),
                        new Bind(buildScriptFilePath.toString(), new Volume("/script.sh")));
    }

    /**
     * Configure a container with the Docker image, the container name, the binds, and the branch to checkout, and set the command that runs when the container starts.
     *
     * @param containerName the name of the container to be created
     * @param volumeConfig  the host configuration for the container containing the binds to the assignment repository, the test repository, and the build script
     * @param branch        the branch to checkout
     * @return {@link CreateContainerResponse} that can be used to start the container
     */
    public CreateContainerResponse configureContainer(String containerName, HostConfig volumeConfig, String branch) {
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

    /**
     * Start the container with the given ID.
     *
     * @param containerId the ID of the container to be started
     */
    public void startContainer(String containerId) {
        dockerClient.startContainerCmd(containerId).exec();
    }

    /**
     * Run the script specified in the container's bind mount and wait for it to finish before returning.
     *
     * @param containerId the id of the container in which the script should be run
     */
    public void runScriptInContainer(String containerId) {
        // The "sh script.sh" execution command specified here is run inside the container as an additional process. This command runs in the background, independent of the
        // container's
        // main process. The execution command can run concurrently with the main process. This setup with the ExecCreateCmdResponse gives us the ability to wait in code until the
        // command has finished before trying to extract the results.
        ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId).withAttachStdout(true).withAttachStderr(true).withCmd("sh", "script.sh").exec();

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

    /**
     * Retrieve the commit hash of the latest commit to a given repository on a given container for a given branch.
     * This is the commit hash that was checked out by the build job.
     *
     * @param containerId    the id of the container in which the repository is located
     * @param repositoryType the type of the repository, either "assignment" or "test"
     * @param branchName     the name of the branch for which the commit hash should be retrieved
     * @return the commit hash of the latest commit to the repository on the container for the given branch
     * @throws IOException if no commit hash could be retrieved
     */
    public String getCommitHashOfBranch(String containerId, LocalCIBuildJobExecutionService.LocalCIBuildJobRepositoryType repositoryType, String branchName) throws IOException {
        // Get an input stream of the file in .git folder of the repository that contains the current commit hash of the branch.
        TarArchiveInputStream repositoryTarInputStream = new TarArchiveInputStream(
                dockerClient.copyArchiveFromContainerCmd(containerId, "/repositories/" + repositoryType.toString() + "-repository/.git/refs/heads/" + branchName).exec());
        repositoryTarInputStream.getNextTarEntry();
        String commitHash = IOUtils.toString(repositoryTarInputStream, StandardCharsets.UTF_8).replace("\n", "");
        repositoryTarInputStream.close();
        return commitHash;
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
        ExecCreateCmdResponse createStopContainerFileCmdResponse = dockerClient.execCreateCmd(containerId).withCmd("touch", "stop_container.txt").exec();
        dockerClient.execStartCmd(createStopContainerFileCmdResponse.getId()).exec(new ResultCallback.Adapter<>());
    }
}
