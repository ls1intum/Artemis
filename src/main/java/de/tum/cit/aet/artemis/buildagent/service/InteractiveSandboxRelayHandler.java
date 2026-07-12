package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.github.dockerjava.api.exception.DockerException;

import de.tum.cit.aet.artemis.buildagent.dto.GenerationSandboxSessionDTO;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResult;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxOpRequest;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxOpResponse;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionContext;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
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
 * {@code max-generation-sandbox-slots}
 * defaults to {@code 0} (the agent hosts nothing and does not even subscribe), a paused agent refuses new sandboxes, and a per-agent semaphore caps reserved slots — so generation
 * never silently competes with CI or exam builds on an agent an operator did not deliberately opt in. The permit is released on {@code DESTROY}.
 *
 * @see RemoteInteractiveSandboxClient the core-node client whose requests this handler serves
 * @see InteractiveSandboxService the local implementation that actually performs each operation
 */
@Lazy(false)
@Component
@Profile(PROFILE_BUILDAGENT)
public class InteractiveSandboxRelayHandler {

    private static final Logger log = LoggerFactory.getLogger(InteractiveSandboxRelayHandler.class);

    /**
     * Stable fragment embedded in the CREATE failure message when this agent declines because it is out of generation sandbox slots. The core client matches on it to fail a create
     * over to
     * another candidate agent rather than surface the refusal, so keep it in sync between the message here and {@link RemoteInteractiveSandboxClient}.
     */
    static final String CAPACITY_REFUSAL_MARKER = "is at its generation sandbox slot capacity";

    /**
     * Stable fragment embedded in the CREATE failure message when this agent declines because it is paused/draining. The core client matches on it to fail a create over to another
     * candidate agent, so keep it in sync between the message here and {@link RemoteInteractiveSandboxClient}.
     */
    static final String DRAINING_REFUSAL_MARKER = "is paused and is not accepting new generation sandboxes";

    /**
     * Stable fragment embedded in the CREATE failure message when this agent's create failed for a transient, agent-local reason (Docker daemon overload, an image-pull network
     * blip,
     * this agent's Docker momentarily unavailable) rather than a deterministic one (bad image reference, malformed spec). The core client matches on it to fail such a create over
     * to
     * another candidate agent — exactly like a capacity/draining decline — instead of surfacing it as fatal, so keep it in sync between the message here and
     * {@link RemoteInteractiveSandboxClient}. Deterministic failures stay untagged so they surface fast rather than storming every candidate with a retry that fails identically.
     */
    static final String RETRYABLE_REFUSAL_MARKER = "encountered a transient error creating a generation sandbox";

    private final ApplicationContext applicationContext;

    private final DistributedDataAccessService distributedDataAccessService;

    /** Consulted so a paused/draining agent sheds generation load too: pausing a build agent stops it accepting new sandboxes, not just new CI build jobs. */
    private final SharedQueueProcessingService sharedQueueProcessingService;

    /** Triggered on sandbox create/destroy so the broadcast agent info reflects the slot count promptly, not only on the next CI-driven refresh. */
    private final BuildAgentInformationService buildAgentInformationService;

    @Value("${artemis.continuous-integration.build-agent.short-name}")
    private String buildAgentShortName;

    /**
     * Maximum number of generation sandbox slots this agent will reserve, on top of its CI build jobs. A sandbox is a long-lived, CI-sized container, so hosting one adds real
     * CPU/memory/Docker load that the build-job scheduler does not account for. It therefore defaults to {@code 0}: an agent hosts generation only when an operator explicitly opts
     * it in, so enabling Hyperion never silently adds load to exam-critical build agents. Set a positive value on spare agents; {@code 0} means this agent never hosts a relayed
     * sandbox (the relay listener is not even registered).
     */
    @Value("${artemis.continuous-integration.build-agent.max-generation-sandbox-slots:0}")
    private int maxGenerationSandboxSlots;

