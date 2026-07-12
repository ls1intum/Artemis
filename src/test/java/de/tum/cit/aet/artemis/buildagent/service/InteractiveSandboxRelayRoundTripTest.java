package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
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
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentStatus;
import de.tum.cit.aet.artemis.buildagent.dto.GenerationSandboxSessionDTO;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResult;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxOpRequest;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxOpResponse;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionContext;
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
        handler = new InteractiveSandboxRelayHandler(applicationContext(localSandbox), handlerAccess, queueProcessingService, mock(BuildAgentInformationService.class));
        ReflectionTestUtils.setField(handler, "buildAgentShortName", AGENT_SHORT_NAME);
        ReflectionTestUtils.setField(handler, "maxGenerationSandboxSlots", 2);
        handler.registerRequestListener();
    }

    private static ApplicationContext applicationContext(InteractiveSandboxService sandbox) {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean(InteractiveSandboxService.class)).thenReturn(sandbox);
        return applicationContext;
    }

    @AfterEach
    void tearDown() {
        handler.shutdown();
        client.removeResponseListener();
    }

    @Test
    void springSelectsRemoteClientForHyperionOnCoLocatedCoreBuildAgentNodes_soCapacityAndDrainStayOnRelayPath() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles(PROFILE_CORE, PROFILE_LOCALCI, PROFILE_BUILDAGENT);
            context.registerBean(DistributedDataAccessService.class, () -> clientAccess);
            context.registerBean(de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration.class, () -> mock(de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration.class));
            context.register(RemoteInteractiveSandboxClient.class, InteractiveSandboxService.class);

            context.refresh();

            assertThat(context.getBean(InteractiveSandbox.class)).isInstanceOf(RemoteInteractiveSandboxClient.class);
            assertThat(context.getBean(InteractiveSandboxService.class)).isNotNull();
        }
    }

    @Test
    void createSession_encodesAgentAffinityIntoHandle() {
        when(localSandbox.createSession(any())).thenReturn(CONTAINER_ID);

        String handle = client.createSession(sessionSpec());

        // The handle pins the owning agent so every later op routes back to the same agent without any shared lookup state.
        assertThat(handle).isEqualTo(AGENT_SHORT_NAME + "::" + CONTAINER_ID);
    }

    @Test
    void createSessionWithoutObservabilityContextFailsBeforeDockerCreate() {
        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> client.createSession(new SandboxSessionSpec("some-image", null))).withMessageContaining("context");

        verify(localSandbox, never()).createSession(any());
    }

    @Test
    void createSession_failsOverToTheNextAgentWhenTheFirstIsUnreachable() {
        // Two candidate agents, both hosting-enabled and equally idle, but only AGENT_SHORT_NAME has a live handler. The first (listed first, so tried first) never answers and
        // times
        // out; the client must fail over to the second and place the session there rather than surfacing the timeout as a failure.
        ReflectionTestUtils.setField(client, "controlOpTimeout", Duration.ofMillis(300));
        when(clientAccess.getBuildAgentInformation()).thenReturn(List.of(idleAgent("dead-agent-0", 0, 4), idleAgent(AGENT_SHORT_NAME, 0, 4)));
        when(localSandbox.createSession(any())).thenReturn(CONTAINER_ID);

        String handle = client.createSession(sessionSpec());

        assertThat(handle).isEqualTo(AGENT_SHORT_NAME + "::" + CONTAINER_ID);
    }

    @Test
    void createSession_throwsWhenNoAgentIsConfiguredToHostSessions() {
        // Every agent has generation hosting disabled (max slots 0): none is a candidate, so placement fails fast with an actionable message instead of broadcasting a request
        // no
        // agent will ever answer.
        when(clientAccess.getBuildAgentInformation())
                .thenReturn(List.of(new BuildAgentInformation(new BuildAgentDTO("no-gen", "127.0.0.1:5701", "no-gen"), 4, 0, List.of(), BuildAgentStatus.IDLE, "", null, 0, 0, 0)));

        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> client.createSession(sessionSpec()))
                .withMessageContaining("No build agent has two free Hyperion generation sandbox slots");
    }

    @Test
    void createSessionRequiresTwoFreeSlotsForAuthoringAndVerificationSandboxes() {
        when(clientAccess.getBuildAgentInformation()).thenReturn(List
                .of(new BuildAgentInformation(new BuildAgentDTO(AGENT_SHORT_NAME, "127.0.0.1:5701", AGENT_SHORT_NAME), 4, 0, List.of(), BuildAgentStatus.IDLE, "", null, 0, 1, 2)));

        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> client.createSession(sessionSpec())).withMessageContaining("two free Hyperion generation sandbox slots");
        verify(localSandbox, never()).createSession(any());
    }

    @Test
    void createVerificationSession_usesTheLoopAgentReservedSlot() {
        when(localSandbox.createSession(any())).thenReturn("loop-container", "verify-container");

        String loopHandle = client.createSession(sessionSpec());
        String verifyHandle = client.createVerificationSession(sessionSpec(), loopHandle);

        assertThat(loopHandle).isEqualTo(AGENT_SHORT_NAME + "::loop-container");
        assertThat(verifyHandle).isEqualTo(AGENT_SHORT_NAME + "::verify-container");
        verify(localSandbox, times(2)).createSession(any());
    }

    @Test
    void listSessions_reportsMetadataAndPermitOwnershipAcrossTheSessionLifecycle() {
        Instant lastActivity = Instant.parse("2026-07-12T10:15:30Z");
        SandboxSessionContext context = new SandboxSessionContext("job-42", 123L, "Sorting exercise", 7L, "instructor", "GENERATE");
        SandboxSessionSpec spec = new SandboxSessionSpec("some-image", null, context);
        when(localSandbox.createSession(any())).thenReturn("loop-container", "verify-container");
        when(localSandbox.lastActivity(anyString())).thenReturn(java.util.Optional.of(lastActivity));

        String loopHandle = client.createSession(spec);

        assertThat(client.listSessions(AGENT_SHORT_NAME)).singleElement().satisfies(session -> {
            assertThat(session.sessionId()).isEqualTo(loopHandle);
            assertThat(session.role()).isEqualTo(GenerationSandboxSessionDTO.Role.AUTHORING);
            assertThat(session.jobId()).isEqualTo("job-42");
            assertThat(session.exerciseId()).isEqualTo(123L);
            assertThat(session.exerciseTitle()).isEqualTo("Sorting exercise");
            assertThat(session.courseId()).isEqualTo(7L);
            assertThat(session.userLogin()).isEqualTo("instructor");
            assertThat(session.mode()).isEqualTo("GENERATE");
            assertThat(session.startedAt()).isNotNull();
            assertThat(session.lastActivityAt()).isEqualTo(lastActivity);
            assertThat(session.reservedSlots()).isEqualTo(2);
        });

        String verifyHandle = client.createVerificationSession(spec, loopHandle);
        assertThat(client.listSessions(AGENT_SHORT_NAME))
                .extracting(GenerationSandboxSessionDTO::sessionId, GenerationSandboxSessionDTO::role, GenerationSandboxSessionDTO::reservedSlots)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple(loopHandle, GenerationSandboxSessionDTO.Role.AUTHORING, 1),
                        org.assertj.core.groups.Tuple.tuple(verifyHandle, GenerationSandboxSessionDTO.Role.VERIFICATION, 1));

        client.destroySession(verifyHandle);
        client.destroySession(loopHandle);
        assertThat(client.listSessions(AGENT_SHORT_NAME)).isEmpty();
    }

    @Test
    void createVerificationSession_requiresAnOwnedAuthoringSandboxReservation() {
        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> client.createVerificationSession(sessionSpec(), AGENT_SHORT_NAME + "::missing-loop-container"))
                .withMessageContaining("owned authoring sandbox");

        verify(localSandbox, never()).createSession(any());
    }

    @Test
    void createVerificationSession_consumesTheReservedVerificationSlotOnlyOnce() {
        when(localSandbox.createSession(any())).thenReturn("loop-container", "verify-container", "duplicate-verify-container");

        String loopHandle = client.createSession(sessionSpec());
        String verifyHandle = client.createVerificationSession(sessionSpec(), loopHandle);

        assertThat(verifyHandle).isEqualTo(AGENT_SHORT_NAME + "::verify-container");
        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> client.createVerificationSession(sessionSpec(), loopHandle))
                .withMessageContaining("owned authoring sandbox");
        verify(localSandbox, times(2)).createSession(any());
    }

    @Test
    void createVerificationSession_canReuseTheReservedSlotAfterDestroyingThePreviousVerifier() {
        when(localSandbox.createSession(any())).thenReturn("loop-container", "verify-container-1", "verify-container-2");

        String loopHandle = client.createSession(sessionSpec());
        String firstVerifyHandle = client.createVerificationSession(sessionSpec(), loopHandle);
        client.destroySession(firstVerifyHandle);
        String secondVerifyHandle = client.createVerificationSession(sessionSpec(), loopHandle);

        assertThat(secondVerifyHandle).isEqualTo(AGENT_SHORT_NAME + "::verify-container-2");
        verify(localSandbox, times(3)).createSession(any());
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

        SandboxExecResult result = client.exec(createOwnedHandle(), Duration.ofSeconds(30), "echo", "hello");

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
        client.copyIn(createOwnedHandle(), "/workspace", new ByteArrayInputStream(tar));

        assertThat(received.get()).isEqualTo(tar);
    }

    @Test
    void copyOut_roundTripsTarBytesBackToCaller() throws Exception {
        byte[] tar = tarWithSingleFile("result.txt", "produced output");
        when(localSandbox.copyOut(eq(CONTAINER_ID), eq("/workspace/out"))).thenReturn(new TarArchiveInputStream(new ByteArrayInputStream(tar)));

        try (TarArchiveInputStream extracted = client.copyOut(createOwnedHandle(), "/workspace/out")) {
            TarArchiveEntry entry = extracted.getNextEntry();
            assertThat(entry.getName()).isEqualTo("result.txt");
            assertThat(new String(extracted.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("produced output");
        }
    }

    @Test
    void copyOut_rejectsLinkedEntriesInsteadOfNormalizingThemToRegularFiles() throws Exception {
        byte[] tar = tarWithSymlink("leak", "/etc/passwd");
        when(localSandbox.copyOut(eq(CONTAINER_ID), eq("/workspace/out"))).thenReturn(new TarArchiveInputStream(new ByteArrayInputStream(tar)));

        String handle = createOwnedHandle();
        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> client.copyOut(handle, "/workspace/out")).withMessageContaining("linked");
    }

    @Test
    void destroySession_ignoresAnUnownedContainerId() {
        client.destroySession(handle());

        verify(localSandbox, never()).destroySession(CONTAINER_ID);
    }

    @Test
    void exec_rejectsAnUnownedContainerId() {
        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> client.exec(handle(), Duration.ofSeconds(1), "echo", "unsafe")).withMessageContaining("not owned");

        verify(localSandbox, never()).exec(anyString(), any(), any(String[].class));
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
        createOwnedHandle();
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
        client.exec(createOwnedHandle(), Duration.ofSeconds(5), "echo", "x");

        assertThat(execThread.get()).isNotSameAs(callerThread);
        assertThat(execThread.get().getName()).startsWith("hyperion-sandbox-relay-");
    }

    @Test
    void oversizeCopyOutArchive_isRejectedAsRelayLimit() {
        // The handler repacks the local copy-out stream and must fail closed when the repacked archive exceeds MAX_PAYLOAD_BYTES, so an oversized extraction cannot overwhelm the
        // messaging layer. The failure must surface to the caller as a relay-limit error, never as silently truncated bytes.
        byte[] oversizeTar = tarWithEntryOfSize("big.bin", RemoteInteractiveSandboxClient.MAX_PAYLOAD_BYTES + 1);
        when(localSandbox.copyOut(eq(CONTAINER_ID), eq("/workspace/out"))).thenReturn(new TarArchiveInputStream(new ByteArrayInputStream(oversizeTar)));

        String handle = createOwnedHandle();
        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> client.copyOut(handle, "/workspace/out")).withMessageContaining("relay limit");
    }

    @Test
    void copyOut_rejectsArchiveWhoseHeadersExceedRelayLimit() {
        byte[] headerOnlyTar = tarWithEmptyDirectories((RemoteInteractiveSandboxClient.MAX_PAYLOAD_BYTES / 512) + 4);
        when(localSandbox.copyOut(eq(CONTAINER_ID), eq("/workspace/out"))).thenReturn(new TarArchiveInputStream(new ByteArrayInputStream(headerOnlyTar)));

        String handle = createOwnedHandle();
        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> client.copyOut(handle, "/workspace/out")).withMessageContaining("relay limit");
    }

    @Test
    void secondCreate_atCapacity_isRefused() {
        try (RelayHarness harness = newHarness(2)) {
            when(harness.localSandbox().createSession(any())).thenReturn(CONTAINER_ID);
            // The first generation loop reserves both its authoring and verification sandbox slots; the second must be refused with a capacity failure rather than self-starving
            // later.
            harness.client().createSession(sessionSpec());

            assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> harness.client().createSession(sessionSpec()))
                    .withMessageContaining("generation sandbox slot capacity");
        }
    }

    @Test
    void pausedAgent_refusesNewSession() {
        when(queueProcessingService.isPaused()).thenReturn(true);

        // A paused (draining) agent must refuse a new session so pausing sheds generation load too, not just new CI build jobs — and it must not start a container.
        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> client.createSession(sessionSpec())).withMessageContaining("paused");
        verify(localSandbox, never()).createSession(any());
    }

    @Test
    void generationHostingDisabled_whenCapIsZero_doesNotEvenSubscribe() {
        // Opt-in placement: an agent with the cap at 0 never hosts a sandbox, so it must not subscribe to the request topic or allocate a worker pool.
        InteractiveSandboxRelayHandler disabled = new InteractiveSandboxRelayHandler(applicationContext(localSandbox), mock(DistributedDataAccessService.class),
                queueProcessingService, mock(BuildAgentInformationService.class));
        ReflectionTestUtils.setField(disabled, "buildAgentShortName", AGENT_SHORT_NAME);
        ReflectionTestUtils.setField(disabled, "maxGenerationSandboxSlots", 0);

        disabled.registerRequestListener();

        assertThat(ReflectionTestUtils.getField(disabled, "requestListenerId")).isNull();
        assertThat(ReflectionTestUtils.getField(disabled, "sandboxSlotPermits")).isNull();
        assertThat(ReflectionTestUtils.getField(disabled, "workerExecutor")).isNull();
    }

    @Test
    void startupCleanupFailure_disablesHostingWithoutSubscribing() {
        InteractiveSandboxService sandbox = mock(InteractiveSandboxService.class);
        when(sandbox.removeSessionsFromPreviousProcess()).thenThrow(new LocalCIException("cleanup failed"));
        BuildAgentInformationService informationService = mock(BuildAgentInformationService.class);
        InteractiveSandboxRelayHandler disabled = new InteractiveSandboxRelayHandler(applicationContext(sandbox), mock(DistributedDataAccessService.class), queueProcessingService,
                informationService);
        ReflectionTestUtils.setField(disabled, "buildAgentShortName", AGENT_SHORT_NAME);
        ReflectionTestUtils.setField(disabled, "maxGenerationSandboxSlots", 2);

        disabled.registerRequestListener();

        assertThat(ReflectionTestUtils.getField(disabled, "requestListenerId")).isNull();
        assertThat(ReflectionTestUtils.getField(disabled, "workerExecutor")).isNull();
        verify(informationService).updateGenerationSandboxSlotState(2, 2);
    }

    @Test
    void redundantDestroy_releasesPermitExactlyOnce() {
        try (RelayHarness harness = newHarness(2)) {
            when(harness.localSandbox().createSession(any())).thenReturn(CONTAINER_ID);
            String handle = harness.client().createSession(sessionSpec());

            // Two DESTROYs for the same owned session, each a distinct correlation id (so the idempotency dedup does NOT swallow the second): the permit is released exactly once,
            // gated by ownedSandboxSlotPermits.remove. If the second destroy wrongly released permits again, both creates below would succeed.
            harness.client().destroySession(handle);
            harness.client().destroySession(handle);

            String reCreated = harness.client().createSession(sessionSpec());
            assertThat(reCreated).isEqualTo(AGENT_SHORT_NAME + "::" + CONTAINER_ID);
            assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> harness.client().createSession(sessionSpec()))
                    .withMessageContaining("generation sandbox slot capacity");
        }
    }

    @Test
    void failedDestroy_keepsTheSandboxPermitReserved() {
        try (RelayHarness harness = newHarness(2)) {
            when(harness.localSandbox().createSession(any())).thenReturn(CONTAINER_ID);
            String handle = harness.client().createSession(sessionSpec());
            doThrow(new LocalCIException("remove failed")).when(harness.localSandbox()).destroySession(CONTAINER_ID);

            assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> harness.client().destroySession(handle)).withMessageContaining("remove failed");
            assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> harness.client().createSession(sessionSpec()))
                    .withMessageContaining("generation sandbox slot capacity");
        }
    }

    @Test
    void timedOutExec_releasesThePermitForTheContainerDestroyedByTheService() {
        try (RelayHarness harness = newHarness(2)) {
            when(harness.localSandbox().createSession(any())).thenReturn(CONTAINER_ID);
            when(harness.localSandbox().exec(eq(CONTAINER_ID), any(), any(String[].class))).thenReturn(new SandboxExecResult(-1, "", "", true));
            String handle = harness.client().createSession(sessionSpec());

            assertThat(harness.client().exec(handle, Duration.ofSeconds(1), "sleep", "10").timedOut()).isTrue();
            assertThat(harness.client().createSession(sessionSpec())).isEqualTo(handle);
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
        InteractiveSandboxRelayHandler handler1 = sharedHandler("agent-1", 2, requests, responses, payloads, sandbox1);
        // Drain agent-1's loop+verifier permits so its CREATE handler declines with the capacity marker (as if a concurrent run already holds them).
        ((Semaphore) ReflectionTestUtils.getField(handler1, "sandboxSlotPermits")).acquireUninterruptibly(2);
        InteractiveSandboxService sandbox2 = mock(InteractiveSandboxService.class);
        when(sandbox2.createSession(any())).thenReturn("container-2");
        InteractiveSandboxRelayHandler handler2 = sharedHandler("agent-2", 2, requests, responses, payloads, sandbox2);

        try {
            String handle = failoverClient.createSession(sessionSpec());
            assertThat(handle).isEqualTo("agent-2::container-2");
            verify(sandbox1, never()).createSession(any());
        }
        finally {
            handler1.shutdown();
            handler2.shutdown();
            failoverClient.removeResponseListener();
        }
    }

    @Test
    void createSession_failsOverToTheNextAgent_whenTheFirstHitsATransientDockerError() {
        // agent-1's Docker throws a transient/agent-local error (a 5xx daemon hiccup) when the container is created; another healthy agent may well succeed, so the handler tags
        // the
        // failure retryable and the client fails over to agent-2 and places the session there — rather than aborting the whole (expensive) generation on one agent's blip.
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
        when(sandbox1.createSession(any())).thenThrow(new DockerException("daemon momentarily overloaded", 500));
        InteractiveSandboxRelayHandler handler1 = sharedHandler("agent-1", 2, requests, responses, payloads, sandbox1);
        InteractiveSandboxService sandbox2 = mock(InteractiveSandboxService.class);
        when(sandbox2.createSession(any())).thenReturn("container-2");
        InteractiveSandboxRelayHandler handler2 = sharedHandler("agent-2", 2, requests, responses, payloads, sandbox2);

        try {
            String handle = failoverClient.createSession(sessionSpec());
            assertThat(handle).isEqualTo("agent-2::container-2");
            // agent-1 really attempted the create (unlike the capacity-decline case, where the guard short-circuits before createSession) — this proves a transient runtime
            // failure,
            // not just a pre-create refusal, drives the failover.
            verify(sandbox1).createSession(any());
        }
        finally {
            handler1.shutdown();
            handler2.shutdown();
            failoverClient.removeResponseListener();
        }
    }

    @Test
    void createSession_failsFast_whenTheFirstHitsADeterministicDockerError() {
        // agent-1's Docker rejects the create with a deterministic 4xx (a missing image, 404) — the same spec would fail identically on every candidate, so the client must surface
        // it
        // immediately rather than storm agent-2 with a retry doomed to fail the same way.
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
        when(sandbox1.createSession(any())).thenThrow(new NotFoundException("no such image: bogus-image"));
        InteractiveSandboxRelayHandler handler1 = sharedHandler("agent-1", 2, requests, responses, payloads, sandbox1);
        InteractiveSandboxService sandbox2 = mock(InteractiveSandboxService.class);
        InteractiveSandboxRelayHandler handler2 = sharedHandler("agent-2", 2, requests, responses, payloads, sandbox2);

        try {
            assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> failoverClient.createSession(new SandboxSessionSpec("bogus-image", null, sessionContext())))
                    .withMessageContaining("no such image");
            // The deterministic failure aborted the create on the first agent; agent-2 was never asked to try the doomed spec.
            verify(sandbox2, never()).createSession(any());
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
        InteractiveSandboxRelayHandler handler = new InteractiveSandboxRelayHandler(applicationContext(sandbox), access, mock(SharedQueueProcessingService.class),
                mock(BuildAgentInformationService.class));
        ReflectionTestUtils.setField(handler, "buildAgentShortName", shortName);
        ReflectionTestUtils.setField(handler, "maxGenerationSandboxSlots", maxSessions);
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

    private static RelayHarness newHarness(int maxGenerationSandboxSlots) {
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

        InteractiveSandboxRelayHandler handler = new InteractiveSandboxRelayHandler(applicationContext(localSandbox), handlerAccess, mock(SharedQueueProcessingService.class),
                mock(BuildAgentInformationService.class));
        ReflectionTestUtils.setField(handler, "buildAgentShortName", AGENT_SHORT_NAME);
        ReflectionTestUtils.setField(handler, "maxGenerationSandboxSlots", maxGenerationSandboxSlots);
        handler.registerRequestListener();
        return new RelayHarness(client, handler, localSandbox);
    }

    private static SandboxSessionSpec sessionSpec() {
        return new SandboxSessionSpec("some-image", null, sessionContext());
    }

    private static SandboxSessionContext sessionContext() {
        return new SandboxSessionContext("job", 1L, "Sorting exercise", 2L, "instructor", "GENERATE");
    }

    private static String handle() {
        return AGENT_SHORT_NAME + "::" + CONTAINER_ID;
    }

    private String createOwnedHandle() {
        when(localSandbox.createSession(any())).thenReturn(CONTAINER_ID);
        return client.createSession(sessionSpec());
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

    private static byte[] tarWithSymlink(String name, String target) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); TarArchiveOutputStream tar = new TarArchiveOutputStream(out)) {
            TarArchiveEntry entry = new TarArchiveEntry(name, TarArchiveEntry.LF_SYMLINK);
            entry.setLinkName(target);
            tar.putArchiveEntry(entry);
            tar.closeArchiveEntry();
            tar.finish();
            return out.toByteArray();
        }
        catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] tarWithEmptyDirectories(int count) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); TarArchiveOutputStream tar = new TarArchiveOutputStream(out)) {
            for (int i = 0; i < count; i++) {
                TarArchiveEntry entry = new TarArchiveEntry("d" + i + "/");
                tar.putArchiveEntry(entry);
                tar.closeArchiveEntry();
            }
            tar.finish();
            return out.toByteArray();
        }
        catch (Exception e) {
            throw new IllegalStateException(e);
        }
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
