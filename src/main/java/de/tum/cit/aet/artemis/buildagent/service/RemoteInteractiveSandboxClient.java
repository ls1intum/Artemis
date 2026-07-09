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
import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResult;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxOp;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxOpRequest;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxOpResponse;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionSpec;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;
import de.tum.cit.aet.artemis.localci.service.distributed.api.topic.DistributedTopic;

/**
 * Core-node {@link InteractiveSandbox} that drives a sandbox container living on a remote build agent — the multi-node counterpart of the co-located
 * {@link InteractiveSandboxService}. A core node (including a core/build-agent co-location) relays every operation to the owning agent over two broadcast topics
 * ({@code hyperion-sandbox-requests}
 * / {@code hyperion-sandbox-responses}); agents commonly run as Hazelcast clients rather than members, so a member-targeted RPC is impossible and the owning agent self-filters on
 * its short name.
 * <p>
 * Session affinity is encoded into the handle itself: {@link #createSession} returns {@code "<agentShortName>::<containerId>"} and every later operation parses the short name back
 * out to route to the same agent, so no shared state remembers which agent owns a session. Each method publishes a request and blocks on a per-correlation-id future until the
 * response arrives (or the relay budget elapses); blocking is safe because these calls run on the {@code hyperionGenerationExecutor}, never on a topic-listener thread. A dead
 * agent lets the future time out, and the orchestrator treats the resulting exception as session-fatal.
 *
 * @see InteractiveSandboxService the local, co-located implementation whose operations this client relays
 * @see InteractiveSandboxRelayHandler the build-agent-side listener that performs the relayed operations
 */
@Lazy
@Component
@Primary
@Profile(PROFILE_CORE + " & " + PROFILE_LOCALCI)
public class RemoteInteractiveSandboxClient implements InteractiveSandbox {

    private static final Logger log = LoggerFactory.getLogger(RemoteInteractiveSandboxClient.class);

    /**
     * Separator between the owning agent short name and the container id inside a composite session handle. A build agent short name is constrained to {@code [a-z0-9-]+}, so this
     * token never collides with a short name and unambiguously splits the handle.
     */
    static final String SESSION_HANDLE_SEPARATOR = "::";

    /**
     * Hard cap on the serialized tar payload of a {@link SandboxOp#COPY_IN} / {@link SandboxOp#COPY_OUT} operation. These operations are infrequent (workspace seeding plus a few
     * verify-time extractions), so the cap protects the distributed messaging layer from an unbounded blob while comfortably exceeding any legitimate exercise repository.
     */
    static final int MAX_PAYLOAD_BYTES = 32 * 1024 * 1024;

    /**
     * Extra wait budget added on top of an operation's own timeout to cover the topic round-trip, response (de)serialization, and Docker overhead, so a long-running build is never
     * spuriously failed by the relay before the inner exec timeout can fire.
     */
    private static final Duration RELAY_SLACK = Duration.ofSeconds(60);

    /**
     * Relay budget for operations that carry no inner exec timeout (create, copy-in, copy-out, destroy). Generous enough for image pulls and large copies, bounded so a dead agent
     * cannot block a generation thread forever.
     */
    private static final Duration CONTROL_OP_TIMEOUT = Duration.ofMinutes(5);

    /**
     * Wait budget for a single control operation (create attempt, copy, destroy). An instance field defaulting to {@link #CONTROL_OP_TIMEOUT} rather than a bare constant so a test
     * can shorten it to exercise timeout-driven create failover without a real multi-minute wait; production never reassigns it.
     */
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
     * Subscribes to the response topic once, so every relayed operation can correlate its reply. The listener does no heavy work: it only completes the waiting future.
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
     * Removes the response listener and fails any still-pending operations on shutdown, so blocked callers unwind promptly instead of waiting out their own timeout.
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
        // Failover: try the least session-loaded agent first, then the next, skipping any that declines (at capacity, draining, or a transient/agent-local Docker error) or is
        // unreachable (timeout); a deterministic failure (bad image, malformed spec) throws immediately from attemptCreate rather than storming every candidate. Bounded by the
        // candidate count so a burst of concurrent creates contending for the same scarce permits spreads across agents instead of every loser eating a timeout or a hard failure.
        List<String> declines = new ArrayList<>();
        for (String targetAgent : candidates) {
            SandboxOpRequest request = SandboxOpRequest.create(newCorrelationId(), targetAgent, spec, 2);
            CreateAttempt attempt = attemptCreate(request);
            if (attempt.containerId() != null) {
                // Encode the owning agent into the handle so every later op can route back to the same agent without any shared lookup state.
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

    /**
     * Outcome of a single CREATE attempt against one agent: either the created container id, or the reason the agent declined (so the caller can fail over to the next candidate).
     *
     * @param containerId   the created container id on success; {@code null} when the agent declined
     * @param declineReason a short human-readable reason when {@code containerId} is {@code null}; {@code null} on success
     */
    private record CreateAttempt(String containerId, String declineReason) {

        static CreateAttempt success(String containerId) {
            return new CreateAttempt(containerId, null);
        }

        static CreateAttempt declined(String reason) {
            return new CreateAttempt(null, reason);
        }
    }

    /**
     * Publishes one CREATE request and waits for the owning agent's reply. An agent-specific decline — a placement refusal (at capacity, draining) or a transient/agent-local
     * Docker
     * failure (daemon hiccup, image-pull blip, Docker down on that agent), each tagged by the handler with a stable marker — is returned as a {@link CreateAttempt#declined} so the
     * caller fails over to the next candidate: an error reply comes back fast (unlike an unreachable-agent timeout), so retrying elsewhere costs a round-trip, not a wasted
     * control-op
     * timeout. A genuinely deterministic failure (bad image reference, malformed spec) recurs identically on every candidate, so it is thrown immediately rather than fanned out
     * across every agent as a pointless retry storm. An unanswered request (dead/overwhelmed agent) times out and is also treated as a decline. Only a publish-side failure
     * (serialization, cluster down), which hits every agent identically, is thrown immediately.
     *
     * @param request the CREATE request to relay
     * @return the attempt outcome (created container id, or a decline reason)
     */
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
            // Fail over only on an agent-specific decline the owning agent tagged: at capacity, draining, or a transient/agent-local Docker error. A deterministic failure (bad
            // image
            // reference, malformed spec) recurs identically on every candidate, so surface it immediately instead of storming all N agents with a retry that will fail the same
            // way.
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
            // A deterministic create failure was already surfaced as a fully-formed session-fatal exception above; let it propagate rather than re-wrapping it as a publish
            // failure.
            throw e;
        }
        catch (RuntimeException e) {
            // A publish failure (serialization, cluster down) hits every agent identically, so it is fatal to the whole create, not a per-agent decline.
            throw new LocalCIException("Failed to publish remote sandbox operation CREATE to agent " + request.targetAgentShortName(), e);
        }
        finally {
            pendingOperations.remove(request.correlationId());
        }
    }

