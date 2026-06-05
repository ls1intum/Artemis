package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResult;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxOpRequest;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxOpResponse;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;
import de.tum.cit.aet.artemis.localci.service.distributed.api.topic.DistributedTopic;

/**
 * Build-agent-side endpoint of the interactive-sandbox relay: it performs, on the local Docker host, the operations a core node requests over the
 * {@code hyperion-sandbox-requests} topic and publishes the result on {@code hyperion-sandbox-responses}.
 * <p>
 * It self-filters on this agent's short name exactly like the existing build-job cancel / pause / resume listeners: every agent receives every broadcast request, but only the
 * agent named in {@link SandboxOpRequest#targetAgentShortName()} acts on it. The work itself ({@code docker exec}, image pull, archive copy) is never run on the topic-listener
 * thread — that thread only hands the request to a small worker pool, mirroring the build-queue listener's rule that no heavy work runs on the distributed event thread.
 * <p>
 * Handling is idempotent per correlation id: a redelivered broadcast (or a {@code CREATE}/{@code DESTROY} already in flight) is dropped rather than performed twice. A small
 * per-agent session semaphore caps how many concurrent generation sessions this agent will host so a generation cannot silently starve CI capacity; the permit is released on
 * {@code DESTROY}. This is a deliberately simple v1 guard — see the TODO on {@link #sessionPermits}.
 *
 * @see RemoteInteractiveSandboxClient the core-node client whose requests this handler serves
 * @see InteractiveSandboxService the local implementation that actually performs each operation
 */
@Lazy(false)
@Component
@Profile(PROFILE_BUILDAGENT)
public class InteractiveSandboxRelayHandler {

    private static final Logger log = LoggerFactory.getLogger(InteractiveSandboxRelayHandler.class);

    private final InteractiveSandboxService interactiveSandboxService;

    private final DistributedDataAccessService distributedDataAccessService;

    @Value("${artemis.continuous-integration.build-agent.short-name}")
    private String buildAgentShortName;

    /**
     * Maximum number of concurrent interactive sandbox sessions this agent will host. A session is long-lived (several minutes), so this is a coarse guard that keeps generation
     * from monopolizing the agent.
     * <p>
     * TODO(v1): this is a standalone per-agent session limit, intentionally decoupled from the build-job scheduler's thread-pool accounting to keep the change low-risk. A tighter
     * integration would have a generation session consume a real build-executor slot (so CI and generation share one capacity budget and the existing availability checks apply
     * uniformly). Wire that in once the scheduler exposes a reservation API.
     */
    @Value("${artemis.continuous-integration.build-agent.max-concurrent-generation-sessions:1}")
    private int maxConcurrentSessions;

    private Semaphore sessionPermits;

    /**
     * Bound on {@link #handledCorrelationIds}: an upper limit on the number of recently-seen correlation ids retained for de-duplication. Each relayed operation mints one fresh,
     * single-use correlation id, so this covers far more in-flight + recently-completed ops than any realistic redelivery window, while keeping the set from growing without bound
     * on
     * a long-lived agent.
     */
    private static final int MAX_REMEMBERED_CORRELATION_IDS = 10_000;

    /**
     * Correlation ids currently being handled or already handled, so a redelivered broadcast is not performed twice. Bounded to {@link #MAX_REMEMBERED_CORRELATION_IDS} entries
     * with
     * insertion-order (FIFO) eviction (see {@link #markHandled}) so it cannot grow without bound on a long-lived agent. Correlation ids are single-use (the client mints a fresh
     * UUID
     * per call and never retries one), so evicting the oldest entries can never resurrect a live de-duplication key. Guarded by its own monitor; the listener hands off to a worker
     * pool, so {@link #handle} can run concurrently.
     */
    private final LinkedHashMap<String, Boolean> handledCorrelationIds = new LinkedHashMap<>();

    /** Container ids of sessions this agent owns and for which a session permit is held, so DESTROY releases a permit at most once. */
    private final Set<String> ownedSessions = ConcurrentHashMap.newKeySet();

    private DistributedTopic<SandboxOpRequest> requestsTopic;

    private DistributedTopic<SandboxOpResponse> responsesTopic;

    private UUID requestListenerId;

    private ExecutorService workerExecutor;

    public InteractiveSandboxRelayHandler(InteractiveSandboxService interactiveSandboxService, DistributedDataAccessService distributedDataAccessService) {
        this.interactiveSandboxService = interactiveSandboxService;
        this.distributedDataAccessService = distributedDataAccessService;
    }