    /**
     * Caps reserved generation sandbox slots: acquired on CREATE, released on DESTROY. If a CREATE succeeds but its response is lost in transit, the core client never learns the
     * container id and can never issue DESTROY; the {@link InteractiveSandboxReaperService} reaps the orphaned container and calls back into {@link #releaseIfOwned(String)} to
     * reclaim the held permit, so repeated orphaning cannot slowly starve the agent of slot capacity between restarts.
     */
    private Semaphore sandboxSlotPermits;

    /** Bound on {@link #handledCorrelationIds}: far more than any realistic in-flight + recently-completed redelivery window, yet bounded on a long-lived agent. */
    private static final int MAX_REMEMBERED_CORRELATION_IDS = 10_000;

    /**
     * Bounded FIFO set of handled correlation ids for at-most-once handling; single-use ids (the client mints a fresh UUID per call and never retries one) make oldest-entry
     * eviction safe. Guarded by its own monitor.
     */
    private final LinkedHashSet<String> handledCorrelationIds = new LinkedHashSet<>();

    /** Container ids this agent owns mapped to the number of generation sandbox slots DESTROY must release exactly once. */
    private final Map<String, Integer> ownedSandboxSlotPermits = new ConcurrentHashMap<>();

    private final Map<String, ActiveSession> activeSessions = new ConcurrentHashMap<>();

    /** Verification sandbox container ids mapped to the authoring sandbox whose reserved slot they temporarily consume. */
    private final Map<String, String> verificationSandboxOwners = new ConcurrentHashMap<>();

    private DistributedTopic<SandboxOpRequest> requestsTopic;

    private DistributedTopic<SandboxOpResponse> responsesTopic;

    private UUID requestListenerId;

    private ExecutorService workerExecutor;

    public InteractiveSandboxRelayHandler(ApplicationContext applicationContext, DistributedDataAccessService distributedDataAccessService,
            SharedQueueProcessingService sharedQueueProcessingService, BuildAgentInformationService buildAgentInformationService) {
        this.applicationContext = applicationContext;
        this.distributedDataAccessService = distributedDataAccessService;
        this.sharedQueueProcessingService = sharedQueueProcessingService;
        this.buildAgentInformationService = buildAgentInformationService;
    }

