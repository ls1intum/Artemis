package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.BadRequestException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.PullResponseItem;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentStatus;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;

/**
 * Service for Docker-related operations in the local CI build agent.
 * <p>
 * This service manages Docker image lifecycle and container cleanup for the build agent, including:
 * <ul>
 * <li><b>Image Management:</b> Pulling Docker images for build jobs with proper architecture handling</li>
 * <li><b>Container Cleanup:</b> Removing dangling or stuck build containers</li>
 * <li><b>Disk Space Management:</b> Automatic cleanup of old images when disk space is low</li>
 * </ul>
 * <p>
 * <b>Architecture Support:</b>
 * The service supports both AMD64 and ARM64 architectures. On macOS ARM systems, it can
 * automatically fall back to AMD64 images via Rosetta 2 emulation when ARM images are unavailable.
 * <p>
 * <b>Scheduled Tasks:</b>
 * <ul>
 * <li>Container cleanup runs periodically (default: every 60 minutes) to remove stuck containers</li>
 * <li>Image cleanup runs daily (default: 3:00 AM) to remove unused images older than the expiry threshold</li>
 * <li>Disk space check runs periodically to trigger cleanup when space falls below threshold</li>
 * </ul>
 * <p>
 * <b>Concurrency:</b>
 * Docker image pulls are protected by a {@link ReentrantLock} to prevent multiple concurrent pulls
 * of the same image, which could waste bandwidth and disk space.
 *
 * @see BuildAgentConfiguration
 * @see BuildJobContainerService
 */
@Lazy(false)
@Service
@Profile(PROFILE_BUILDAGENT)
@ConditionalOnProperty(prefix = "artemis.continuous-integration", name = "build-runner", havingValue = "docker", matchIfMissing = true)
public class BuildAgentDockerService {

    /**
     * Lock to serialize Docker image pull operations.
     * Prevents multiple concurrent pulls of the same image.
     */
    private final ReentrantLock lock = new ReentrantLock();

    private static final Logger log = LoggerFactory.getLogger(BuildAgentDockerService.class);

    /**
     * How often a running pull is re-examined while waiting, short enough to notice a stall promptly and long enough to
     * keep the polling overhead negligible over a multi-minute pull. Not final so tests can shorten it.
     */
    private long pullProgressPollIntervalMillis = TimeUnit.SECONDS.toMillis(5);

    private final BuildAgentConfiguration buildAgentConfiguration;

    private final DistributedDataAccessService distributedDataAccessService;

    private final BuildJobContainerService buildJobContainerService;

    private final TaskScheduler taskScheduler;

    private boolean isFirstCleanup = true;

    @Value("${artemis.continuous-integration.image-cleanup.enabled:false}")
    private Boolean imageCleanupEnabled;

    @Value("${artemis.continuous-integration.image-cleanup.expiry-days:2}")
    private int imageExpiryDays;

    @Value("${artemis.continuous-integration.image-cleanup.disk-space-threshold-mb:2000}")
    private int imageCleanupDiskSpaceThresholdMb;

    @Value("${artemis.continuous-integration.build-container-prefix:local-ci-}")
    private String buildContainerPrefix;

    // with the default value, containers running for longer than 5 minutes when the cleanup starts
    @Value("${artemis.continuous-integration.container-cleanup.expiry-minutes:5}")
    private int containerExpiryMinutes;

    // With the default value, the cleanup is triggered every 60 minutes
    @Value("${artemis.continuous-integration.container-cleanup.cleanup-schedule-minutes:60}")
    private int containerCleanupScheduleMinutes;

    // The image architecture that is supported by the build agent
    // amd64 is the default value, as this is the architecture of Intel and AMD CPUs, which most systems still use
    @Value("${artemis.continuous-integration.image-architecture:amd64}")
    private String imageArchitecture;

    @Value("${artemis.continuous-integration.build-agent.short-name}")
    private String buildAgentShortName;

    /**
     * Maximum time a single Docker image pull may take before it is aborted.
     * <p>
     * This bounds the image pull independently of the per-exercise build timeout: how long a pull takes depends on the image size and on the registry and network,
     * not on the exercise, so a slow registry must not eat into the time budget a student's build gets. Without this, a pull that never makes progress would block
     * the build thread indefinitely.
     */
    @Value("${artemis.continuous-integration.image-pull-timeout-seconds:300}")
    private int imagePullTimeoutSeconds;

