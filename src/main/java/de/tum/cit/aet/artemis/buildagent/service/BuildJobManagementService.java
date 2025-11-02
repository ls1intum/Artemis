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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.BuildLogDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;
import de.tum.cit.aet.artemis.core.exception.LocalCIException;
import de.tum.cit.aet.artemis.programming.service.localci.DistributedDataAccessService;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.topic.DistributedTopic;

/**
 * Coordinates submission, tracking, timeout handling, and cancellation of build jobs
 * executed by the Integrated Code Lifecycle build executor.
 *
 * <p>
 * <strong>Responsibilities</strong>
 * </p>
 * <ul>
 * <li>Submit build jobs and expose a {@link CompletableFuture} for their results.</li>
 * <li>Record and stream log messages (incl. timeouts and exceptions) to the build log.</li>
 * <li>Maintain per-node state of running jobs for targeted cancellation.</li>
 * <li>React to cluster-wide “cancel build” events and stop the corresponding job if this node owns it.</li>
 * </ul>
 *
 * <p>
 * <strong>Concurrency model</strong>
 * </p>
 * <ul>
 * <li>{@code runningFutures}/{@code runningFuturesWrapper} are concurrent maps tracking submitted jobs.</li>
 * <li>{@code cancelledBuildJobs} is a concurrent set of job ids that were cancelled on this node.</li>
 * <li>{@link #jobLifecycleLock} serializes the critical sections that
 * (a) receive cancellation events and (b) submit new jobs + register them,
 * preventing a race where a job is cancelled concurrently with (or just before) submission.</li>
 * </ul>
 *
 * <p>
 * <strong>Failure handling</strong>
 * </p>
 * <ul>
 * <li>Timeouts: stop unresponsive containers and log guidance for students and instructors.</li>
 * <li>Exceptions: log details (incl. stack trace), stop containers, and complete futures exceptionally.</li>
 * <li>Cancellation: interrupt job execution (if running), stop containers, and clean up state.</li>
 * </ul>
 */
@Lazy(false)
@Service
@Profile(PROFILE_BUILDAGENT)
public class BuildJobManagementService {

    private static final Logger log = LoggerFactory.getLogger(BuildJobManagementService.class);

    private final BuildJobExecutionService buildJobExecutionService;

    private final BuildAgentConfiguration buildAgentConfiguration;

    private final BuildJobContainerService buildJobContainerService;

    private final DistributedDataAccessService distributedDataAccessService;

    private final BuildLogsMap buildLogsMap;

    /**
     * Guards job lifecycle state transitions that must be atomic across multiple data structures:
     * <ul>
     * <li>Submission (creating and registering {@code runningFutures}).</li>
     * <li>Cancellation event handling (checking {@code runningFutures} and invoking {@link #cancelBuildJob(String)}).</li>
     * </ul>
     *
     * Without this lock, a race is possible:
     * <ol>
     * <li>A cluster cancel event arrives while a job is being submitted.</li>
     * <li>The listener checks {@code runningFutures} before the job id is registered and finds nothing to cancel.</li>
     * <li>The job gets submitted and starts running despite being cancelled.</li>
     * </ol>
     *
     * By locking both the event listener and the submission path, we ensure:
     * <ul>
     * <li>Cancelled-before-submit: we detect the cancelled id and skip submission.</li>
     * <li>Cancelled-during-submit: the listener will see the registration or the submitter will see the cancelled flag.</li>
     * </ul>
     */
    private final ReentrantLock jobLifecycleLock = new ReentrantLock();

    @Value("${artemis.continuous-integration.build-timeout-seconds.max:240}")
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
     * Per-node registry of the public, higher-level {@link CompletableFuture} wrappers returned to callers.
     * Used by REST/websocket layers to observe completion and stream logs.
     */
    private final Map<String, CompletableFuture<BuildResult>> runningFuturesWrapper = new ConcurrentHashMap<>();

    /**
     * A set that contains all build jobs that were cancelled by the user.
     * This set is unique for each node and contains only the build jobs that were cancelled on this node.
     */
    private final Set<String> cancelledBuildJobs = new ConcurrentSkipListSet<>();

    public BuildJobManagementService(DistributedDataAccessService distributedDataAccessService, BuildJobExecutionService buildJobExecutionService,
            BuildAgentConfiguration buildAgentConfiguration, BuildJobContainerService buildJobContainerService, BuildLogsMap buildLogsMap) {
        this.buildJobExecutionService = buildJobExecutionService;
        this.buildAgentConfiguration = buildAgentConfiguration;
        this.buildJobContainerService = buildJobContainerService;
        this.distributedDataAccessService = distributedDataAccessService;
        this.buildLogsMap = buildLogsMap;
    }

    /**
     * Add a listener to the canceledBuildJobsTopic that cancels the build job for the given buildJobId.
     * It gets broadcast to all nodes in the cluster. Only the node that is running the build job will cancel it.
     * EventListener cannot be used here, as the bean is lazy
     * <a href="https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events-annotation">Spring Docs</a>
     */
    @PostConstruct
    public void init() {
        DistributedTopic<String> canceledBuildJobsTopic = distributedDataAccessService.getCanceledBuildJobsTopic();
        canceledBuildJobsTopic.addMessageListener(buildJobId -> {
            jobLifecycleLock.lock();
            try {
                if (runningFutures.containsKey(buildJobId)) {
                    cancelBuildJob(buildJobId);
                }
            }
            finally {
                jobLifecycleLock.unlock();
            }
        });
    }