    /**
     * Subscribes to the request topic and starts the worker pool. The listener thread only filters and hands off; all Docker work happens on the worker pool.
     */
    @PostConstruct
    public void registerRequestListener() {
        // Opt-in placement: an agent with the cap at 0 never hosts a relayed Hyperion sandbox, so it adds no generation load to CI/exam builds. Do not even subscribe.
        if (maxGenerationSandboxSlots <= 0) {
            log.info("Interactive sandbox relay hosting disabled on build agent '{}' (max-generation-sandbox-slots=0); it will not host Hyperion sandboxes.", buildAgentShortName);
            return;
        }
        this.sandboxSlotPermits = new Semaphore(maxGenerationSandboxSlots);
        this.workerExecutor = Executors.newFixedThreadPool(maxGenerationSandboxSlots + 1, namedDaemonThreadFactory());
        try {
            int removedPreviousSessions = interactiveSandboxService().removeSessionsFromPreviousProcess();
            if (removedPreviousSessions > 0) {
                log.info("Removed {} leftover interactive sandbox session(s) before advertising generation capacity on build agent '{}'.", removedPreviousSessions,
                        buildAgentShortName);
            }
        }
        catch (RuntimeException e) {
            workerExecutor.shutdownNow();
            workerExecutor = null;
            sandboxSlotPermits.acquireUninterruptibly(maxGenerationSandboxSlots);
            buildAgentInformationService.updateGenerationSandboxSlotState(maxGenerationSandboxSlots, maxGenerationSandboxSlots);
            buildAgentInformationService.refreshLocalBuildAgentInformationPreservingFailures(sharedQueueProcessingService.isPaused());
            log.error("Interactive sandbox relay hosting stays disabled on build agent '{}' because previous sessions could not be reconciled.", buildAgentShortName, e);
            return;
        }
        // Publish the cap (0 active) so admins see "0 / N" on an opted-in-but-idle agent, distinct from "0 / 0" on an agent that never hosts.
        buildAgentInformationService.updateGenerationSandboxSlotState(0, maxGenerationSandboxSlots);
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
        log.info("InteractiveSandboxRelayHandler initialized for build agent '{}' (max generation sandbox slots: {})", buildAgentShortName, maxGenerationSandboxSlots);
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
                case LIST -> handleList(request);
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
        if (request.sessionSpec() == null || request.sessionSpec().context() == null) {
            return SandboxOpResponse.failure(request.correlationId(), "Generation sandbox CREATE requires an observability context.");
        }
        // Drain guard: a paused agent (manual drain or auto-paused after failures) must not take on a new long-lived session, mirroring how pause stops new CI build jobs.
        // In-flight
        // sessions keep running (EXEC/COPY/DESTROY stay ungated) so an active generation can finish and tear down cleanly.
        if (sharedQueueProcessingService.isPaused()) {
            return SandboxOpResponse.failure(request.correlationId(), "Build agent '" + buildAgentShortName + "' " + DRAINING_REFUSAL_MARKER + ".");
        }
        // Capacity guard: refuse rather than silently starve CI when this agent is already at its sandbox-slot cap.
        int permitsToAcquire = request.createPermits();
        if (permitsToAcquire < 0) {
            return SandboxOpResponse.failure(request.correlationId(), "CREATE requested a negative generation sandbox slot count.");
        }
        if (!sandboxSlotPermits.tryAcquire(permitsToAcquire)) {
            return SandboxOpResponse.failure(request.correlationId(),
                    "Build agent '" + buildAgentShortName + "' " + CAPACITY_REFUSAL_MARKER + " (" + maxGenerationSandboxSlots + ").");
        }
        boolean created = false;
        try {
            String containerId;
            if (permitsToAcquire == 0) {
                synchronized (ownedSandboxSlotPermits) {
                    if (!hasReservedVerificationSlot(request.sessionId())) {
                        return SandboxOpResponse.failure(request.correlationId(),
                                "Verification sandbox CREATE must reference an owned authoring sandbox with a reserved verification slot.");
                    }
                    containerId = interactiveSandboxService().createSession(request.sessionSpec());
                    ownedSandboxSlotPermits.computeIfPresent(request.sessionId(), (ignored, permits) -> permits - 1);
                    ownedSandboxSlotPermits.put(containerId, 1);
                    verificationSandboxOwners.put(containerId, request.sessionId());
                    activeSessions.computeIfPresent(request.sessionId(), (ignored, session) -> session.withReservedSlots(1));
                    registerSession(containerId, request.sessionSpec().context(), GenerationSandboxSessionDTO.Role.VERIFICATION, 1);
                }
            }
            else {
                containerId = interactiveSandboxService().createSession(request.sessionSpec());
                synchronized (ownedSandboxSlotPermits) {
                    ownedSandboxSlotPermits.put(containerId, permitsToAcquire);
                    registerSession(containerId, request.sessionSpec().context(), GenerationSandboxSessionDTO.Role.AUTHORING, permitsToAcquire);
                }
            }
            created = true;
            publishSessionState();
            return SandboxOpResponse.created(request.correlationId(), containerId);
        }
        catch (RuntimeException e) {
            // Classify the failure so the core client can decide whether to fail over. A transient, agent-local Docker error (daemon overload, image-pull network blip, Docker down
            // on this agent) may well succeed on another healthy agent, so tag it with RETRYABLE_REFUSAL_MARKER to fail it over like a capacity decline. A deterministic error (bad
            // image reference, malformed spec) recurs identically on every agent, so leave it untagged and let the client surface it fast rather than storm every candidate.
            if (isTransientDockerFailure(e)) {
                return SandboxOpResponse.failure(request.correlationId(), "Build agent '" + buildAgentShortName + "' " + RETRYABLE_REFUSAL_MARKER + ": " + e.getMessage());
            }
            return SandboxOpResponse.failure(request.correlationId(), e.getMessage());
        }
        finally {
            // Release the permit if the container never came up, so a failed create does not leak capacity.
            if (!created) {
                sandboxSlotPermits.release(permitsToAcquire);
            }
        }
    }

