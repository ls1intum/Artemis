package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResult;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionSpec;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;

/**
 * Build-agent-side {@link InteractiveSandbox}: a warm, resource-limited Docker container an exercise-Hyperion sandbox drives through many cheap operations.
 * <p>
 * Reuses the build-agent Docker client and {@link BuildAgentConfiguration#hostConfig()} so isolation matches a CI build container (same CPU/memory/PID limits; runs untrusted
 * code but holds no credentials or database access), and applies a build-safe hardening delta on top ({@code no-new-privileges} and dropping Linux capabilities)
 * capability). A session spec may further restrict Docker networking; Hyperion generation uses {@code network=none} by default so generated code cannot reach the network.
 * Unlike the regular build path it captures and returns each command's stdout/stderr as agent observations. Containers carry the {@value #SANDBOX_CONTAINER_PREFIX} prefix so
 * {@link InteractiveSandboxReaperService} never reaps a live session as if it were a CI build container.
 */
@Lazy
@Service
@Profile(PROFILE_BUILDAGENT)
public class InteractiveSandboxService implements InteractiveSandbox {

    private static final Logger log = LoggerFactory.getLogger(InteractiveSandboxService.class);

    /** Name prefix for sandbox containers, distinct from the CI {@code local-ci-} prefix so each reaper matches only its own containers. */
    public static final String SANDBOX_CONTAINER_PREFIX = "hyperion-gen-";

    @Value("${artemis.continuous-integration.build-agent.short-name:build-agent}")
    private String buildAgentShortName;

    private static final String WORKING_DIRECTORY = "/workspace";

    private static final String STOP_SENTINEL = WORKING_DIRECTORY + "/.stop_sandbox";

    /** Cap on captured stdout/stderr returned to the caller; longer output is truncated to the tail (where compiler/test failures appear) to bound the agent's context. */
    private static final int MAX_CAPTURED_OUTPUT_CHARS = 50_000;

    private final BuildAgentConfiguration buildAgentConfiguration;

    /**
     * Wall-clock of the last operation driven against each live session, keyed by container id. {@link InteractiveSandboxReaperService} reads this to tell a long-but-healthy
     * session apart from a genuine orphan: Docker labels are immutable once a container is created, so a daemon-side "last activity" stamp is impossible, and this in-JVM registry
     * is the cheapest in-process equivalent. Every session on this agent is driven through this bean (directly when co-located, via {@link InteractiveSandboxRelayHandler}
     * otherwise), so the registry sees all activity. A container absent from the map — e.g. one left behind by a previous agent process — has no known activity, and the reaper
     * falls back to its creation time so genuine orphans are still collected.
     */
    private final Map<String, SessionState> sessionStates = new ConcurrentHashMap<>();

    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();

    private static final class SessionState {

        private Instant lastActivity;

        private int activeOperations;

        private boolean closing;

        private SessionState(Instant lastActivity) {
            this.lastActivity = lastActivity;
        }
    }

    private final class OperationLease implements Closeable {

        private final SessionState state;

        private boolean closed;

        private OperationLease(SessionState state) {
            this.state = state;
        }

        @Override
        public void close() {
            synchronized (state) {
                if (closed) {
                    return;
                }
                closed = true;
                state.activeOperations--;
                if (!state.closing) {
                    state.lastActivity = Instant.now();
                }
            }
        }
    }

    public InteractiveSandboxService(BuildAgentConfiguration buildAgentConfiguration) {
        this.buildAgentConfiguration = buildAgentConfiguration;
    }

    /** Stamps the given session as active now, so the reaper does not mistake a long-running healthy session for an orphan. */
    void markActive(String containerId) {
        lifecycleLock.readLock().lock();
        try {
            SessionState state = sessionStates.computeIfAbsent(containerId, ignored -> new SessionState(Instant.now()));
            synchronized (state) {
                if (!state.closing) {
                    state.lastActivity = Instant.now();
                }
            }
        }
        finally {
            lifecycleLock.readLock().unlock();
        }
    }

    /** The wall-clock of the last recorded activity for the given container, or empty if this process never drove it (the reaper then falls back to creation time). */
    Optional<Instant> lastActivity(String containerId) {
        SessionState state = sessionStates.get(containerId);
        if (state == null) {
            return Optional.empty();
        }
        synchronized (state) {
            return Optional.of(state.lastActivity);
        }
    }

