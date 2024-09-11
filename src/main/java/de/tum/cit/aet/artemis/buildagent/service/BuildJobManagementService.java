package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.topic.ITopic;

import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;
import de.tum.cit.aet.artemis.core.exception.LocalCIException;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;

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

    private final HazelcastInstance hazelcastInstance;

    private final BuildLogsMap buildLogsMap;

    private final ReentrantLock lock = new ReentrantLock();

    @Value("${artemis.continuous-integration.timeout-seconds:240}")
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
    private final Map<String, Future<BuildResult>> runningFutures = new ConcurrentHashMap<>();

    /**
     * A set that contains all build jobs that were cancelled by the user.
     * This set is unique for each node and contains only the build jobs that were cancelled on this node.
     */
    private final Set<String> cancelledBuildJobs = new ConcurrentSkipListSet<>();

    public BuildJobManagementService(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance, BuildJobExecutionService buildJobExecutionService,
            ExecutorService localCIBuildExecutorService, BuildJobContainerService buildJobContainerService, BuildLogsMap buildLogsMap) {
        this.buildJobExecutionService = buildJobExecutionService;
        this.localCIBuildExecutorService = localCIBuildExecutorService;
        this.buildJobContainerService = buildJobContainerService;
        this.hazelcastInstance = hazelcastInstance;
        this.buildLogsMap = buildLogsMap;
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
            lock.lock();
            try {
                if (runningFutures.containsKey(buildJobId)) {
                    cancelBuildJob(buildJobId);
                }
            }
            finally {
                lock.unlock();
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
    public CompletableFuture<BuildResult> executeBuildJob(BuildJobQueueItem buildJobItem) throws LocalCIException {

        // Prepare the Docker container name before submitting the build job to the executor service, so we can remove the container if something goes wrong.
        String containerName = buildContainerPrefix + buildJobItem.id();

        // Prepare a Callable that will later be called. It contains the actual steps needed to execute the build job.
        Callable<BuildResult> buildJob = () -> buildJobExecutionService.runBuildJob(buildJobItem, containerName);

        /*
         * Submit the build job to the executor service. This runs in a separate thread, so it does not block the main thread.
         * createCompletableFuture() is only used to provide a way to run build jobs synchronously for testing and debugging purposes and depends on the
         * artemis.continuous-integration.asynchronous environment variable.
         * Usually, when using asynchronous build jobs, it will just resolve to "CompletableFuture.supplyAsync".
         * The future is stored in the runningFutures map so that it can be cancelled if needed.
         * We add a lock to prevent the job from being submitted even though it was cancelled.
         */
        lock.lock();
        Future<BuildResult> future;
        try {
            if (cancelledBuildJobs.contains(buildJobItem.id())) {
                finishCancelledBuildJob(buildJobItem.repositoryInfo().assignmentRepositoryUri(), buildJobItem.id(), containerName);
                String msg = "Build job with id " + buildJobItem.id() + " was cancelled before it was submitted to the executor service.";
                buildLogsMap.appendBuildLogEntry(buildJobItem.id(), msg);
                throw new CompletionException(msg, null);
            }
            future = localCIBuildExecutorService.submit(buildJob);
            runningFutures.put(buildJobItem.id(), future);
        }
        finally {
            lock.unlock();
        }

        CompletableFuture<BuildResult> futureResult = createCompletableFuture(() -> {
            try {
                return future.get(timeoutSeconds, TimeUnit.SECONDS);
            }
            catch (Exception e) {
                // RejectedExecutionException is thrown if the queue size limit (defined in "artemis.continuous-integration.queue-size-limit") is reached.
                // Wrap the exception in a CompletionException so that the future is completed exceptionally and the thenAccept block is not run.
                // This CompletionException will not resurface anywhere else as it is thrown in this completable future's separate thread.
                if (cancelledBuildJobs.contains(buildJobItem.id())) {
                    finishCancelledBuildJob(buildJobItem.repositoryInfo().assignmentRepositoryUri(), buildJobItem.id(), containerName);
                    String msg = "Build job with id " + buildJobItem.id() + " was cancelled.";
                    String stackTrace = stackTraceToString(e);
                    buildLogsMap.appendBuildLogEntry(buildJobItem.id(), new BuildLogEntry(ZonedDateTime.now(), msg + "\n" + stackTrace));
                    throw new CompletionException(msg, e);
                }
                else {
                    finishBuildJobExceptionally(buildJobItem.id(), containerName, e);
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
    private CompletableFuture<BuildResult> createCompletableFuture(Supplier<BuildResult> supplier) {
        if (runBuildJobsAsynchronously) {
            // Just use the normal supplyAsync.
            return CompletableFuture.supplyAsync(supplier);
        }
        else {
            // Use a synchronous CompletableFuture, e.g. in the test environment.
            // Otherwise, tests will not wait for the CompletableFuture to complete before asserting on the database.
            CompletableFuture<BuildResult> future = new CompletableFuture<>();
            try {
                BuildResult result = supplier.get();
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
     * @param buildJobId    The id of the build job that failed.
     * @param containerName The name of the Docker container that was used to execute the build job.
     * @param exception     The exception that occurred while building and testing the repository.
     */
    private void finishBuildJobExceptionally(String buildJobId, String containerName, Exception exception) {
        String msg = "Error while executing build job " + buildJobId + ": " + exception.getMessage();
        String stackTrace = stackTraceToString(exception);
        buildLogsMap.appendBuildLogEntry(buildJobId, new BuildLogEntry(ZonedDateTime.now(), msg + "\n" + stackTrace));
        log.error(msg);

        log.info("Getting ID of running container {}", containerName);
        String containerId = buildJobContainerService.getIDOfRunningContainer(containerName);
        log.info("Stopping unresponsive container with ID {}", containerId);
        if (containerId != null) {
            buildJobContainerService.stopUnresponsiveContainer(containerId);
        }
    }

    /**
     * Cancel the build job for the given buildJobId.
     *
     * @param buildJobId The id of the build job that should be cancelled.
     */
    private void cancelBuildJob(String buildJobId) {
        Future<BuildResult> future = runningFutures.get(buildJobId);
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

    private String stackTraceToString(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
}