    /**
     * Maximum time a Docker image pull may report no progress at all before it is aborted.
     * <p>
     * This separates the two ways a pull goes wrong. A large image over a slow link keeps emitting progress and is
     * allowed to run until {@link #imagePullTimeoutSeconds}. A pull that is not getting through at all, because the
     * registry is unreachable or a firewall silently drops the packets rather than refusing the connection, emits
     * nothing, and there is no reason to hold a build thread and an agent slot for the full budget waiting for it.
     */
    @Value("${artemis.continuous-integration.image-pull-stall-timeout-seconds:60}")
    private int imagePullStallTimeoutSeconds;

    /**
     * IDs of the build jobs that are currently pulling a Docker image, with the time the pull started.
     * <p>
     * A job is registered here for the whole time it spends in {@link #pullDockerImage}, which includes waiting for {@link #lock} while another job pulls. During
     * that window the job legitimately has no Docker container yet, so {@link SharedQueueProcessingService} must not treat it as stale.
     */
    private final Map<String, Instant> ongoingImagePulls = new ConcurrentHashMap<>();

    private static final String AMD64_ARCHITECTURE = "amd64";

    private static final String ARM64_ARCHITECTURE = "arm64";

    public BuildAgentDockerService(BuildAgentConfiguration buildAgentConfiguration, DistributedDataAccessService distributedDataAccessService,
            BuildJobContainerService buildJobContainerService, @Qualifier("taskScheduler") TaskScheduler taskScheduler) {
        this.buildAgentConfiguration = buildAgentConfiguration;
        this.distributedDataAccessService = distributedDataAccessService;
        this.buildJobContainerService = buildJobContainerService;
        this.taskScheduler = taskScheduler;
    }