    /** Returns whether Docker still knows the session container. */
    boolean sessionExists(String containerId) {
        DockerClient dockerClient = buildAgentConfiguration.getDockerClient();
        try (final var inspectCommand = dockerClient.inspectContainerCmd(containerId)) {
            inspectCommand.exec();
            return true;
        }
        catch (NotFoundException e) {
            return false;
        }
    }

    /** Drops the activity entry for a session that has been (or is about to be) removed, bounding the registry to sessions still alive on this agent. */
    void forgetActivity(String containerId) {
        sessionStates.remove(containerId);
    }

    /** Atomically rechecks inactivity and removes the container, preventing a concurrent operation from being admitted between those actions. */
    boolean reapSessionIfInactive(String containerId, long createdEpochSecond, long idleThresholdSeconds) {
        lifecycleLock.writeLock().lock();
        try {
            SessionState state = sessionStates.computeIfAbsent(containerId, ignored -> new SessionState(Instant.ofEpochSecond(createdEpochSecond)));
            synchronized (state) {
                if (state.closing || state.activeOperations > 0 || Instant.now().getEpochSecond() - state.lastActivity.getEpochSecond() <= idleThresholdSeconds) {
                    return false;
                }
                state.closing = true;
            }
            try {
                removeSession(containerId);
                return true;
            }
            catch (RuntimeException e) {
                synchronized (state) {
                    state.closing = false;
                }
                throw e;
            }
        }
        finally {
            lifecycleLock.writeLock().unlock();
        }
    }

    /**
     * Removes every sandbox container owned by this build-agent identity. This reconciles leftovers before startup and closes sessions during shutdown, including containers whose
     * CREATE response was lost before the relay recorded their id.
     *
     * @return the number of leftover containers removed
     */
    int removeSessionsForCurrentAgent() {
        if (!buildAgentConfiguration.isDockerAvailable()) {
            throw new LocalCIException("Docker is not available. Cannot reconcile interactive sandbox sessions.");
        }
        DockerClient dockerClient = buildAgentConfiguration.getDockerClient();
        String namePrefix = containerNamePrefix();
        List<Container> previousSessions = dockerClient.listContainersCmd().withShowAll(true).exec().stream().filter(container -> hasSandboxContainerName(container, namePrefix))
                .toList();
        int removed = 0;
        RuntimeException firstFailure = null;
        for (Container container : previousSessions) {
            try (final var removeCommand = dockerClient.removeContainerCmd(container.getId()).withForce(true)) {
                removeCommand.exec();
                forgetActivity(container.getId());
                removed++;
            }
            catch (NotFoundException ignored) {
                forgetActivity(container.getId());
            }
            catch (RuntimeException e) {
                if (firstFailure == null) {
                    firstFailure = e;
                }
                log.warn("Failed to remove interactive sandbox session {} during reconciliation: {}", container.getId(), e.getMessage());
            }
        }
        if (firstFailure != null) {
            throw new LocalCIException("Could not reconcile interactive sandbox sessions.", firstFailure);
        }
        return removed;
    }

    @Override
    public String createSession(SandboxSessionSpec spec) {
        if (!buildAgentConfiguration.isDockerAvailable()) {
            throw new LocalCIException("Docker is not available. Cannot create interactive sandbox session.");
        }
        String containerName = containerNamePrefix() + UUID.randomUUID();
        DockerClient dockerClient = buildAgentConfiguration.getDockerClient();
        HostConfig hostConfig = hardenedHostConfig();
        if (spec.runConfig() != null && spec.runConfig().network() != null && !spec.runConfig().network().isBlank()) {
            if (!"none".equals(spec.runConfig().network())) {
                throw new LocalCIException("Interactive sandbox sessions only allow Docker network mode 'none'.");
            }
        }
        try (final var createCommand = dockerClient.createContainerCmd(spec.image())) {
            // Main process is an idle wait-loop keeping the container warm until the stop sentinel appears; the session is driven entirely by separate `docker exec` calls.
            var response = createCommand.withName(containerName).withHostConfig(hostConfig).withEntrypoint()
                    .withCmd("sh", "-c", "mkdir -p " + WORKING_DIRECTORY + "; while [ ! -f " + STOP_SENTINEL + " ]; do sleep 0.5; done").exec();
            String containerId = response.getId();
            try {
                try (final var startCommand = dockerClient.startContainerCmd(containerId)) {
                    startCommand.exec();
                }
            }
            catch (RuntimeException startFailure) {
                try (final var removeCommand = dockerClient.removeContainerCmd(containerId).withForce(true)) {
                    removeCommand.exec();
                }
                catch (RuntimeException cleanupFailure) {
                    startFailure.addSuppressed(cleanupFailure);
                }
                throw startFailure;
            }
            markActive(containerId);
            log.info("Started interactive sandbox session {} (container {})", containerName, containerId);
            return containerId;
        }
    }

