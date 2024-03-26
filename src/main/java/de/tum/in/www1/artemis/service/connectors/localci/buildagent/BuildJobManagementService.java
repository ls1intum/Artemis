package de.tum.in.www1.artemis.service.connectors.localci.buildagent;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_BUILDAGENT;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.topic.ITopic;

import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildJobQueueItem;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildResult;

/**
 * This service is responsible for adding build jobs to the Integrated Code Lifecycle executor service.
 * It handles timeouts as well as exceptions that occur during the execution of the build job.
 */
@Service
@Profile(PROFILE_BUILDAGENT)
public class BuildJobManagementService {

    private static final Logger log = LoggerFactory.getLogger(BuildJobManagementService.class);

    private final BuildJobExecutionService buildJobExecutionService;

    private final ExecutorService localCIBuildExecutorService;

    private final BuildJobContainerService buildJobContainerService;

    private final LocalCIDockerService localCIDockerService;

    private final HazelcastInstance hazelcastInstance;

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

    public BuildJobManagementService(HazelcastInstance hazelcastInstance, BuildJobExecutionService buildJobExecutionService, ExecutorService localCIBuildExecutorService,
            BuildJobContainerService buildJobContainerService, LocalCIDockerService localCIDockerService) {
        this.buildJobExecutionService = buildJobExecutionService;
        this.localCIBuildExecutorService = localCIBuildExecutorService;
        this.buildJobContainerService = buildJobContainerService;
        this.localCIDockerService = localCIDockerService;
        this.hazelcastInstance = hazelcastInstance;
    }

    /**
     * Add a listener to the canceledBuildJobsTopic that cancels the build job for the given buildJobId.
     * It gets broadcast to all nodes in the cluster. Only the node that is running the build job will cancel it.
     */
    @PostConstruct
    public void init() {
        ITopic<String> canceledBuildJobsTopic = hazelcastInstance.getTopic("canceledBuildJobsTopic");
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
     * @param buildJobItem The build job that should be executed.
     * @return A future that will be completed with the build result.
     * @throws LocalCIException If the build job could not be submitted to the executor service.
     */
    public CompletableFuture<LocalCIBuildResult> executeBuildJob(LocalCIBuildJobQueueItem buildJobItem) throws LocalCIException {

        // Check if the Docker image is available. If not, pull it.
        localCIDockerService.pullDockerImage(buildJobItem.buildConfig().dockerImage());

        // Prepare the Docker container name before submitting the build job to the executor service, so we can remove the container if something goes wrong.
        String containerName = buildContainerPrefix + buildJobItem.participationId() + "-" + ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));

        // Prepare a Callable that will later be called. It contains the actual steps needed to execute the build job.
        Callable<LocalCIBuildResult> buildJob = () -> buildJobExecutionService.runBuildJob(buildJobItem, containerName);

        /*
         * Submit the build job to the executor service. This runs in a separate thread, so it does not block the main thread.
         * createCompletableFuture() is only used to provide a way to run build jobs synchronously for testing and debugging purposes and depends on the
         * artemis.continuous-integration.asynchronous environment variable.
         * Usually, when using asynchronous build jobs, it will just resolve to "CompletableFuture.supplyAsync".
         */
        Future<LocalCIBuildResult> future = localCIBuildExecutorService.submit(buildJob);
        runningFutures.put(buildJobItem.id(), future);

        CompletableFuture<LocalCIBuildResult> futureResult = createCompletableFuture(() -> {
            try {
                return future.get(timeoutSeconds, TimeUnit.SECONDS);
            }
            catch (RejectedExecutionException | CancellationException | ExecutionException | InterruptedException | TimeoutException e) {
                // RejectedExecutionException is thrown if the queue size limit (defined in "artemis.continuous-integration.queue-size-limit") is reached.
                // Wrap the exception in a CompletionException so that the future is completed exceptionally and the thenAccept block is not run.
                // This CompletionException will not resurface anywhere else as it is thrown in this completable future's separate thread.
                if (cancelledBuildJobs.contains(buildJobItem.id())) {
                    finishCancelledBuildJob(buildJobItem.repositoryInfo().assignmentRepositoryUri(), buildJobItem.id(), containerName);
                    throw new CompletionException("Build job with id " + buildJobItem.id() + " was cancelled.", e);
                }
                else {
                    finishBuildJobExceptionally(buildJobItem.repositoryInfo().assignmentRepositoryUri(), buildJobItem.buildConfig().commitHash(), containerName, e);
                    throw new CompletionException(e);
                }
            }
        });
        futureResult.whenComplete(((result, throwable) -> runningFutures.remove(buildJobItem.id())));

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
     * @param repositoryUri The URI of the repository for which the build job was executed.
     * @param containerName The name of the Docker container that was used to execute the build job.
     * @param exception     The exception that occurred while building and testing the repository.
     */
    private void finishBuildJobExceptionally(String repositoryUri, String commitHash, String containerName, Exception exception) {
        log.error("Error while building and testing commit {} in repository {}", commitHash, repositoryUri, exception);

        buildJobContainerService.stopContainer(containerName);
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
     * @param repositoryUri the URI of the repository for which the build job was cancelled
     * @param buildJobId    The id of the cancelled build job
     * @param containerName The name of the Docker container that was used to execute the build job.
     */
    private void finishCancelledBuildJob(String repositoryUri, String buildJobId, String containerName) {
        log.debug("Build job with id {} in repository {} was cancelled", buildJobId, repositoryUri);

        buildJobContainerService.stopContainer(containerName);

        cancelledBuildJobs.remove(buildJobId);
    }
}
