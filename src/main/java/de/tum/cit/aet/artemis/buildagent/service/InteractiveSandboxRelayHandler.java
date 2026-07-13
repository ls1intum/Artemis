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

/** Relays distributed interactive-sandbox operations to this build agent. */
@Lazy(false)
@Component
@Profile(PROFILE_BUILDAGENT)
public class InteractiveSandboxRelayHandler {

    private static final Logger log = LoggerFactory.getLogger(InteractiveSandboxRelayHandler.class);

    static final String CAPACITY_REFUSAL_MARKER = "is at its generation sandbox slot capacity";

    static final String DRAINING_REFUSAL_MARKER = "is paused and is not accepting new generation sandboxes";

    static final String RETRYABLE_REFUSAL_MARKER = "encountered a transient error creating a generation sandbox";

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

    private static final int MAX_REMEMBERED_CORRELATION_IDS = 10_000;

    /** Guarded FIFO used for at-most-once request handling. */
    private final LinkedHashSet<String> handledCorrelationIds = new LinkedHashSet<>();

    private final Map<String, Integer> ownedSandboxSlotPermits = new ConcurrentHashMap<>();

    private final Map<String, ActiveSession> activeSessions = new ConcurrentHashMap<>();

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
            if (!buildAgentShortName.equals(request.targetAgentShortName())) {
                return;
            }
            workerExecutor.submit(() -> handle(request));
        });
        log.info("InteractiveSandboxRelayHandler initialized for build agent '{}' (max generation sandbox slots: {})", buildAgentShortName, maxGenerationSandboxSlots);
    }

    /**
     * Stops the relay listener and workers.
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
        // Pausing blocks new sessions but leaves existing session operations available for orderly teardown.
        if (sharedQueueProcessingService.isPaused()) {
            return SandboxOpResponse.failure(request.correlationId(), "Build agent '" + buildAgentShortName + "' " + DRAINING_REFUSAL_MARKER + ".");
        }
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
            if (isTransientDockerFailure(e)) {
                return SandboxOpResponse.failure(request.correlationId(), "Build agent '" + buildAgentShortName + "' " + RETRYABLE_REFUSAL_MARKER + ": " + e.getMessage());
            }
            return SandboxOpResponse.failure(request.correlationId(), e.getMessage());
        }
        finally {
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

    /** Returns whether a create failure may succeed on another agent; Docker 4xx failures are deterministic. */
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
        // Keyed staging avoids broadcasting the payload; this worker consumes the entry and the client cleans up abandoned entries.
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

    /** Idempotently releases permits for an owned sandbox removed by the orphan reaper. */
    void releaseIfOwned(String containerId) {
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

    /** Refreshes advertised slot load without resetting build-job failure counters. */
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
