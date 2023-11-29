package de.tum.in.www1.artemis.service.connectors.localci;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
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
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;

import de.tum.in.www1.artemis.domain.BuildLogEntry;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.service.connectors.aeolus.ScriptAction;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationService.RepositoryCheckoutPath;

/**
 * This service contains methods that are used to interact with the Docker containers when executing build jobs in the local CI system.
 * It is closely related to the {@link LocalCIBuildJobExecutionService} which contains the methods that are used to execute the build jobs.
 */
@Service
@Profile("localci")
public class LocalCIContainerService {

    private final Logger log = LoggerFactory.getLogger(LocalCIContainerService.class);

    private final DockerClient dockerClient;

    private final HostConfig hostConfig;

    @Value("${artemis.continuous-integration.build.images.java.default}")
    String dockerImage;

    @Value("${artemis.continuous-integration.local-cis-build-scripts-path}")
    String localCIBuildScriptBasePath;

    public LocalCIContainerService(DockerClient dockerClient, HostConfig hostConfig) {
        this.dockerClient = dockerClient;
        this.hostConfig = hostConfig;
    }

    /**
     * Configure a container with the Docker image, the container name, the binds, and the branch to checkout, and set the command that runs when the container starts.
     *
     * @param containerName the name of the container to be created
     * @param branch        the branch to checkout
     * @param commitHash    the commit hash to checkout. If it is null, the latest commit of the branch will be checked out.
     * @param image         the Docker image to use for the container
     * @return {@link CreateContainerResponse} that can be used to start the container
     */
    public CreateContainerResponse configureContainer(String containerName, String branch, String commitHash, String image) {
        return dockerClient.createContainerCmd(image).withName(containerName).withHostConfig(hostConfig)
                .withEnv("ARTEMIS_BUILD_TOOL=gradle", "ARTEMIS_DEFAULT_BRANCH=" + branch, "ARTEMIS_ASSIGNMENT_REPOSITORY_COMMIT_HASH=" + (commitHash != null ? commitHash : ""))
                // Command to run when the container starts. This is the command that will be executed in the container's main process, which runs in the foreground and blocks the
                // container from exiting until it finishes.
                // It waits until the script that is running the tests (see below execCreateCmdResponse) is completed, and until the result files are extracted which is indicated
                // by the creation of a file "stop_container.txt" in the container's root directory.
                // .withCmd("sh", "-c", "while [ ! -f /stop_container.txt ]; do sleep 0.5; done")
                .withCmd("tail", "-f", "/dev/null") // Activate for debugging purposes instead of the above command to get a running container that you can peek into using
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
        return executeDockerCommand(containerId, true, true, "sh", "script.sh");
    }

