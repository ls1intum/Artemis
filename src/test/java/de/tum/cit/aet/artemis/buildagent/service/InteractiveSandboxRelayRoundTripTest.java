package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentStatus;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResult;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxOpRequest;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxOpResponse;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionSpec;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;
import de.tum.cit.aet.artemis.localci.service.distributed.api.topic.DistributedTopic;
import de.tum.cit.aet.artemis.localci.service.distributed.local.LocalMap;
import de.tum.cit.aet.artemis.localci.service.distributed.local.LocalTopic;

/**
 * Round-trip test for the multi-node interactive-sandbox relay: it wires a {@link RemoteInteractiveSandboxClient} (core node) to an {@link InteractiveSandboxRelayHandler} (build
 * agent) through the in-JVM {@link LocalTopic} backend of the {@link DistributedTopic} abstraction, with the agent's local {@link InteractiveSandboxService} mocked so no Docker is
 * needed.
 * <p>
 * It proves the contract the orchestrator relies on: createSession encodes affinity into the handle, exec returns the agent's stdout/exit, copy-in/copy-out round-trip the tar
 * bytes, destroy is idempotent, an oversize copy-in payload is rejected before it reaches the wire, and a request for a different agent short name is ignored by the handler. It
 * also
 * pins the correctness-critical relay invariants: the Docker work runs off the topic-listener thread (worker-pool handoff), an oversize copy-out archive is rejected as a
 * relay-limit
 * failure, and the per-agent session semaphore refuses at capacity and releases a permit exactly once per owned session (so a redundant DESTROY cannot over-release capacity).
 */
class InteractiveSandboxRelayRoundTripTest {

    private static final String AGENT_SHORT_NAME = "agent-1";

    private static final String CONTAINER_ID = "container-abc";

    private InteractiveSandboxService localSandbox;

    private RemoteInteractiveSandboxClient client;

    private InteractiveSandboxRelayHandler handler;

    private SharedQueueProcessingService queueProcessingService;

    private LocalTopic<SandboxOpRequest> requestsTopic;

    private DistributedDataAccessService clientAccess;

    @BeforeEach
    void setUp() {
        // One shared request topic and one shared response topic stand in for the cluster-wide distributed topics; LocalTopic delivers synchronously in-JVM. The keyed payload map
        // is shared the same way: the sender stages the bytes under the correlation id and the single recipient removes them (copy-in: client→agent, copy-out: agent→client).
        requestsTopic = new LocalTopic<>();
        LocalTopic<SandboxOpResponse> responsesTopic = new LocalTopic<>();
        LocalMap<String, byte[]> payloads = new LocalMap<>();

        clientAccess = mock(DistributedDataAccessService.class);
        when(clientAccess.getHyperionSandboxRequestsTopic()).thenReturn(requestsTopic);
        when(clientAccess.getHyperionSandboxResponsesTopic()).thenReturn(responsesTopic);
        when(clientAccess.getHyperionSandboxPayloads()).thenReturn(payloads);
        when(clientAccess.getBuildAgentInformation()).thenReturn(List.of(idleAgent(AGENT_SHORT_NAME, 0, 4)));

        DistributedDataAccessService handlerAccess = mock(DistributedDataAccessService.class);
        when(handlerAccess.getHyperionSandboxRequestsTopic()).thenReturn(requestsTopic);
        when(handlerAccess.getHyperionSandboxResponsesTopic()).thenReturn(responsesTopic);
        when(handlerAccess.getHyperionSandboxPayloads()).thenReturn(payloads);

        localSandbox = mock(InteractiveSandboxService.class);

        client = new RemoteInteractiveSandboxClient(clientAccess);
        client.registerResponseListener();

        queueProcessingService = mock(SharedQueueProcessingService.class);
        handler = new InteractiveSandboxRelayHandler(localSandbox, handlerAccess, queueProcessingService, new GenerationSessionState(), mock(BuildAgentInformationService.class));
        ReflectionTestUtils.setField(handler, "buildAgentShortName", AGENT_SHORT_NAME);
        ReflectionTestUtils.setField(handler, "maxConcurrentSessions", 2);
        handler.registerRequestListener();
    }

    @AfterEach
    void tearDown() {
        handler.shutdown();
        client.removeResponseListener();
    }

