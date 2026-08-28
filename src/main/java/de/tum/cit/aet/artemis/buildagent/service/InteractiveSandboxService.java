package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResultDTO;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionSpecDTO;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;

/**
 * Build-agent-side {@link InteractiveSandbox}: a warm, resource-limited Docker container driven through many cheap operations.
 * <p>
 * Reuses the build-agent Docker client and {@link BuildAgentConfiguration#hostConfig()} so isolation matches a CI build container (same CPU/memory/PID limits), then applies the
 * hardening delta documented on {@code hardenedHostConfig}. Unlike the regular build path it captures and returns each command's stdout/stderr to the caller.
 */
@Lazy
@Service
@Profile(PROFILE_BUILDAGENT)
public class InteractiveSandboxService implements InteractiveSandbox {

    private static final Logger log = LoggerFactory.getLogger(InteractiveSandboxService.class);

    /** Name prefix for sandbox containers, distinct from the CI {@code local-ci-} prefix so each reaper matches only its own containers. */
    public static final String SANDBOX_CONTAINER_PREFIX = "hyperion-gen-";

    // No default on purpose: a fallback here could only ever disagree with the name the rest of the build agent advertises.
    @Value("${artemis.continuous-integration.build-agent.short-name}")
    private String buildAgentShortName;

    private static final String WORKING_DIRECTORY = "/workspace";

    private static final Map<String, String> WRITABLE_FILESYSTEMS = Map.of(WORKING_DIRECTORY, "rw,exec,nosuid,nodev,size=512m", "/tmp", "rw,exec,nosuid,nodev,size=512m",
            "/opt/hyperion", "rw,exec,nosuid,nodev,size=256m", "/opt/hyperion-readiness-fixture", "rw,exec,nosuid,nodev,size=64m");

    private static final String IMAGE_VOLUME_TMPFS_OPTIONS = "rw,exec,nosuid,nodev,size=256m";

    private static final int MAX_IMAGE_VOLUME_COUNT = 16;

    /** Cap on captured stdout/stderr returned to the caller; longer output is truncated to the tail (where compiler/test failures appear) to bound the agent's context. */
    private static final int MAX_CAPTURED_OUTPUT_CHARS = 50_000;

    private static final int MAX_CAPTURED_OUTPUT_BYTES = MAX_CAPTURED_OUTPUT_CHARS * 4;

    private static final Duration COPY_TIMEOUT = Duration.ofMinutes(2);

    /**
     * Grace Docker gives PID 1 to exit on SIGTERM before escalating to SIGKILL while {@link #resetSession} restarts the container. Short because SIGKILL is safe here: every
     * writable path is a tmpfs the reset discards anyway, and PID 1 holds no state worth flushing.
     */
    static final int SESSION_RESET_STOP_GRACE_SECONDS = 5;

    private final BuildAgentConfiguration buildAgentConfiguration;

    private final BuildAgentDockerService buildAgentDockerService;