    private boolean hasReservedVerificationSlot(String authoringSessionId) {
        if (authoringSessionId == null || authoringSessionId.isBlank()) {
            return false;
        }
        Integer reservedPermits = ownedSandboxSlotPermits.get(authoringSessionId);
        return reservedPermits != null && reservedPermits > 1;
    }

    /**
     * Whether a CREATE failure is transient/agent-local (worth failing over to another agent) rather than deterministic (recurs identically on every agent). A Docker 4xx anywhere
     * in
     * the cause chain — a missing image (404), a malformed spec (400), an auth failure (401/403) — is deterministic; anything else (a 5xx daemon error, a connection failure, or
     * this
     * agent's Docker being unavailable) is agent-local and may succeed elsewhere, so the caller tags it retryable.
     *
     * @param failure the exception thrown by the local create
     * @return {@code true} if the failure should be tagged retryable so the core client fails it over; {@code false} if it is deterministic and should surface fast
     */
    private static boolean isTransientDockerFailure(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof DockerException dockerException && dockerException.getHttpStatus() >= 400 && dockerException.getHttpStatus() < 500) {
                return false;
            }
        }
        return true;
    }

    private SandboxOpResponse handleExec(SandboxOpRequest request) {
        requireOwnedSession(request.sessionId());
        SandboxExecResult result = interactiveSandboxService().exec(request.sessionId(), Duration.ofSeconds(request.timeoutSeconds()), request.command());
        if (result.timedOut()) {
            releaseOwnedPermits(request.sessionId());
        }
        return SandboxOpResponse.exec(request.correlationId(), request.sessionId(), result);
    }

    private SandboxOpResponse handleCopyIn(SandboxOpRequest request) {
        requireOwnedSession(request.sessionId());
        // The tar payload rides the keyed staging map, not the broadcast request itself, so only this (target) agent transfers the bytes. This worker is the sole reader and
        // removes
        // the entry on consumption; the client re-removes it defensively if we never got here.
        byte[] payload = distributedDataAccessService.getHyperionSandboxPayloads().remove(request.correlationId());
        if (payload == null) {
            return SandboxOpResponse.failure(request.correlationId(),
                    "Copy-in payload for correlation id " + request.correlationId() + " was not staged (already consumed or evicted).");
        }
        try (InputStream tar = new ByteArrayInputStream(payload)) {
            interactiveSandboxService().copyIn(request.sessionId(), request.workspacePath(), tar);
        }
        catch (IOException e) {
            return SandboxOpResponse.failure(request.correlationId(), "Failed to read copy-in payload: " + e.getMessage());
        }
        return SandboxOpResponse.ok(request.correlationId(), request.sessionId());
    }

    private SandboxOpResponse handleCopyOut(SandboxOpRequest request) {
        requireOwnedSession(request.sessionId());
        try (TarArchiveInputStream tar = interactiveSandboxService().copyOut(request.sessionId(), request.workspacePath())) {
            byte[] payload = repackTar(tar);
            // Stage the repacked archive in the keyed map rather than on the response topic, so only the originating core node fetches the bytes instead of every response
            // subscriber
            // deserializing them on its event thread. The client reads and removes the entry.
            distributedDataAccessService.getHyperionSandboxPayloads().put(request.correlationId(), payload);
            return SandboxOpResponse.copiedOut(request.correlationId(), request.sessionId());
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
        try (BoundedByteArrayOutputStream out = new BoundedByteArrayOutputStream(RemoteInteractiveSandboxClient.MAX_PAYLOAD_BYTES);
                TarArchiveOutputStream tar = new TarArchiveOutputStream(out)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            long total = 0;
            byte[] buffer = new byte[8192];
            TarArchiveEntry entry;
            while ((entry = source.getNextEntry()) != null) {
                if (entry.isSymbolicLink() || entry.isLink()) {
                    throw new IOException("Interactive sandbox copy-out archive contains a linked entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    TarArchiveEntry directory = new TarArchiveEntry(entry.getName().endsWith("/") ? entry.getName() : entry.getName() + "/");
                    directory.setMode(entry.getMode());
                    tar.putArchiveEntry(directory);
                    tar.closeArchiveEntry();
                    continue;
                }
                if (!entry.isFile()) {
                    throw new IOException("Interactive sandbox copy-out archive contains an unsupported non-regular entry: " + entry.getName());
                }
                long declaredSize = entry.getSize();
                if (declaredSize < 0 || declaredSize > RemoteInteractiveSandboxClient.MAX_PAYLOAD_BYTES
                        || total > RemoteInteractiveSandboxClient.MAX_PAYLOAD_BYTES - declaredSize) {
                    throw new IOException("Interactive sandbox copy-out archive exceeds the " + RemoteInteractiveSandboxClient.MAX_PAYLOAD_BYTES + " byte relay limit.");
                }
                TarArchiveEntry copy = new TarArchiveEntry(entry.getName());
                copy.setMode(entry.getMode());
                copy.setSize(declaredSize);
                tar.putArchiveEntry(copy);
                int read;
                while ((read = source.read(buffer)) != -1) {
                    total += read;
                    if (total > RemoteInteractiveSandboxClient.MAX_PAYLOAD_BYTES) {
                        throw new IOException("Interactive sandbox copy-out archive exceeds the " + RemoteInteractiveSandboxClient.MAX_PAYLOAD_BYTES + " byte relay limit.");
                    }
                    tar.write(buffer, 0, read);
                }
                tar.closeArchiveEntry();
            }
            tar.finish();
            return out.toByteArray();
        }
        catch (PayloadLimitExceededException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    private static final class BoundedByteArrayOutputStream extends ByteArrayOutputStream {

        private final int maxBytes;

        private BoundedByteArrayOutputStream(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        @Override
        public synchronized void write(byte[] bytes, int offset, int length) {
            checkLimit(length);
            super.write(bytes, offset, length);
        }

        @Override
        public synchronized void write(int byteValue) {
            checkLimit(1);
            super.write(byteValue);
        }

        private void checkLimit(int bytesToAdd) {
            if (count > maxBytes - bytesToAdd) {
                throw new PayloadLimitExceededException("Interactive sandbox copy-out archive exceeds the " + maxBytes + " byte relay limit.");
            }
        }
    }

    private static final class PayloadLimitExceededException extends RuntimeException {

        private PayloadLimitExceededException(String message) {
            super(message);
        }
    }

    private SandboxOpResponse handleDestroy(SandboxOpRequest request) {
        if (!ownsSession(request.sessionId())) {
            return SandboxOpResponse.ok(request.correlationId(), request.sessionId());
        }
        interactiveSandboxService().destroySession(request.sessionId());
        releaseOwnedPermits(request.sessionId());
        return SandboxOpResponse.ok(request.correlationId(), request.sessionId());
    }

    private SandboxOpResponse handleList(SandboxOpRequest request) {
        List<GenerationSandboxSessionDTO> sessions;
        synchronized (ownedSandboxSlotPermits) {
            sessions = activeSessions.entrySet().stream().map(entry -> entry.getValue().toDto(entry.getKey(), interactiveSandboxService().lastActivity(entry.getKey()))).toList();
        }
        return SandboxOpResponse.sessions(request.correlationId(), sessions);
    }

    private void registerSession(String containerId, SandboxSessionContext context, GenerationSandboxSessionDTO.Role role, int reservedSlots) {
        if (context != null) {
            activeSessions.put(containerId, new ActiveSession(context, role, Instant.now(), reservedSlots));
        }
    }

    private void requireOwnedSession(String sessionId) {
        if (!ownsSession(sessionId)) {
            throw new LocalCIException("Interactive sandbox session " + sessionId + " is not owned by this relay");
        }
    }

    private boolean ownsSession(String sessionId) {
        synchronized (ownedSandboxSlotPermits) {
            return ownedSandboxSlotPermits.containsKey(sessionId);
        }
    }

    /**
     * Reconciles the session permit for a container the {@link InteractiveSandboxReaperService} force-removed out from under this handler. An orphaned container — a CREATE whose
     * response was lost in transit (so the core client never learned the id and can never DESTROY it), a create that succeeded after the client already failed over, or a lost
     * DESTROY — otherwise keeps its permit held until this agent restarts, so repeated orphaning would monotonically deplete {@link #sandboxSlotPermits} until the agent refuses
     * all
     * new sandboxes. This releases the sandbox's recorded permits if and only if this handler currently owns it, gated on the {@link #ownedSandboxSlotPermits} removal — identical
     * semantics to the DESTROY release path, so a foreign or already-reclaimed container reconciles nothing and repeated calls never over-release.
     *
     * @param containerId the container id of the reaped session (as this agent understands it)
     */
    void releaseIfOwned(String containerId) {
        // Release the generation sandbox slots exactly once per owned sandbox, gated by the ownedSandboxSlotPermits removal, exactly as handleDestroy does.
        releaseOwnedPermits(containerId);
    }

    private void releaseOwnedPermits(String containerId) {
        Integer permits;
        boolean restoredToAuthoringReservation = false;
        synchronized (ownedSandboxSlotPermits) {
            permits = ownedSandboxSlotPermits.remove(containerId);
            activeSessions.remove(containerId);
            String authoringSessionId = verificationSandboxOwners.remove(containerId);
            if (permits != null && authoringSessionId != null && ownedSandboxSlotPermits.containsKey(authoringSessionId)) {
                ownedSandboxSlotPermits.computeIfPresent(authoringSessionId, (ignored, authoringPermits) -> authoringPermits + permits);
                activeSessions.computeIfPresent(authoringSessionId, (ignored, session) -> session.withReservedSlots(session.reservedSlots() + permits));
                restoredToAuthoringReservation = true;
            }
        }
        if (permits != null) {
            if (!restoredToAuthoringReservation) {
                sandboxSlotPermits.release(permits);
            }
            publishSessionState();
        }
    }

    /**
     * Publishes the current slot load to the seam bean and refreshes the broadcast agent info so admins see the change on the build-agent page promptly. Refreshes without
     * touching the consecutive-failure bookkeeping: a sandbox create/destroy is unrelated to build-job outcomes, so it must not reset the displayed failure count (which
     * {@link BuildAgentInformationService#updateLocalBuildAgentInformation(boolean)} would).
     */
    private void publishSessionState() {
        int usedPermits = ownedSandboxSlotPermits.values().stream().mapToInt(Integer::intValue).sum();
        buildAgentInformationService.updateGenerationSandboxSlotState(usedPermits, maxGenerationSandboxSlots);
        buildAgentInformationService.refreshLocalBuildAgentInformationPreservingFailures(sharedQueueProcessingService.isPaused());
    }

    private InteractiveSandboxService interactiveSandboxService() {
        return applicationContext.getBean(InteractiveSandboxService.class);
    }

    private static ThreadFactory namedDaemonThreadFactory() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "hyperion-sandbox-relay-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private record ActiveSession(SandboxSessionContext context, GenerationSandboxSessionDTO.Role role, Instant startedAt, int reservedSlots) {

        ActiveSession withReservedSlots(int newReservedSlots) {
            return new ActiveSession(context, role, startedAt, newReservedSlots);
        }

        GenerationSandboxSessionDTO toDto(String containerId, java.util.Optional<Instant> lastActivity) {
            return new GenerationSandboxSessionDTO(containerId, role, context.jobId(), context.exerciseId(), context.exerciseTitle(), context.courseId(), context.userLogin(),
                    context.mode(), startedAt, lastActivity.orElse(startedAt), reservedSlots);
        }
    }
}
