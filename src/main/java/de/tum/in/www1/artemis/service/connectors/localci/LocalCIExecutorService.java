package de.tum.in.www1.artemis.service.connectors.localci;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.exception.localvc.LocalVCInternalException;
import de.tum.in.www1.artemis.service.ResourceLoaderService;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildResult;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCRepositoryUrl;
import de.tum.in.www1.artemis.service.connectors.vcs.VersionControlService;
import de.tum.in.www1.artemis.service.programming.ProgrammingMessagingService;
import de.tum.in.www1.artemis.web.websocket.programmingSubmission.BuildTriggerWebsocketError;

/**
 * Service for submitting build jobs to the executor service.
 */
@Service
@Profile("localci")
public class LocalCIExecutorService {

    private final Logger log = LoggerFactory.getLogger(LocalCIExecutorService.class);

    private final Optional<VersionControlService> versionControlService;

    private final ExecutorService localCIBuildExecutorService;

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

    @Value("${artemis.continuous-integration.asynchronous:true}")
    private boolean runBuildJobsAsynchronously;

    public LocalCIExecutorService(Optional<VersionControlService> versionControlService, ExecutorService localCIBuildExecutorService, LocalCIBuildJobService localCIBuildJobService,
            ResourceLoaderService resourceLoaderService, LocalCIBuildPlanService localCIBuildPlanService, ProgrammingMessagingService programmingMessagingService) {
        this.versionControlService = versionControlService;
        this.localCIBuildExecutorService = localCIBuildExecutorService;
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

        Callable<LocalCIBuildResult> buildJob = () -> {
            // Add "_BUILDING" to the build plan id to indicate that the build plan is currently building.
            localCIBuildPlanService.updateBuildPlanStatus(participation, ContinuousIntegrationService.BuildStatus.BUILDING);
            return localCIBuildJobService.runBuildJob(participation, containerId, branch, scriptPath);
        };

        // Submit the build job to the executor service. This runs in a separate thread, so it does not block the main thread.
        CompletableFuture<LocalCIBuildResult> futureResult = createCompletableFuture(() -> {
            // Submit the task and get a Future.
            // Use Future to be able to interrupt the build job's thread if running the build job takes too long.
            // Canceling a CompletableFuture would merely mark the CompletableFuture as completed exceptionally but steps running inside the CompletableFuture will never throw an
            // InterruptedException and thus never stop execution.
            Future<LocalCIBuildResult> future = localCIBuildExecutorService.submit(buildJob);
            try {
                // Get the result of the build job at the latest after the timeout.
                return future.get(timeoutSeconds, TimeUnit.SECONDS);
            }
            catch (InterruptedException | ExecutionException | TimeoutException e) {
                // The InterruptedException is thrown if the thread is interrupted from somewhere else (e.g. the executor service is shut down).
                // The ExecutionException is thrown if the build job throws an exception (i.e. a LocalCIException in this case).
                // The TimeoutException is thrown if the build job takes too long.

                log.error("Error while running build job", e);

                if (!future.isDone()) {
                    // Cancel the task if it is still running.
                    future.cancel(true);
                }

                // Set the build status to "INACTIVE" to indicate that the build is not running anymore.
                localCIBuildPlanService.updateBuildPlanStatus(participation, ContinuousIntegrationService.BuildStatus.INACTIVE);

                // Notify the user, that the build job produced an exception. This is also the case if the build job timed out.
                log.error("Error while building and testing repository " + participation.getRepositoryUrl());
                BuildTriggerWebsocketError error = new BuildTriggerWebsocketError(
                        e instanceof TimeoutException ? "Build timed out after " + timeoutSeconds + " seconds" : e.getMessage(), participation.getId());
                // This cast to Participation is safe as the participation is either a ProgrammingExerciseStudentParticipation, a TemplateProgrammingExerciseParticipation, or a
                // SolutionProgrammingExerciseParticipation, which all extend Participation.
                programmingMessagingService.notifyUserAboutSubmissionError((Participation) participation, error);

                localCIBuildJobService.stopContainer(containerId);
                localCIBuildJobService.deleteTemporaryBuildScript(scriptPath);

                // Wrap the exception in a CompletionException so that the future is completed exceptionally and the thenAccept block is not run.
                throw new CompletionException(e);
            }
        });

        // Add "_QUEUED" to the build plan id to indicate that the build job is queued.
        localCIBuildPlanService.updateBuildPlanStatus(participation, ContinuousIntegrationService.BuildStatus.QUEUED);

        return futureResult;
    }

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