    /**
     * Submit a build job to the executor and return a {@link CompletableFuture} that completes with the {@link BuildResult}.
     * The method enforces a per-job timeout (bounded by {@code timeoutSeconds}) and ensures proper cleanup on failure.
     *
     * <p>
     * <strong>Concurrency & Cancellation</strong>:
     * Submission and initial registration are serialized with {@link #jobLifecycleLock} so that a concurrent
     * cancel signal cannot cause a job to run after it was requested to be cancelled.
     * </p>
     *
     * @param buildJobItem the job to execute
     * @return a future that completes with the build result or exceptionally on timeout/cancellation/error
     * @throws LocalCIException if the job cannot be submitted to the executor
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
        jobLifecycleLock.lock();
        Future<BuildResult> future;
        try {
            if (cancelledBuildJobs.contains(buildJobItem.id())) {
                finishCancelledBuildJob(buildJobItem.repositoryInfo().assignmentRepositoryUri(), buildJobItem.id(), containerName);
                String msg = "Build job with id " + buildJobItem.id() + " was cancelled before it was submitted to the executor service.";
                buildLogsMap.appendBuildLogEntry(buildJobItem.id(), msg);
                throw new CompletionException(msg, null);
            }
            future = buildAgentConfiguration.getBuildExecutor().submit(buildJob);
            runningFutures.put(buildJobItem.id(), future);
        }
        finally {
            jobLifecycleLock.unlock();
        }

        int buildJobTimeoutSeconds;
        if (buildJobItem.buildConfig().timeoutSeconds() > 0 && buildJobItem.buildConfig().timeoutSeconds() < this.timeoutSeconds) {
            buildJobTimeoutSeconds = buildJobItem.buildConfig().timeoutSeconds();
        }
        else {
            buildJobTimeoutSeconds = this.timeoutSeconds;
        }

        CompletableFuture<BuildResult> futureResult = createCompletableFuture(() -> {
            try {
                return future.get(buildJobTimeoutSeconds, TimeUnit.SECONDS);
            }
            catch (Exception ex) {
                Throwable cause = ex.getCause();
                if (cause != null && DockerUtil.isDockerNotAvailable(cause)) {
                    log.error("Cannot connect to Docker Host. Make sure Docker is running and configured properly! {}", cause.getMessage());
                    throw new CompletionException(ex);
                }
                // RejectedExecutionException is thrown if the queue size limit (defined in "artemis.continuous-integration.queue-size-limit") is reached.
                // Wrap the exception in a CompletionException so that the future is completed exceptionally and the thenAccept block is not run.
                // This CompletionException will not resurface anywhere else as it is thrown in this completable future's separate thread.
                if (cancelledBuildJobs.contains(buildJobItem.id())) {
                    finishCancelledBuildJob(buildJobItem.repositoryInfo().assignmentRepositoryUri(), buildJobItem.id(), containerName);
                    String msg = "Build job with id " + buildJobItem.id() + " was cancelled.";
                    String stackTrace = stackTraceToString(ex);
                    buildLogsMap.appendBuildLogEntry(buildJobItem.id(), new BuildLogDTO(ZonedDateTime.now(), msg + "\n" + stackTrace));
                    throw new CompletionException(msg, ex);
                }
                else {
                    finishBuildJobExceptionally(buildJobItem.id(), containerName, ex);
                    if (ex instanceof TimeoutException) {
                        logTimedOutBuildJob(buildJobItem, buildJobTimeoutSeconds);
                    }
                    throw new CompletionException(ex);
                }
            }
        });

        runningFuturesWrapper.put(buildJobItem.id(), futureResult);
        return futureResult.whenComplete(((result, throwable) -> {
            runningFutures.remove(buildJobItem.id());
            runningFuturesWrapper.remove(buildJobItem.id());
        }));
    }

    private void logTimedOutBuildJob(BuildJobQueueItem buildJobItem, int buildJobTimeoutSeconds) {
        String msg = "Timed out after " + buildJobTimeoutSeconds + " seconds. "
                + "This may be due to an infinite loop or inefficient code. Please review your code for potential issues. "
                + "If the problem persists, contact your instructor for assistance. (Build job ID: " + buildJobItem.id() + ")";
        buildLogsMap.appendBuildLogEntry(buildJobItem.id(), msg);
        log.warn(msg);

        msg = "Executing build job with id " + buildJobItem.id() + " timed out after " + buildJobTimeoutSeconds + " seconds."
                + "This may be due to strict timeout settings. Consider increasing the exercise timeout and applying stricter timeout constraints within the test cases using @StrictTimeout.";
        buildLogsMap.appendBuildLogEntry(buildJobItem.id(), msg);
    }

    Set<String> getRunningBuildJobIds() {
        return Set.copyOf(runningFutures.keySet());
    }

    CompletableFuture<BuildResult> getRunningBuildJobFutureWrapper(String buildJobId) {
        return runningFuturesWrapper.get(buildJobId);
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
        buildLogsMap.appendBuildLogEntry(buildJobId, new BuildLogDTO(ZonedDateTime.now(), msg + "\n" + stackTrace));
        log.error(msg, exception);

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
    void cancelBuildJob(String buildJobId) {
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