    /**
     * Wall-clock of the last operation driven against each live session, keyed by container id. {@link InteractiveSandboxReaperService} reads this to tell a long-but-healthy
     * session apart from a genuine orphan. Docker labels are immutable once a container is created, so a daemon-side "last activity" stamp is impossible; every session on this
     * agent is driven through this bean, so this in-JVM registry sees all activity. A container absent from the map (for instance one left behind by a previous agent process) has
     * no known activity, and the reaper falls back to its creation time.
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

    static final class BoundedOutput {

        private final byte[] bytes = new byte[MAX_CAPTURED_OUTPUT_BYTES];

        private int size;

        synchronized void append(byte[] payload) {
            if (payload.length >= bytes.length) {
                System.arraycopy(payload, payload.length - bytes.length, bytes, 0, bytes.length);
                size = bytes.length;
                return;
            }
            int overflow = Math.max(0, size + payload.length - bytes.length);
            if (overflow > 0) {
                System.arraycopy(bytes, overflow, bytes, 0, size - overflow);
                size -= overflow;
            }
            System.arraycopy(payload, 0, bytes, size, payload.length);
            size += payload.length;
        }

        synchronized String snapshot() {
            return truncateTail(new String(bytes, 0, size, StandardCharsets.UTF_8));
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

    public InteractiveSandboxService(BuildAgentConfiguration buildAgentConfiguration, BuildAgentDockerService buildAgentDockerService) {
        this.buildAgentConfiguration = buildAgentConfiguration;
        this.buildAgentDockerService = buildAgentDockerService;
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

    Optional<Instant> lastActivity(String containerId) {
        SessionState state = sessionStates.get(containerId);
        if (state == null) {
            return Optional.empty();
        }
        synchronized (state) {
            return Optional.of(state.lastActivity);
        }
    }

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

    /** Bounds the registry to sessions still alive on this agent. */
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
     * Removes every sandbox container owned by this build-agent identity, including containers whose CREATE response was lost before the caller ever learned their id.
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
                removeCommand.withRemoveVolumes(true);
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
    public String createSession(SandboxSessionSpecDTO spec) {
        if (!buildAgentConfiguration.isDockerAvailable()) {
            throw new LocalCIException("Docker is not available. Cannot create interactive sandbox session.");
        }
        String containerName = containerNamePrefix() + UUID.randomUUID();
        DockerClient dockerClient = buildAgentConfiguration.getDockerClient();
        if (spec.runConfig() != null && spec.runConfig().network() != null && !spec.runConfig().network().isBlank()) {
            if (!"none".equals(spec.runConfig().network())) {
                throw new LocalCIException("Interactive sandbox sessions only allow Docker network mode 'none'.");
            }
        }
        String immutableImageId = buildAgentDockerService.ensureDockerImageAvailable(spec.image());
        HostConfig hostConfig = hardenedHostConfig(dockerClient, immutableImageId);
        try (final var createCommand = dockerClient.createContainerCmd(immutableImageId)) {
            // The main process only keeps the container warm; the session is driven by separate `docker exec` calls and removed explicitly at teardown.
            var response = createCommand.withName(containerName).withHostConfig(hostConfig).withEntrypoint()
                    .withCmd("sh", "-c", "mkdir -p " + WORKING_DIRECTORY + "; while :; do sleep 3600; done").exec();
            String containerId = response.getId();
            try {
                try (final var startCommand = dockerClient.startContainerCmd(containerId)) {
                    startCommand.exec();
                }
            }
            catch (RuntimeException startFailure) {
                try (final var removeCommand = dockerClient.removeContainerCmd(containerId).withForce(true)) {
                    removeCommand.withRemoveVolumes(true);
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
     * The CI host config plus a build-safe hardening delta. Starts from {@link BuildAgentConfiguration#hostConfig()} (fresh per call, safe to mutate) to inherit the CI
     * CPU/memory/PID limits, drops privileges and capabilities, and puts every writable path on a bounded tmpfs. Two settings are less obvious:
     * <ul>
     * <li>auto-remove is disabled: the container is torn down explicitly by {@link #destroySession}, and auto-remove would race that and could delete it under an in-flight
     * exec;</li>
     * <li>Docker's init (tini) runs as PID 1. The container command is a bare shell loop, and the kernel discards signals a PID 1 has no handler for, so a shell PID 1 would
     * ignore the SIGTERM {@link #resetSession} sends and every reset would burn the full stop grace before Docker escalated to SIGKILL. Init also reaps processes orphaned by
     * exec'd builds that would otherwise accumulate against the container PID limit over a long session.</li>
     * </ul>
     */
    private HostConfig hardenedHostConfig(DockerClient dockerClient, String immutableImageId) {
        Map<String, String> tmpFs = new LinkedHashMap<>(WRITABLE_FILESYSTEMS);
        try (var inspectImage = dockerClient.inspectImageCmd(immutableImageId)) {
            var image = inspectImage.exec();
            if (image.getConfig() != null && image.getConfig().getVolumes() != null) {
                if (image.getConfig().getVolumes().size() > MAX_IMAGE_VOLUME_COUNT) {
                    throw new LocalCIException("Interactive sandbox image declares too many volume paths");
                }
                for (String volumePath : image.getConfig().getVolumes().keySet()) {
                    if (isUnsafeImageVolume(volumePath)) {
                        throw new LocalCIException("Interactive sandbox image declares an unsafe volume path: " + volumePath);
                    }
                    tmpFs.putIfAbsent(volumePath, IMAGE_VOLUME_TMPFS_OPTIONS);
                }
            }
        }
        HostConfig hostConfig = buildAgentConfiguration.hostConfig();
        requireResourceLimits(hostConfig);
        return hostConfig.withAutoRemove(false).withNetworkMode("none").withSecurityOpts(List.of("no-new-privileges")).withCapDrop(Capability.ALL).withReadonlyRootfs(true)
                .withTmpFs(Map.copyOf(tmpFs)).withInit(true);
    }

    private static boolean isUnsafeImageVolume(String path) {
        return path == null || !path.startsWith("/") || "/".equals(path) || path.equals("/proc") || path.startsWith("/proc/") || path.equals("/sys") || path.startsWith("/sys/")
                || path.equals("/dev") || path.startsWith("/dev/");
    }

    private static void requireResourceLimits(HostConfig hostConfig) {
        long cpuQuota = Optional.ofNullable(hostConfig.getCpuQuota()).orElse(0L);
        long memory = Optional.ofNullable(hostConfig.getMemory()).orElse(0L);
        long memorySwap = Optional.ofNullable(hostConfig.getMemorySwap()).orElse(0L);
        long pidsLimit = Optional.ofNullable(hostConfig.getPidsLimit()).orElse(0L);
        if (cpuQuota <= 0 || memory <= 0 || memorySwap != memory || pidsLimit <= 0) {
            throw new LocalCIException(
                    "Interactive sandboxes require positive CPU, memory, and PID limits, with memory-swap equal to memory so generated code cannot exhaust the host");
        }
    }

    String containerNamePrefix() {
        return containerNamePrefix(buildAgentShortName);
    }

    static String containerNamePrefix(String buildAgentShortName) {
        return SANDBOX_CONTAINER_PREFIX + sanitizedBuildAgentShortName(buildAgentShortName) + "-";
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

    private static String sanitizedBuildAgentShortName(String buildAgentShortName) {
        String shortName = buildAgentShortName == null || buildAgentShortName.isBlank() ? "build-agent" : buildAgentShortName;
        return shortName.replaceAll("[^a-zA-Z0-9_.-]", "-");
    }

    @Override
    public SandboxExecResultDTO exec(String sessionId, Duration timeout, String... command) {
        try (OperationLease ignored = beginOperation(sessionId)) {
            DockerClient dockerClient = buildAgentConfiguration.getDockerClient();
            try (final var execCreateCommand = dockerClient.execCreateCmd(sessionId).withAttachStdout(true).withAttachStderr(true).withCmd(command)) {
                ExecCreateCmdResponse execCreateResponse = execCreateCommand.exec();
                String execId = execCreateResponse.getId();

                BoundedOutput stdout = new BoundedOutput();
                BoundedOutput stderr = new BoundedOutput();
                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<Throwable> errorRef = new AtomicReference<>();

                // withDetach(false) keeps the stream open until the command finishes, so onComplete fires only once it has. The callback owns an HTTP connection from the shared
                // docker client pool that CI builds also draw on, so it must be closed on every exit path — especially the timeout branch, where onComplete never fires.
                ResultCallback.Adapter<Frame> callback = dockerClient.execStartCmd(execId).withDetach(false).exec(new ResultCallback.Adapter<>() {

                    @Override
                    public void onNext(Frame item) {
                        if (item.getStreamType() == StreamType.STDERR) {
                            stderr.append(item.getPayload());
                        }
                        else {
                            stdout.append(item.getPayload());
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        log.error("Sandbox command stream failed in session {} ({})", sessionId, throwable.getClass().getSimpleName());
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
                        LocalCIException failure = new LocalCIException("Interrupted while executing sandbox command", e);
                        invalidateSessionAfterOperationFailure(sessionId, callback, failure);
                        throw failure;
                    }

                    if (!completed) {
                        destroySession(sessionId);
                        closeQuietly(callback);
                        return new SandboxExecResultDTO(-1, stdout.snapshot(), stderr.snapshot(), true);
                    }

                    Throwable execError = errorRef.get();
                    if (execError != null) {
                        LocalCIException failure = new LocalCIException("Sandbox command failed", execError);
                        invalidateSessionAfterOperationFailure(sessionId, callback, failure);
                        throw failure;
                    }

                    int exitCode;
                    try {
                        try (final var inspectCommand = dockerClient.inspectExecCmd(execId)) {
                            InspectExecResponse inspectResponse = inspectCommand.exec();
                            Long exitCodeLong = inspectResponse.getExitCodeLong();
                            exitCode = exitCodeLong != null ? exitCodeLong.intValue() : -1;
                        }
                    }
                    catch (RuntimeException inspectFailure) {
                        LocalCIException failure = new LocalCIException("Could not inspect sandbox command", inspectFailure);
                        invalidateSessionAfterOperationFailure(sessionId, callback, failure);
                        throw failure;
                    }
                    return new SandboxExecResultDTO(exitCode, stdout.snapshot(), stderr.snapshot(), false);
                }
                finally {
                    closeQuietly(callback);
                }
            }
        }
    }

    @Override
    public void copyIn(String sessionId, String destinationPath, InputStream tarArchive) {
        try (OperationLease ignored = beginOperation(sessionId)) {
            validateCopyInDestination(destinationPath);
            byte[] archive;
            try {
                archive = tarArchive.readNBytes(RemoteInteractiveSandboxClient.MAX_PAYLOAD_BYTES + 1);
            }
            catch (IOException e) {
                throw new LocalCIException("Could not read files for sandbox session " + sessionId, e);
            }
            if (archive.length > RemoteInteractiveSandboxClient.MAX_PAYLOAD_BYTES) {
                throw new LocalCIException("Sandbox copy-in archive exceeds the " + RemoteInteractiveSandboxClient.MAX_PAYLOAD_BYTES + " byte relay limit.");
            }
            validateCopyInArchive(archive);
            DockerClient dockerClient = buildAgentConfiguration.getDockerClient();
            String copyCommand = "head -c \"$1\" | tar -xf - -C \"$2\"";
            try (final var createCommand = dockerClient.execCreateCmd(sessionId).withAttachStdin(true).withAttachStdout(true).withAttachStderr(true).withCmd("sh", "-c",
                    copyCommand, "sandbox-copy-in", Integer.toString(archive.length), destinationPath)) {
                String execId = createCommand.exec().getId();
                BoundedOutput stderr = new BoundedOutput();
                AtomicReference<Throwable> errorRef = new AtomicReference<>();
                ResultCallback.Adapter<Frame> callback = dockerClient.execStartCmd(execId).withDetach(false).withStdIn(new ByteArrayInputStream(archive))
                        .exec(new ResultCallback.Adapter<>() {

                            @Override
                            public void onNext(Frame frame) {
                                if (frame.getStreamType() == StreamType.STDERR) {
                                    stderr.append(frame.getPayload());
                                }
                            }

                            @Override
                            public void onError(Throwable throwable) {
                                errorRef.set(throwable);
                                super.onError(throwable);
                            }
                        });
                try {
                    if (!callback.awaitCompletion(COPY_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                        throw new LocalCIException("Timed out while copying files into sandbox session " + sessionId);
                    }
                    if (errorRef.get() != null) {
                        throw new LocalCIException("Could not copy files into sandbox session " + sessionId, errorRef.get());
                    }
                    try (final var inspectCommand = dockerClient.inspectExecCmd(execId)) {
                        Long exitCode = inspectCommand.exec().getExitCodeLong();
                        if (exitCode == null || exitCode != 0) {
                            throw new LocalCIException("Could not extract files into sandbox session " + sessionId + ": " + stderr.snapshot());
                        }
                    }
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LocalCIException failure = new LocalCIException("Interrupted while copying files into sandbox session " + sessionId, e);
                    invalidateSessionAfterOperationFailure(sessionId, callback, failure);
                    throw failure;
                }
                catch (RuntimeException failure) {
                    invalidateSessionAfterOperationFailure(sessionId, callback, failure);
                    throw failure;
                }
                finally {
                    closeQuietly(callback);
                }
            }
        }
    }

    static void validateCopyInDestination(String destinationPath) {
        Path destination;
        try {
            destination = Path.of(destinationPath).normalize();
        }
        catch (RuntimeException e) {
            throw new LocalCIException("Invalid sandbox copy-in destination", e);
        }
        boolean writable = destination.isAbsolute() && WRITABLE_FILESYSTEMS.keySet().stream().map(Path::of).anyMatch(root -> destination.startsWith(root.normalize()));
        if (!writable) {
            throw new LocalCIException("Sandbox copy-in destination is outside a writable sandbox root");
        }
    }

    static void validateCopyInArchive(byte[] archive) {
        try (TarArchiveInputStream tar = new TarArchiveInputStream(new ByteArrayInputStream(archive))) {
            var entry = tar.getNextEntry();
            while (entry != null) {
                Path name = Path.of(entry.getName()).normalize();
                if (name.isAbsolute() || name.startsWith("..") || entry.isSymbolicLink() || entry.isLink() || !entry.isFile() && !entry.isDirectory()) {
                    throw new LocalCIException("Sandbox copy-in archive contains an unsafe entry");
                }
                entry = tar.getNextEntry();
            }
        }
        catch (IOException | RuntimeException e) {
            if (e instanceof LocalCIException localCIException) {
                throw localCIException;
            }
            throw new LocalCIException("Could not validate sandbox copy-in archive", e);
        }
    }

    @Override
    public TarArchiveInputStream copyOut(String sessionId, String path) {
        try (OperationLease ignored = beginOperation(sessionId)) {
            DockerClient dockerClient = buildAgentConfiguration.getDockerClient();
            String copyCommand = "parent=${1%/*}; name=${1##*/}; [ -n \"$parent\" ] || parent=/; [ \"$parent\" != \"$1\" ] || parent=.; tar -cf - -C \"$parent\" \"$name\"";
            try (final var createCommand = dockerClient.execCreateCmd(sessionId).withAttachStdout(true).withAttachStderr(true).withCmd("sh", "-c", copyCommand, "sandbox-copy-out",
                    path)) {
                String execId = createCommand.exec().getId();
                ByteArrayOutputStream archive = new ByteArrayOutputStream();
                BoundedOutput stderr = new BoundedOutput();
                CountDownLatch latch = new CountDownLatch(1);
                AtomicBoolean oversized = new AtomicBoolean();
                AtomicReference<Throwable> errorRef = new AtomicReference<>();
                ResultCallback.Adapter<Frame> callback = dockerClient.execStartCmd(execId).withDetach(false).exec(new ResultCallback.Adapter<>() {

                    @Override
                    public void onNext(Frame frame) {
                        if (frame.getStreamType() == StreamType.STDERR) {
                            stderr.append(frame.getPayload());
                        }
                        else if (!oversized.get()) {
                            byte[] payload = frame.getPayload();
                            if (archive.size() + payload.length > RemoteInteractiveSandboxClient.MAX_PAYLOAD_BYTES) {
                                oversized.set(true);
                            }
                            else {
                                archive.writeBytes(payload);
                            }
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        errorRef.set(throwable);
                        latch.countDown();
                    }

                    @Override
                    public void onComplete() {
                        latch.countDown();
                    }
                });
                try {
                    if (!latch.await(COPY_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                        throw new LocalCIException("Timed out while copying files from sandbox session " + sessionId);
                    }
                    if (errorRef.get() != null) {
                        throw new LocalCIException("Could not copy files from sandbox session " + sessionId, errorRef.get());
                    }
                    if (oversized.get()) {
                        throw new LocalCIException("Sandbox copy-out archive exceeds the " + RemoteInteractiveSandboxClient.MAX_PAYLOAD_BYTES + " byte relay limit.");
                    }
                    try (final var inspectCommand = dockerClient.inspectExecCmd(execId)) {
                        Long exitCode = inspectCommand.exec().getExitCodeLong();
                        if (exitCode == null || exitCode != 0) {
                            throw new LocalCIException("Could not archive files from sandbox session " + sessionId + ": " + stderr.snapshot());
                        }
                    }
                    return new TarArchiveInputStream(new ByteArrayInputStream(archive.toByteArray()));
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LocalCIException failure = new LocalCIException("Interrupted while copying files from sandbox session " + sessionId, e);
                    invalidateSessionAfterOperationFailure(sessionId, callback, failure);
                    throw failure;
                }
                catch (RuntimeException failure) {
                    invalidateSessionAfterOperationFailure(sessionId, callback, failure);
                    throw failure;
                }
                finally {
                    closeQuietly(callback);
                }
            }
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

    @Override
    public void resetSession(String sessionId) {
        if (!buildAgentConfiguration.isDockerAvailable()) {
            throw new LocalCIException("Cannot reset interactive sandbox session " + sessionId + " because Docker is unavailable");
        }
        lifecycleLock.writeLock().lock();
        try {
            if (!sessionStates.containsKey(sessionId)) {
                throw new LocalCIException("Interactive sandbox session " + sessionId + " is not active on this build agent");
            }
            // A restart is what makes the reset authoritative: stopping the container tears down its mount and PID namespaces, so the kernel discards every writable tmpfs and
            // SIGKILLs every process left behind, including ones that ignore SIGTERM. Clearing paths from inside the container could not offer that guarantee, and a path missed
            // there would silently feed a stale candidate into the pristine verification build.
            try (var restartCommand = buildAgentConfiguration.getDockerClient().restartContainerCmd(sessionId).withTimeout(SESSION_RESET_STOP_GRACE_SECONDS)) {
                restartCommand.exec();
            }
            markActive(sessionId);
        }
        catch (RuntimeException e) {
            throw new LocalCIException("Failed to reset interactive sandbox session " + sessionId, e);
        }
        finally {
            lifecycleLock.writeLock().unlock();
        }
    }

    private void removeSession(String sessionId) {
        DockerClient dockerClient = buildAgentConfiguration.getDockerClient();
        try (final var removeCommand = dockerClient.removeContainerCmd(sessionId).withForce(true)) {
            removeCommand.withRemoveVolumes(true);
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

    private void invalidateSessionAfterOperationFailure(String sessionId, Closeable callback, RuntimeException failure) {
        closeQuietly(callback);
        try {
            destroySession(sessionId);
        }
        catch (RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
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
