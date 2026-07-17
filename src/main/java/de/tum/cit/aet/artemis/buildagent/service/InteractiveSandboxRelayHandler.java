package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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

import de.tum.cit.aet.artemis.buildagent.dto.GenerationSandboxSessionDTO;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResult;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxOpRequest;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxOpResponse;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionContext;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionSpec;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;
import de.tum.cit.aet.artemis.localci.service.distributed.api.topic.DistributedTopic;

/** Relays distributed interactive-sandbox operations to this build agent. */
@Lazy(false)
@Component
@Profile(PROFILE_BUILDAGENT)
public class InteractiveSandboxRelayHandler {

    private static final Logger log = LoggerFactory.getLogger(InteractiveSandboxRelayHandler.class);

    static final String CAPACITY_REFUSAL_MARKER = "is at its generation sandbox slot capacity";

    static final String DRAINING_REFUSAL_MARKER = "is paused and is not accepting new generation sandboxes";

    static final String OVERLOAD_REFUSAL_MARKER = "is overloaded and cannot accept another sandbox request";

    private final ApplicationContext applicationContext;

    private final DistributedDataAccessService distributedDataAccessService;

    private final SharedQueueProcessingService sharedQueueProcessingService;

    private final BuildAgentInformationService buildAgentInformationService;

    @Value("${artemis.continuous-integration.build-agent.short-name}")
    private String buildAgentShortName;

    /** Generation hosting is opt-in; zero disables the relay listener. */
    @Value("${artemis.continuous-integration.build-agent.max-generation-sandbox-slots:0}")
    private int maxGenerationSandboxSlots;

    /** Permits are reclaimed on destroy or by orphan reaping. */
    private Semaphore sandboxSlotPermits;

    static final int MAX_REMEMBERED_CORRELATION_IDS = 10_000;

    private final Object requestDeduplicationLock = new Object();

    /** In-flight ids are never evicted; doing so could repeat a side effect when a retry arrives. */
    private final Map<String, Long> inFlightCorrelationIds = new HashMap<>();

    /** Terminal responses retained with their correlation ids so a retried request can recover a lost response without repeating the side effect. */
    private final Map<String, CachedResponse> completedResponses = new LinkedHashMap<>();

    private static final long REQUEST_DEADLINE_GRACE_MILLIS = Duration.ofMinutes(1).toMillis();

    private static final long UNDATED_REQUEST_RETENTION_MILLIS = Duration.ofMinutes(5).toMillis();

    private final Set<String> ownedSessionIds = ConcurrentHashMap.newKeySet();

    private final Map<String, ActiveSession> activeSessions = new ConcurrentHashMap<>();

    private final Map<String, JobCoordination> jobCoordinations = new ConcurrentHashMap<>();

    private final Map<String, String> sessionIdsByJobId = new ConcurrentHashMap<>();

    private final Map<String, String> jobIdsBySessionId = new ConcurrentHashMap<>();

    private DistributedTopic<SandboxOpRequest> requestsTopic;

    private DistributedTopic<SandboxOpResponse> responsesTopic;

    private UUID requestListenerId;

    private ExecutorService workerExecutor;

    private final AtomicBoolean shuttingDown = new AtomicBoolean();

    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock(true);

    public InteractiveSandboxRelayHandler(ApplicationContext applicationContext, DistributedDataAccessService distributedDataAccessService,
            SharedQueueProcessingService sharedQueueProcessingService, BuildAgentInformationService buildAgentInformationService) {
        this.applicationContext = applicationContext;
        this.distributedDataAccessService = distributedDataAccessService;
        this.sharedQueueProcessingService = sharedQueueProcessingService;
        this.buildAgentInformationService = buildAgentInformationService;
    }

