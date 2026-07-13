package de.tum.cit.aet.artemis.buildagent.service;

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
import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResult;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxOpRequest;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxOpResponse;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionSpec;
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

    private static final Duration OBSERVABILITY_OP_TIMEOUT = Duration.ofSeconds(10);

    private Duration controlOpTimeout = CONTROL_OP_TIMEOUT;

    private final DistributedDataAccessService distributedDataAccessService;

    /** Pending operations keyed by correlation id; completed by the response listener when the matching reply arrives. */
    private final Map<String, CompletableFuture<SandboxOpResponse>> pendingOperations = new ConcurrentHashMap<>();

    private DistributedTopic<SandboxOpResponse> responsesTopic;

    private UUID responseListenerId;

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
            CompletableFuture<SandboxOpResponse> future = pendingOperations.remove(response.correlationId());
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
        if (responseListenerId != null && responsesTopic != null) {
            responsesTopic.removeMessageListener(responseListenerId);
        }
        pendingOperations.forEach((correlationId, future) -> future.completeExceptionally(new LocalCIException("Remote interactive sandbox client is shutting down.")));
        pendingOperations.clear();
    }

    @Override
    public String createSession(SandboxSessionSpec spec) {
        List<String> candidates = selectCandidateAgents(2);
        if (candidates.isEmpty()) {
            throw new LocalCIException(
                    "No build agent has two free Hyperion generation sandbox slots. A successful generation run temporarily needs an authoring sandbox and a verification sandbox, "
                            + "so set artemis.continuous-integration.build-agent.max-generation-sandbox-slots accordingly on spare agents.");
        }
        List<String> declines = new ArrayList<>();
        for (String targetAgent : candidates) {
            SandboxOpRequest request = SandboxOpRequest.create(newCorrelationId(), targetAgent, spec, 2);
            CreateAttempt attempt = attemptCreate(request);
            if (attempt.containerId() != null) {
                return targetAgent + SESSION_HANDLE_SEPARATOR + attempt.containerId();
            }
            declines.add(targetAgent + " (" + attempt.declineReason() + ")");
        }
        throw new LocalCIException("Could not place an interactive sandbox session on any of the " + candidates.size()
                + " candidate build agent(s); all declined or were unreachable: " + String.join(", ", declines) + ".");
    }

    @Override
    public String createVerificationSession(SandboxSessionSpec spec, String loopSessionId) {
        String targetAgent = agentOf(loopSessionId);
        SandboxOpRequest request = SandboxOpRequest.createVerification(newCorrelationId(), targetAgent, spec, containerOf(loopSessionId));
        CreateAttempt attempt = attemptCreate(request);
        if (attempt.containerId() == null) {
            throw new LocalCIException("Could not place the verification sandbox on agent " + targetAgent + " next to the authoring sandbox: " + attempt.declineReason());
        }
        return targetAgent + SESSION_HANDLE_SEPARATOR + attempt.containerId();
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
    private CreateAttempt attemptCreate(SandboxOpRequest request) {
        CompletableFuture<SandboxOpResponse> future = new CompletableFuture<>();
        pendingOperations.put(request.correlationId(), future);
        try {
            distributedDataAccessService.getHyperionSandboxRequestsTopic().publish(request);
            SandboxOpResponse response = future.get(controlOpTimeout.toMillis(), TimeUnit.MILLISECONDS);
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
            return CreateAttempt.declined("unreachable, timed out after " + controlOpTimeout.toSeconds() + "s");
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
                || errorMessage.contains(InteractiveSandboxRelayHandler.RETRYABLE_REFUSAL_MARKER);
    }

    @Override
    public SandboxExecResult exec(String sessionId, Duration timeout, String... command) {
        String targetAgent = agentOf(sessionId);
        String containerId = containerOf(sessionId);
        SandboxOpRequest request = SandboxOpRequest.exec(newCorrelationId(), targetAgent, containerId, command, timeout.toSeconds());
        SandboxOpResponse response = relay(request, timeout.plus(RELAY_SLACK));
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
            SandboxOpRequest request = SandboxOpRequest.copyIn(correlationId, targetAgent, containerId, destinationPath);
            relay(request, controlOpTimeout);
        }
        finally {
            // The target worker removes the entry on consumption; this reclaims it if the agent never consumed it (dropped duplicate, dead agent, timeout).
            distributedDataAccessService.getHyperionSandboxPayloads().remove(correlationId);
        }
    }

    @Override
    public TarArchiveInputStream copyOut(String sessionId, String path) {
        String targetAgent = agentOf(sessionId);
        String containerId = containerOf(sessionId);
        String correlationId = newCorrelationId();
        SandboxOpRequest request = SandboxOpRequest.copyOut(correlationId, targetAgent, containerId, path);
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
    public void destroySession(String sessionId) {
        String targetAgent = agentOf(sessionId);
        String containerId = containerOf(sessionId);
        SandboxOpRequest request = SandboxOpRequest.destroy(newCorrelationId(), targetAgent, containerId);
        relay(request, controlOpTimeout);
    }

    /**
     * Returns the active sandbox sessions reported by an agent.
     *
     * @param agentName the agent short name
     * @return active sessions with composite session identifiers
     */
    public List<GenerationSandboxSessionDTO> listSessions(String agentName) {
        SandboxOpRequest request = SandboxOpRequest.list(newCorrelationId(), agentName);
        List<GenerationSandboxSessionDTO> sessions = relay(request, OBSERVABILITY_OP_TIMEOUT).sessions();
        if (sessions == null) {
            return List.of();
        }
        return sessions.stream().map(session -> session.withSessionId(agentName + SESSION_HANDLE_SEPARATOR + session.sessionId())).toList();
    }

    /** Relays one operation and translates failed or late responses to {@link LocalCIException}. */
    private SandboxOpResponse relay(SandboxOpRequest request, Duration budget) {
        CompletableFuture<SandboxOpResponse> future = new CompletableFuture<>();
        pendingOperations.put(request.correlationId(), future);
        try {
            distributedDataAccessService.getHyperionSandboxRequestsTopic().publish(request);
            SandboxOpResponse response = future.get(budget.toMillis(), TimeUnit.MILLISECONDS);
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

    /** Selects hosting agents with enough free generation slots, least loaded first. */
    private List<String> selectCandidateAgents(int requiredFreeSlots) {
        List<BuildAgentInformation> agents = distributedDataAccessService.getBuildAgentInformation();
        return agents.stream().filter(agent -> agent.status() == BuildAgentStatus.ACTIVE || agent.status() == BuildAgentStatus.IDLE)
                .filter(agent -> agent.maxGenerationSandboxSlots() > 0 && agent.maxGenerationSandboxSlots() - agent.reservedGenerationSandboxSlots() >= requiredFreeSlots)
                .sorted(Comparator.comparingInt(BuildAgentInformation::reservedGenerationSandboxSlots)).map(agent -> agent.buildAgent().name()).toList();
    }

    public boolean hasAvailableGenerationSandboxSlots(int requiredFreeSlots) {
        return !selectCandidateAgents(requiredFreeSlots).isEmpty();
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
