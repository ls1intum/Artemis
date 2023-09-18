package de.tum.in.www1.artemis.service.connectors.localci;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
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
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildResult;
import de.tum.in.www1.artemis.service.programming.ProgrammingMessagingService;
import de.tum.in.www1.artemis.web.websocket.programmingSubmission.BuildTriggerWebsocketError;

/**
 * This service is responsible for adding build jobs to the local CI executor service.
 * It handles timeouts as well as exceptions that occur during the execution of the build job.
 */
@Service
@Profile("localci")
public class LocalCIBuildJobManagementService {

    private final Logger log = LoggerFactory.getLogger(LocalCIBuildJobManagementService.class);

    private final LocalCIBuildJobExecutionService localCIBuildJobExecutionService;

    private final ExecutorService localCIBuildExecutorService;

    private final ProgrammingMessagingService programmingMessagingService;

    private final LocalCIBuildPlanService localCIBuildPlanService;

    private final LocalCIContainerService localCIContainerService;

    private final LocalCIDockerService localCIDockerService;

    @Value("${artemis.continuous-integration.timeout-seconds:120}")
    private int timeoutSeconds;

    @Value("${artemis.continuous-integration.asynchronous:true}")
    private boolean runBuildJobsAsynchronously;

    @Value("${artemis.continuous-integration.build.images.java.default}")
    private String dockerImage;

    public LocalCIBuildJobManagementService(LocalCIBuildJobExecutionService localCIBuildJobExecutionService, ExecutorService localCIBuildExecutorService,
            ProgrammingMessagingService programmingMessagingService, LocalCIBuildPlanService localCIBuildPlanService, LocalCIContainerService localCIContainerService,
            LocalCIDockerService localCIDockerService) {
        this.localCIBuildJobExecutionService = localCIBuildJobExecutionService;
        this.localCIBuildExecutorService = localCIBuildExecutorService;
        this.programmingMessagingService = programmingMessagingService;
        this.localCIBuildPlanService = localCIBuildPlanService;
        this.localCIContainerService = localCIContainerService;
        this.localCIDockerService = localCIDockerService;
    }

    /**
     * Submit a build job for a given participation to the executor service.
     *
     * @param participation The participation of the repository for which the build job should be executed.
     * @param commitHash    The commit hash of the submission that led to this build. If it is "null", the latest commit of the repository will be used.
     * @return A future that will be completed with the build result.
     * @throws LocalCIException If the build job could not be submitted to the executor service.
     */
    public CompletableFuture<LocalCIBuildResult> addBuildJobToQueue(ProgrammingExerciseParticipation participation, String commitHash) {
        ProjectType projectType = participation.getProgrammingExercise().getProjectType();
        if (projectType == null || !projectType.isGradle()) {
            throw new LocalCIException("Project type must be Gradle.");
        }

        // Check if the Docker image is available. It should be available, because it is pulled during the creation of the programming exercise.
        localCIDockerService.pullDockerImage(dockerImage);

        // Prepare the Docker container name before submitting the build job to the executor service, so we can remove the container if something goes wrong.
        String containerName = "artemis-local-ci-" + participation.getId() + "-" + ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));

        // Prepare a Callable that will later be called. It contains the actual steps needed to execute the build job.
        Callable<LocalCIBuildResult> buildJob = () -> localCIBuildJobExecutionService.runBuildJob(participation, commitHash, containerName);

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
            catch (RejectedExecutionException | CancellationException | ExecutionException | InterruptedException e) {
                // RejectedExecutionException is thrown if the queue size limit (defined in "artemis.continuous-integration.queue-size-limit") is reached.
                finishBuildJobExceptionally(participation, commitHash, containerName, e);
                // Wrap the exception in a CompletionException so that the future is completed exceptionally and the thenAccept block is not run.
                // This CompletionException will not resurface anywhere else as it is thrown in this completable future's separate thread.
                throw new CompletionException(e);
            }
        });

        // Update the build plan status to "QUEUED".
        localCIBuildPlanService.updateBuildPlanStatus(participation, ContinuousIntegrationService.BuildStatus.QUEUED);

        return futureResult;
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
    private void finishBuildJobExceptionally(ProgrammingExerciseParticipation participation, String commitHash, String containerName, Exception exception) {
        log.error("Error while building and testing commit {} in repository {}", commitHash, participation.getRepositoryUrl(), exception);

        // Set the build status to "INACTIVE" to indicate that the build is not running anymore.
        localCIBuildPlanService.updateBuildPlanStatus(participation, ContinuousIntegrationService.BuildStatus.INACTIVE);

        // Notify the user, that the build job produced an exception.
        BuildTriggerWebsocketError error = new BuildTriggerWebsocketError(exception.getMessage(), participation.getId());
        // This cast to Participation is safe as the participation is either a ProgrammingExerciseStudentParticipation, a TemplateProgrammingExerciseParticipation, or a
        // SolutionProgrammingExerciseParticipation, which all extend Participation.
        programmingMessagingService.notifyUserAboutSubmissionError((Participation) participation, error);

        localCIContainerService.stopContainer(containerName);
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
                    // Something went wrong while executing the build job.
                    // The exception is stored in the Future and will resurface as an ExecutionException when running "future.get()" below.
                    throw new CompletionException(e);
                }
            });

            try {
                // When the build job is called, wait for the result or for the timeout to pass.
                return future.get(timeoutSeconds, TimeUnit.SECONDS);
            }
            catch (ExecutionException | InterruptedException | TimeoutException e) {
                // Cancel the future if it is not completed or cancelled yet.
                future.cancel(true);
                // This exception will resurface in the catch block of "localCIBuildExecutorService.submit(timedBuildJob).get()"
                // where the container is stopped and the user is notified.
                throw e;
            }
        }
    }

}