    /**
     * The CI host config plus a build-safe hardening delta.
     * <p>
     * Starts from {@link BuildAgentConfiguration#hostConfig()} (fresh per call, safe to mutate) to inherit the CI CPU/memory/PID limits, then:
     * <ul>
     * <li>disables auto-remove — the container is torn down explicitly by {@link #destroySession}; auto-remove would race that and could delete it under an in-flight exec;</li>
     * <li>adds {@code no-new-privileges} so no exec inside the container can gain privileges via setuid binaries;</li>
     * <li>drops all Linux capabilities; the Java toolchain does not require privileged kernel operations.</li>
     * </ul>
     */
    private HostConfig hardenedHostConfig() {
        return buildAgentConfiguration.hostConfig().withAutoRemove(false).withNetworkMode("none").withSecurityOpts(List.of("no-new-privileges")).withCapDrop(Capability.ALL);
    }

    String containerNamePrefix() {
        return SANDBOX_CONTAINER_PREFIX + sanitizedBuildAgentShortName() + "-";
    }

    static boolean hasSandboxContainerName(Container container, String namePrefix) {
        if (container.getNames() == null) {
            return false;
        }
        String prefix = "/" + namePrefix;
        return List.of(container.getNames()).stream().anyMatch(name -> {
            if (!name.startsWith(prefix)) {
                return false;
            }
            try {
                UUID.fromString(name.substring(prefix.length()));
                return true;
            }
            catch (IllegalArgumentException ignored) {
                return false;
            }
        });
    }

    private String sanitizedBuildAgentShortName() {
        String shortName = buildAgentShortName == null || buildAgentShortName.isBlank() ? "build-agent" : buildAgentShortName;
        return shortName.replaceAll("[^a-zA-Z0-9_.-]", "-");
    }

    @Override
    public SandboxExecResult exec(String sessionId, Duration timeout, String... command) {
        try (OperationLease ignored = beginOperation(sessionId)) {
            DockerClient dockerClient = buildAgentConfiguration.getDockerClient();
            try (final var execCreateCommand = dockerClient.execCreateCmd(sessionId).withAttachStdout(true).withAttachStderr(true).withCmd(command)) {
                ExecCreateCmdResponse execCreateResponse = execCreateCommand.exec();
                String execId = execCreateResponse.getId();

                StringBuilder stdout = new StringBuilder();
                StringBuilder stderr = new StringBuilder();
                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<Throwable> errorRef = new AtomicReference<>();

                // withDetach(false) keeps the stream open until the command finishes, so onComplete fires only after the command completed. The callback owns the HTTP stream to
                // the
                // daemon from the shared docker client pool; it must be closed on every exit path (especially the timeout branch, where onComplete never fires) or the connection
                // leaks.
                ResultCallback.Adapter<Frame> callback = dockerClient.execStartCmd(execId).withDetach(false).exec(new ResultCallback.Adapter<>() {

                    @Override
                    public void onNext(Frame item) {
                        String payload = new String(item.getPayload(), StandardCharsets.UTF_8);
                        if (item.getStreamType() == StreamType.STDERR) {
                            appendBounded(stderr, payload);
                        }
                        else {
                            appendBounded(stdout, payload);
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        log.error("Error while executing a sandbox command in session {}", sessionId, throwable);
                        errorRef.set(throwable);
                        latch.countDown();
                    }

                    @Override
                    public void onComplete() {
                        latch.countDown();
                    }
                });
                try {
                    boolean completed;
                    try {
                        completed = latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
                    }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new LocalCIException("Interrupted while executing sandbox command: " + String.join(" ", command), e);
                    }

                    if (!completed) {
                        destroySession(sessionId);
                        closeQuietly(callback);
                        return new SandboxExecResult(-1, snapshotTail(stdout), snapshotTail(stderr), true);
                    }

                    Throwable execError = errorRef.get();
                    if (execError != null) {
                        throw new LocalCIException("Sandbox command failed: " + String.join(" ", command), execError);
                    }

                    int exitCode;
                    try (final var inspectCommand = dockerClient.inspectExecCmd(execId)) {
                        InspectExecResponse inspectResponse = inspectCommand.exec();
                        Long exitCodeLong = inspectResponse.getExitCodeLong();
                        exitCode = exitCodeLong != null ? exitCodeLong.intValue() : -1;
                    }
                    return new SandboxExecResult(exitCode, snapshotTail(stdout), snapshotTail(stderr), false);
                }
                finally {
                    // Release the client-side stream/connection back to the shared pool on every path; failing to close would leak a connection also used by CI builds.
                    closeQuietly(callback);
                }
            }
        }
    }