    /**
     * Subscribes to the request topic and starts the worker pool. The listener thread only filters and hands off; all Docker work happens on the worker pool.
     */
    @PostConstruct
    public void registerRequestListener() {
        this.sessionPermits = new Semaphore(Math.max(1, maxConcurrentSessions));
        this.workerExecutor = Executors.newFixedThreadPool(Math.max(1, maxConcurrentSessions) + 1, namedDaemonThreadFactory());
        this.requestsTopic = distributedDataAccessService.getHyperionSandboxRequestsTopic();
        this.responsesTopic = distributedDataAccessService.getHyperionSandboxResponsesTopic();
        this.requestListenerId = requestsTopic.addMessageListener(request -> {
            // Self-filter: ignore every request that does not target this agent, exactly like the pause/resume/cancel listeners.
            if (!buildAgentShortName.equals(request.targetAgentShortName())) {
                return;
            }
            // Never do Docker work on the topic-listener (distributed event) thread: hand off to the worker pool.
            workerExecutor.submit(() -> handle(request));
        });
        log.info("InteractiveSandboxRelayHandler initialized for build agent '{}' (max concurrent generation sessions: {})", buildAgentShortName, maxConcurrentSessions);
    }

    /**
     * Removes the request listener and stops the worker pool on shutdown, so a redeployed agent does not leave a dangling subscription.
     */
    @PreDestroy
    public void shutdown() {
        if (requestListenerId != null && requestsTopic != null) {
            requestsTopic.removeMessageListener(requestListenerId);
        }
        if (workerExecutor != null) {
            workerExecutor.shutdownNow();
        }
    }

    /**
     * Performs one relayed operation on the local Docker host and publishes its response. Runs on a worker thread, never on the listener thread.
     *
     * @param request the operation to perform (already confirmed to target this agent)
     */
    private void handle(SandboxOpRequest request) {
        // Idempotency: the first delivery for a correlation id wins; any redelivery is dropped without re-running the operation or re-publishing a response.
        // Invariant: correlation ids are single-use; a failed op is never retried under the same id, so marking handled before doing the work is safe.
        if (!markHandled(request.correlationId())) {
            log.debug("Dropping duplicate sandbox request {} ({})", request.correlationId(), request.op());
            return;
        }
        try {
            SandboxOpResponse response = switch (request.op()) {
                case CREATE -> handleCreate(request);
                case EXEC -> handleExec(request);
                case COPY_IN -> handleCopyIn(request);
                case COPY_OUT -> handleCopyOut(request);
                case DESTROY -> handleDestroy(request);
            };
            responsesTopic.publish(response);
        }
        catch (Exception e) {
            log.warn("Interactive sandbox relay operation {} ({}) failed on agent '{}': {}", request.op(), request.correlationId(), buildAgentShortName, e.getMessage());
            responsesTopic.publish(SandboxOpResponse.failure(request.correlationId(), e.getMessage()));
        }
    }

