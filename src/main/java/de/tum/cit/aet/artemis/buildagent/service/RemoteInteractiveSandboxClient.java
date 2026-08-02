package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.buildagent.config.GenerationSandboxHostingEnabled.MAX_GENERATION_SANDBOX_SLOTS_PROPERTY;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentStatus;
import de.tum.cit.aet.artemis.buildagent.dto.GenerationSandboxSessionDTO;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResultDTO;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxOpRequestDTO;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxOpResponseDTO;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionSpecDTO;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;
import de.tum.cit.aet.artemis.localci.service.distributed.api.topic.DistributedTopic;

/** Relays sandbox operations to the build agent encoded in each session handle. */
@Lazy
@Component
@Primary
@Profile(PROFILE_CORE + " & " + PROFILE_LOCALCI)
public class RemoteInteractiveSandboxClient implements InteractiveSandbox {

    private static final Logger log = LoggerFactory.getLogger(RemoteInteractiveSandboxClient.class);

    /** Separates the agent name from the container id in a session handle. */
    static final String SESSION_HANDLE_SEPARATOR = "::";

    /** Hard cap for tar payloads carried by the distributed relay. */
    static final int MAX_PAYLOAD_BYTES = 32 * 1024 * 1024;

    /** Allows relay overhead beyond the inner operation timeout. */
    private static final Duration RELAY_SLACK = Duration.ofSeconds(60);

    /** Bounds control operations that have no inner execution timeout. */
    private static final Duration CONTROL_OP_TIMEOUT = Duration.ofMinutes(5);

    /** Re-publish interval for the same idempotency key, recovering a request or response dropped by the distributed topic without repeating the operation. */
    private static final Duration RELAY_RETRY_INTERVAL = Duration.ofSeconds(5);

    private static final Duration OBSERVABILITY_OP_TIMEOUT = Duration.ofSeconds(10);

    private Duration controlOpTimeout = CONTROL_OP_TIMEOUT;

    private Duration relayRetryInterval = RELAY_RETRY_INTERVAL;

    private final DistributedDataAccessService distributedDataAccessService;

    /** Pending operations keyed by correlation id; completed by the response listener when the matching reply arrives. */
    private final Map<String, CompletableFuture<SandboxOpResponseDTO>> pendingOperations = new ConcurrentHashMap<>();

    private DistributedTopic<SandboxOpResponseDTO> responsesTopic;

    private UUID responseListenerId;

    private final AtomicBoolean shuttingDown = new AtomicBoolean();

    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock(true);

    public RemoteInteractiveSandboxClient(DistributedDataAccessService distributedDataAccessService) {
        this.distributedDataAccessService = distributedDataAccessService;
    }

    /**
     * Registers the response listener.
     */
    @PostConstruct
    public void registerResponseListener() {
        this.responsesTopic = distributedDataAccessService.getHyperionSandboxResponsesTopic();
        this.responseListenerId = responsesTopic.addMessageListener(response -> {
            CompletableFuture<SandboxOpResponseDTO> future = pendingOperations.remove(response.correlationId());
            if (future != null) {
                future.complete(response);
            }
            // A response for an unknown correlation id is simply ignored: it belongs to a different core node, or to a request that already timed out.
        });
    }

    /**
     * Removes the response listener and fails pending operations.
     */
    @PreDestroy
    public void removeResponseListener() {
        Lock shutdownLock = lifecycleLock.writeLock();
        shutdownLock.lock();
        try {
            if (!shuttingDown.compareAndSet(false, true)) {
                return;
            }
            if (responseListenerId != null && responsesTopic != null) {
                responsesTopic.removeMessageListener(responseListenerId);
            }
            pendingOperations.forEach((correlationId, future) -> future.completeExceptionally(new LocalCIException("Remote interactive sandbox client is shutting down.")));
            pendingOperations.clear();
        }
        finally {
            shutdownLock.unlock();
        }
    }

