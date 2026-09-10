package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import jakarta.annotation.PostConstruct;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.BuildLogDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;
import de.tum.cit.aet.artemis.buildagent.service.runner.BuildJobRunner;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;

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
 * <li>Timeouts: stop unresponsive executions and log guidance for students and instructors.</li>
 * <li>Exceptions: log details (incl. stack trace), stop executions, and complete futures exceptionally.</li>
 * <li>Cancellation: interrupt job execution (if running), stop it, and clean up state.</li>
 * </ul>
 */
@Lazy(false)
@Service
@Profile(PROFILE_BUILDAGENT)
public class BuildJobManagementService {

    private static final Logger log = LoggerFactory.getLogger(BuildJobManagementService.class);

    /**
     * Upper bound for waiting on a cancelled execution to leave its cleanup block, so that a build callable that ignores the interrupt cannot block a build-result thread.
     */
    private static final Duration CANCELLATION_TERMINATION_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Interval between retries when waiting for cluster connection during startup.
     * Uses the same interval as the availability check in SharedQueueProcessingService for consistency.
     */
    private static final java.time.Duration CLUSTER_CONNECTION_RETRY_INTERVAL = java.time.Duration.ofSeconds(5);

    /**
     * How long a single wait slice for the build result lasts. The build timeout is enforced in slices of this length so
     * that slices spent pulling the Docker image can be excluded from the build budget, see
     * {@link #awaitBuildResult(Future, String, int)}. Short enough to report a timeout promptly, long enough to keep the
     * polling overhead negligible over a multi-minute build.
     */
    private static final long BUILD_TIMEOUT_POLL_INTERVAL_MILLIS = 250;

    private final BuildJobExecutionService buildJobExecutionService;

    private final BuildAgentConfiguration buildAgentConfiguration;

    private final BuildJobRunner buildJobRunner;

    private final DistributedDataAccessService distributedDataAccessService;

    private final BuildLogsMap buildLogsMap;

    private final TaskScheduler taskScheduler;

    /**
     * Scheduled future for retrying cluster connection and initialization.
     * This is used when the build agent starts before any core node is available.
     * Uses AtomicReference for thread-safe check-then-act operations.
     */
    private final AtomicReference<ScheduledFuture<?>> connectionRetryFuture = new AtomicReference<>();

    /**
     * Flag to track whether initialization has completed successfully.
     * Uses AtomicBoolean to ensure thread-safe access from the retry task.
     */
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * UUID of the cancel build job message listener. Stored to allow removal on reconnection.
     */
    private java.util.UUID cancelListenerId;

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

    /**
     * Maximum allowed timeout for build jobs in seconds. Individual jobs can specify a lower timeout,
     * but not higher than this value. Default is 240 seconds (4 minutes).
     */
    @Value("${artemis.continuous-integration.build-timeout-seconds.max:240}")
    private int timeoutSeconds;

    /**
     * Whether to run build jobs asynchronously (true) or synchronously (false).
     * Synchronous mode is primarily used for testing to ensure deterministic behavior.
     */
    @Value("${artemis.continuous-integration.asynchronous:true}")
    private boolean runBuildJobsAsynchronously;

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

    /** Tracks when the submitted callable has actually left its cleanup block after cancellation. */
    private final Map<String, BuildExecutionTracker> runningExecutionTrackers = new ConcurrentHashMap<>();

    /** Keeps exact attempt resources available for conditional cleanup after a same-id handoff. */
    private final Map<CompletableFuture<BuildResult>, BuildAttemptResources> buildAttemptResources = new ConcurrentHashMap<>();

    /**
     * A set that contains all build jobs that were cancelled by the user.
     * This set is unique for each node and contains only the build jobs that were cancelled on this node.
     */
    private final Set<String> cancelledBuildJobs = new ConcurrentSkipListSet<>();

    public BuildJobManagementService(DistributedDataAccessService distributedDataAccessService, BuildJobExecutionService buildJobExecutionService,
            BuildAgentConfiguration buildAgentConfiguration, BuildJobRunner buildJobRunner, BuildLogsMap buildLogsMap, TaskScheduler taskScheduler) {
        this.buildJobExecutionService = buildJobExecutionService;
        this.buildAgentConfiguration = buildAgentConfiguration;
        this.buildJobRunner = buildJobRunner;
        this.distributedDataAccessService = distributedDataAccessService;
        this.buildLogsMap = buildLogsMap;
        this.taskScheduler = taskScheduler;
    }