    /**
     * Records a correlation id as handled, returning {@code true} only on its first appearance. The backing map is bounded to {@link #MAX_REMEMBERED_CORRELATION_IDS} entries with
     * insertion-order (FIFO) eviction so it cannot grow without bound; because correlation ids are single-use, evicting the oldest entries never resurrects a live de-duplication
     * key.
     *
     * @param correlationId the correlation id of the request being handled
     * @return {@code true} if this is the first delivery for the id (caller should proceed), {@code false} if it was already handled (caller should drop it)
     */
    private boolean markHandled(String correlationId) {
        synchronized (handledCorrelationIds) {
            if (handledCorrelationIds.putIfAbsent(correlationId, Boolean.TRUE) != null) {
                return false;
            }
            // Evict the oldest entries once the cap is exceeded; a LinkedHashMap iterates in insertion order, so this is FIFO.
            Iterator<String> iterator = handledCorrelationIds.keySet().iterator();
            while (handledCorrelationIds.size() > MAX_REMEMBERED_CORRELATION_IDS && iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
            return true;
        }
    }

    private SandboxOpResponse handleCreate(SandboxOpRequest request) {
        // Capacity guard: refuse rather than silently starve CI when this agent is already at its session cap.
        if (!sessionPermits.tryAcquire()) {
            return SandboxOpResponse.failure(request.correlationId(),
                    "Build agent '" + buildAgentShortName + "' is at its interactive sandbox session capacity (" + maxConcurrentSessions + ").");
        }
        boolean created = false;
        try {
            String containerId = interactiveSandboxService.createSession(request.sessionSpec());
            ownedSessions.add(containerId);
            created = true;
            return new SandboxOpResponse(request.correlationId(), true, containerId, null, null, null);
        }
        finally {
            // Release the permit if the container never came up, so a failed create does not leak capacity.
            if (!created) {
                sessionPermits.release();
            }
        }
    }

    private SandboxOpResponse handleExec(SandboxOpRequest request) {
        SandboxExecResult result = interactiveSandboxService.exec(request.sessionId(), Duration.ofSeconds(request.timeoutSeconds()), request.command());
        return new SandboxOpResponse(request.correlationId(), true, request.sessionId(), result, null, null);
    }

    private SandboxOpResponse handleCopyIn(SandboxOpRequest request) {
        byte[] payload = request.payload() != null ? request.payload() : new byte[0];
        try (InputStream tar = new ByteArrayInputStream(payload)) {
            interactiveSandboxService.copyIn(request.sessionId(), request.workspacePath(), tar);
        }
        catch (IOException e) {
            return SandboxOpResponse.failure(request.correlationId(), "Failed to read copy-in payload: " + e.getMessage());
        }
        return SandboxOpResponse.ok(request.correlationId(), request.sessionId());
    }

    private SandboxOpResponse handleCopyOut(SandboxOpRequest request) {
        try (TarArchiveInputStream tar = interactiveSandboxService.copyOut(request.sessionId(), request.workspacePath())) {
            byte[] payload = repackTar(tar);
            return new SandboxOpResponse(request.correlationId(), true, request.sessionId(), null, payload, null);
        }
        catch (IOException e) {
            return SandboxOpResponse.failure(request.correlationId(), "Failed to buffer copy-out archive: " + e.getMessage());
        }
    }

    /**
     * Re-serializes the entries of a {@link TarArchiveInputStream} into a fresh tar byte array for transport, preserving each entry's name, size, content, and (for regular files)
     * directory flag. The local {@code copyOut} hands back a decoding {@link TarArchiveInputStream} rather than the raw Docker bytes, so the relay rebuilds an equivalent archive
     * that the core-node client can re-wrap as a {@link TarArchiveInputStream} and read exactly as it would in the co-located case. Fails closed if the repacked archive exceeds
     * {@link RemoteInteractiveSandboxClient#MAX_PAYLOAD_BYTES} so an oversized extraction cannot overwhelm the messaging layer.
     *
     * @param source the decoded tar stream from the local sandbox
     * @return the repacked tar bytes
     * @throws IOException if reading or repacking fails, or the archive exceeds the relay payload limit
     */
    private static byte[] repackTar(TarArchiveInputStream source) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); TarArchiveOutputStream tar = new TarArchiveOutputStream(out)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            long total = 0;
            TarArchiveEntry entry;
            while ((entry = source.getNextEntry()) != null) {
                byte[] content = entry.isDirectory() ? new byte[0] : source.readAllBytes();
                total += content.length;
                if (total > RemoteInteractiveSandboxClient.MAX_PAYLOAD_BYTES) {
                    throw new IOException("Interactive sandbox copy-out archive exceeds the " + RemoteInteractiveSandboxClient.MAX_PAYLOAD_BYTES + " byte relay limit.");
                }
                TarArchiveEntry copy = new TarArchiveEntry(entry.getName());
                copy.setMode(entry.getMode());
                if (!entry.isDirectory()) {
                    copy.setSize(content.length);
                }
                tar.putArchiveEntry(copy);
                if (!entry.isDirectory()) {
                    tar.write(content);
                }
                tar.closeArchiveEntry();
            }
            tar.finish();
            return out.toByteArray();
        }
    }

    private SandboxOpResponse handleDestroy(SandboxOpRequest request) {
        try {
            interactiveSandboxService.destroySession(request.sessionId());
        }
        finally {
            // Release the session permit exactly once per owned session, even if the destroy was redundant.
            if (ownedSessions.remove(request.sessionId())) {
                sessionPermits.release();
            }
        }
        return SandboxOpResponse.ok(request.correlationId(), request.sessionId());
    }

    private static ThreadFactory namedDaemonThreadFactory() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "hyperion-sandbox-relay-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