    @Override
    public String createSession(SandboxSessionSpecDTO spec) {
        List<String> candidates = selectCandidateAgents();
        if (candidates.isEmpty()) {
            GenerationSandboxCapacity capacity = generationSandboxCapacity();
            log.warn("Cannot place a Hyperion generation sandbox: {}. Set {} to a positive value on the build agents that should host generation runs.", capacity,
                    MAX_GENERATION_SANDBOX_SLOTS_PROPERTY);
            throw new LocalCIException(
                    "No build agent has a free Hyperion generation sandbox slot. Set " + MAX_GENERATION_SANDBOX_SLOTS_PROPERTY + " accordingly on spare agents.");
        }
        List<String> declines = new ArrayList<>();
        for (String targetAgent : candidates) {
            SandboxOpRequestDTO request = SandboxOpRequestDTO.create(newCorrelationId(), targetAgent, spec).withDeadline(controlOpTimeout);
            CreateAttempt attempt = attemptCreate(request);
            if (attempt.containerId() != null) {
                return targetAgent + SESSION_HANDLE_SEPARATOR + attempt.containerId();
            }
            declines.add(targetAgent + " (" + attempt.declineReason() + ")");
        }
        throw new LocalCIException("Could not place an interactive sandbox session on any of the " + candidates.size()
                + " candidate build agent(s); all declined or were unreachable: " + String.join(", ", declines) + ".");
    }

    private record CreateAttempt(String containerId, String declineReason) {

        static CreateAttempt success(String containerId) {
            return new CreateAttempt(containerId, null);
        }

        static CreateAttempt declined(String reason) {
            return new CreateAttempt(null, reason);
        }
    }

    /** Attempts one create and distinguishes agent-local declines from deterministic failures. */
    private CreateAttempt attemptCreate(SandboxOpRequestDTO request) {
        try {
            CompletableFuture<SandboxOpResponseDTO> future = registerAndPublish(request);
            SandboxOpResponseDTO response = awaitResponse(request, future, controlOpTimeout);
            if (response.success()) {
                return CreateAttempt.success(response.sessionId());
            }
            String errorMessage = response.errorMessage() == null ? "" : response.errorMessage();
            if (isFailoverDecline(errorMessage)) {
                return CreateAttempt.declined(errorMessage);
            }
            throw new LocalCIException("Remote sandbox operation CREATE on agent " + request.targetAgentShortName() + " failed: " + errorMessage);
        }
        catch (TimeoutException e) {
            // The same correlation id was retried, but the request may still have reached the agent without a recoverable response. Trying another agent could create a second
            // container for the same job.
            throw new LocalCIException("Remote sandbox operation CREATE on agent " + request.targetAgentShortName() + " timed out after " + controlOpTimeout.toMillis()
                    + "ms; placement outcome is unknown, so no other agent was tried.", e);
        }
        catch (ExecutionException e) {
            throw new LocalCIException("Remote sandbox operation CREATE on agent " + request.targetAgentShortName() + " failed", e.getCause());
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LocalCIException("Interrupted while waiting for remote sandbox operation CREATE on agent " + request.targetAgentShortName(), e);
        }
        catch (LocalCIException e) {
            throw e;
        }
        catch (RuntimeException e) {
            throw new LocalCIException("Failed to publish remote sandbox operation CREATE to agent " + request.targetAgentShortName(), e);
        }
        finally {
            pendingOperations.remove(request.correlationId());
        }
    }

    /** Returns whether the client should try the next candidate agent. */
    private static boolean isFailoverDecline(String errorMessage) {
        return errorMessage.contains(InteractiveSandboxRelayHandler.CAPACITY_REFUSAL_MARKER) || errorMessage.contains(InteractiveSandboxRelayHandler.DRAINING_REFUSAL_MARKER)
                || errorMessage.contains(InteractiveSandboxRelayHandler.OVERLOAD_REFUSAL_MARKER);
    }

    @Override
    public SandboxExecResultDTO exec(String sessionId, Duration timeout, String... command) {
        String targetAgent = agentOf(sessionId);
        String containerId = containerOf(sessionId);
        SandboxOpRequestDTO request = SandboxOpRequestDTO.exec(newCorrelationId(), targetAgent, containerId, command, timeout.toSeconds());
        SandboxOpResponseDTO response = relay(request, timeout.plus(RELAY_SLACK));
        return response.execResult();
    }

    @Override
    public void copyIn(String sessionId, String destinationPath, InputStream tarArchive) {
        byte[] payload = readBounded(tarArchive);
        String targetAgent = agentOf(sessionId);
        String containerId = containerOf(sessionId);
        String correlationId = newCorrelationId();
        // Stage the (up to 32 MB) tar in the keyed map instead of on the broadcast request, so only the target agent transfers the bytes rather than every subscriber deserializing
        // them on its event thread.
        distributedDataAccessService.getHyperionSandboxPayloads().put(correlationId, payload);
        try {
            SandboxOpRequestDTO request = SandboxOpRequestDTO.copyIn(correlationId, targetAgent, containerId, destinationPath);
            relay(request, controlOpTimeout);
        }
        finally {
            // Keep the payload available while the same correlation id may be retried, then reclaim it after a terminal response or timeout.
            distributedDataAccessService.getHyperionSandboxPayloads().remove(correlationId);
        }
    }

