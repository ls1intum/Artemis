package de.tum.in.www1.artemis.service.connectors.localci;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.topic.ITopic;

import de.tum.in.www1.artemis.domain.participation.*;
import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildResult;
import de.tum.in.www1.artemis.service.programming.ProgrammingMessagingService;
import de.tum.in.www1.artemis.web.websocket.programmingSubmission.BuildTriggerWebsocketError;

/**
 * This service is responsible for adding build jobs to the Integrated Code Lifecycle executor service.
 * It handles timeouts as well as exceptions that occur during the execution of the build job.
 */
@Service
@Profile("localci")
public class LocalCIBuildJobManagementService {

    private static final Logger log = LoggerFactory.getLogger(LocalCIBuildJobManagementService.class);

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

    @Value("${artemis.continuous-integration.build-container-prefix:local-ci-}")
    private String buildContainerPrefix;

    /**
     * A map that contains all build jobs that are currently running.
     * The key is the id of the build job, the value is the future that will be completed with the build result.
     * This map is unique for each node and contains only the build jobs that are running on this node.
     */
    private final Map<String, Future<LocalCIBuildResult>> runningFutures = new ConcurrentHashMap<>();

    /**
     * A set that contains all build jobs that were cancelled by the user.
     * This set is unique for each node and contains only the build jobs that were cancelled on this node.
     */
    private final Set<String> cancelledBuildJobs = new ConcurrentSkipListSet<>();

    private final ITopic<String> canceledBuildJobsTopic;

    public LocalCIBuildJobManagementService(HazelcastInstance hazelcastInstance, LocalCIBuildJobExecutionService localCIBuildJobExecutionService,
            ExecutorService localCIBuildExecutorService, ProgrammingMessagingService programmingMessagingService, LocalCIBuildPlanService localCIBuildPlanService,
            LocalCIContainerService localCIContainerService, LocalCIDockerService localCIDockerService) {
        this.localCIBuildJobExecutionService = localCIBuildJobExecutionService;
        this.localCIBuildExecutorService = localCIBuildExecutorService;
        this.programmingMessagingService = programmingMessagingService;
        this.localCIBuildPlanService = localCIBuildPlanService;
        this.localCIContainerService = localCIContainerService;
        this.localCIDockerService = localCIDockerService;
        this.canceledBuildJobsTopic = hazelcastInstance.getTopic("canceledBuildJobsTopic");
    }

    /**
     * Add a listener to the canceledBuildJobsTopic that cancels the build job for the given buildJobId.
     * It gets broadcast to all nodes in the cluster. Only the node that is running the build job will cancel it.
     */
    @PostConstruct
    public void addListener() {
        canceledBuildJobsTopic.addMessageListener(message -> {
            String buildJobId = message.getMessageObject();
            if (runningFutures.containsKey(buildJobId)) {
                cancelBuildJob(buildJobId);
            }
        });
    }