    /**
     * Starts relay hosting when this agent has configured capacity.
     */
    @PostConstruct
    public void registerRequestListener() {
        // Opt-in placement: an agent with the cap at 0 never hosts a relayed Hyperion sandbox, so it adds no generation load to CI/exam builds. Do not even subscribe.
        if (maxGenerationSandboxSlots <= 0) {
            log.info("Interactive sandbox relay hosting disabled on build agent '{}' (max-generation-sandbox-slots=0); it will not host Hyperion sandboxes.", buildAgentShortName);
            return;
        }
        this.sandboxSlotPermits = new Semaphore(maxGenerationSandboxSlots);
        int workerCount = maxGenerationSandboxSlots + 1;
        ExecutorService executor = new ThreadPoolExecutor(workerCount, workerCount, 0L, TimeUnit.MILLISECONDS, new SynchronousQueue<>(), namedDaemonThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
        this.workerExecutor = executor;
        try {
            int removedPreviousSessions = interactiveSandboxService().removeSessionsForCurrentAgent();
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
            if (!buildAgentShortName.equals(request.targetAgentShortName())) {
                return;
            }
            if (shuttingDown.get()) {
                publishResponse(SandboxOpResponse.failure(request.correlationId(), "Build agent '" + buildAgentShortName + "' " + DRAINING_REFUSAL_MARKER + "."));
                return;
            }
            RequestClaim claim = claimRequest(request);
            if (claim.completedResponse() != null) {
                publishResponse(claim.completedResponse());
                return;
            }
            if (!claim.accepted()) {
                log.debug("Ignoring retried sandbox request {} ({}) while the original operation is still running", request.correlationId(), request.op());
                return;
            }
            try {
                executor.submit(() -> handle(request));
            }
            catch (RejectedExecutionException ignored) {
                boolean draining = shuttingDown.get();
                if (!draining) {
                    log.warn("Interactive sandbox relay rejected request {} because its bounded worker queue is full", request.correlationId());
                }
                String refusal = draining ? DRAINING_REFUSAL_MARKER : OVERLOAD_REFUSAL_MARKER;
                SandboxOpResponse response = SandboxOpResponse.failure(request.correlationId(), "Build agent '" + buildAgentShortName + "' " + refusal + ".");
                rememberCompletedResponse(response);
                publishResponse(response);
            }
        });
        log.info("InteractiveSandboxRelayHandler initialized for build agent '{}' (max generation sandbox slots: {})", buildAgentShortName, maxGenerationSandboxSlots);
    }