    @Override
    public TarArchiveInputStream copyOut(String sessionId, String path) {
        String targetAgent = agentOf(sessionId);
        String containerId = containerOf(sessionId);
        String correlationId = newCorrelationId();
        SandboxOpRequestDTO request = SandboxOpRequestDTO.copyOut(correlationId, targetAgent, containerId, path);
        try {
            relay(request, controlOpTimeout);
            byte[] payload = distributedDataAccessService.getHyperionSandboxPayloads().get(correlationId);
            if (payload == null) {
                throw new LocalCIException("Remote sandbox COPY_OUT on agent " + targetAgent + " reported success but staged no payload (evicted?).");
            }
            return new TarArchiveInputStream(new ByteArrayInputStream(payload));
        }
        finally {
            // Always reclaim the staged entry, whether we just read it or relay failed before the agent could stage/we could read, so a large blob never lingers in the map.
            distributedDataAccessService.getHyperionSandboxPayloads().remove(correlationId);
        }
    }

    @Override
    public void resetSession(String sessionId) {
        String targetAgent = agentOf(sessionId);
        String containerId = containerOf(sessionId);
        relay(SandboxOpRequestDTO.reset(newCorrelationId(), targetAgent, containerId), controlOpTimeout);
    }

    @Override
    public void destroySession(String sessionId) {
        String targetAgent = agentOf(sessionId);
        String containerId = containerOf(sessionId);
        SandboxOpRequestDTO request = SandboxOpRequestDTO.destroy(newCorrelationId(), targetAgent, containerId);
        relay(request, controlOpTimeout);
    }

    /**
     * Returns the active sandbox sessions reported by an agent.
     *
     * @param agentName the agent short name
     * @return active sessions with composite session identifiers
     */
    public List<GenerationSandboxSessionDTO> listSessions(String agentName) {
        SandboxOpRequestDTO request = SandboxOpRequestDTO.list(newCorrelationId(), agentName);
        List<GenerationSandboxSessionDTO> sessions = relay(request, OBSERVABILITY_OP_TIMEOUT).sessions();
        if (sessions == null) {
            return List.of();
        }
        return sessions.stream().map(session -> session.withSessionId(agentName + SESSION_HANDLE_SEPARATOR + session.sessionId())).toList();
    }

    /** Relays one operation and translates failed or late responses to {@link LocalCIException}. */
    private SandboxOpResponseDTO relay(SandboxOpRequestDTO request, Duration budget) {
        request = request.withDeadline(budget);
        try {
            CompletableFuture<SandboxOpResponseDTO> future = registerAndPublish(request);
            SandboxOpResponseDTO response = awaitResponse(request, future, budget);
            if (!response.success()) {
                throw new LocalCIException("Remote sandbox operation " + request.op() + " failed on agent " + request.targetAgentShortName() + ": " + response.errorMessage());
            }
            return response;
        }
        catch (TimeoutException e) {
            throw new LocalCIException("Remote sandbox operation " + request.op() + " on agent " + request.targetAgentShortName() + " timed out after " + budget.toSeconds()
                    + "s (agent unreachable?)", e);
        }
        catch (ExecutionException e) {
            throw new LocalCIException("Remote sandbox operation " + request.op() + " on agent " + request.targetAgentShortName() + " failed", e.getCause());
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LocalCIException("Interrupted while waiting for remote sandbox operation " + request.op(), e);
        }
        catch (LocalCIException e) {
            throw e;
        }
        catch (RuntimeException e) {
            throw new LocalCIException("Failed to publish remote sandbox operation " + request.op() + " to agent " + request.targetAgentShortName(), e);
        }
        finally {
            pendingOperations.remove(request.correlationId());
        }
    }

    private CompletableFuture<SandboxOpResponseDTO> registerAndPublish(SandboxOpRequestDTO request) {
        Lock operationLock = lifecycleLock.readLock();
        operationLock.lock();
        try {
            if (shuttingDown.get()) {
                throw new LocalCIException("Remote interactive sandbox client is shutting down.");
            }
            CompletableFuture<SandboxOpResponseDTO> future = new CompletableFuture<>();
            pendingOperations.put(request.correlationId(), future);
            try {
                distributedDataAccessService.getHyperionSandboxRequestsTopic().publish(request);
                return future;
            }
            catch (RuntimeException e) {
                pendingOperations.remove(request.correlationId(), future);
                throw e;
            }
        }
        finally {
            operationLock.unlock();
        }
    }