    /**
     * Submit a build job for a given participation to the executor service.
     *
     * @param participation               The participation of the repository for which the build job should be executed.
     * @param commitHash                  The commit hash of the submission that led to this build. If it is "null", the latest commit of the repository will be used.
     * @param isRetry                     Whether this build job is a retry of a previous build job.
     * @param isPushToTestOrAuxRepository Defines if the build job is triggered by a push to a test or auxiliary repository.
     * @param buildJobId                  The id of the build job.
     * @param dockerImage                 The Docker image that should be used for the build job container.
     * @return A future that will be completed with the build result.
     * @throws LocalCIException If the build job could not be submitted to the executor service.
     */
    public CompletableFuture<LocalCIBuildResult> executeBuildJob(ProgrammingExerciseParticipation participation, String commitHash, boolean isRetry,
            boolean isPushToTestOrAuxRepository, String buildJobId, String dockerImage) throws LocalCIException {

        // Check if the Docker image is available. If not, pull it.
        localCIDockerService.pullDockerImage(dockerImage);

        // Prepare the Docker container name before submitting the build job to the executor service, so we can remove the container if something goes wrong.
        String containerName = buildContainerPrefix + participation.getId() + "-" + ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));

        // Prepare a Callable that will later be called. It contains the actual steps needed to execute the build job.
        Callable<LocalCIBuildResult> buildJob = () -> localCIBuildJobExecutionService.runBuildJob(participation, commitHash, isPushToTestOrAuxRepository, containerName,
                dockerImage);

        /*
         * Submit the build job to the executor service. This runs in a separate thread, so it does not block the main thread.
         * createCompletableFuture() is only used to provide a way to run build jobs synchronously for testing and debugging purposes and depends on the
         * artemis.continuous-integration.asynchronous environment variable.
         * Usually, when using asynchronous build jobs, it will just resolve to "CompletableFuture.supplyAsync".
         */
        Future<LocalCIBuildResult> future = localCIBuildExecutorService.submit(buildJob);
        runningFutures.put(buildJobId, future);

        CompletableFuture<LocalCIBuildResult> futureResult = createCompletableFuture(() -> {
            try {
                return future.get(timeoutSeconds, TimeUnit.SECONDS);
            }
            catch (RejectedExecutionException | CancellationException | ExecutionException | InterruptedException | TimeoutException e) {
                // RejectedExecutionException is thrown if the queue size limit (defined in "artemis.continuous-integration.queue-size-limit") is reached.
                // Wrap the exception in a CompletionException so that the future is completed exceptionally and the thenAccept block is not run.
                // This CompletionException will not resurface anywhere else as it is thrown in this completable future's separate thread.
                if (cancelledBuildJobs.contains(buildJobId)) {
                    finishCancelledBuildJob(participation, buildJobId, containerName);
                    throw new CompletionException("Build job with id " + buildJobId + " was cancelled.", e);
                }
                else {
                    finishBuildJobExceptionally(participation, commitHash, containerName, isRetry, e);
                    throw new CompletionException(e);
                }
            }
        });
        // Update the build plan status to "QUEUED".
        localCIBuildPlanService.updateBuildPlanStatus(participation, ContinuousIntegrationService.BuildStatus.QUEUED);
        futureResult.whenComplete(((result, throwable) -> runningFutures.remove(buildJobId)));

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
    private void finishBuildJobExceptionally(ProgrammingExerciseParticipation participation, String commitHash, String containerName, boolean isRetry, Exception exception) {
        log.error("Error while building and testing commit {} in repository {}", commitHash, participation.getRepositoryUri(), exception);

        // Set the build status to "INACTIVE" to indicate that the build is not running anymore.
        localCIBuildPlanService.updateBuildPlanStatus(participation, ContinuousIntegrationService.BuildStatus.INACTIVE);

        // Notify the user, that the build job produced an exception.
        BuildTriggerWebsocketError error = new BuildTriggerWebsocketError(exception.getMessage(), participation.getId());
        if (!isRetry) {
            // This cast to Participation is safe as the participation is either a ProgrammingExerciseStudentParticipation, a TemplateProgrammingExerciseParticipation, or a
            // SolutionProgrammingExerciseParticipation, which all extend Participation.
            programmingMessagingService.notifyUserAboutSubmissionError((Participation) participation, error);
        }

        localCIContainerService.deleteScriptFile(containerName);

        localCIContainerService.stopContainer(containerName);
    }

    /**
     * Trigger the cancellation of the build job for the given buildJobId.
     * The listener for the canceledBuildJobsTopic will then cancel the build job.
     *
     * @param buildJobId The id of the build job that should be cancelled.
     */
    public void triggerBuildJobCancellation(String buildJobId) {
        // Publish a message to the topic indicating that the specific build job should be canceled
        canceledBuildJobsTopic.publish(buildJobId);
    }

    /**
     * Cancel the build job for the given buildJobId.
     *
     * @param buildJobId The id of the build job that should be cancelled.
     */
    private void cancelBuildJob(String buildJobId) {
        Future<LocalCIBuildResult> future = runningFutures.get(buildJobId);
        if (future != null) {
            try {
                cancelledBuildJobs.add(buildJobId);
                future.cancel(true); // Attempt to interrupt the build job
            }
            catch (CancellationException e) {
                log.warn("Build job already cancelled or completed for id {}", buildJobId);
            }
        }
        else {
            log.warn("Could not cancel build job with id {} as it was not found in the running build jobs", buildJobId);
        }
    }

    /**
     * Finish the build job if it was cancelled by the user.
     *
     * @param participation The participation of the repository for which the build job was executed.
     * @param buildJobId    The id of the cancelled build job
     * @param containerName The name of the Docker container that was used to execute the build job.
     */
    private void finishCancelledBuildJob(ProgrammingExerciseParticipation participation, String buildJobId, String containerName) {
        log.debug("Build job with id {} in repository {} was cancelled", buildJobId, participation.getRepositoryUri());

        // Set the build status to "INACTIVE" to indicate that the build is not running anymore.
        localCIBuildPlanService.updateBuildPlanStatus(participation, ContinuousIntegrationService.BuildStatus.INACTIVE);

        // Notify the user, that the build job produced an exception.
        BuildTriggerWebsocketError error = new BuildTriggerWebsocketError("Build job was cancelled", participation.getId());
        // This cast to Participation is safe as the participation is either a ProgrammingExerciseStudentParticipation, a TemplateProgrammingExerciseParticipation, or a
        // SolutionProgrammingExerciseParticipation, which all extend Participation.
        programmingMessagingService.notifyUserAboutSubmissionError((Participation) participation, error);

        localCIContainerService.deleteScriptFile(containerName);

        localCIContainerService.stopContainer(containerName);

        cancelledBuildJobs.remove(buildJobId);
    }
}
