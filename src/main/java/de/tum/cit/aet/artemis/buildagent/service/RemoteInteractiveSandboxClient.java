package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

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
 * Core-node implementation of the {@link InteractiveSandbox} primitive that drives a sandbox container physically living on a <em>remote</em> build agent.
 * <p>
 * It is the multi-node counterpart of the co-located {@link InteractiveSandboxService}: a core-only Artemis node holds the LLM client and database but has no Docker, so every
 * sandbox operation is relayed to the owning build agent over two broadcast topics ({@code hyperion-sandbox-requests} / {@code hyperion-sandbox-responses}). Build agents commonly
 * run as Hazelcast <em>clients</em> rather than members, so a member-targeted RPC is impossible; the request is broadcast and the owning agent self-filters on its short name —
 * exactly the pattern Artemis already uses for build-job cancel / agent pause / resume.
 * <p>
 * Session affinity is encoded into the session handle itself: {@link #createSession} picks a target agent and returns {@code "<agentShortName>::<containerId>"}. Every later
 * operation parses the short name back out and targets that same agent, so no new shared state is needed to remember which agent owns a session. The composite encoding is private
 * to this client; the build agent only ever sees the plain container id.
 * <p>
 * Each method publishes a request and blocks on a per-correlation-id future until the matching response arrives (or the relay budget elapses). Blocking is intentional and safe:
 * these calls run on the {@code hyperionGenerationExecutor}, never on a topic-listener thread. A dead agent simply lets the future time out; the resulting exception is treated as
 * session-fatal by the orchestrator, which tears the session down and the per-agent reaper removes the orphaned container.
 *
 * @see InteractiveSandboxService the local, co-located implementation whose operations this client relays
 * @see InteractiveSandboxRelayHandler the build-agent-side listener that performs the relayed operations
 */
@Lazy
@Component
@Profile(PROFILE_CORE + " & !" + PROFILE_BUILDAGENT)
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
        SandboxOpRequest request = new SandboxOpRequest(newCorrelationId(), targetAgent, SandboxOp.CREATE, null, spec, null, 0L, null, null);
        SandboxOpResponse response = relay(request, CONTROL_OP_TIMEOUT);
        // Encode the owning agent into the handle so every later op can route back to the same agent without any shared lookup state.
        return targetAgent + SESSION_HANDLE_SEPARATOR + response.sessionId();
    }

    @Override
    public SandboxExecResult exec(String sessionId, Duration timeout, String... command) {
        String targetAgent = agentOf(sessionId);
        String containerId = containerOf(sessionId);
        SandboxOpRequest request = new SandboxOpRequest(newCorrelationId(), targetAgent, SandboxOp.EXEC, containerId, null, command, timeout.toSeconds(), null, null);
        SandboxOpResponse response = relay(request, timeout.plus(RELAY_SLACK));
        return response.execResult();
    }

    @Override
    public void copyIn(String sessionId, String destinationPath, InputStream tarArchive) {
        byte[] payload = readBounded(tarArchive);
        String targetAgent = agentOf(sessionId);
        String containerId = containerOf(sessionId);
        SandboxOpRequest request = new SandboxOpRequest(newCorrelationId(), targetAgent, SandboxOp.COPY_IN, containerId, null, null, 0L, payload, destinationPath);
        relay(request, CONTROL_OP_TIMEOUT);
    }

    @Override
    public TarArchiveInputStream copyOut(String sessionId, String path) {
        String targetAgent = agentOf(sessionId);
        String containerId = containerOf(sessionId);
        SandboxOpRequest request = new SandboxOpRequest(newCorrelationId(), targetAgent, SandboxOp.COPY_OUT, containerId, null, null, 0L, null, path);
        SandboxOpResponse response = relay(request, CONTROL_OP_TIMEOUT);
        byte[] payload = response.payload() != null ? response.payload() : new byte[0];
        return new TarArchiveInputStream(new ByteArrayInputStream(payload));
    }

    @Override
    public void destroySession(String sessionId) {
        String targetAgent = agentOf(sessionId);
        String containerId = containerOf(sessionId);
        SandboxOpRequest request = new SandboxOpRequest(newCorrelationId(), targetAgent, SandboxOp.DESTROY, containerId, null, null, 0L, null, null);
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
        finally {
            pendingOperations.remove(request.correlationId());
        }
    }

    /**
     * Picks a build agent to own a new session: an active, non-paused agent with spare build capacity, preferring the least-loaded one so a generation session does not pile onto a
     * busy agent. The chosen short name is encoded into the session handle and pins all later operations to that agent.
     *
     * @return the short name of the selected agent
     */
    private String selectTargetAgent() {
        List<BuildAgentInformation> agents = distributedDataAccessService.getBuildAgentInformation();
        Optional<BuildAgentInformation> target = agents.stream().filter(agent -> agent.status() == BuildAgentStatus.ACTIVE || agent.status() == BuildAgentStatus.IDLE)
                .filter(agent -> agent.numberOfCurrentBuildJobs() < agent.maxNumberOfConcurrentBuildJobs())
                .min(Comparator.comparingInt(BuildAgentInformation::numberOfCurrentBuildJobs));
        return target.map(agent -> agent.buildAgent().name())
                .orElseThrow(() -> new LocalCIException("No build agent with spare capacity is available to host an interactive sandbox session."));
    }

    private static String newCorrelationId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Extracts the owning agent short name from a composite session handle produced by {@link #createSession}.
     *
     * @param sessionHandle the composite handle {@code "<agentShortName>::<containerId>"}
     * @return the agent short name
     */
    private static String agentOf(String sessionHandle) {
        return splitSessionHandle(sessionHandle)[0];
    }

    /**
     * Extracts the container id from a composite session handle produced by {@link #createSession}.
     *
     * @param sessionHandle the composite handle {@code "<agentShortName>::<containerId>"}
     * @return the container id the owning agent understands
     */
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