    private SandboxOpResponseDTO awaitResponse(SandboxOpRequestDTO request, CompletableFuture<SandboxOpResponseDTO> future, Duration budget)
            throws InterruptedException, ExecutionException, TimeoutException {
        long deadline = System.nanoTime() + budget.toNanos();
        while (true) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                throw new TimeoutException();
            }
            try {
                return future.get(Math.min(remainingNanos, relayRetryInterval.toNanos()), TimeUnit.NANOSECONDS);
            }
            catch (TimeoutException e) {
                if (System.nanoTime() >= deadline) {
                    throw e;
                }
                publishRetry(request);
            }
        }
    }

    private void publishRetry(SandboxOpRequestDTO request) {
        Lock operationLock = lifecycleLock.readLock();
        operationLock.lock();
        try {
            if (shuttingDown.get()) {
                throw new LocalCIException("Remote interactive sandbox client is shutting down.");
            }
            distributedDataAccessService.getHyperionSandboxRequestsTopic().publish(request);
        }
        finally {
            operationLock.unlock();
        }
    }

    /** Selects hosting agents with a free generation slot, least loaded first. */
    private List<String> selectCandidateAgents() {
        return reachableAgents().stream().filter(agent -> agent.maxGenerationSandboxSlots() > agent.reservedGenerationSandboxSlots())
                .sorted(Comparator.comparingInt(BuildAgentInformation::reservedGenerationSandboxSlots)).map(agent -> agent.buildAgent().name()).toList();
    }

    private List<BuildAgentInformation> reachableAgents() {
        return distributedDataAccessService.getBuildAgentInformation().stream()
                .filter(agent -> agent.status() == BuildAgentStatus.ACTIVE || agent.status() == BuildAgentStatus.IDLE).toList();
    }

    /** Returns whether advertised fleet state has a free generation slot; placement remains authoritative. */
    public boolean hasAvailableGenerationSandboxSlot() {
        return !selectCandidateAgents().isEmpty();
    }

    /** Returns advertised fleet capacity for health diagnostics. */
    public GenerationSandboxCapacity generationSandboxCapacity() {
        List<BuildAgentInformation> agents = reachableAgents();
        int hostingAgents = 0;
        int totalSlots = 0;
        int reservedSlots = 0;
        for (BuildAgentInformation agent : agents) {
            if (agent.maxGenerationSandboxSlots() > 0) {
                hostingAgents++;
                totalSlots += agent.maxGenerationSandboxSlots();
                reservedSlots += Math.min(agent.reservedGenerationSandboxSlots(), agent.maxGenerationSandboxSlots());
            }
        }
        return new GenerationSandboxCapacity(agents.size(), hostingAgents, totalSlots, reservedSlots);
    }

    public record GenerationSandboxCapacity(int reachableAgents, int hostingAgents, int totalSlots, int reservedSlots) {

        public boolean noAgentAdvertisesCapacity() {
            return hostingAgents == 0;
        }

        public int freeSlots() {
            return Math.max(0, totalSlots - reservedSlots);
        }
    }

    private static String newCorrelationId() {
        return UUID.randomUUID().toString();
    }

    private static String agentOf(String sessionHandle) {
        return splitSessionHandle(sessionHandle)[0];
    }

    private static String containerOf(String sessionHandle) {
        return splitSessionHandle(sessionHandle)[1];
    }

    private static String[] splitSessionHandle(String sessionHandle) {
        int separator = sessionHandle.indexOf(SESSION_HANDLE_SEPARATOR);
        if (separator < 0) {
            throw new LocalCIException("Malformed remote sandbox session handle (missing agent prefix): " + sessionHandle);
        }
        return new String[] { sessionHandle.substring(0, separator), sessionHandle.substring(separator + SESSION_HANDLE_SEPARATOR.length()) };
    }

    /** Buffers a tar payload while enforcing {@link #MAX_PAYLOAD_BYTES}. */
    private static byte[] readBounded(InputStream input) {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(chunk)) != -1) {
                total += read;
                if (total > MAX_PAYLOAD_BYTES) {
                    throw new LocalCIException("Interactive sandbox copy payload exceeds the " + MAX_PAYLOAD_BYTES + " byte relay limit.");
                }
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        }
        catch (IOException e) {
            throw new UncheckedIOException("Failed to buffer interactive sandbox copy payload for relay", e);
        }
    }
}