    /**
     * Whether a CREATE failure reply from an agent is an agent-specific decline the client should fail over from (try the next candidate), rather than a deterministic failure it
     * should surface immediately. Matches the stable markers the {@link InteractiveSandboxRelayHandler} embeds for a capacity refusal, a draining refusal, or a
     * transient/agent-local
     * Docker error; any other failure (bad image reference, malformed spec) recurs identically on every agent and so is not a failover case.
     *
     * @param errorMessage the agent's failure message
     * @return {@code true} if the client should fail over to the next candidate agent; {@code false} if the failure is deterministic and should surface immediately
     */
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
            // relay() throws on failure, so on return the agent has staged the repacked tar in the keyed map (off the broadcast response path); this node is its only reader.
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
     * Publishes a request and blocks until the owning agent's response arrives or the budget elapses, translating a missing/failed/late response into a {@link LocalCIException} so
     * the orchestrator can treat it as session-fatal.
     *
     * @param request the operation to relay
     * @param budget  the maximum time to wait for the response
     * @return the successful response
     */
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
            // The !success branch above already threw a fully-formed session-fatal exception; let it propagate unchanged rather than re-wrapping it as a publish failure.
            throw e;
        }
        catch (RuntimeException e) {
            // A publish failure (serialization error, cluster down) is session-fatal just like a timeout, so surface it as a LocalCIException rather than an opaque runtime error.
            throw new LocalCIException("Failed to publish remote sandbox operation " + request.op() + " to agent " + request.targetAgentShortName(), e);
        }
        finally {
            pendingOperations.remove(request.correlationId());
        }
    }

    /**
     * Orders the build agents eligible to start a new generation loop, least slot-loaded first, so {@link #createSession} can try them in turn and fail over. Generation hosting
     * is opt-in per agent ({@code max-generation-sandbox-slots}, default 0), and an agent with the cap at 0 never subscribes to the request topic — so selection MUST filter
     * on generation sandbox slot headroom, not build-job headroom, or a CREATE routed to a non-hosting agent would hang unanswered until the control-op timeout. A loop is admitted
     * only when the agent has at least two free permits: one for the authoring sandbox and one for the verification sandbox that will be created on the same agent via
     * {@link #createVerificationSession(SandboxSessionSpec, String)}.
     *
     * @param requiredFreeSlots the number of free generation sandbox slots required on the agent
     * @return the short names of the candidate agents, ascending by current session load
     */
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

    /** The owning agent short name of a composite session handle produced by {@link #createSession}. */
    private static String agentOf(String sessionHandle) {
        return splitSessionHandle(sessionHandle)[0];
    }

    /** The container id (as the owning agent understands it) of a composite session handle produced by {@link #createSession}. */
    private static String containerOf(String sessionHandle) {
        return splitSessionHandle(sessionHandle)[1];
    }

    /**
     * Splits a composite session handle into its agent short name and container id at the {@link #SESSION_HANDLE_SEPARATOR}.
     *
     * @param sessionHandle the composite handle {@code "<agentShortName>::<containerId>"}
     * @return a two-element array of {@code [agentShortName, containerId]}
     */
    private static String[] splitSessionHandle(String sessionHandle) {
        int separator = sessionHandle.indexOf(SESSION_HANDLE_SEPARATOR);
        if (separator < 0) {
            throw new LocalCIException("Malformed remote sandbox session handle (missing agent prefix): " + sessionHandle);
        }
        return new String[] { sessionHandle.substring(0, separator), sessionHandle.substring(separator + SESSION_HANDLE_SEPARATOR.length()) };
    }

    /**
     * Buffers a tar stream into a bounded byte array, failing closed if it exceeds {@link #MAX_PAYLOAD_BYTES} so an oversized copy cannot overwhelm the messaging layer.
     *
     * @param input the tar stream to buffer
     * @return the buffered bytes
     */
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
