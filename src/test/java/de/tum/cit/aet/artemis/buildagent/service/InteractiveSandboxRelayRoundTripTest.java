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
import de.tum.cit.aet.artemis.buildagent.dto.SandboxOp;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxOpRequest;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxOpResponse;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionSpec;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;
import de.tum.cit.aet.artemis.localci.service.distributed.api.topic.DistributedTopic;
import de.tum.cit.aet.artemis.localci.service.distributed.local.LocalTopic;

/**
 * Round-trip test for the multi-node interactive-sandbox relay: it wires a {@link RemoteInteractiveSandboxClient} (core node) to an {@link InteractiveSandboxRelayHandler} (build
 * agent) through the in-JVM {@link LocalTopic} backend of the {@link DistributedTopic} abstraction, with the agent's local {@link InteractiveSandboxService} mocked so no Docker is
 * needed.
 * <p>
 * It proves the contract the orchestrator relies on: createSession encodes affinity into the handle, exec returns the agent's stdout/exit, copy-in/copy-out round-trip the tar
 * bytes, destroy is idempotent, an oversize copy-in payload is rejected before it reaches the wire, and a request for a different agent short name is ignored by the handler.
 */
class InteractiveSandboxRelayRoundTripTest {

    private static final String AGENT_SHORT_NAME = "agent-1";

    private static final String CONTAINER_ID = "container-abc";

    private InteractiveSandboxService localSandbox;

    private RemoteInteractiveSandboxClient client;

    private InteractiveSandboxRelayHandler handler;

    private LocalTopic<SandboxOpRequest> requestsTopic;

    @BeforeEach
    void setUp() {
        // One shared request topic and one shared response topic stand in for the cluster-wide distributed topics; LocalTopic delivers synchronously in-JVM.
        requestsTopic = new LocalTopic<>();
        LocalTopic<SandboxOpResponse> responsesTopic = new LocalTopic<>();

        DistributedDataAccessService clientAccess = mock(DistributedDataAccessService.class);
        when(clientAccess.getHyperionSandboxRequestsTopic()).thenReturn(requestsTopic);
        when(clientAccess.getHyperionSandboxResponsesTopic()).thenReturn(responsesTopic);
        when(clientAccess.getBuildAgentInformation()).thenReturn(List.of(idleAgent(AGENT_SHORT_NAME, 0, 4)));

        DistributedDataAccessService handlerAccess = mock(DistributedDataAccessService.class);
        when(handlerAccess.getHyperionSandboxRequestsTopic()).thenReturn(requestsTopic);
        when(handlerAccess.getHyperionSandboxResponsesTopic()).thenReturn(responsesTopic);

        localSandbox = mock(InteractiveSandboxService.class);

        client = new RemoteInteractiveSandboxClient(clientAccess);
        client.registerResponseListener();

        handler = new InteractiveSandboxRelayHandler(localSandbox, handlerAccess);
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
        // Capture exactly the bytes the local sandbox is asked to extract, so we can assert the payload survived serialization unchanged.
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
    void destroySession_isIdempotent() {
        client.destroySession(handle());
        client.destroySession(handle());

        // Two distinct relay requests (different correlation ids) both reach the local sandbox; destroySession itself is documented safe-to-call-twice.
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
        SandboxOpRequest foreignRequest = new SandboxOpRequest("corr-foreign", "some-other-agent", SandboxOp.DESTROY, CONTAINER_ID, null, null, 0L, null, null);
        requestsTopic.publish(foreignRequest);

        await().during(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> verify(localSandbox, never()).destroySession(anyString()));
    }

    @Test
    void duplicateCorrelationId_isHandledOnlyOnce() {
        // A redelivered broadcast carries the same correlation id; the handler's idempotency guard must perform the operation exactly once.
        SandboxOpRequest request = new SandboxOpRequest("corr-dup", AGENT_SHORT_NAME, SandboxOp.DESTROY, CONTAINER_ID, null, null, 0L, null, null);
        requestsTopic.publish(request);
        requestsTopic.publish(request);

        await().during(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> verify(localSandbox, times(1)).destroySession(CONTAINER_ID));
    }

    private static String handle() {
        return AGENT_SHORT_NAME + "::" + CONTAINER_ID;
    }

    private static BuildAgentInformation idleAgent(String name, int currentJobs, int maxJobs) {
        return new BuildAgentInformation(new BuildAgentDTO(name, "127.0.0.1:5701", name), maxJobs, currentJobs, List.of(), BuildAgentStatus.IDLE, "", null, 0);
    }

    private static byte[] tarWithSingleFile(String name, String content) {
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
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
