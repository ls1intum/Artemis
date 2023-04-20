package de.tum.in.www1.artemis.service.connectors.localci;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.exception.localvc.LocalVCInternalException;
import de.tum.in.www1.artemis.service.ResourceLoaderService;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildResult;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCRepositoryUrl;
import de.tum.in.www1.artemis.service.connectors.vcs.VersionControlService;
import de.tum.in.www1.artemis.service.programming.ProgrammingMessagingService;

/**
 * Service for submitting build jobs to the executor service.
 */
@Service
@Profile("localci")
public class LocalCIExecutorService {

    private final Logger log = LoggerFactory.getLogger(LocalCIExecutorService.class);

    private final Optional<VersionControlService> versionControlService;

    private final ExecutorService localCIBuildExecutorService;

    private final ScheduledExecutorService localCIBuildTimeoutExecutorService;

    private final LocalCIBuildJobService localCIBuildJobService;

    private final ResourceLoaderService resourceLoaderService;

    private final LocalCIBuildPlanService localCIBuildPlanService;

    private final ProgrammingMessagingService programmingMessagingService;

    @Value("${artemis.version-control.url}")
    private URL localVCBaseUrl;

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private String localVCBasePath;

    @Value("${artemis.continuous-integration.timeout-seconds:60}")
    private int timeoutSeconds;

    public LocalCIExecutorService(Optional<VersionControlService> versionControlService, ExecutorService localCIBuildExecutorService,
            ScheduledExecutorService localCIBuildTimeoutExecutorService, LocalCIBuildJobService localCIBuildJobService, ResourceLoaderService resourceLoaderService,
            LocalCIBuildPlanService localCIBuildPlanService, ProgrammingMessagingService programmingMessagingService) {
        this.versionControlService = versionControlService;
        this.localCIBuildExecutorService = localCIBuildExecutorService;
        this.localCIBuildTimeoutExecutorService = localCIBuildTimeoutExecutorService;
        this.localCIBuildJobService = localCIBuildJobService;
        this.resourceLoaderService = resourceLoaderService;
        this.localCIBuildPlanService = localCIBuildPlanService;
        this.programmingMessagingService = programmingMessagingService;
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

        LocalVCRepositoryUrl assignmentRepositoryUrl;
        LocalVCRepositoryUrl testsRepositoryUrl;
        try {
            assignmentRepositoryUrl = new LocalVCRepositoryUrl(participation.getRepositoryUrl(), localVCBaseUrl);
            testsRepositoryUrl = new LocalVCRepositoryUrl(participation.getProgrammingExercise().getTestRepositoryUrl(), localVCBaseUrl);
        }
        catch (LocalVCInternalException e) {
            throw new LocalCIException("Error while creating LocalVCRepositoryUrl", e);
        }

        Path assignmentRepositoryPath = assignmentRepositoryUrl.getLocalRepositoryPath(localVCBasePath).toAbsolutePath();
        Path testsRepositoryPath = testsRepositoryUrl.getLocalRepositoryPath(localVCBasePath).toAbsolutePath();

        // Get script file out of resources.
        Path scriptPath = getBuildScriptPath();

        String branch;
        try {
            branch = versionControlService.orElseThrow().getOrRetrieveBranchOfParticipation(participation);
        }
        catch (LocalVCInternalException e) {
            throw new LocalCIException("Error while getting branch of participation", e);
        }

        // Prepare the Docker container before submitting the build job to the executor service, so we can remove the container if something goes wrong.
        String containerId;
        try {
            containerId = localCIBuildJobService.prepareDockerContainer(assignmentRepositoryPath, testsRepositoryPath, scriptPath, branch);
        }
        catch (LocalCIException e) {
            // Remove the temporary build script file.
            localCIBuildJobService.deleteTemporaryBuildScript(scriptPath);
            throw new LocalCIException("Error while preparing Docker container", e);
        }

        // Submit the build job to the executor service. This runs in a separate thread, so it does not block the main thread.
        // Use Future instead of CompletableFuture to be able to interrupt the build job's thread if running the build job takes too long.
        // Canceling a CompletableFuture would merely mark the CompletableFuture as completed exceptionally but steps running inside the CompletableFuture will never throw an
        // InterruptedException and thus never stop execution.
        Future<LocalCIBuildResult> futureResult = localCIBuildExecutorService.submit(() -> {
            try {
                // Add "_BUILDING" to the build plan id to indicate that the build plan is currently building.
                localCIBuildPlanService.updateBuildPlanStatus(participation, ContinuousIntegrationService.BuildStatus.BUILDING);
                return localCIBuildJobService.runBuildJob(participation, containerId, branch, scriptPath);
            }
            catch (LocalCIException e) {
                log.error("Error while running build job", e);
                // Set the build status to "INACTIVE" to indicate that the build is not running anymore.
                localCIBuildPlanService.updateBuildPlanStatus(participation, ContinuousIntegrationService.BuildStatus.INACTIVE);
                // Notify the user, that the build job produced an exception. This is also the case if the build job timed out.
                programmingMessagingService.notifyUserAboutBuildTriggerError(participation, e);
                localCIBuildJobService.stopContainer(containerId);
                localCIBuildJobService.deleteTemporaryBuildScript(scriptPath);
                // Wrap the exception in a CompletionException so that the future is completed exceptionally and the thenAccept block is not run.
                throw new CompletionException(e);
            }
        });

        // Schedule a task that will cancel the CompletableFuture if it is not completed within the timeout period.
        // This will raise an InterruptedException for interruptible steps (i.e. the running of the script in the container).
        // This task to cancel the build after the timeout runs on a separate thread, so it does not block the main thread.
        localCIBuildTimeoutExecutorService.schedule(() -> {
            if (!futureResult.isDone()) {
                log.error("Build job timed out after {} seconds", timeoutSeconds);
                futureResult.cancel(true);
            }
        }, 5, TimeUnit.SECONDS);

        // Add "_QUEUED" to the build plan id to indicate that the build job is queued.
        localCIBuildPlanService.updateBuildPlanStatus(participation, ContinuousIntegrationService.BuildStatus.QUEUED);

        // Convert the Future to a CompletableFuture to simplify the logic needed to process the result after the build finished successfully.
        return toCompletableFuture(futureResult);
    }

    private <T> CompletableFuture<T> toCompletableFuture(Future<T> future) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return future.get();
            }
            catch (InterruptedException | ExecutionException e) {
                throw new CompletionException(e);
            }
        });
    }

    private Path getBuildScriptPath() {
        Path resourcePath = Path.of("templates", "localci", "java", "build_and_run_tests.sh");
        Path scriptPath;
        try {
            scriptPath = resourceLoaderService.getResourceFilePath(resourcePath);
        }
        catch (IOException | URISyntaxException | IllegalArgumentException e) {
            throw new LocalCIException("Could not retrieve build script.", e);
        }

        return scriptPath;
    }
}