    @Test
    void createSession_encodesAgentAffinityIntoHandle() {
        when(localSandbox.createSession(any())).thenReturn(CONTAINER_ID);

        String handle = client.createSession(new SandboxSessionSpec("some-image", null));

        // The handle pins the owning agent so every later op routes back to the same agent without any shared lookup state.
        assertThat(handle).isEqualTo(AGENT_SHORT_NAME + "::" + CONTAINER_ID);
    }

    @Test
    void createSession_failsOverToTheNextAgentWhenTheFirstIsUnreachable() {
        // Two candidate agents, both hosting-enabled and equally idle, but only AGENT_SHORT_NAME has a live handler. The first (listed first, so tried first) never answers and
        // times
        // out; the client must fail over to the second and place the session there rather than surfacing the timeout as a failure.
        ReflectionTestUtils.setField(client, "controlOpTimeout", Duration.ofMillis(300));
        when(clientAccess.getBuildAgentInformation()).thenReturn(List.of(idleAgent("dead-agent-0", 0, 4), idleAgent(AGENT_SHORT_NAME, 0, 4)));
        when(localSandbox.createSession(any())).thenReturn(CONTAINER_ID);

        String handle = client.createSession(new SandboxSessionSpec("some-image", null));

        assertThat(handle).isEqualTo(AGENT_SHORT_NAME + "::" + CONTAINER_ID);
    }

