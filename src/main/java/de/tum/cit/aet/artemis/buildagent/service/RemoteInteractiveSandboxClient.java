package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * {@link InteractiveSandboxService}. A core-only node has no Docker, so every operation is relayed to the owning agent over two broadcast topics ({@code hyperion-sandbox-requests}
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
@Profile(PROFILE_CORE + " & !" + PROFILE_BUILDAGENT + " & " + PROFILE_LOCALCI)
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
        String targetAgent = selectTargetAgent();
        SandboxOpRequest request = SandboxOpRequest.create(newCorrelationId(), targetAgent, spec);
        SandboxOpResponse response = relay(request, CONTROL_OP_TIMEOUT);
        // Encode the owning agent into the handle so every later op can route back to the same agent without any shared lookup state.
        return targetAgent + SESSION_HANDLE_SEPARATOR + response.sessionId();
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
        SandboxOpRequest request = SandboxOpRequest.copyIn(newCorrelationId(), targetAgent, containerId, payload, destinationPath);
        relay(request, CONTROL_OP_TIMEOUT);
    }

    @Override
    public TarArchiveInputStream copyOut(String sessionId, String path) {
        String targetAgent = agentOf(sessionId);
        String containerId = containerOf(sessionId);
        SandboxOpRequest request = SandboxOpRequest.copyOut(newCorrelationId(), targetAgent, containerId, path);
        SandboxOpResponse response = relay(request, CONTROL_OP_TIMEOUT);
        // A successful COPY_OUT response always carries the repacked tar bytes (relay() throws on failure), so the payload is non-null here.
        return new TarArchiveInputStream(new ByteArrayInputStream(response.payload()));
    }

    @Override
    public void destroySession(String sessionId) {
        String targetAgent = agentOf(sessionId);
        String containerId = containerOf(sessionId);
        SandboxOpRequest request = SandboxOpRequest.destroy(newCorrelationId(), targetAgent, containerId);
        relay(request, CONTROL_OP_TIMEOUT);
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
     * Picks a build agent to host a new session. Generation hosting is opt-in per agent ({@code max-concurrent-generation-sessions}, default 0), and an agent with the cap at 0
     * never subscribes to the request topic — so selection MUST filter on generation-session headroom, not build-job headroom, or a CREATE routed to a non-hosting agent would hang
     * unanswered until the control-op timeout. Among active, non-paused agents that host generation and have a free session permit, the least session-loaded is chosen; its short
     * name is encoded into the session handle and pins all later operations to that agent.
     *
     * @return the short name of the selected agent
     */
    private String selectTargetAgent() {
        List<BuildAgentInformation> agents = distributedDataAccessService.getBuildAgentInformation();
        Optional<BuildAgentInformation> target = agents.stream().filter(agent -> agent.status() == BuildAgentStatus.ACTIVE || agent.status() == BuildAgentStatus.IDLE)
                .filter(agent -> agent.maxNumberOfConcurrentGenerationSessions() > 0 && agent.numberOfCurrentGenerationSessions() < agent.maxNumberOfConcurrentGenerationSessions())
                .min(Comparator.comparingInt(BuildAgentInformation::numberOfCurrentGenerationSessions));
        return target.map(agent -> agent.buildAgent().name()).orElseThrow(() -> new LocalCIException(
                "No build agent is configured to host interactive sandbox sessions (set artemis.continuous-integration.build-agent.max-concurrent-generation-sessions > 0 on a spare agent)."));
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
