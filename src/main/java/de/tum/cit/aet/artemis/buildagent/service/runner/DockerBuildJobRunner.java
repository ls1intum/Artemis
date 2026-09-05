package de.tum.cit.aet.artemis.buildagent.service.runner;

import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY;
import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL_CI_RESULTS_DIRECTORY;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.github.dockerjava.api.exception.NotFoundException;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.DockerRunConfig;
import de.tum.cit.aet.artemis.buildagent.service.BuildAgentDockerService;
import de.tum.cit.aet.artemis.buildagent.service.BuildJobContainerService;
import de.tum.cit.aet.artemis.buildagent.service.BuildLogsMap;
import de.tum.cit.aet.artemis.localci.exception.DockerImagePullException;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;

/**
 * LocalCI runner that preserves the existing Docker container execution flow.
 */
@Lazy(false)
@Component
@Profile(PROFILE_BUILDAGENT)
@ConditionalOnProperty(prefix = "artemis.continuous-integration", name = "build-runner", havingValue = "docker", matchIfMissing = true)
public class DockerBuildJobRunner implements BuildJobRunner {

    private static final Logger log = LoggerFactory.getLogger(DockerBuildJobRunner.class);

    private final BuildAgentConfiguration buildAgentConfiguration;

    private final BuildJobContainerService buildJobContainerService;

    private final BuildAgentDockerService buildAgentDockerService;

    private final BuildLogsMap buildLogsMap;

    private final String buildContainerPrefix;

    public DockerBuildJobRunner(BuildAgentConfiguration buildAgentConfiguration, BuildJobContainerService buildJobContainerService, BuildAgentDockerService buildAgentDockerService,
            BuildLogsMap buildLogsMap, @Value("${artemis.continuous-integration.build-container-prefix:local-ci-}") String buildContainerPrefix) {
        this.buildAgentConfiguration = buildAgentConfiguration;
        this.buildJobContainerService = buildJobContainerService;
        this.buildAgentDockerService = buildAgentDockerService;
        this.buildLogsMap = buildLogsMap;
        this.buildContainerPrefix = buildContainerPrefix;
    }

    @Override
    public BuildRunnerType type() {
        return BuildRunnerType.DOCKER;
    }

    @Override
    public BuildRunnerStatus status() {
        if (buildAgentConfiguration.getDockerClient() == null) {
            return BuildRunnerStatus.unavailable("Docker is unavailable");
        }
        try {
            return BuildRunnerStatus.available(buildAgentConfiguration.getDockerClient().versionCmd().exec().getVersion());
        }
        catch (Exception e) {
            return BuildRunnerStatus.unavailable(e.getMessage());
        }
    }

    @Override
    public BuildJobRunnerResult execute(BuildJobQueueItem buildJob, PreparedBuildJob preparedBuildJob) {
        String containerName = executionName(buildJob.id());
        boolean executionCreated = false;
        boolean resultHandedOff = false;
        try {
            try {
                buildAgentDockerService.pullDockerImage(buildJob, buildLogsMap);
            }
            catch (LocalCIException e) {
                String message = "Could not pull Docker image " + buildJob.buildConfig().dockerImage();
                buildLogsMap.appendBuildLogEntry(buildJob.id(), message);
                throw new DockerImagePullException(message, e);
            }

            BuildConfig buildConfig = buildJob.buildConfig();
            DockerRunConfig runConfig = buildConfig.dockerRunConfig() != null ? buildConfig.dockerRunConfig() : new DockerRunConfig(null, null, 0, 0, 0);
            var container = buildJobContainerService.configureContainer(containerName, buildConfig.dockerImage(), buildConfig.buildScript(), runConfig);
            executionCreated = true;
            buildJobContainerService.startContainer(container.getId());

            append(buildJob.id(), "~~~~~~~~~~~~~~~~~~~~ Started Docker execution " + containerName + " ~~~~~~~~~~~~~~~~~~~~");
            append(buildJob.id(), "~~~~~~~~~~~~~~~~~~~~ Populating build environment with repositories and build script ~~~~~~~~~~~~~~~~~~~~");
            buildJobContainerService.populateBuildJobContainer(container.getId(), buildJob.id(), preparedBuildJob.assignmentRepository(), preparedBuildJob.testRepository(),
                    preparedBuildJob.solutionRepository(), preparedBuildJob.auxiliaryRepositories().toArray(Path[]::new),
                    buildJob.repositoryInfo().auxiliaryRepositoryCheckoutDirectories(), buildConfig.programmingLanguage(), buildConfig.assignmentCheckoutPath(),
                    buildConfig.testCheckoutPath(), buildConfig.solutionCheckoutPath());

            append(buildJob.id(), "~~~~~~~~~~~~~~~~~~~~ Executing build script for build job " + buildJob.id() + " ~~~~~~~~~~~~~~~~~~~~");
            int exitCode = buildJobContainerService.runScriptInContainer(container.getId(), buildJob.id());
            ZonedDateTime completedAt = ZonedDateTime.now();
            append(buildJob.id(), "~~~~~~~~~~~~~~~~~~~~ Finished executing build script for build job " + buildJob.id() + " ~~~~~~~~~~~~~~~~~~~~");

            buildJobContainerService.moveResultsToSpecifiedDirectory(container.getId(), buildConfig.resultPaths(),
                    LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + LOCAL_CI_RESULTS_DIRECTORY);
            InputStream archive;
            try {
                archive = buildJobContainerService.getArchiveFromContainer(container.getId(), LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + LOCAL_CI_RESULTS_DIRECTORY,
                        buildJob.id());
            }
            catch (NotFoundException e) {
                log.warn("No result archive was produced by Docker execution {}", containerName, e);
                archive = null;
            }
            BuildJobRunnerResult result = new BuildJobRunnerResult(archive, exitCode, completedAt, () -> buildJobContainerService.stopContainer(containerName));
            resultHandedOff = true;
            return result;
        }
        catch (LocalCIException e) {
            throw e;
        }
        catch (Exception e) {
            throw new LocalCIException("Docker execution failed for build job " + buildJob.id(), e);
        }
        finally {
            if (executionCreated && !resultHandedOff) {
                buildJobContainerService.stopContainer(containerName);
            }
        }
    }

    @Override
    public void cancel(String buildJobId) {
        String containerId = buildJobContainerService.getIDOfRunningContainer(executionName(buildJobId));
        if (containerId != null) {
            buildJobContainerService.stopUnresponsiveContainer(containerId);
        }
    }

    @Override
    public boolean isActive(String buildJobId) {
        return buildJobContainerService.getIDOfRunningContainer(executionName(buildJobId)) != null;
    }

    @Override
    public boolean isFetchingImage(String buildJobId) {
        return buildAgentDockerService.isImagePullInProgress(buildJobId);
    }

    @Override
    public void cleanupOrphans() {
        buildAgentDockerService.cleanUpContainers();
    }

    private String executionName(String buildJobId) {
        return buildContainerPrefix + buildJobId;
    }

    private void append(String buildJobId, String message) {
        buildLogsMap.appendBuildLogEntry(buildJobId, message);
        log.info(message);
    }
}