    @Test
    void createSession_throwsWhenNoAgentIsConfiguredToHostSessions() {
        // Every agent has generation hosting disabled (max sessions 0): none is a candidate, so placement fails fast with an actionable message instead of broadcasting a request
        // no
        // agent will ever answer.
        when(clientAccess.getBuildAgentInformation())
                .thenReturn(List.of(new BuildAgentInformation(new BuildAgentDTO("no-gen", "127.0.0.1:5701", "no-gen"), 4, 0, List.of(), BuildAgentStatus.IDLE, "", null, 0, 0, 0)));

        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> client.createSession(new SandboxSessionSpec("some-image", null)))
                .withMessageContaining("No build agent is configured to host");
    }

    @Test
    void malformedSessionHandle_failsClosedWithoutPublishing() {
        // A handle without the "<agentShortName>::" prefix carries no routing target, so the client must fail closed before publishing any request rather than broadcasting an
        // unroutable operation.
        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> client.exec("no-separator-handle", Duration.ofSeconds(1), "echo", "x"))
                .withMessageContaining("Malformed");

        verify(localSandbox, never()).exec(anyString(), any(), any(String[].class));
    }

    @Test
    void exec_returnsStdoutAndExitFromAgent() {
        SandboxExecResult agentResult = new SandboxExecResult(0, "hello stdout", "", false);
        when(localSandbox.exec(eq(CONTAINER_ID), any(), eq("echo"), eq("hello"))).thenReturn(agentResult);

        SandboxExecResult result = client.exec(handle(), Duration.ofSeconds(30), "echo", "hello");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).isEqualTo("hello stdout");
    }

    @Test
    void copyIn_roundTripsTarBytesToAgent() {
        AtomicReference<byte[]> received = new AtomicReference<>();
        // Capture exactly the bytes the local sandbox is asked to extract: LocalTopic delivers by reference in-JVM (no serialization), so this asserts the client's bounded read
        // and the handler's ByteArrayInputStream re-wrap preserve the payload byte-for-byte.
        when(localSandbox.createSession(any())).thenReturn(CONTAINER_ID);
        doAnswer(invocation -> {
            InputStream in = invocation.getArgument(2);
            received.set(in.readAllBytes());
            return null;
        }).when(localSandbox).copyIn(eq(CONTAINER_ID), eq("/workspace"), any());

        byte[] tar = tarWithSingleFile("greeting.txt", "hi there");
        client.copyIn(handle(), "/workspace", new ByteArrayInputStream(tar));

        assertThat(received.get()).isEqualTo(tar);
    }

    @Test
    void copyOut_roundTripsTarBytesBackToCaller() throws Exception {
        byte[] tar = tarWithSingleFile("result.txt", "produced output");
        when(localSandbox.copyOut(eq(CONTAINER_ID), eq("/workspace/out"))).thenReturn(new TarArchiveInputStream(new ByteArrayInputStream(tar)));

        try (TarArchiveInputStream extracted = client.copyOut(handle(), "/workspace/out")) {
            TarArchiveEntry entry = extracted.getNextEntry();
            assertThat(entry.getName()).isEqualTo("result.txt");
            assertThat(new String(extracted.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("produced output");
        }
    }

    @Test
    void destroySession_forwardsEachCallToOwningAgent() {
        client.destroySession(handle());
        client.destroySession(handle());

        // The client always forwards: two distinct relay requests (different correlation ids) both reach the owning agent. (Idempotency of destroy itself lives in
        // InteractiveSandboxService, which is mocked here.)
        verify(localSandbox, times(2)).destroySession(CONTAINER_ID);
    }

    @Test
    void oversizeCopyInPayload_isRejectedBeforeReachingTheWire() {
        byte[] huge = new byte[RemoteInteractiveSandboxClient.MAX_PAYLOAD_BYTES + 1];

        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> client.copyIn(handle(), "/workspace", new ByteArrayInputStream(huge)))
                .withMessageContaining("relay limit");

        // The payload never reached the agent.
        verify(localSandbox, never()).copyIn(anyString(), anyString(), any());
    }

    @Test
    void requestForDifferentAgent_isIgnoredByHandler() {
        // A request that targets another agent must be dropped by the self-filter without touching this agent's local sandbox.
        SandboxOpRequest foreignRequest = SandboxOpRequest.destroy("corr-foreign", "some-other-agent", CONTAINER_ID);
        requestsTopic.publish(foreignRequest);

        await().during(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> verify(localSandbox, never()).destroySession(anyString()));
    }

    @Test
    void duplicateCorrelationId_isHandledOnlyOnce() {
        // A redelivered broadcast carries the same correlation id; the handler's idempotency guard must perform the operation exactly once.
        SandboxOpRequest request = SandboxOpRequest.destroy("corr-dup", AGENT_SHORT_NAME, CONTAINER_ID);
        requestsTopic.publish(request);
        requestsTopic.publish(request);

        await().during(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> verify(localSandbox, times(1)).destroySession(CONTAINER_ID));
    }

    @Test
    void execRunsOffTheTopicListenerThread() {
        // The single most safety-relevant relay invariant: Docker work must never run on the topic-listener (distributed event) thread. With the synchronous LocalTopic, the
        // listener
        // runs on the publishing caller thread, so capturing the thread the local exec runs on and asserting it differs (and is a named relay worker) directly proves the handoff.
        AtomicReference<Thread> execThread = new AtomicReference<>();
        when(localSandbox.exec(eq(CONTAINER_ID), any(), eq("echo"), eq("x"))).thenAnswer(invocation -> {
            execThread.set(Thread.currentThread());
            return new SandboxExecResult(0, "", "", false);
        });

        Thread callerThread = Thread.currentThread();
        client.exec(handle(), Duration.ofSeconds(5), "echo", "x");

        assertThat(execThread.get()).isNotSameAs(callerThread);
        assertThat(execThread.get().getName()).startsWith("hyperion-sandbox-relay-");
    }

    @Test
    void oversizeCopyOutArchive_isRejectedAsRelayLimit() {
        // The handler repacks the local copy-out stream and must fail closed when the repacked archive exceeds MAX_PAYLOAD_BYTES, so an oversized extraction cannot overwhelm the
        // messaging layer. The failure must surface to the caller as a relay-limit error, never as silently truncated bytes.
        byte[] oversizeTar = tarWithEntryOfSize("big.bin", RemoteInteractiveSandboxClient.MAX_PAYLOAD_BYTES + 1);
        when(localSandbox.copyOut(eq(CONTAINER_ID), eq("/workspace/out"))).thenReturn(new TarArchiveInputStream(new ByteArrayInputStream(oversizeTar)));

        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> client.copyOut(handle(), "/workspace/out")).withMessageContaining("relay limit");
    }

    @Test
    void secondCreate_atCapacity_isRefused() {
        try (RelayHarness harness = newHarness(1)) {
            when(harness.localSandbox().createSession(any())).thenReturn(CONTAINER_ID);
            // The first create holds the single permit; the second must be refused with a capacity failure rather than queued or silently starving CI.
            harness.client().createSession(new SandboxSessionSpec("some-image", null));

            assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> harness.client().createSession(new SandboxSessionSpec("some-image", null)))
                    .withMessageContaining("session capacity");
        }
    }

    @Test
    void pausedAgent_refusesNewSession() {
        when(queueProcessingService.isPaused()).thenReturn(true);

        // A paused (draining) agent must refuse a new session so pausing sheds generation load too, not just new CI build jobs — and it must not start a container.
        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> client.createSession(new SandboxSessionSpec("some-image", null))).withMessageContaining("paused");
        verify(localSandbox, never()).createSession(any());
    }

    @Test
    void generationHostingDisabled_whenCapIsZero_doesNotEvenSubscribe() {
        // Opt-in placement: an agent with the cap at 0 never hosts a session, so it must not subscribe to the request topic or allocate a worker pool.
        InteractiveSandboxRelayHandler disabled = new InteractiveSandboxRelayHandler(localSandbox, mock(DistributedDataAccessService.class), queueProcessingService,
                new GenerationSessionState(), mock(BuildAgentInformationService.class));
        ReflectionTestUtils.setField(disabled, "buildAgentShortName", AGENT_SHORT_NAME);
        ReflectionTestUtils.setField(disabled, "maxConcurrentSessions", 0);

        disabled.registerRequestListener();

        assertThat(ReflectionTestUtils.getField(disabled, "requestListenerId")).isNull();
        assertThat(ReflectionTestUtils.getField(disabled, "sessionPermits")).isNull();
        assertThat(ReflectionTestUtils.getField(disabled, "workerExecutor")).isNull();
    }

    @Test
    void redundantDestroy_releasesPermitExactlyOnce() {
        try (RelayHarness harness = newHarness(1)) {
            when(harness.localSandbox().createSession(any())).thenReturn(CONTAINER_ID);
            String handle = harness.client().createSession(new SandboxSessionSpec("some-image", null));

            // Two DESTROYs for the same owned session, each a distinct correlation id (so the idempotency dedup does NOT swallow the second): the permit is released exactly once,
            // gated by ownedSessions.remove. If the second destroy wrongly released a second permit, the cap-1 semaphore would then hold two permits and BOTH creates below
            // succeed.
            harness.client().destroySession(handle);
            harness.client().destroySession(handle);

            String reCreated = harness.client().createSession(new SandboxSessionSpec("some-image", null));
            assertThat(reCreated).isEqualTo(AGENT_SHORT_NAME + "::" + CONTAINER_ID);
            assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> harness.client().createSession(new SandboxSessionSpec("some-image", null)))
                    .withMessageContaining("session capacity");
        }
    }

    @Test
    void createSession_failsOverToTheNextAgent_whenTheFirstDeclinesAtCapacity() {
        // Two agents both ADVERTISE generation headroom (the info map is momentarily stale), but the first is actually at its permit cap when the CREATE lands. The client must
        // fail
        // over to the second agent and succeed there, not surface the first's capacity refusal or hang until the control-op timeout.
        LocalTopic<SandboxOpRequest> requests = new LocalTopic<>();
        LocalTopic<SandboxOpResponse> responses = new LocalTopic<>();
        LocalMap<String, byte[]> payloads = new LocalMap<>();
        DistributedDataAccessService clientAccess = mock(DistributedDataAccessService.class);
        when(clientAccess.getHyperionSandboxRequestsTopic()).thenReturn(requests);
        when(clientAccess.getHyperionSandboxResponsesTopic()).thenReturn(responses);
        when(clientAccess.getHyperionSandboxPayloads()).thenReturn(payloads);
        when(clientAccess.getBuildAgentInformation()).thenReturn(List.of(idleAgent("agent-1", 0, 4), idleAgent("agent-2", 0, 4)));
        RemoteInteractiveSandboxClient failoverClient = new RemoteInteractiveSandboxClient(clientAccess);
        failoverClient.registerResponseListener();

        InteractiveSandboxService sandbox1 = mock(InteractiveSandboxService.class);
        InteractiveSandboxRelayHandler handler1 = sharedHandler("agent-1", 1, requests, responses, payloads, sandbox1);
        // Drain agent-1's single permit so its CREATE handler declines with the capacity marker (as if a concurrent session already holds it).
        ((Semaphore) ReflectionTestUtils.getField(handler1, "sessionPermits")).acquireUninterruptibly();
        InteractiveSandboxService sandbox2 = mock(InteractiveSandboxService.class);
        when(sandbox2.createSession(any())).thenReturn("container-2");
        InteractiveSandboxRelayHandler handler2 = sharedHandler("agent-2", 1, requests, responses, payloads, sandbox2);

        try {
            String handle = failoverClient.createSession(new SandboxSessionSpec("some-image", null));
            assertThat(handle).isEqualTo("agent-2::container-2");
            verify(sandbox1, never()).createSession(any());
        }
        finally {
            handler1.shutdown();
            handler2.shutdown();
            failoverClient.removeResponseListener();
        }
    }

    /** A relay handler on caller-provided SHARED in-JVM topics, so a test can run several agents against one topic pair (e.g. to exercise create failover across agents). */
    private static InteractiveSandboxRelayHandler sharedHandler(String shortName, int maxSessions, LocalTopic<SandboxOpRequest> requests, LocalTopic<SandboxOpResponse> responses,
            LocalMap<String, byte[]> payloads, InteractiveSandboxService sandbox) {
        DistributedDataAccessService access = mock(DistributedDataAccessService.class);
        when(access.getHyperionSandboxRequestsTopic()).thenReturn(requests);
        when(access.getHyperionSandboxResponsesTopic()).thenReturn(responses);
        when(access.getHyperionSandboxPayloads()).thenReturn(payloads);
        InteractiveSandboxRelayHandler handler = new InteractiveSandboxRelayHandler(sandbox, access, mock(SharedQueueProcessingService.class), new GenerationSessionState(),
                mock(BuildAgentInformationService.class));
        ReflectionTestUtils.setField(handler, "buildAgentShortName", shortName);
        ReflectionTestUtils.setField(handler, "maxConcurrentSessions", maxSessions);
        handler.registerRequestListener();
        return handler;
    }

    /**
     * An isolated client+handler pair over its own in-JVM topics, so a test can exercise a specific session-capacity cap without the shared agent registered in {@link #setUp()}
     * also answering (both would self-filter on the same short name and race to respond).
     */
    private record RelayHarness(RemoteInteractiveSandboxClient client, InteractiveSandboxRelayHandler handler, InteractiveSandboxService localSandbox) implements AutoCloseable {

        @Override
        public void close() {
            handler.shutdown();
            client.removeResponseListener();
        }
    }

    private static RelayHarness newHarness(int maxConcurrentSessions) {
        LocalTopic<SandboxOpRequest> requests = new LocalTopic<>();
        LocalTopic<SandboxOpResponse> responses = new LocalTopic<>();

        DistributedDataAccessService clientAccess = mock(DistributedDataAccessService.class);
        when(clientAccess.getHyperionSandboxRequestsTopic()).thenReturn(requests);
        when(clientAccess.getHyperionSandboxResponsesTopic()).thenReturn(responses);
        when(clientAccess.getBuildAgentInformation()).thenReturn(List.of(idleAgent(AGENT_SHORT_NAME, 0, 4)));

        DistributedDataAccessService handlerAccess = mock(DistributedDataAccessService.class);
        when(handlerAccess.getHyperionSandboxRequestsTopic()).thenReturn(requests);
        when(handlerAccess.getHyperionSandboxResponsesTopic()).thenReturn(responses);

        InteractiveSandboxService localSandbox = mock(InteractiveSandboxService.class);
        RemoteInteractiveSandboxClient client = new RemoteInteractiveSandboxClient(clientAccess);
        client.registerResponseListener();

        InteractiveSandboxRelayHandler handler = new InteractiveSandboxRelayHandler(localSandbox, handlerAccess, mock(SharedQueueProcessingService.class),
                new GenerationSessionState(), mock(BuildAgentInformationService.class));
        ReflectionTestUtils.setField(handler, "buildAgentShortName", AGENT_SHORT_NAME);
        ReflectionTestUtils.setField(handler, "maxConcurrentSessions", maxConcurrentSessions);
        handler.registerRequestListener();
        return new RelayHarness(client, handler, localSandbox);
    }

    private static String handle() {
        return AGENT_SHORT_NAME + "::" + CONTAINER_ID;
    }

    private static BuildAgentInformation idleAgent(String name, int currentJobs, int maxJobs) {
        return new BuildAgentInformation(new BuildAgentDTO(name, "127.0.0.1:5701", name), maxJobs, currentJobs, List.of(), BuildAgentStatus.IDLE, "", null, 0, 0, 2);
    }

    private static byte[] tarWithSingleFile(String name, String content) {
        return tarWithBody(name, content.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] tarWithEntryOfSize(String name, int size) {
        return tarWithBody(name, new byte[size]);
    }

    private static byte[] tarWithBody(String name, byte[] body) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); TarArchiveOutputStream tar = new TarArchiveOutputStream(out)) {
            TarArchiveEntry entry = new TarArchiveEntry(name);
            entry.setSize(body.length);
            tar.putArchiveEntry(entry);
            tar.write(body);
            tar.closeArchiveEntry();
            tar.finish();
            return out.toByteArray();
        }
        catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
