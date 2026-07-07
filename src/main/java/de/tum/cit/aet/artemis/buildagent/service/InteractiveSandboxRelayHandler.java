package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashSet;
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
 * It self-filters on this agent's short name like the build-job cancel / pause / resume listeners: every agent receives every broadcast request, but only the agent named in
 * {@link SandboxOpRequest#targetAgentShortName()} acts on it. The work itself ({@code docker exec}, image pull, archive copy) never runs on the topic-listener thread — that thread
 * only hands the request to a small worker pool, so no heavy work runs on the distributed event thread.
 * <p>
 * Handling is idempotent per correlation id: a redelivered broadcast is dropped rather than performed twice. Hosting is opt-in per agent:
 * {@code max-concurrent-generation-sessions}
 * defaults to {@code 0} (the agent hosts nothing and does not even subscribe), a paused agent refuses new sessions, and a per-agent semaphore caps concurrent sessions — so
 * generation
 * never silently competes with CI or exam builds on an agent an operator did not deliberately dedicate to it. The permit is released on {@code DESTROY}.
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

    /** Consulted so a paused/draining agent sheds generation load too: pausing a build agent stops it accepting new sessions, not just new CI build jobs. */
    private final SharedQueueProcessingService sharedQueueProcessingService;

    @Value("${artemis.continuous-integration.build-agent.short-name}")
    private String buildAgentShortName;

    /**
     * Maximum number of concurrent interactive sandbox sessions this agent will host, on top of its CI build jobs. A session is a long-lived (several-minute), CI-sized container,
     * so
     * hosting one adds real CPU/memory/Docker load that the build-job scheduler does not account for. It therefore defaults to {@code 0}: an agent hosts generation only when an
     * operator explicitly opts it in, so enabling Hyperion never silently adds load to exam-critical build agents. Set a positive value on the spare agents you dedicate to
     * generation; {@code 0} means this agent never hosts a relayed session (the relay listener is not even registered).
     */
    @Value("${artemis.continuous-integration.build-agent.max-concurrent-generation-sessions:0}")
    private int maxConcurrentSessions;

    /**
     * Caps concurrent hosted sessions: acquired on CREATE, released on DESTROY. Known gap: if a CREATE succeeds but its response is lost in transit, the core client never learns
     * the container id and can never issue DESTROY, so the permit stays held until this agent restarts. The reaper reclaims the orphaned container but not its permit; acceptable
     * because agents restart routinely.
     */
    private Semaphore sessionPermits;

    /** Bound on {@link #handledCorrelationIds}: far more than any realistic in-flight + recently-completed redelivery window, yet bounded on a long-lived agent. */
    private static final int MAX_REMEMBERED_CORRELATION_IDS = 10_000;

    /**
     * Bounded FIFO set of handled correlation ids for at-most-once handling; single-use ids (the client mints a fresh UUID per call and never retries one) make oldest-entry
     * eviction safe. Guarded by its own monitor.
     */
    private final LinkedHashSet<String> handledCorrelationIds = new LinkedHashSet<>();

    /** Container ids of sessions this agent owns and for which a session permit is held, so DESTROY releases a permit at most once. */
    private final Set<String> ownedSessions = ConcurrentHashMap.newKeySet();

    private DistributedTopic<SandboxOpRequest> requestsTopic;

    private DistributedTopic<SandboxOpResponse> responsesTopic;

    private UUID requestListenerId;

    private ExecutorService workerExecutor;

    public InteractiveSandboxRelayHandler(InteractiveSandboxService interactiveSandboxService, DistributedDataAccessService distributedDataAccessService,
            @Lazy SharedQueueProcessingService sharedQueueProcessingService) {
        this.interactiveSandboxService = interactiveSandboxService;
        this.distributedDataAccessService = distributedDataAccessService;
        this.sharedQueueProcessingService = sharedQueueProcessingService;
    }

    /**
     * Subscribes to the request topic and starts the worker pool. The listener thread only filters and hands off; all Docker work happens on the worker pool.
     */
    @PostConstruct
    public void registerRequestListener() {
        // Opt-in placement: an agent with the cap at 0 never hosts a relayed generation session, so it adds no generation load to CI/exam builds. Do not even subscribe.
        if (maxConcurrentSessions <= 0) {
            log.info("Interactive sandbox relay hosting disabled on build agent '{}' (max-concurrent-generation-sessions=0); it will not host generation sessions.",
                    buildAgentShortName);
            return;
        }
        this.sessionPermits = new Semaphore(maxConcurrentSessions);
        this.workerExecutor = Executors.newFixedThreadPool(maxConcurrentSessions + 1, namedDaemonThreadFactory());
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
     * Records a correlation id as handled, returning {@code true} only on its first appearance.
     *
     * @param correlationId the correlation id of the request being handled
     * @return {@code true} if this is the first delivery for the id (caller should proceed), {@code false} if it was already handled (caller should drop it)
     */
    private boolean markHandled(String correlationId) {
        synchronized (handledCorrelationIds) {
            if (!handledCorrelationIds.add(correlationId)) {
                return false;
            }
            Iterator<String> iterator = handledCorrelationIds.iterator();
            while (handledCorrelationIds.size() > MAX_REMEMBERED_CORRELATION_IDS && iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
            return true;
        }
    }

    private SandboxOpResponse handleCreate(SandboxOpRequest request) {
        // Drain guard: a paused agent (manual drain or auto-paused after failures) must not take on a new long-lived session, mirroring how pause stops new CI build jobs.
        // In-flight
        // sessions keep running (EXEC/COPY/DESTROY stay ungated) so an active generation can finish and tear down cleanly.
        if (sharedQueueProcessingService.isPaused()) {
            return SandboxOpResponse.failure(request.correlationId(), "Build agent '" + buildAgentShortName + "' is paused and is not accepting new interactive sandbox sessions.");
        }
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
            return SandboxOpResponse.created(request.correlationId(), containerId);
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
        return SandboxOpResponse.exec(request.correlationId(), request.sessionId(), result);
    }

    private SandboxOpResponse handleCopyIn(SandboxOpRequest request) {
        // A COPY_IN request always carries a non-null (bounded) payload from the client's readBounded; a null here would surface as a caught NPE -> failure response.
        try (InputStream tar = new ByteArrayInputStream(request.payload())) {
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
            return SandboxOpResponse.copiedOut(request.correlationId(), request.sessionId(), payload);
        }
        catch (IOException e) {
            return SandboxOpResponse.failure(request.correlationId(), "Failed to buffer copy-out archive: " + e.getMessage());
        }
    }

    /**
     * Re-serializes the entries of a {@link TarArchiveInputStream} into a fresh tar byte array for transport. The local {@code copyOut} hands back a decoding
     * {@link TarArchiveInputStream} rather than the raw Docker bytes, so the relay rebuilds an equivalent archive the core-node client can re-wrap and read as in the co-located
     * case. Fails closed if the repacked archive exceeds {@link RemoteInteractiveSandboxClient#MAX_PAYLOAD_BYTES}.
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