    @Override
    public void copyIn(String sessionId, String destinationPath, InputStream tarArchive) {
        try (OperationLease ignored = beginOperation(sessionId)) {
            DockerClient dockerClient = buildAgentConfiguration.getDockerClient();
            try (final var copyCommand = dockerClient.copyArchiveToContainerCmd(sessionId).withTarInputStream(tarArchive).withRemotePath(destinationPath)) {
                copyCommand.exec();
            }
        }
    }

    @Override
    public TarArchiveInputStream copyOut(String sessionId, String path) {
        OperationLease lease = beginOperation(sessionId);
        DockerClient dockerClient = buildAgentConfiguration.getDockerClient();
        try (final var copyCommand = dockerClient.copyArchiveFromContainerCmd(sessionId, path)) {
            InputStream archiveStream = copyCommand.exec();
            InputStream leasedStream = new FilterInputStream(archiveStream) {

                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    }
                    finally {
                        lease.close();
                    }
                }
            };
            try {
                return new TarArchiveInputStream(leasedStream);
            }
            catch (RuntimeException e) {
                closeQuietly(leasedStream); // do not leak the Docker response stream or operation lease if the wrapper cannot be constructed
                throw e;
            }
        }
        catch (RuntimeException e) {
            lease.close();
            throw e;
        }
    }

    @Override
    public void destroySession(String sessionId) {
        if (!buildAgentConfiguration.isDockerAvailable()) {
            throw new LocalCIException("Cannot remove interactive sandbox session " + sessionId + " because Docker is unavailable");
        }
        lifecycleLock.writeLock().lock();
        try {
            removeSession(sessionId);
        }
        finally {
            lifecycleLock.writeLock().unlock();
        }
    }

    private void removeSession(String sessionId) {
        DockerClient dockerClient = buildAgentConfiguration.getDockerClient();
        try (final var removeCommand = dockerClient.removeContainerCmd(sessionId).withForce(true)) {
            removeCommand.exec();
            forgetActivity(sessionId);
        }
        catch (NotFoundException e) {
            forgetActivity(sessionId);
        }
        catch (RuntimeException e) {
            throw new LocalCIException("Failed to remove interactive sandbox session " + sessionId, e);
        }
    }

    private OperationLease beginOperation(String sessionId) {
        lifecycleLock.readLock().lock();
        try {
            SessionState state = sessionStates.get(sessionId);
            if (state == null) {
                throw new LocalCIException("Interactive sandbox session " + sessionId + " is not active on this build agent");
            }
            synchronized (state) {
                if (state.closing) {
                    throw new LocalCIException("Interactive sandbox session " + sessionId + " is being removed");
                }
                state.activeOperations++;
                state.lastActivity = Instant.now();
            }
            return new OperationLease(state);
        }
        finally {
            lifecycleLock.readLock().unlock();
        }
    }

    private static void closeQuietly(Closeable closeable) {
        try {
            closeable.close();
        }
        catch (IOException e) {
            log.debug("Failed to close sandbox stream: {}", e.getMessage());
        }
    }

    static void appendBounded(StringBuilder builder, String payload) {
        synchronized (builder) {
            int retainedCharacters = MAX_CAPTURED_OUTPUT_CHARS * 2;
            if (payload.length() >= retainedCharacters) {
                builder.setLength(0);
                builder.append(payload, payload.length() - retainedCharacters, payload.length());
                return;
            }
            builder.append(payload);
            if (builder.length() > retainedCharacters) {
                builder.delete(0, builder.length() - retainedCharacters);
            }
        }
    }

    private static String snapshotTail(StringBuilder builder) {
        synchronized (builder) {
            return truncateTail(builder.toString());
        }
    }

    static String truncateTail(String value) {
        if (value.length() <= MAX_CAPTURED_OUTPUT_CHARS) {
            return value;
        }
        String tail = value.substring(value.length() - MAX_CAPTURED_OUTPUT_CHARS);
        return "[... output truncated, showing last " + MAX_CAPTURED_OUTPUT_CHARS + " characters ...]\n" + tail;
    }
}