    /**
     * Retrieve an archive from a running Docker container.
     *
     * @param containerId the id of the container.
     * @param path        the path to the file or directory to be retrieved.
     * @return a {@link TarArchiveInputStream} that can be used to read the archive.
     */
    public TarArchiveInputStream getArchiveFromContainer(String containerId, String path) {
        try {
            return new TarArchiveInputStream(dockerClient.copyArchiveFromContainerCmd(containerId, path).exec());
        }
        catch (NotFoundException e) {
            return null;
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
    public String getCommitHashOfBranch(String containerId, LocalCIBuildJobExecutionService.LocalCIBuildJobRepositoryType repositoryType, String branchName,
            ProgrammingLanguage programmingLanguage) throws IOException {
        // Get an input stream of the file in .git folder of the repository that contains the current commit hash of the branch.

        String repositoryCheckoutPath = RepositoryCheckoutPath.valueOf(repositoryType.toString().toUpperCase()).forProgrammingLanguage(programmingLanguage);
        TarArchiveInputStream repositoryTarInputStream;

        if (repositoryCheckoutPath == null) {
            repositoryTarInputStream = getArchiveFromContainer(containerId, "/repositories/.git/refs/heads/" + branchName);
        }
        else {
            repositoryTarInputStream = getArchiveFromContainer(containerId, "/repositories/" + repositoryCheckoutPath + "/.git/refs/heads/" + branchName);
        }

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
     * Copy the repositories and build script from the Artemis container to the build job container.
     *
     * @param buildJobContainerId                    the id of the build job container
     * @param assignmentRepositoryPath               the path to the assignment repository
     * @param testRepositoryPath                     the path to the test repository
     * @param auxiliaryRepositoriesPaths             the paths to the auxiliary repositories
     * @param auxiliaryRepositoryCheckoutDirectories the names of the auxiliary repositories
     * @param buildScriptPath                        the path to the build script
     */
    public void populateBuildJobContainer(String buildJobContainerId, Path assignmentRepositoryPath, Path testRepositoryPath, Path[] auxiliaryRepositoriesPaths,
            String[] auxiliaryRepositoryCheckoutDirectories, Path buildScriptPath, ProgrammingLanguage programmingLanguage) {

        String testCheckoutPath = "repositories/" + RepositoryCheckoutPath.TEST.forProgrammingLanguage(programmingLanguage);
        String assignmentCheckoutPath = "repositories/" + RepositoryCheckoutPath.ASSIGNMENT.forProgrammingLanguage(programmingLanguage);

        addAndPrepareDirectory(buildJobContainerId, testRepositoryPath, testCheckoutPath);
        addAndPrepareDirectory(buildJobContainerId, assignmentRepositoryPath, assignmentCheckoutPath);
        for (int i = 0; i < auxiliaryRepositoriesPaths.length; i++) {
            addAndPrepareDirectory(buildJobContainerId, auxiliaryRepositoriesPaths[i], "repositories/" + auxiliaryRepositoryCheckoutDirectories[i]);
        }
        convertDosFilesToUnix("repositories/", buildJobContainerId);

        addAndPrepareDirectory(buildJobContainerId, buildScriptPath, "script.sh");
        convertDosFilesToUnix("script.sh", buildJobContainerId);
    }

    private void addAndPrepareDirectory(String containerId, Path repositoryPath, String newDirectoryName) {
        copyToContainer(repositoryPath.toString(), containerId);
        renameDirectoryOrFile(containerId, repositoryPath.getFileName().toString(), newDirectoryName);
    }

    private void renameDirectoryOrFile(String containerId, String oldName, String newName) {
        executeDockerCommand(containerId, false, false, "mv", oldName, newName);
    }

    private void convertDosFilesToUnix(String path, String containerId) {
        executeDockerCommand(containerId, false, false, "sh", "-c", "find " + path + " -type f -exec sed -i 's/\\r$//' {} \\;");
    }

    private void copyToContainer(String sourcePath, String containerId) {
        try (InputStream uploadStream = new ByteArrayInputStream(createTarArchive(sourcePath).toByteArray())) {
            dockerClient.copyArchiveToContainerCmd(containerId).withRemotePath("/").withTarInputStream(uploadStream).exec();
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

    private List<BuildLogEntry> executeDockerCommand(String containerId, boolean attachStdout, boolean attachStderr, String... command) {
        ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId).withAttachStdout(attachStdout).withAttachStderr(attachStderr).withCmd(command).exec();
        List<BuildLogEntry> buildLogEntries = new ArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);
        dockerClient.execStartCmd(execCreateCmdResponse.getId()).exec(new ResultCallback.Adapter<>() {

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

    /**
     * Creates a build script for a given programming exercise.
     * The build script is stored in a file in the local-ci-scripts directory.
     * The build script is used to build the programming exercise in a Docker container.
     *
     * @param participation the participation for which to create the build script
     * @return the path to the build script file
     */
    public Path createBuildScript(ProgrammingExerciseParticipation participation) {
        ProgrammingExercise programmingExercise = participation.getProgrammingExercise();
        boolean hasSequentialTestRuns = programmingExercise.hasSequentialTestRuns();

        List<ScriptAction> actions;

        try {
            actions = programmingExercise.getWindfile().getScriptActions();
        }
        catch (NullPointerException e) {
            actions = null;
        }

        Path scriptsPath = Path.of(localCIBuildScriptBasePath);

        if (!Files.exists(scriptsPath)) {
            try {
                Files.createDirectory(scriptsPath);
            }
            catch (IOException e) {
                throw new LocalCIException("Failed to create directory for local CI scripts", e);
            }
        }

        Path buildScriptPath = scriptsPath.toAbsolutePath().resolve(participation.getId().toString() + "-build.sh");

        StringBuilder buildScript = new StringBuilder("""
                #!/bin/bash
                cd /repositories
                """);

        if (actions != null) {
            actions.forEach(action -> buildScript.append(action.getScript()).append("\n"));
        }
        else {
            // Windfile actions are not defined, use default build script
            switch (programmingExercise.getProgrammingLanguage()) {
                case JAVA, KOTLIN -> scriptForJavaKotlin(programmingExercise, buildScript, hasSequentialTestRuns);
                case PYTHON -> scriptForPython(buildScript);
                default -> throw new IllegalArgumentException("No build stage setup for programming language " + programmingExercise.getProgrammingLanguage());
            }
        }

        try {
            FileUtils.writeStringToFile(buildScriptPath.toFile(), buildScript.toString(), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new LocalCIException("Failed to create build script file", e);
        }

        return buildScriptPath;
    }

    private void scriptForJavaKotlin(ProgrammingExercise programmingExercise, StringBuilder buildScript, boolean hasSequentialTestRuns) {
        boolean isMaven = ProjectType.isMavenProject(programmingExercise.getProjectType());

        if (hasSequentialTestRuns) {
            if (isMaven) {
                buildScript.append("""
                        cd structural
                        mvn clean test
                        if [ $? -eq 0 ]; then
                            cd ..
                            cd behavior
                            mvn clean test
                        fi
                        cd ..
                        """);
            }
            else {
                buildScript.append("""
                        chmod +x gradlew
                        ./gradlew clean structuralTests
                        if [ $? -eq 0 ]; then
                            ./gradlew behaviorTests
                        fi
                        """);
            }
        }
        else {
            if (isMaven) {
                buildScript.append("""
                        mvn clean test
                        """);
            }
            else {
                buildScript.append("""
                        chmod +x gradlew
                        ./gradlew clean test
                        """);
            }
        }
    }

    private void scriptForPython(StringBuilder buildScript) {
        buildScript.append("""
                python3 -m compileall . -q || error=true
                if [ ! $error ]
                then
                    pytest --junitxml=test-reports/results.xml
                else
                    exit 1
                fi
                """);
    }

    /**
     * Deletes the build script for a given programming exercise.
     * The build script is stored in a file in the local-ci-scripts directory.
     *
     * @param patricipationID the ID of the participation for which to delete the build script
     */
    public void deleteScriptFile(String patricipationID) {
        Path scriptsPath = Path.of("local-ci-scripts");
        Path buildScriptPath = scriptsPath.resolve(patricipationID + "-build.sh").toAbsolutePath();
        try {
            Files.deleteIfExists(buildScriptPath);
        }
        catch (IOException e) {
            throw new LocalCIException("Failed to delete build script file", e);
        }
    }

}