    /**
     * Stops the relay listener and workers.
     */
    @PreDestroy
    public void shutdown() {
        if (!shuttingDown.compareAndSet(false, true)) {
            return;
        }
        if (requestListenerId != null && requestsTopic != null) {
            requestsTopic.removeMessageListener(requestListenerId);
            requestListenerId = null;
        }
        ExecutorService executor = workerExecutor;
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("Interactive sandbox relay workers did not stop on build agent '{}'; leaving session cleanup to startup reconciliation.", buildAgentShortName);
                    return;
                }
            }
            catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
                log.warn("Interrupted while stopping interactive sandbox relay workers on build agent '{}'; leaving session cleanup to startup reconciliation.",
                        buildAgentShortName);
                return;
            }
            workerExecutor = null;
        }
        Lock cleanupLock = lifecycleLock.writeLock();
        cleanupLock.lock();
        try {
            try {
                interactiveSandboxService().removeSessionsForCurrentAgent();
            }
            catch (RuntimeException e) {
                log.warn("Could not remove all sandbox sessions while shutting down build agent '{}': {}", buildAgentShortName, e.getMessage());
                return;
            }
            ownedSessionIds.clear();
            activeSessions.clear();
            sessionIdsByJobId.clear();
            jobIdsBySessionId.clear();
        }
        finally {
            cleanupLock.unlock();
        }
    }

    private void handle(SandboxOpRequest request) {
        Lock operationLock = lifecycleLock.readLock();
        operationLock.lock();
        SandboxOpResponse response;
        try {
            if (shuttingDown.get()) {
                response = SandboxOpResponse.failure(request.correlationId(), "Build agent '" + buildAgentShortName + "' " + DRAINING_REFUSAL_MARKER + ".");
            }
            else if (isDeadlineExpired(request)) {
                response = deadlineExpiredResponse(request);
            }
            else {
                response = switch (request.op()) {
                    case CREATE -> handleCreate(request);
                    case EXEC -> handleExec(request);
                    case COPY_IN -> handleCopyIn(request);
                    case COPY_OUT -> handleCopyOut(request);
                    case LIST -> handleList(request);
                    case DESTROY -> handleDestroy(request);
                };
            }
        }
        catch (Exception e) {
            log.warn("Interactive sandbox relay operation {} ({}) failed on agent '{}': {}", request.op(), request.correlationId(), buildAgentShortName, e.getMessage());
            response = SandboxOpResponse.failure(request.correlationId(), e.getMessage());
        }
        finally {
            operationLock.unlock();
        }
        rememberCompletedResponse(response);
        publishResponse(response);
    }

    RequestClaim claimRequest(SandboxOpRequest request) {
        synchronized (requestDeduplicationLock) {
            long now = System.currentTimeMillis();
            completedResponses.values().removeIf(cached -> cached.expiresAtEpochMillis() < now);
            CachedResponse completedResponse = completedResponses.get(request.correlationId());
            if (completedResponse != null) {
                return new RequestClaim(false, completedResponse.response());
            }
            long requestDeadline = request.deadlineEpochMillis() > 0 ? request.deadlineEpochMillis() : now + UNDATED_REQUEST_RETENTION_MILLIS;
            if (requestDeadline <= now) {
                return new RequestClaim(false, SandboxOpResponse.failure(request.correlationId(), "Interactive sandbox request deadline expired before execution."));
            }
            if (inFlightCorrelationIds.containsKey(request.correlationId())) {
                return new RequestClaim(false, null);
            }
            if (completedResponses.size() >= MAX_REMEMBERED_CORRELATION_IDS) {
                return new RequestClaim(false, SandboxOpResponse.failure(request.correlationId(), "Build agent '" + buildAgentShortName + "' " + OVERLOAD_REFUSAL_MARKER + "."));
            }
            inFlightCorrelationIds.put(request.correlationId(), requestDeadline + REQUEST_DEADLINE_GRACE_MILLIS);
            return new RequestClaim(true, null);
        }
    }

    void rememberCompletedResponse(SandboxOpResponse response) {
        synchronized (requestDeduplicationLock) {
            Long expiresAt = inFlightCorrelationIds.remove(response.correlationId());
            if (expiresAt == null) {
                return;
            }
            completedResponses.put(response.correlationId(), new CachedResponse(response, expiresAt));
        }
    }

    private void publishResponse(SandboxOpResponse response) {
        try {
            responsesTopic.publish(response);
        }
        catch (RuntimeException e) {
            log.warn("Could not publish terminal response for interactive sandbox request {}; a retry can recover the cached result", response.correlationId(), e);
        }
    }

    record RequestClaim(boolean accepted, SandboxOpResponse completedResponse) {
    }

    private record CachedResponse(SandboxOpResponse response, long expiresAtEpochMillis) {
    }

    private SandboxOpResponse handleCreate(SandboxOpRequest request) {
        if (request.sessionSpec() == null || request.sessionSpec().context() == null) {
            return SandboxOpResponse.failure(request.correlationId(), "Generation sandbox CREATE requires an observability context.");
        }
        SandboxSessionContext context = request.sessionSpec().context();
        if (context.jobId() == null || context.jobId().isBlank()) {
            return SandboxOpResponse.failure(request.correlationId(), "Generation sandbox CREATE requires a job id.");
        }
        JobCoordination jobCoordination = acquireJobCoordination(context.jobId());
        try {
            if (isDeadlineExpired(request)) {
                return deadlineExpiredResponse(request);
            }
            String existingSessionId = sessionIdsByJobId.get(context.jobId());
            if (existingSessionId != null && ownsSession(existingSessionId)) {
                ActiveSession existingSession = activeSessions.get(existingSessionId);
                if (existingSession == null || !existingSession.sessionSpec().equals(request.sessionSpec())) {
                    return SandboxOpResponse.failure(request.correlationId(), "Generation job " + context.jobId() + " is already bound to a different sandbox specification.");
                }
                return SandboxOpResponse.created(request.correlationId(), existingSessionId);
            }

            if (!sharedQueueProcessingService.tryAcquireGenerationAdmission()) {
                return SandboxOpResponse.failure(request.correlationId(), "Build agent '" + buildAgentShortName + "' " + DRAINING_REFUSAL_MARKER + ".");
            }
            try {
                if (!sandboxSlotPermits.tryAcquire()) {
                    return SandboxOpResponse.failure(request.correlationId(),
                            "Build agent '" + buildAgentShortName + "' " + CAPACITY_REFUSAL_MARKER + " (" + maxGenerationSandboxSlots + ").");
                }
                boolean created = false;
                try {
                    if (isDeadlineExpired(request)) {
                        return deadlineExpiredResponse(request);
                    }
                    String containerId = interactiveSandboxService().createSession(request.sessionSpec());
                    if (isDeadlineExpired(request)) {
                        created = retainExpiredContainerWhenCleanupFails(containerId, request.sessionSpec());
                        if (created) {
                            // The originating core may already have timed out. Cache CREATED so a retry on this handler recovers the retained container instead of replaying a
                            // false failure that could trigger duplicate placement.
                            return SandboxOpResponse.created(request.correlationId(), containerId);
                        }
                        return deadlineExpiredResponse(request);
                    }
                    registerSession(containerId, request.sessionSpec());
                    ownedSessionIds.add(containerId);
                    created = true;
                    try {
                        publishSessionState();
                    }
                    catch (RuntimeException e) {
                        // The container already exists. Returning a retryable CREATE failure could place the same job on another agent, so observability refresh is best-effort
                        // here.
                        log.warn("Could not publish generation sandbox slot state after creating session {}: {}", containerId, e.getMessage());
                    }
                    return SandboxOpResponse.created(request.correlationId(), containerId);
                }
                catch (RuntimeException e) {
                    return SandboxOpResponse.failure(request.correlationId(), e.getMessage());
                }
                finally {
                    if (!created) {
                        sandboxSlotPermits.release();
                    }
                }
            }
            finally {
                sharedQueueProcessingService.releaseGenerationAdmission();
            }
        }
        finally {
            releaseJobCoordination(context.jobId(), jobCoordination);
        }
    }

    private SandboxOpResponse handleExec(SandboxOpRequest request) {
        requireOwnedSession(request.sessionId());
        SandboxExecResult result;
        try {
            result = interactiveSandboxService().exec(request.sessionId(), Duration.ofSeconds(request.timeoutSeconds()), request.command());
        }
        catch (RuntimeException e) {
            try {
                if (!interactiveSandboxService().sessionExists(request.sessionId())) {
                    releaseOwnedPermit(request.sessionId());
                }
            }
            catch (RuntimeException reconciliationFailure) {
                e.addSuppressed(reconciliationFailure);
            }
            throw e;
        }
        if (result.timedOut()) {
            releaseOwnedPermit(request.sessionId());
        }
        return SandboxOpResponse.exec(request.correlationId(), request.sessionId(), result);
    }

    private SandboxOpResponse handleCopyIn(SandboxOpRequest request) {
        requireOwnedSession(request.sessionId());
        // Keep the staged payload until the caller receives a terminal response, so a retried request can recover after a lost response without losing the input.
        byte[] payload = distributedDataAccessService.getHyperionSandboxPayloads().get(request.correlationId());
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
        catch (RuntimeException e) {
            reconcileMissingSessionAfterFailure(request.sessionId(), e);
            throw e;
        }
        return SandboxOpResponse.ok(request.correlationId(), request.sessionId());
    }

    private SandboxOpResponse handleCopyOut(SandboxOpRequest request) {
        requireOwnedSession(request.sessionId());
        try (TarArchiveInputStream tar = interactiveSandboxService().copyOut(request.sessionId(), request.workspacePath())) {
            byte[] payload = repackTar(tar);
            if (isDeadlineExpired(request)) {
                return deadlineExpiredResponse(request);
            }
            // Stage the repacked archive in the keyed map rather than on the response topic, so only the originating core node fetches the bytes instead of every response
            // subscriber
            // deserializing them on its event thread. The client reads and removes the entry.
            var payloads = distributedDataAccessService.getHyperionSandboxPayloads();
            payloads.put(request.correlationId(), payload);
            if (isDeadlineExpired(request)) {
                payloads.remove(request.correlationId());
                return deadlineExpiredResponse(request);
            }
            return SandboxOpResponse.copiedOut(request.correlationId(), request.sessionId());
        }
        catch (IOException e) {
            return SandboxOpResponse.failure(request.correlationId(), "Failed to buffer copy-out archive: " + e.getMessage());
        }
        catch (RuntimeException e) {
            reconcileMissingSessionAfterFailure(request.sessionId(), e);
            throw e;
        }
    }

    private void reconcileMissingSessionAfterFailure(String sessionId, RuntimeException failure) {
        try {
            if (!interactiveSandboxService().sessionExists(sessionId)) {
                releaseOwnedPermit(sessionId);
            }
        }
        catch (RuntimeException reconciliationFailure) {
            failure.addSuppressed(reconciliationFailure);
        }
    }

    private boolean retainExpiredContainerWhenCleanupFails(String containerId, SandboxSessionSpec sessionSpec) {
        try {
            interactiveSandboxService().destroySession(containerId);
            return false;
        }
        catch (RuntimeException cleanupFailure) {
            try {
                if (!interactiveSandboxService().sessionExists(containerId)) {
                    log.info("Sandbox session {} was already absent after its expired CREATE cleanup failed.", containerId);
                    return false;
                }
            }
            catch (RuntimeException reconciliationFailure) {
                cleanupFailure.addSuppressed(reconciliationFailure);
            }
            registerSession(containerId, sessionSpec);
            ownedSessionIds.add(containerId);
            try {
                publishSessionState();
            }
            catch (RuntimeException publicationFailure) {
                cleanupFailure.addSuppressed(publicationFailure);
            }
            log.warn("Could not remove sandbox session {} after its CREATE deadline expired; retaining ownership for duplicate recovery and orphan reaping.", containerId,
                    cleanupFailure);
            return true;
        }
    }

    private static boolean isDeadlineExpired(SandboxOpRequest request) {
        return request.deadlineEpochMillis() > 0 && request.deadlineEpochMillis() <= System.currentTimeMillis();
    }

    private static SandboxOpResponse deadlineExpiredResponse(SandboxOpRequest request) {
        return SandboxOpResponse.failure(request.correlationId(), "Interactive sandbox request deadline expired before execution completed.");
    }

    /** Re-packs a decoded tar stream for bounded transport through the relay. */
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
        String jobId = jobIdsBySessionId.get(request.sessionId());
        if (jobId == null) {
            return destroyOwnedSession(request);
        }
        JobCoordination jobCoordination = acquireJobCoordination(jobId);
        try {
            return destroyOwnedSession(request);
        }
        finally {
            releaseJobCoordination(jobId, jobCoordination);
        }
    }

    private SandboxOpResponse destroyOwnedSession(SandboxOpRequest request) {
        if (!ownsSession(request.sessionId())) {
            return SandboxOpResponse.ok(request.correlationId(), request.sessionId());
        }
        try {
            interactiveSandboxService().destroySession(request.sessionId());
        }
        catch (RuntimeException destroyFailure) {
            // A lost Docker response can report failure after the daemon removed the container. Reconcile before retaining the only permit indefinitely.
            if (interactiveSandboxService().sessionExists(request.sessionId())) {
                throw destroyFailure;
            }
            log.info("Sandbox session {} was already absent after an ambiguous destroy failure; releasing its slot.", request.sessionId());
        }
        releaseOwnedPermitLocked(request.sessionId());
        return SandboxOpResponse.ok(request.correlationId(), request.sessionId());
    }

    private SandboxOpResponse handleList(SandboxOpRequest request) {
        List<GenerationSandboxSessionDTO> sessions = activeSessions.entrySet().stream()
                .map(entry -> entry.getValue().toDto(entry.getKey(), interactiveSandboxService().lastActivity(entry.getKey()))).toList();
        return SandboxOpResponse.sessions(request.correlationId(), sessions);
    }

    private void registerSession(String containerId, SandboxSessionSpec sessionSpec) {
        SandboxSessionContext context = sessionSpec.context();
        activeSessions.put(containerId, new ActiveSession(sessionSpec, Instant.now()));
        sessionIdsByJobId.put(context.jobId(), containerId);
        jobIdsBySessionId.put(containerId, context.jobId());
    }

    private void requireOwnedSession(String sessionId) {
        if (!ownsSession(sessionId)) {
            throw new LocalCIException("Interactive sandbox session " + sessionId + " is not owned by this relay");
        }
    }

    private boolean ownsSession(String sessionId) {
        return ownedSessionIds.contains(sessionId);
    }

    /** Idempotently releases permits for an owned sandbox removed by the orphan reaper. */
    void releaseIfOwned(String containerId) {
        releaseOwnedPermit(containerId);
    }

    Set<String> ownedSessionIdsSnapshot() {
        return Set.copyOf(ownedSessionIds);
    }

    private void releaseOwnedPermit(String containerId) {
        String jobId = jobIdsBySessionId.get(containerId);
        if (jobId == null) {
            releaseOwnedPermitLocked(containerId);
            return;
        }
        JobCoordination jobCoordination = acquireJobCoordination(jobId);
        try {
            releaseOwnedPermitLocked(containerId);
        }
        finally {
            releaseJobCoordination(jobId, jobCoordination);
        }
    }

    private void releaseOwnedPermitLocked(String containerId) {
        if (ownedSessionIds.remove(containerId)) {
            ActiveSession removedSession = activeSessions.remove(containerId);
            String jobId = jobIdsBySessionId.remove(containerId);
            if (jobId != null) {
                sessionIdsByJobId.remove(jobId, containerId);
            }
            else if (removedSession != null) {
                sessionIdsByJobId.remove(removedSession.sessionSpec().context().jobId(), containerId);
            }
            sandboxSlotPermits.release();
            try {
                publishSessionState();
            }
            catch (RuntimeException e) {
                log.warn("Could not publish generation sandbox slot state after releasing session {}: {}", containerId, e.getMessage());
            }
        }
    }

    /** Refreshes advertised slot load without resetting build-job failure counters. */
    private void publishSessionState() {
        buildAgentInformationService.updateGenerationSandboxSlotState(ownedSessionIds.size(), maxGenerationSandboxSlots);
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

    private JobCoordination acquireJobCoordination(String jobId) {
        JobCoordination coordination = jobCoordinations.compute(jobId, (ignored, current) -> {
            JobCoordination retained = current == null ? new JobCoordination() : current;
            retained.users++;
            return retained;
        });
        coordination.lock.lock();
        return coordination;
    }

    private void releaseJobCoordination(String jobId, JobCoordination coordination) {
        coordination.lock.unlock();
        jobCoordinations.compute(jobId, (ignored, current) -> {
            if (current != coordination) {
                throw new IllegalStateException("Generation job coordination changed while in use for " + jobId);
            }
            coordination.users--;
            return coordination.users == 0 ? null : coordination;
        });
    }

    private static final class JobCoordination {

        private final ReentrantLock lock = new ReentrantLock();

        /** Accessed only from {@link ConcurrentHashMap#compute(Object, java.util.function.BiFunction)} for this job id. */
        private int users;
    }

    private record ActiveSession(SandboxSessionSpec sessionSpec, Instant startedAt) {

        GenerationSandboxSessionDTO toDto(String containerId, java.util.Optional<Instant> lastActivity) {
            SandboxSessionContext context = sessionSpec.context();
            return new GenerationSandboxSessionDTO(containerId, context.jobId(), context.exerciseId(), context.exerciseTitle(), context.courseId(), context.userLogin(),
                    context.mode(), startedAt, lastActivity.orElse(startedAt));
        }
    }
}