    /**
     * Validates the image pull configuration and schedules the periodic cleanup of dangling build containers.
     * <p>
     * EventListener cannot be used here, as the bean is lazy, see the
     * <a href="https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events-annotation">Spring docs</a>.
     *
     * @throws IllegalArgumentException if the configured image pull timeout is not positive
     */
    @PostConstruct
    public void applicationReady() {
        // A non-positive timeout would make awaitCompletion return immediately, so every image pull would be reported as timed out and no build could ever run. Fail fast
        // at startup instead of turning every build into a confusing pull failure.
        if (imagePullTimeoutSeconds <= 0) {
            String errorMessage = "The Docker image pull timeout must be a positive number of seconds, but was " + imagePullTimeoutSeconds
                    + ". It should be changed in the application properties under 'artemis.continuous-integration.image-pull-timeout-seconds'.";
            log.error(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }
        if (imagePullStallTimeoutSeconds <= 0) {
            String errorMessage = "The Docker image pull stall timeout must be a positive number of seconds, but was " + imagePullStallTimeoutSeconds
                    + ". It should be changed in the application properties under 'artemis.continuous-integration.image-pull-stall-timeout-seconds'.";
            log.error(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }

        // Schedule the cleanup of dangling build containers once 10 seconds after the application has started and then every containerCleanupScheduleMinutes minutes
        taskScheduler.scheduleAtFixedRate(this::cleanUpContainers, Instant.now().plusSeconds(10), Duration.ofMinutes(containerCleanupScheduleMinutes));
    }

    /**
     * Cleans up dangling build containers from the system. This method differentiates between the initial cleanup
     * and subsequent cleanups to handle containers differently based on their age and status.
     * <p>
     * For the initial cleanup, it removes all containers that match the build container prefix, assuming these containers
     * are left from before the application started. For subsequent cleanups, it only removes containers that are older
     * than a specified age threshold (defaulted to 5 minutes), targeting containers likely stuck or inactive.
     * <p>
     * Detailed steps include:
     * - Logging the start of the cleanup process.
     * - Determining whether it's the initial or a subsequent cleanup.
     * - Listing all containers, filtering them based on name prefix and, for subsequent cleanups, their age.
     * - Forcibly removing the identified dangling containers.
     * - Logging the results and completion of the cleanup process.
     *
     * @implNote The method uses Docker commands to list and remove containers. It handles state changes using a flag
     *           (`isFirstCleanup`) to toggle the cleanup logic between the initial and subsequent runs.
     */
    public void cleanUpContainers() {
        List<Container> danglingBuildContainers;
        log.info("Start cleanup dangling build containers");

        if (dockerClientNotAvailable("Cannot clean up dangling build containers.")) {
            return;
        }

        DockerClient dockerClient = buildAgentConfiguration.getDockerClient();
        if (isFirstCleanup) {
            // Cleanup all dangling build containers after the application has started
            try {
                danglingBuildContainers = dockerClient.listContainersCmd().withShowAll(true).exec().stream()
                        .filter(container -> container.getNames() != null && container.getNames().length > 0 && container.getNames()[0].startsWith("/" + buildContainerPrefix))
                        .toList();
            }
            catch (Exception ex) {
                if (DockerUtil.isDockerNotAvailable(ex)) {
                    log.debug("Docker is not available. Skipping container cleanup: {}", ex.getMessage());
                    return;
                }
                log.error("Error while listing containers for cleanup: {}", ex.getMessage(), ex);
                return;
            }
            finally {
                isFirstCleanup = false;
            }
        }
        else {
            // Cleanup all containers that are older than 5 minutes (or ageThreshold) for all subsequent cleanups
            // Get current time in seconds
            long now = Instant.now().getEpochSecond();

            // Threshold for "stuck" containers in seconds
            long ageThreshold = containerExpiryMinutes * 60L;

            try {
                danglingBuildContainers = dockerClient.listContainersCmd().withShowAll(true).exec().stream()
                        .filter(container -> container.getNames() != null && container.getNames().length > 0 && container.getNames()[0].startsWith("/" + buildContainerPrefix))
                        .filter(container -> (now - container.getCreated()) > ageThreshold).toList();
            }
            catch (Exception ex) {
                if (DockerUtil.isDockerNotAvailable(ex)) {
                    log.debug("Docker is not available. Skipping container cleanup: {}", ex.getMessage());
                    return;
                }
                log.error("Error while listing containers for cleanup: {}", ex.getMessage(), ex);
                return;
            }
        }

        if (!danglingBuildContainers.isEmpty()) {
            log.info("Found {} dangling build containers", danglingBuildContainers.size());
            danglingBuildContainers.forEach(container -> buildJobContainerService.stopUnresponsiveContainer(container.getId()));
        }
        log.info("Cleanup dangling build containers done");
    }

    /**
     * Callback that allows us to provide more information about the docker pull operation results
     */
    public static class MyPullImageResultCallback extends PullImageResultCallback {

        /**
         * How many updates the daemon has reported for this pull. Written from the docker-java callback thread and
         * read by the waiting build thread, hence atomic.
         * <p>
         * A counter rather than a timestamp: the caller owns the clock, so "no progress yet" is measured from when
         * the wait started rather than from when this object happened to be constructed.
         */
        private final AtomicLong progressCount = new AtomicLong();

        /**
         * Signals that the pull has finished, whether it completed or failed. Counted down from {@link #close()}, which
         * docker-java invokes on both {@code onComplete} and {@code onError}.
         * <p>
         * The build thread waits on this rather than on {@link #awaitCompletion(long, java.util.concurrent.TimeUnit)}.
         * That method is meant to be called once, after the pull has finished: on every call it runs {@code throwFirstError()}
         * and closes the pull stream in its {@code finally} block. Calling it in a polling loop therefore both aborts a
         * healthy pull after the first timed-out slice and, because a pull still in progress has no success response yet,
         * throws {@code "Could not pull image"} the moment a slice elapses. This latch lets the wait be sliced for stall
         * detection without triggering either.
         */
        private final CountDownLatch pullFinished = new CountDownLatch(1);

        /**
         * How many updates the daemon has reported so far.
         *
         * @return the number of progress updates received for this pull
         */
        public long progressCount() {
            return progressCount.get();
        }

        @Override
        public void onNext(PullResponseItem item) {
            progressCount.incrementAndGet();
            String msg = "~~~~~~~~~~~~~~~~~~~~ Pull image progress: " + item.getStatus() + " ~~~~~~~~~~~~~~~~~~~~";
            log.debug(msg);
            super.onNext(item);
        }

        @Override
        public void onComplete() {
            String msg = "~~~~~~~~~~~~~~~~~~~~ Pull image complete ~~~~~~~~~~~~~~~~~~~~";
            log.debug(msg);
            super.onComplete();
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            }
            finally {
                pullFinished.countDown();
            }
        }

        /**
         * Waits up to the given time for the pull to finish, returning {@code true} once it has and {@code false} if the
         * time elapsed first. Unlike {@link #awaitCompletion(long, java.util.concurrent.TimeUnit)} it never closes the
         * pull stream, so it is safe to call repeatedly while the pull is still running.
         *
         * Public so that the shared docker mock in {@code DockerClientTestService} (a different package) can stub it.
         *
         * @param timeout the maximum time to wait
         * @param unit    the unit of {@code timeout}
         * @return {@code true} if the pull finished within the given time
         * @throws InterruptedException if interrupted while waiting
         */
        public boolean awaitFinished(long timeout, TimeUnit unit) throws InterruptedException {
            return pullFinished.await(timeout, unit);
        }

        /**
         * Re-throws a pull failure once the pull has finished, mirroring what
         * {@link #awaitCompletion(long, java.util.concurrent.TimeUnit)} did through {@code throwFirstError()}: a pull that
         * reported an error, or whose final response does not indicate success, fails the build rather than being treated
         * as a successful pull. Must only be called after {@link #awaitFinished(long, java.util.concurrent.TimeUnit)}
         * reported completion, so the final response is available.
         */
        void throwIfPullFailed() {
            throwFirstError();
        }
    }

    /**
     * Attempts to pull a specified Docker image associated with a build job if it is not already present on the local system.
     * This method uses a locking mechanism to ensure that the Docker image is not concurrently pulled by multiple threads.
     * <p>
     * The process includes:
     * - Checking if the Docker image is already available locally.
     * - If not available, acquiring a lock to prevent concurrent pulls.
     * - Checking for usable disk space and triggering image cleanup if the threshold is exceeded.
     * - Re-inspecting the image to confirm its absence after acquiring the lock.
     * - Pulling the image if both checks confirm its absence.
     * - Logging the operations and their outcomes to build logs for user visibility.
     * <p>
     * This method handles specific exceptions that might occur during the Docker operations, such as NotFoundException or BadRequestException,
     * by attempting to pull the image within a locked section. Other exceptions, including interruptions during the pull process,
     * are caught and rethrown as a LocalCIException with appropriate messages.
     *
     * @param buildJob     the build job that includes the configuration with the name of the Docker image.
     * @param buildLogsMap a map for appending log entries related to the build process, facilitating real-time logging for end users.
     * @throws LocalCIException if the image pull is interrupted or fails due to other exceptions.
     */
    public void pullDockerImage(BuildJobQueueItem buildJob, BuildLogsMap buildLogsMap) {
        // Register the job for the whole pull phase, including the time spent waiting for the lock, so that the stale build job detection does not cancel a job that is
        // simply waiting for its image.
        ongoingImagePulls.put(buildJob.id(), Instant.now());
        try {
            doPullDockerImage(buildJob, buildLogsMap);
        }
        finally {
            ongoingImagePulls.remove(buildJob.id());
        }
    }

    /**
     * Returns whether the given build job is currently pulling its Docker image.
     *
     * @param buildJobId the ID of the build job
     * @return true if an image pull is in progress for this build job
     */
    public boolean isImagePullInProgress(String buildJobId) {
        return ongoingImagePulls.containsKey(buildJobId);
    }

    private void doPullDockerImage(BuildJobQueueItem buildJob, BuildLogsMap buildLogsMap) {
        final String imageName = buildJob.buildConfig().dockerImage();
        if (dockerClientNotAvailable("Cannot pull Docker image.")) {
            throw new LocalCIException("Docker is not available. Cannot pull image " + imageName);
        }
        DockerClient dockerClient = buildAgentConfiguration.getDockerClient();
        try (var inspectImageCommand = dockerClient.inspectImageCmd(imageName)) {
            // First check if the image is already available
            String msg = "~~~~~~~~~~~~~~~~~~~~ Inspecting docker image " + imageName + " ~~~~~~~~~~~~~~~~~~~~";
            log.info(msg);
            buildLogsMap.appendBuildLogEntry(buildJob.id(), msg);
            var inspectImageResponse = inspectImageCommand.exec();
            checkImageArchitecture(imageName, inspectImageResponse, buildJob, buildLogsMap);
        }
        catch (NotFoundException | BadRequestException e) {
            lock.lock();

            // Check again if image was pulled in the meantime
            try {
                String msg = "~~~~~~~~~~~~~~~~~~~~ Inspecting docker image " + imageName + " again with a lock due to error " + e.getMessage() + " ~~~~~~~~~~~~~~~~~~~~";
                log.info(msg);
                buildLogsMap.appendBuildLogEntry(buildJob.id(), msg);
                var inspectImageResponse = dockerClient.inspectImageCmd(imageName).exec();
                checkImageArchitecture(imageName, inspectImageResponse, buildJob, buildLogsMap);
            }
            catch (NotFoundException | BadRequestException e2) {
                checkUsableDiskSpaceThenCleanUp();

                long start = System.nanoTime();
                String msg = "~~~~~~~~~~~~~~~~~~~~ Pulling docker image " + imageName + " with a lock after error " + e.getMessage() + " ~~~~~~~~~~~~~~~~~~~~";
                log.info(msg);
                buildLogsMap.appendBuildLogEntry(buildJob.id(), msg);

                try {
                    // Only pull the image if the inspect command failed
                    var command = dockerClient.pullImageCmd(imageName).withPlatform(imageArchitecture);
                    var exec = command.exec(new MyPullImageResultCallback());
                    awaitPullCompletion(exec, imageName, buildJob, buildLogsMap);

                    // Check if the image is compatible with the current architecture
                    var inspectImageResponse = dockerClient.inspectImageCmd(imageName).exec();
                    checkImageArchitecture(imageName, inspectImageResponse, buildJob, buildLogsMap);
                }
                catch (InterruptedException ie) {
                    throw new LocalCIException("Interrupted while pulling docker image " + imageName, ie);
                }
                catch (Exception ex) {
                    // On macOS ARM, if the ARM image is not available, fall back to amd64 (Rosetta emulation)
                    if (isMacOS() && ARM64_ARCHITECTURE.equals(imageArchitecture) && isNoMatchingManifestError(ex)) {
                        String fallbackMsg = "~~~~~~~~~~~~~~~~~~~~ No ARM image available for " + imageName + ", falling back to amd64 (Rosetta emulation) ~~~~~~~~~~~~~~~~~~~~";
                        log.warn(fallbackMsg);
                        buildLogsMap.appendBuildLogEntry(buildJob.id(), fallbackMsg);

                        try {
                            var fallbackCommand = dockerClient.pullImageCmd(imageName).withPlatform(AMD64_ARCHITECTURE);
                            var fallbackExec = fallbackCommand.exec(new MyPullImageResultCallback());
                            awaitPullCompletion(fallbackExec, imageName, buildJob, buildLogsMap);

                            // Verify the fallback image was pulled successfully
                            var inspectImageResponse = dockerClient.inspectImageCmd(imageName).exec();
                            checkImageArchitecture(imageName, inspectImageResponse, buildJob, buildLogsMap);
                        }
                        catch (InterruptedException ie) {
                            throw new LocalCIException("Interrupted while pulling docker image " + imageName + " with amd64 fallback", ie);
                        }
                        catch (Exception fallbackEx) {
                            throw new LocalCIException("Error while pulling docker image " + imageName + " with amd64 fallback", fallbackEx);
                        }
                    }
                    else {
                        throw new LocalCIException("Error while pulling docker image " + imageName, ex);
                    }
                }
                String msg2 = "~~~~~~~~~~~~~~~~~~~~ Pulling docker image " + imageName + " done after " + TimeLogUtil.formatDurationFrom(start) + " ~~~~~~~~~~~~~~~~~~~~";
                log.info(msg2);
                buildLogsMap.appendBuildLogEntry(buildJob.id(), msg2);
            }
            catch (Exception ex) {
                if (DockerUtil.isDockerNotAvailable(ex)) {
                    log.warn("Docker is not available. Error while inspecting image {}: {}", imageName, ex.getMessage());
                    throw new LocalCIException("Docker is not available. Cannot pull image " + imageName, ex);
                }
                throw new LocalCIException("Error while inspecting image " + imageName, ex);
            }
            finally {
                lock.unlock();
            }
        }
    }

    /**
     * Waits for a Docker image pull to finish, aborting it once {@code artemis.continuous-integration.image-pull-timeout-seconds} has elapsed.
     * <p>
     * Without a timeout a pull that stops making progress, for example because a configured registry mirror silently drops packets, blocks the build thread forever.
     * <p>
     * The wait must not be sliced with docker-java's {@code awaitCompletion(timeout, unit)}: that method is built to be called once, after the pull has finished, and on
     * every call it runs {@code throwFirstError()} and closes the pull stream in its {@code finally} block. Used in a loop it aborts a healthy pull after the first
     * timed-out slice and throws {@code "Could not pull image"} as soon as a slice elapses, because a pull still in progress has no success response yet. The wait is
     * therefore sliced on the callback's own completion signal instead, and {@link MyPullImageResultCallback#throwIfPullFailed()} surfaces a genuine pull failure once the
     * pull has actually finished, exactly as {@code awaitCompletion} used to.
     *
     * @param callback     the callback of the running pull command
     * @param imageName    the name of the Docker image being pulled
     * @param buildJob     the build job the pull belongs to
     * @param buildLogsMap a map for appending log entries related to the build process
     * @throws InterruptedException if the current thread is interrupted while waiting
     * @throws LocalCIException     if the pull does not finish within the configured timeout
     */
    private void awaitPullCompletion(MyPullImageResultCallback callback, String imageName, BuildJobQueueItem buildJob, BuildLogsMap buildLogsMap) throws InterruptedException {
        final long pollIntervalNanos = TimeUnit.MILLISECONDS.toNanos(pullProgressPollIntervalMillis);
        final long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(imagePullTimeoutSeconds);
        final long stallNanos = TimeUnit.SECONDS.toNanos(imagePullStallTimeoutSeconds);
        long lastProgressAtNanos = System.nanoTime();
        long lastProgressCount = callback.progressCount();

        while (true) {
            // Wait in slices rather than one long wait, so the pull can also be judged on whether it is still moving. A slice never reaches beyond the next deadline, so a
            // timeout shorter than the poll interval is honoured just as precisely as a longer one.
            long nowNanos = System.nanoTime();
            long sliceNanos = Math.max(0, Math.min(pollIntervalNanos, Math.min(lastProgressAtNanos + stallNanos - nowNanos, deadlineNanos - nowNanos)));
            try {
                if (callback.awaitFinished(sliceNanos, TimeUnit.NANOSECONDS)) {
                    callback.throwIfPullFailed();
                    return;
                }
            }
            catch (InterruptedException e) {
                try {
                    callback.close();
                }
                catch (IOException closeException) {
                    log.warn("Could not close the callback of the interrupted pull of docker image {}", imageName, closeException);
                }
                Thread.currentThread().interrupt();
                throw e;
            }
            long progressCount = callback.progressCount();
            if (progressCount != lastProgressCount) {
                lastProgressCount = progressCount;
                lastProgressAtNanos = System.nanoTime();
            }
            if (System.nanoTime() - lastProgressAtNanos >= stallNanos) {
                abortPull(callback, imageName, buildJob, buildLogsMap,
                        "reported no progress for " + imagePullStallTimeoutSeconds + " seconds. The registry is most likely unreachable from this agent, for example "
                                + "because a firewall drops the packets instead of refusing the connection");
            }
            if (System.nanoTime() - deadlineNanos >= 0) {
                abortPull(callback, imageName, buildJob, buildLogsMap, "did not finish within " + imagePullTimeoutSeconds + " seconds");
            }
        }
    }

    /**
     * Closes the callback so the pull is really abandoned rather than left running in the background, then fails the build job.
     *
     * @param callback     the callback of the running pull command
     * @param imageName    the name of the Docker image being pulled
     * @param buildJob     the build job the pull belongs to
     * @param buildLogsMap a map for appending log entries related to the build process
     * @param reason       what went wrong, phrased to continue "Pulling docker image <name> ..."
     */
    private static void abortPull(MyPullImageResultCallback callback, String imageName, BuildJobQueueItem buildJob, BuildLogsMap buildLogsMap, String reason) {
        try {
            callback.close();
        }
        catch (IOException e) {
            log.warn("Could not close the callback of the aborted pull of docker image {}", imageName, e);
        }
        String msg = "~~~~~~~~~~~~~~~~~~~~ Pulling docker image " + imageName + " " + reason + " ~~~~~~~~~~~~~~~~~~~~";
        log.error(msg);
        buildLogsMap.appendBuildLogEntry(buildJob.id(), msg);
        throw new LocalCIException("Pulling docker image " + imageName + " " + reason);
    }

    /**
     * Checks if the architecture of the Docker image is compatible with the current system.
     * On macOS ARM, amd64 images are allowed as they can run via Rosetta 2 emulation.
     *
     * @param imageName            the name of the Docker image
     * @param inspectImageResponse the response from the inspect image command
     * @param buildJob             the build job that includes the configuration with the name of the Docker image
     * @param buildLogsMap         a map for appending log entries related to the build process
     */
    private void checkImageArchitecture(String imageName, InspectImageResponse inspectImageResponse, BuildJobQueueItem buildJob, BuildLogsMap buildLogsMap) {
        String actualArch = inspectImageResponse.getArch();
        // Skip check if the image doesn't report its architecture (empty or null)
        // This can happen with some multi-arch images or when architecture metadata is missing
        if (actualArch == null || actualArch.isEmpty()) {
            log.warn("Docker image {} does not report its architecture, skipping architecture check", imageName);
            return;
        }

        // Allow amd64 images on macOS ARM via Rosetta 2 emulation
        if (isMacOS() && ARM64_ARCHITECTURE.equals(imageArchitecture) && AMD64_ARCHITECTURE.equals(actualArch)) {
            log.info("Docker image {} is amd64, running on macOS ARM via Rosetta 2 emulation", imageName);
            return;
        }

        if (!imageArchitecture.equals(actualArch)) {
            var msg = "Docker image " + imageName + " is not compatible with the current architecture. Needed 'linux/" + imageArchitecture + "', but got '" + actualArch + "'";
            log.error(msg);
            buildLogsMap.appendBuildLogEntry(buildJob.id(), msg);
            throw new LocalCIException(msg);
        }
    }

    /**
     * Schedules the deletion of Docker images that have not been used within a specified number of days, determined by {@link #imageExpiryDays}.
     * The default schedule for this cleanup task is daily at 3:00 AM, configurable via the 'cleanup-schedule-time' property in the application settings.
     * <p>
     * The process involves:
     * - Checking if image cleanup is enabled; if disabled, the operation is aborted.
     * - Retrieving a map of Docker images and their last usage dates.
     * - Getting a set of image names that are not associated with any running containers.
     * - Removing images that have exceeded the configured expiry days and are not associated with any running containers.
     * <p>
     * Exception handling includes catching NotFoundException for cases where images are already deleted or not found during the cleanup process.
     *
     * @implNote This method relies on Docker commands to list images and containers, and uses Hazelcast for managing image usage data.
     * @throws NotFoundException if an attempt is made to delete an image that no longer exists on the Docker host.
     */

    @Scheduled(cron = "${artemis.continuous-integration.image-cleanup.cleanup-schedule-time:0 0 3 * * *}")
    public void deleteOldDockerImages() {
        if (!imageCleanupEnabled) {
            log.info("Docker image cleanup is disabled");
            return;
        }

        if (dockerClientNotAvailable("Cannot delete old Docker images.")) {
            return;
        }

        Set<String> imageNames = getUnusedDockerImages();

        // Get map of docker images and their last build dates
        Map<String, ZonedDateTime> dockerImageCleanupInfo = distributedDataAccessService.getDockerImageCleanupInfoMap();

        // Delete images that have not been used for more than imageExpiryDays days
        for (String dockerImage : dockerImageCleanupInfo.keySet()) {
            if (imageNames.contains(dockerImage)) {
                if (dockerImageCleanupInfo.get(dockerImage).isBefore(ZonedDateTime.now().minusDays(imageExpiryDays))) {
                    log.info("Remove docker image {} because it was not used for at least {} days", dockerImage, imageExpiryDays);
                    try (final var removeCommand = buildAgentConfiguration.getDockerClient().removeImageCmd(dockerImage)) {
                        removeCommand.exec();
                    }
                    catch (NotFoundException e) {
                        log.warn("Docker image {} not found during cleaning up old docker images", dockerImage);
                    }
                }
            }
        }
    }

    /**
     * Checks for available disk space and triggers the cleanup of old Docker images if the available space falls below
     * {@link BuildAgentDockerService#imageCleanupDiskSpaceThresholdMb}.
     *
     * @implNote - We use the Docker root directory to check disk space availability. This is in case the Docker images are stored on a separate partition.
     *           - We need to iterate over the map entries since don't remove the oldest image from the map.
     */

    @Scheduled(fixedRateString = "${artemis.continuous-integration.image-cleanup.disk-space-check-interval-minutes:60}", initialDelayString = "${artemis.continuous-integration.image-cleanup.disk-space-check-interval-minutes:60}", timeUnit = TimeUnit.MINUTES)
    public void checkUsableDiskSpaceThenCleanUp() {
        if (!imageCleanupEnabled || dockerClientNotAvailable("Cannot check disk space for Docker image cleanup.")) {
            return;
        }

        DockerClient dockerClient = buildAgentConfiguration.getDockerClient();
        try {
            // Get the Docker root directory to check disk space.
            Path dockerRootDirectory = Path.of(Objects.requireNonNullElse(dockerClient.infoCmd().exec().getDockerRootDir(), "/"));
            long usableSpace = Files.getFileStore(dockerRootDirectory).getUsableSpace();

            long threshold = convertMegabytesToBytes(imageCleanupDiskSpaceThresholdMb);

            if (usableSpace >= threshold) {
                return;
            }

            // Get map of docker images and their last build dates
            Map<String, ZonedDateTime> dockerImageCleanupInfo = distributedDataAccessService.getDockerImageCleanupInfoMap();

            // Get unused images
            Set<String> unusedImages = getUnusedDockerImages();

            // Get a sorted list of images by last build date
            // We cast to ArrayList since we need the list to be mutable
            List<Map.Entry<String, ZonedDateTime>> sortedImagesByLastBuildDate = dockerImageCleanupInfo.entrySet().stream().sorted(Map.Entry.comparingByValue()).toList();
            List<Map.Entry<String, ZonedDateTime>> mutableSortedImagesByLastBuildDate = new java.util.ArrayList<>(sortedImagesByLastBuildDate);

            if (mutableSortedImagesByLastBuildDate.isEmpty()) {
                return;
            }

            int deleteAttempts = 5;
            int totalAttempts = mutableSortedImagesByLastBuildDate.size(); // We limit the total number of attempts to avoid infinite loops
            Map.Entry<String, ZonedDateTime> oldestImage = mutableSortedImagesByLastBuildDate.getFirst();
            while (oldestImage != null && usableSpace < threshold && deleteAttempts > 0 && totalAttempts > 0) {
                if (unusedImages.contains(oldestImage.getKey())) {
                    log.info("Remove oldest docker image {} to cleanup disk space to avoid filling up the hard disk", oldestImage.getKey());
                    try {
                        dockerClient.removeImageCmd(oldestImage.getKey()).exec();
                        usableSpace = Files.getFileStore(dockerRootDirectory).getUsableSpace();
                        deleteAttempts--;
                    }
                    catch (NotFoundException e) {
                        log.warn("Docker image {} not found during disk cleanup", oldestImage.getKey());
                    }
                }
                mutableSortedImagesByLastBuildDate.remove(oldestImage);
                oldestImage = mutableSortedImagesByLastBuildDate.isEmpty() ? null : mutableSortedImagesByLastBuildDate.getFirst();
                totalAttempts--;
            }
        }
        catch (Exception e) {
            log.error("Error while checking disk space for Docker image cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Gets a set of Docker image names that are not associated with any running containers.
     *
     * @return a set of image names that are not associated with any running containers.
     */
    private Set<String> getUnusedDockerImages() {
        // Callers (deleteOldDockerImages, checkUsableDiskSpaceThenCleanUp) already check dockerClientNotAvailable()
        DockerClient dockerClient = buildAgentConfiguration.getDockerClient();

        // Get list of all running containers
        List<Container> containers = dockerClient.listContainersCmd().exec();

        // Create a set of image IDs of containers in use
        Set<String> imageIdsInUse = containers.stream().map(Container::getImageId).collect(Collectors.toSet());

        // Get list of all images
        List<Image> allImages = dockerClient.listImagesCmd().exec();

        // Filter out images that are in use
        List<Image> unusedImages = allImages.stream().filter(image -> !imageIdsInUse.contains(image.getId())).toList();

        Set<String> imageNames = new HashSet<>();
        for (Image image : unusedImages) {
            String[] imageRepoTags = image.getRepoTags();
            if (imageRepoTags != null) {
                Collections.addAll(imageNames, imageRepoTags);
            }
        }
        return imageNames;
    }

    private long convertMegabytesToBytes(int mb) {
        long byteConversionRate = 1024L;
        return mb * byteConversionRate * byteConversionRate;
    }

    private boolean dockerClientNotAvailable(String additionalLogInfo) {
        DockerClient dockerClient = buildAgentConfiguration.getDockerClient();
        if (dockerClient == null) {
            BuildAgentStatus status = distributedDataAccessService.getBuildAgentStatus(buildAgentShortName);
            if ((status == BuildAgentStatus.PAUSED || status == BuildAgentStatus.SELF_PAUSED)) {
                log.info("Docker client is not available because the build agent is paused. {} This is expected behavior.", additionalLogInfo);
                return true;
            }
            log.error("Docker client is not available. {}", additionalLogInfo);
            return true;
        }
        if (!buildAgentConfiguration.isDockerAvailable()) {
            log.debug("Docker is not available. {}", additionalLogInfo);
            return true;
        }
        return false;
    }

    /**
     * Checks if the current operating system is macOS.
     * macOS can run amd64 Docker images on ARM hardware via Rosetta 2 emulation.
     *
     * @return true if running on macOS, false otherwise
     */
    private boolean isMacOS() {
        String osName = System.getProperty("os.name");
        return osName != null && osName.toLowerCase(Locale.ROOT).contains("mac");
    }

    /**
     * Checks if the exception indicates that no matching manifest was found for the requested platform.
     * This typically occurs when trying to pull an ARM image that only has an AMD64 variant available.
     *
     * @param ex the exception to check
     * @return true if the exception indicates a missing platform manifest, false otherwise
     */
    private boolean isNoMatchingManifestError(Exception ex) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable cause = ex;
        while (cause != null && visited.add(cause)) {
            String message = cause.getMessage();
            if (message != null && message.contains("no matching manifest")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
