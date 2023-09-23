package de.tum.in.www1.artemis.service.connectors.localci;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.FileUtils;
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

import de.tum.in.www1.artemis.domain.AuxiliaryRepository;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
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

    @Value("${artemis.continuous-integration.build.images.java.default}")
    String dockerImage;

    public LocalCIContainerService(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    /**
     * Configure the volumes of the container such that it can access the assignment repository, the test repository, and the build script.
     *
     * @param assignmentRepositoryPath   the path to the assignment repository in the file system
     * @param testRepositoryPath         the path to the test repository in the file system
     * @param auxiliaryRepositoriesPaths the paths to the auxiliary repositories in the file system
     * @param auxiliaryRepositoryNames   the names of the auxiliary repositories
     * @param buildScriptPath            the path to the build script in the file system
     * @return the host configuration for the container containing the binds to the assignment repository, the test repository, and the build script
     */
    public HostConfig createVolumeConfig(Path assignmentRepositoryPath, Path testRepositoryPath, Path[] auxiliaryRepositoriesPaths, String[] auxiliaryRepositoryNames,
            Path buildScriptPath) {

        Bind[] binds = new Bind[3 + auxiliaryRepositoriesPaths.length];
        binds[0] = new Bind(assignmentRepositoryPath.toString(), new Volume("/" + LocalCIBuildJobExecutionService.LocalCIBuildJobRepositoryType.ASSIGNMENT + "-repository"));
        binds[1] = new Bind(testRepositoryPath.toString(), new Volume("/" + LocalCIBuildJobExecutionService.LocalCIBuildJobRepositoryType.TEST + "-repository"));
        for (int i = 0; i < auxiliaryRepositoriesPaths.length; i++) {
            binds[2 + i] = new Bind(auxiliaryRepositoriesPaths[i].toString(), new Volume("/" + auxiliaryRepositoryNames[i] + "-repository"));
        }
        binds[2 + auxiliaryRepositoriesPaths.length] = new Bind(buildScriptPath.toString(), new Volume("/script.sh"));

        return HostConfig.newHostConfig().withAutoRemove(true).withBinds(binds); // Automatically remove the container when it exits.
    }

    /**
     * Configure a container with the Docker image, the container name, the binds, and the branch to checkout, and set the command that runs when the container starts.
     *
     * @param containerName the name of the container to be created
     * @param volumeConfig  the host configuration for the container containing the binds to the assignment repository, the test repository, and the build script
     * @param branch        the branch to checkout
     * @param commitHash    the commit hash to checkout. If it is null, the latest commit of the branch will be checked out.
     * @return {@link CreateContainerResponse} that can be used to start the container
     */
    public CreateContainerResponse configureContainer(String containerName, HostConfig volumeConfig, String branch, String commitHash) {
        return dockerClient.createContainerCmd(dockerImage).withName(containerName).withHostConfig(volumeConfig)
                .withEnv("ARTEMIS_BUILD_TOOL=gradle", "ARTEMIS_DEFAULT_BRANCH=" + branch, "ARTEMIS_ASSIGNMENT_REPOSITORY_COMMIT_HASH=" + (commitHash != null ? commitHash : ""))
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
            }
        });

        try {
            log.info("Started running the build script for build job in container with id {}", containerId);
            // Block until the latch reaches 0 or until the thread is interrupted.
            latch.await();
        }
        catch (InterruptedException e) {
            throw new LocalCIException("Interrupted while waiting for command to complete", e);
        }
    }

    /**
     * Retrieve an archive from a running Docker container.
     *
     * @param containerId the id of the container.
     * @param path        the path to the file or directory to be retrieved.
     * @return a {@link TarArchiveInputStream} that can be used to read the archive.
     */
    public TarArchiveInputStream getArchiveFromContainer(String containerId, String path) {
        return new TarArchiveInputStream(dockerClient.copyArchiveFromContainerCmd(containerId, path).exec());
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
        TarArchiveInputStream repositoryTarInputStream = getArchiveFromContainer(containerId,
                "/repositories/" + repositoryType.toString() + "-repository/.git/refs/heads/" + branchName);
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

    /**
     * Creates a build script for a given programming exercise.
     * The build script is stored in a file in the local-ci-scripts directory.
     * The build script is used to build the programming exercise in a Docker container.
     *
     * @param programmingExercise   the programming exercise for which to create the build script
     * @param auxiliaryRepositories the auxiliary repositories of the programming exercise
     * @return the path to the build script file
     */
    public Path createBuildScript(ProgrammingExercise programmingExercise, List<AuxiliaryRepository> auxiliaryRepositories) {
        Long programmingExerciseId = programmingExercise.getId();
        boolean hasAuxiliaryRepositories = auxiliaryRepositories != null && !auxiliaryRepositories.isEmpty();

        Path scriptsPath = Path.of("local-ci-scripts");

        if (!Files.exists(scriptsPath)) {
            try {
                Files.createDirectory(scriptsPath);
            }
            catch (IOException e) {
                throw new LocalCIException("Failed to create directory for local CI scripts", e);
            }
        }

        Path buildScriptPath = scriptsPath.toAbsolutePath().resolve(programmingExerciseId.toString() + "-build.sh");

        StringBuilder buildScript = new StringBuilder("""
                #!/bin/bash
                mkdir /repositories
                cd /repositories
                """);

        // Checkout tasks
        buildScript.append("""
                git clone --depth 1 --branch $ARTEMIS_DEFAULT_BRANCH file:///test-repository
                git clone --depth 1 --branch $ARTEMIS_DEFAULT_BRANCH file:///assignment-repository
                """);

        if (hasAuxiliaryRepositories) {
            for (AuxiliaryRepository auxiliaryRepository : auxiliaryRepositories) {
                buildScript.append("git clone --depth 1 --branch $ARTEMIS_DEFAULT_BRANCH file:///").append(auxiliaryRepository.getName()).append("-repository\n");
            }
        }

        buildScript.append("""
                cd assignment-repository
                if [ -n "$ARTEMIS_ASSIGNMENT_REPOSITORY_COMMIT_HASH" ]; then
                    git fetch --depth 1 origin "$ARTEMIS_ASSIGNMENT_REPOSITORY_COMMIT_HASH"
                    git checkout "$ARTEMIS_ASSIGNMENT_REPOSITORY_COMMIT_HASH"
                fi
                mkdir /repositories/test-repository/assignment
                cp -a /repositories/assignment-repository/. /repositories/test-repository/assignment/
                """);

        // Copy auxiliary repositories to checkout directories
        if (hasAuxiliaryRepositories) {
            for (AuxiliaryRepository auxiliaryRepository : auxiliaryRepositories) {
                buildScript.append("cp -a /repositories/").append(auxiliaryRepository.getName()).append("-repository/. /repositories/test-repository/")
                        .append(auxiliaryRepository.getCheckoutDirectory()).append("/\n");
            }
        }

        buildScript.append("cd /repositories/test-repository\n");

        // programming language specific tasks
        buildScript.append("""
                chmod +x gradlew
                sed -i -e 's/\\r$//' gradlew
                ./gradlew clean test""");

        try {
            FileUtils.writeStringToFile(buildScriptPath.toFile(), buildScript.toString(), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new LocalCIException("Failed to create build script file", e);
        }

        return buildScriptPath;
    }

    /**
     * Deletes the build script for a given programming exercise.
     * The build script is stored in a file in the local-ci-scripts directory.
     *
     * @param exerciseID the ID of the programming exercise for which to delete the build script
     */
    public void deleteScriptFile(String exerciseID) {
        Path scriptsPath = Path.of("local-ci-scripts");
        Path buildScriptPath = scriptsPath.resolve(exerciseID + "-build.sh").toAbsolutePath();
        try {
            Files.deleteIfExists(buildScriptPath);
        }
        catch (IOException e) {
            throw new LocalCIException("Failed to delete build script file", e);
        }
    }
}