    /**
     * Initialize the service by setting up the cancel listener for build jobs.
     * <p>
     * When running as a Hazelcast client with asyncStart=true, the client may not yet be
     * connected to the cluster when this method is called. In that case, we schedule
     * periodic retries until the connection is established and initialization completes.
     * <p>
     * Additionally, a connection state listener is registered to handle reconnection after
     * a connection loss. When the client reconnects to the cluster, the listener re-initializes
     * the distributed topic listener which may have been lost during the disconnection.
     * <p>
     * EventListener cannot be used here, as the bean is lazy
     * <a href="https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events-annotation">Spring Docs</a>
     */
    @PostConstruct
    public void init() {
        // Register a connection state listener to handle both initial connection and reconnection.
        // On reconnection (isInitialConnection=false), the topic listener needs to be re-registered
        // because it may have been lost when the connection was interrupted.
        distributedDataAccessService.addConnectionStateListener(isInitialConnection -> {
            if (!isInitialConnection) {
                // This is a reconnection - reset the initialized flag so listeners are re-registered
                log.info("Reconnected to the distributed data provider. Re-initializing BuildJobManagementService listeners.");
                initialized.set(false);
            }
            boolean initSucceeded = tryInitialize();
            // If initialization failed, schedule retries (handles both connection issues and transient failures)
            if (!initSucceeded) {
                scheduleConnectionRetryIfNeeded();
            }
        });

        // If already connected, tryInitialize was called by the listener above.
        // If not connected yet, schedule periodic retries as a fallback.
        if (!initialized.get() && !distributedDataAccessService.isConnectedToCluster()) {
            log.info("Not connected to the distributed data provider yet. Scheduling periodic initialization retries every {} seconds.",
                    CLUSTER_CONNECTION_RETRY_INTERVAL.toSeconds());
            scheduleConnectionRetryIfNeeded();
        }
    }

    /**
     * Atomically schedules a connection retry task if one is not already running.
     * Uses AtomicReference.updateAndGet to prevent race conditions where multiple
     * threads could schedule duplicate retry tasks.
     */
    private void scheduleConnectionRetryIfNeeded() {
        connectionRetryFuture.updateAndGet(current -> {
            if (current == null || current.isDone()) {
                return taskScheduler.scheduleAtFixedRate(() -> {
                    if (tryInitialize()) {
                        // Initialization succeeded - cancel the retry task
                        ScheduledFuture<?> future = connectionRetryFuture.get();
                        if (future != null) {
                            future.cancel(false);
                        }
                    }
                }, CLUSTER_CONNECTION_RETRY_INTERVAL);
            }
            return current;
        });
    }

    /**
     * Attempts to initialize the cancel listener for build jobs.
     * <p>
     * This method checks if the Hazelcast client is connected to the cluster before
     * attempting to access distributed data structures. If not connected, it returns
     * false so the caller can retry later.
     *
     * @return true if initialization succeeded, false if not connected to cluster
     */
    private synchronized boolean tryInitialize() {
        if (initialized.get()) {
            return true;
        }

        if (!distributedDataAccessService.isConnectedToCluster()) {
            log.debug("Cannot initialize BuildJobManagementService: not connected to the distributed data provider yet");
            return false;
        }

        try {
            var canceledBuildJobsTopic = distributedDataAccessService.getCanceledBuildJobsTopic();

            // Remove old listener if it exists (prevents duplicate listeners on reconnection)
            if (cancelListenerId != null) {
                canceledBuildJobsTopic.removeMessageListener(cancelListenerId);
                cancelListenerId = null;
            }

            cancelListenerId = canceledBuildJobsTopic.addMessageListener(buildJobId -> {
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

            initialized.set(true);
            log.info("BuildJobManagementService initialized successfully - cancel listener registered");
            return true;
        }
        catch (Exception e) {
            // This can happen if the connection is lost between the check and the access
            log.warn("Failed to initialize BuildJobManagementService: {}. Will retry.", e.getMessage());
            return false;
        }
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

        BuildExecutionTracker executionTracker = new BuildExecutionTracker();
        // Prepare a Callable that will later be called. It contains the actual steps needed to execute the build job.
        Callable<BuildResult> buildJob = () -> {
            if (!executionTracker.beginExecution()) {
                throw new CancellationException("Build execution was cancelled before it started");
            }
            try {
                return buildJobExecutionService.runBuildJob(buildJobItem);
            }
            finally {
                executionTracker.finishExecution();
            }
        };

        /*
         * Submit the build job to the executor service. This runs in a separate thread, so it does not block the main thread.
         * createCompletableFuture() is only used to provide a way to run build jobs synchronously for testing and debugging purposes and depends on the
         * artemis.continuous-integration.asynchronous environment variable.
         * Usually, when using asynchronous build jobs, it runs on the dedicated build result executor.
         * The future is stored in the runningFutures map so that it can be cancelled if needed.
         * We add a lock to prevent the job from being submitted even though it was cancelled.
         */
        jobLifecycleLock.lock();
        Future<BuildResult> future;
        try {
            if (cancelledBuildJobs.contains(buildJobItem.id())) {
                finishCancelledBuildJob(buildJobItem.repositoryInfo().assignmentRepositoryUri(), buildJobItem.id());
                String msg = "Build job with id " + buildJobItem.id() + " was cancelled before it was submitted to the executor service.";
                buildLogsMap.appendBuildLogEntry(buildJobItem.id(), msg);
                throw new CompletionException(msg, null);
            }
            // Refuse before anything is registered when the executors are gone, which is what a pause leaves behind.
            // Discovering it only after the build has been submitted would leave a build running that nothing waits
            // for, so nothing would ever complete its public future or release the attempt.
            rejectIfExecutorsAreUnavailable(buildJobItem.id());
            // Register the job before it can start running. submit() hands the task to a worker thread before it
            // returns, so registering afterwards left a window in which the build was already executing while
            // runningFutures was still empty. A cancel or pause arriving in that window concluded that nothing was
            // running: the pause then skipped its grace period and closed the build agent services underneath the
            // job, which kept running until it hit the build timeout and was reported to the student as a failure.
            // Executing the task only after the registration keeps "registered" strictly ahead of "running".
            FutureTask<BuildResult> task = new FutureTask<>(buildJob);
            runningFutures.put(buildJobItem.id(), task);
            runningExecutionTrackers.put(buildJobItem.id(), executionTracker);
            try {
                buildAgentConfiguration.getBuildExecutor().execute(task);
            }
            catch (RuntimeException notAccepted) {
                runningFutures.remove(buildJobItem.id());
                runningExecutionTrackers.remove(buildJobItem.id());
                throw notAccepted;
            }
            future = task;
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

        CompletableFuture<BuildResult> futureResult = createResultFutureOrReleaseJob(buildJobItem.id(), future, executionTracker, () -> {
            try {
                return awaitBuildResult(future, buildJobItem.id(), buildJobTimeoutSeconds);
            }
            catch (Exception ex) {
                if (DockerUtil.isDockerNotAvailable(ex)) {
                    log.warn("Docker is not available. Build job {} cannot be executed.", buildJobItem.id());
                    throw new CompletionException(ex);
                }
                // RejectedExecutionException is thrown if the queue size limit (defined in "artemis.continuous-integration.queue-size-limit") is reached.
                // Wrap the exception in a CompletionException so that the future is completed exceptionally and the thenAccept block is not run.
                // This CompletionException will not resurface anywhere else as it is thrown in this completable future's separate thread.
                if (cancelledBuildJobs.contains(buildJobItem.id())) {
                    if (!executionTracker.awaitTermination(CANCELLATION_TERMINATION_TIMEOUT)) {
                        log.warn("Build job {} did not release its execution resources within {}", buildJobItem.id(), CANCELLATION_TERMINATION_TIMEOUT);
                    }
                    finishCancelledBuildJob(buildJobItem.repositoryInfo().assignmentRepositoryUri(), buildJobItem.id());
                    String msg = "Build job with id " + buildJobItem.id() + " was cancelled.";
                    String stackTrace = stackTraceToString(ex);
                    buildLogsMap.appendBuildLogEntry(buildJobItem.id(), new BuildLogDTO(ZonedDateTime.now(), msg + "\n" + stackTrace));
                    throw new CompletionException(msg, ex);
                }
                else {
                    finishBuildJobExceptionally(buildJobItem.id(), ex);
                    if (ex instanceof TimeoutException) {
                        // Cancel the underlying future to interrupt the build job that's still running.
                        // Without this, the build job continues running in the background and may create
                        // a "zombie" container after the timeout has already been reported.
                        future.cancel(true);
                        logTimedOutBuildJob(buildJobItem, buildJobTimeoutSeconds);
                    }
                    throw new CompletionException(ex);
                }
            }
        });

        buildAttemptResources.put(futureResult, new BuildAttemptResources(buildJobItem.id(), future, executionTracker));
        runningFuturesWrapper.put(buildJobItem.id(), futureResult);
        return futureResult;
    }

    /**
     * Refuses a submission the build agent cannot see through, before it changes any state.
     * <p>
     * A build needs two threads: one to run it and one to wait for its result. Pausing the agent closes both executors,
     * and a build submitted afterwards would either not run at all or run with nothing waiting for it, so its public
     * future would never complete and the queue processor would neither publish a result nor release the attempt.
     * <p>
     * Rejecting here rather than at the executor keeps the failure an ordinary rejected submission, which the caller
     * already handles by putting the job back on the queue. Reaching the executor with a closed one would instead raise
     * a {@link NullPointerException}, which no caller expects.
     *
     * @param buildJobId the job that is about to be submitted
     * @throws RejectedExecutionException if either executor is unavailable
     */
    private void rejectIfExecutorsAreUnavailable(String buildJobId) {
        if (!runBuildJobsAsynchronously) {
            return;
        }
        if (isUnavailable(buildAgentConfiguration.getBuildExecutor()) || isUnavailable(buildAgentConfiguration.getBuildResultExecutor())) {
            throw new RejectedExecutionException("Build job " + buildJobId + " was not submitted because the build executors of this build agent are closed");
        }
    }

    private static boolean isUnavailable(@Nullable ThreadPoolExecutor executor) {
        return executor == null || executor.isShutdown();
    }

    /**
     * Creates the public result future and undoes the submission if that fails.
     * <p>
     * The build is already registered and running by this point. Leaving it there after a failure here would leave a
     * build nothing waits for, so the registration is rolled back and the build is interrupted, which turns the failure
     * into an ordinary rejected submission for the caller.
     *
     * @param buildJobId       the job being submitted
     * @param future           the submitted build task
     * @param executionTracker the tracker registered for that task
     * @param supplier         produces the build result by waiting for the submitted task
     * @return the public future for this attempt
     */
    private CompletableFuture<BuildResult> createResultFutureOrReleaseJob(String buildJobId, Future<BuildResult> future, BuildExecutionTracker executionTracker,
            Supplier<BuildResult> supplier) {
        try {
            return createCompletableFuture(supplier);
        }
        catch (RuntimeException notAccepted) {
            future.cancel(true);
            runningFutures.remove(buildJobId, future);
            runningExecutionTrackers.remove(buildJobId, executionTracker);
            throw notAccepted;
        }
    }

    /**
     * Releases local tracking after the queue processor has atomically claimed and handled the
     * terminal result. Keeping the attempt registered until then allows a concurrent external
     * cancellation to race against completion through {@link Future#cancel(boolean)} instead of
     * falling into a gap between future completion and result publication.
     *
     * @param futureResult the exact result future returned for this attempt
     */
    void releaseBuildJob(CompletableFuture<BuildResult> futureResult) {
        BuildAttemptResources resources = buildAttemptResources.remove(futureResult);
        if (resources != null) {
            runningFuturesWrapper.remove(resources.buildJobId(), futureResult);
            runningFutures.remove(resources.buildJobId(), resources.future());
            runningExecutionTrackers.remove(resources.buildJobId(), resources.executionTracker());
        }
    }

    /**
     * Waits for the build result, without letting the time spent pulling the Docker image consume the build timeout.
     * <p>
     * Pulling the image is the first step of {@link BuildJobExecutionService#runBuildJob}, so it runs inside the window
     * guarded by the build timeout. A cold pull of a large image can easily take longer than
     * {@code artemis.continuous-integration.build-timeout-seconds.max} (240 seconds by default), which would cancel the
     * job and report it as a build timeout even though the build itself never started. The pull has its own, much longer
     * budget ({@code artemis.continuous-integration.image-pull-timeout-seconds}) and must therefore be excluded here.
     * <p>
     * Rather than starting the timer after image preparation, the wait is sliced: only slices during which no pull is in
     * progress count against the build budget. That keeps the accounting correct for images that are already present
     * locally (no pull, so the full budget applies from the start) as well as for pulls that finish mid-build.
     *
     * Package-private for testing.
     *
     * @param future                 the future of the running build job
     * @param buildJobId             the ID of the build job, used to check whether its image pull is still running
     * @param buildJobTimeoutSeconds the build budget in seconds, excluding any time spent pulling the image
     * @return the build result
     * @throws TimeoutException if the build itself, not counting image pulls, exceeded the build budget
     */
    BuildResult awaitBuildResult(Future<BuildResult> future, String buildJobId, int buildJobTimeoutSeconds) throws Exception {
        final long budgetNanos = TimeUnit.SECONDS.toNanos(buildJobTimeoutSeconds);
        long consumedNanos = 0;

        while (true) {
            final long sliceStartNanos = System.nanoTime();
            try {
                return future.get(BUILD_TIMEOUT_POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
            }
            catch (TimeoutException timeout) {
                if (buildJobRunner.isFetchingImage(buildJobId)) {
                    // The fetch is bounded by the image pull timeout, so this slice does not count against the build budget.
                    continue;
                }
                consumedNanos += System.nanoTime() - sliceStartNanos;
                if (consumedNanos >= budgetNanos) {
                    throw timeout;
                }
            }
        }
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

    /**
     * Returns a snapshot of all currently running build job IDs on this node.
     * <p>
     * This is useful for monitoring and debugging purposes. The returned set is a copy,
     * so modifications won't affect the internal state.
     *
     * @return an immutable set of build job IDs currently being executed on this node
     */
    Set<String> getRunningBuildJobIds() {
        return Set.copyOf(runningFutures.keySet());
    }

    /**
     * Returns the public-facing CompletableFuture wrapper for a running build job.
     * <p>
     * This wrapper is used by REST/websocket layers to observe build completion and stream logs.
     * Returns null if no build job with the given ID is currently running on this node.
     *
     * @param buildJobId the ID of the build job
     * @return the CompletableFuture wrapper, or null if not found
     */
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
            return CompletableFuture.supplyAsync(supplier, buildAgentConfiguration.getBuildResultExecutor());
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
     * This method logs the error, provides user-friendly messaging for infrastructure issues,
     * and ensures the container is properly stopped.
     *
     * @param buildJobId The id of the build job that failed.
     * @param exception  The exception that occurred while building and testing the repository.
     */
    private void finishBuildJobExceptionally(String buildJobId, Exception exception) {
        String msg = "Error while executing build job " + buildJobId + ": " + exception.getMessage();
        String stackTrace = stackTraceToString(exception);

        // Check if this is a tar archive failure (infrastructure issue)
        boolean isTarFailure = isTarArchiveFailure(exception);
        if (isTarFailure) {
            String userFriendlyMsg = "Build failed due to a temporary infrastructure issue while preparing the build environment. "
                    + "This is not caused by your code. Please try rerunning your build.";
            buildLogsMap.appendBuildLogEntry(buildJobId, new BuildLogDTO(ZonedDateTime.now(), userFriendlyMsg));
            log.error("Tar archive failure for build job {}: {}", buildJobId, exception.getMessage(), exception);
        }
        else {
            buildLogsMap.appendBuildLogEntry(buildJobId, new BuildLogDTO(ZonedDateTime.now(), msg + "\n" + stackTrace));
            log.error(msg, exception);
        }

        buildJobRunner.cancel(buildJobId);
    }

    /**
     * Checks if the exception is related to a tar archive operation failure.
     *
     * @param exception the exception to check
     * @return true if the exception is related to tar archive operations
     */
    private boolean isTarArchiveFailure(Exception exception) {
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }

        // Check for tar-related error messages
        return message.contains("tar archive") || message.contains("Could not copy to container") || message.contains("Could not create tar")
                || message.contains("Failed to retrieve archive") || (exception.getCause() != null && exception.getCause().getMessage() != null
                        && (exception.getCause().getMessage().contains("tar") || exception.getCause().getMessage().contains("IOException")));
    }

    /**
     * Cancel the build job for the given buildJobId.
     *
     * @param buildJobId The id of the build job that should be cancelled.
     * @return {@code true} if the job was still running and this call stopped it, {@code false} if there was nothing
     *         left to stop because it had already finished or was never registered. A caller that puts cancelled
     *         jobs back on the queue has to check this: re-queueing a job that finished on its own would run the
     *         same build a second time.
     */
    boolean cancelBuildJob(String buildJobId) {
        jobLifecycleLock.lock();
        try {
            Future<BuildResult> future = runningFutures.get(buildJobId);
            if (future == null) {
                log.warn("Could not cancel build job with id {} as it was not found in the running build jobs", buildJobId);
                return false;
            }
            try {
                boolean markerAdded = cancelledBuildJobs.add(buildJobId);
                // A future that is already cancelled returns false, but the job is still being cancelled. Treating that as accepted keeps repeated cancel signals
                // idempotent; otherwise the second signal would drop the marker set by the first one and the job would be reported as FAILED instead of CANCELLED.
                boolean cancellationAccepted = future.cancel(true) || future.isCancelled(); // Attempt to interrupt the build job
                if (cancellationAccepted) {
                    BuildExecutionTracker executionTracker = runningExecutionTrackers.get(buildJobId);
                    if (executionTracker != null) {
                        executionTracker.cancelBeforeStart();
                    }
                    buildJobRunner.cancel(buildJobId);
                }
                else if (markerAdded) {
                    cancelledBuildJobs.remove(buildJobId);
                }
                return cancellationAccepted;
            }
            catch (CancellationException e) {
                log.warn("Build job already cancelled or completed for id {}", buildJobId);
                return false;
            }
        }
        finally {
            jobLifecycleLock.unlock();
        }
    }

    /**
     * Finish the build job if it was cancelled by the user.
     *
     * @param repositoryUri the URI of the repository for which the build job was cancelled
     * @param buildJobId    The id of the cancelled build job
     */
    private void finishCancelledBuildJob(String repositoryUri, String buildJobId) {
        log.debug("Build job with id {} in repository {} was cancelled", buildJobId, repositoryUri);

        buildJobRunner.cancel(buildJobId);

        cancelledBuildJobs.remove(buildJobId);
    }

    /**
     * Converts an exception's stack trace to a string for logging purposes.
     * <p>
     * This is useful for including full stack traces in build logs to help with debugging.
     *
     * @param e the throwable whose stack trace should be converted
     * @return the stack trace as a string
     */
    private String stackTraceToString(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }

    static final class BuildExecutionTracker {

        private final AtomicReference<ExecutionState> state = new AtomicReference<>(ExecutionState.NOT_STARTED);

        private final CompletableFuture<Void> termination = new CompletableFuture<>();

        boolean beginExecution() {
            return state.compareAndSet(ExecutionState.NOT_STARTED, ExecutionState.RUNNING);
        }

        void finishExecution() {
            state.set(ExecutionState.FINISHED);
            termination.complete(null);
        }

        void cancelBeforeStart() {
            if (state.compareAndSet(ExecutionState.NOT_STARTED, ExecutionState.CANCELLED_BEFORE_START)) {
                termination.complete(null);
            }
        }

        /**
         * Waits until the execution left its cleanup block, but never longer than the given timeout.
         * <p>
         * A build callable that ignores the interrupt would otherwise block a build-result thread forever, so the public future would never complete and the queue
         * bookkeeping in {@code SharedQueueProcessingService} would never release the attempt.
         *
         * @param timeout the maximum time to wait for the execution to terminate
         * @return {@code true} if the execution terminated within the timeout, {@code false} otherwise
         */
        boolean awaitTermination(Duration timeout) {
            try {
                termination.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                return true;
            }
            catch (TimeoutException e) {
                return false;
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            catch (ExecutionException e) {
                // The execution terminated, the outcome itself is handled by the caller of the public future.
                return true;
            }
        }

        private enum ExecutionState {
            NOT_STARTED, RUNNING, FINISHED, CANCELLED_BEFORE_START,
        }
    }

    private record BuildAttemptResources(String buildJobId, Future<BuildResult> future, BuildExecutionTracker executionTracker) {
    }
}
