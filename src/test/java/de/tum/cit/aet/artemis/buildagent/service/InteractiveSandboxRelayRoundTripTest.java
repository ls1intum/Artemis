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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
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
import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResult;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxOpRequest;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxOpResponse;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionContext;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionSpec;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;
import de.tum.cit.aet.artemis.localci.service.distributed.local.LocalMap;
import de.tum.cit.aet.artemis.localci.service.distributed.local.LocalTopic;

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

        assertThat(handle).isEqualTo(AGENT_SHORT_NAME + "::" + CONTAINER_ID);
    }

    @Test
    void createSessionWithoutObservabilityContextFailsBeforeDockerCreate() {
        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> client.createSession(new SandboxSessionSpec("some-image", null))).withMessageContaining("context");

        verify(localSandbox, never()).createSession(any());
    }

    @Test
    void createSession_doesNotFailOverAfterAnAmbiguousTimeout() {
        ReflectionTestUtils.setField(client, "controlOpTimeout", Duration.ofMillis(300));
        when(clientAccess.getBuildAgentInformation()).thenReturn(List.of(idleAgent("dead-agent-0", 0, 4), idleAgent(AGENT_SHORT_NAME, 0, 4)));
        when(localSandbox.createSession(any())).thenReturn(CONTAINER_ID);

        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> client.createSession(sessionSpec())).withMessageContaining("timed out");
        verify(localSandbox, never()).createSession(any());
    }

    @Test
    void createSession_throwsWhenNoAgentIsConfiguredToHostSessions() {
        when(clientAccess.getBuildAgentInformation())
                .thenReturn(List.of(new BuildAgentInformation(new BuildAgentDTO("no-gen", "127.0.0.1:5701", "no-gen"), 4, 0, List.of(), BuildAgentStatus.IDLE, "", null, 0, 0, 0)));

        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> client.createSession(sessionSpec()))
                .withMessageContaining("No build agent has a free Hyperion generation sandbox slot");
    }

    @Test
    void createSessionRequiresOneFreeSlot() {
        when(clientAccess.getBuildAgentInformation()).thenReturn(List
                .of(new BuildAgentInformation(new BuildAgentDTO(AGENT_SHORT_NAME, "127.0.0.1:5701", AGENT_SHORT_NAME), 4, 0, List.of(), BuildAgentStatus.IDLE, "", null, 0, 1, 2)));
        when(localSandbox.createSession(any())).thenReturn(CONTAINER_ID);

        assertThat(client.createSession(sessionSpec())).isEqualTo(AGENT_SHORT_NAME + "::" + CONTAINER_ID);
        verify(localSandbox).createSession(any());
    }

    @Test
    void listSessions_reportsMetadataAndPermitOwnershipAcrossTheSessionLifecycle() {
        Instant lastActivity = Instant.parse("2026-07-12T10:15:30Z");
        SandboxSessionContext context = new SandboxSessionContext("job-42", 123L, "Sorting exercise", 7L, "instructor", "GENERATE");
        SandboxSessionSpec spec = new SandboxSessionSpec("some-image", null, context);
        when(localSandbox.createSession(any())).thenReturn("generation-container");
        when(localSandbox.lastActivity(anyString())).thenReturn(java.util.Optional.of(lastActivity));

        String sessionHandle = client.createSession(spec);

        assertThat(client.listSessions(AGENT_SHORT_NAME)).singleElement().satisfies(session -> {
            assertThat(session.sessionId()).isEqualTo(sessionHandle);
            assertThat(session.jobId()).isEqualTo("job-42");
            assertThat(session.exerciseId()).isEqualTo(123L);
            assertThat(session.exerciseTitle()).isEqualTo("Sorting exercise");
            assertThat(session.courseId()).isEqualTo(7L);
            assertThat(session.userLogin()).isEqualTo("instructor");
            assertThat(session.mode()).isEqualTo("GENERATE");
            assertThat(session.startedAt()).isNotNull();
            assertThat(session.lastActivityAt()).isEqualTo(lastActivity);
        });

        client.destroySession(sessionHandle);
        assertThat(client.listSessions(AGENT_SHORT_NAME)).isEmpty();
    }

    @Test
    void malformedSessionHandle_failsClosedWithoutPublishing() {
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
        SandboxOpRequest foreignRequest = SandboxOpRequest.destroy("corr-foreign", "some-other-agent", CONTAINER_ID);
        requestsTopic.publish(foreignRequest);

        await().during(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> verify(localSandbox, never()).destroySession(anyString()));
    }

    @Test
    void duplicateCorrelationId_isHandledOnlyOnce() {
        createOwnedHandle();
        SandboxOpRequest request = SandboxOpRequest.destroy("corr-dup", AGENT_SHORT_NAME, CONTAINER_ID);
        requestsTopic.publish(request);
        requestsTopic.publish(request);

        await().during(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> verify(localSandbox, times(1)).destroySession(CONTAINER_ID));
    }

    @Test
    void execRunsOffTheTopicListenerThread() {
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
    void thirdCreate_atCapacity_isRefused() {
        try (RelayHarness harness = newHarness(2)) {
            when(harness.localSandbox().createSession(any())).thenReturn(CONTAINER_ID, CONTAINER_ID + "-2");
            assertThat(harness.client().createSession(sessionSpec())).isEqualTo(AGENT_SHORT_NAME + "::" + CONTAINER_ID);
            assertThat(harness.client().createSession(sessionSpec())).isEqualTo(AGENT_SHORT_NAME + "::" + CONTAINER_ID + "-2");

            assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> harness.client().createSession(sessionSpec()))
                    .withMessageContaining("generation sandbox slot capacity");
        }
    }

    @Test
    void createSucceedsWhenPublishingTheUpdatedSlotStateFails() {
        try (RelayHarness harness = newHarness(1)) {
            when(harness.localSandbox().createSession(any())).thenReturn(CONTAINER_ID);
            doThrow(new LocalCIException("state store unavailable")).when(harness.informationService()).updateGenerationSandboxSlotState(1, 1);

            assertThat(harness.client().createSession(sessionSpec())).isEqualTo(AGENT_SHORT_NAME + "::" + CONTAINER_ID);
            assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> harness.client().createSession(sessionSpec()))
                    .withMessageContaining("generation sandbox slot capacity");
        }
    }

    @Test
    void pausedAgent_refusesNewSession() {
        when(queueProcessingService.isPaused()).thenReturn(true);

        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> client.createSession(sessionSpec())).withMessageContaining("paused");
        verify(localSandbox, never()).createSession(any());
    }

    @Test
    void generationHostingDisabled_whenCapIsZero_doesNotEvenSubscribe() {
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
        try (RelayHarness harness = newHarness(1)) {
            when(harness.localSandbox().createSession(any())).thenReturn(CONTAINER_ID);
            String handle = harness.client().createSession(sessionSpec());

            harness.client().destroySession(handle);
            harness.client().destroySession(handle);

            String reCreated = harness.client().createSession(sessionSpec());
            assertThat(reCreated).isEqualTo(AGENT_SHORT_NAME + "::" + CONTAINER_ID);
            assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> harness.client().createSession(sessionSpec()))
                    .withMessageContaining("generation sandbox slot capacity");
        }
    }

    @Test
    void concurrentDestroy_removesTheContainerExactlyOnce() throws Exception {
        try (RelayHarness harness = newHarness(1)) {
            when(harness.localSandbox().createSession(any())).thenReturn(CONTAINER_ID);
            String handle = harness.client().createSession(sessionSpec());
            CountDownLatch destroyStarted = new CountDownLatch(1);
            CountDownLatch finishDestroy = new CountDownLatch(1);
            doAnswer(invocation -> {
                destroyStarted.countDown();
                finishDestroy.await(5, TimeUnit.SECONDS);
                return null;
            }).when(harness.localSandbox()).destroySession(CONTAINER_ID);

            CompletableFuture<Void> first = CompletableFuture.runAsync(() -> harness.client().destroySession(handle));
            assertThat(destroyStarted.await(2, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<Void> second = CompletableFuture.runAsync(() -> harness.client().destroySession(handle));

            assertThat(second).failsWithin(Duration.ofMillis(200)).withThrowableThat().isInstanceOf(java.util.concurrent.TimeoutException.class);
            finishDestroy.countDown();
            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
            verify(harness.localSandbox(), times(1)).destroySession(CONTAINER_ID);
        }
    }

    @Test
    void failedDestroy_keepsTheSandboxPermitReserved() {
        try (RelayHarness harness = newHarness(1)) {
            when(harness.localSandbox().createSession(any())).thenReturn(CONTAINER_ID);
            String handle = harness.client().createSession(sessionSpec());
            doThrow(new LocalCIException("remove failed")).when(harness.localSandbox()).destroySession(CONTAINER_ID);
            when(harness.localSandbox().sessionExists(CONTAINER_ID)).thenReturn(true);

            assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> harness.client().destroySession(handle)).withMessageContaining("remove failed");
            assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> harness.client().createSession(sessionSpec()))
                    .withMessageContaining("generation sandbox slot capacity");
        }
    }

    @Test
    void ambiguousDestroy_releasesThePermitWhenTheContainerIsAlreadyAbsent() {
        try (RelayHarness harness = newHarness(1)) {
            when(harness.localSandbox().createSession(any())).thenReturn(CONTAINER_ID);
            String handle = harness.client().createSession(sessionSpec());
            doThrow(new LocalCIException("Docker response lost")).when(harness.localSandbox()).destroySession(CONTAINER_ID);
            when(harness.localSandbox().sessionExists(CONTAINER_ID)).thenReturn(false);

            harness.client().destroySession(handle);

            assertThat(harness.client().createSession(sessionSpec())).isEqualTo(AGENT_SHORT_NAME + "::" + CONTAINER_ID);
        }
    }

    @Test
    void timedOutExec_releasesThePermitForTheContainerDestroyedByTheService() {
        try (RelayHarness harness = newHarness(1)) {
            when(harness.localSandbox().createSession(any())).thenReturn(CONTAINER_ID);
            when(harness.localSandbox().exec(eq(CONTAINER_ID), any(), any(String[].class))).thenReturn(new SandboxExecResult(-1, "", "", true));
            String handle = harness.client().createSession(sessionSpec());

            assertThat(harness.client().exec(handle, Duration.ofSeconds(1), "sleep", "10").timedOut()).isTrue();
            assertThat(harness.client().createSession(sessionSpec())).isEqualTo(handle);
        }
    }

    @Test
    void createSession_failsOverToTheNextAgent_whenTheFirstDeclinesAtCapacity() {
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
        // Drain both job permits so agent-1 declines with the capacity marker (as if two concurrent runs already hold them).
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
    void createSession_doesNotFailOverWhenTheFirstAgentReportsACreateFailure() {
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
            assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> failoverClient.createSession(sessionSpec())).withMessageContaining("momentarily overloaded");
            verify(sandbox1).createSession(any());
            verify(sandbox2, never()).createSession(any());
        }
        finally {
            handler1.shutdown();
            handler2.shutdown();
            failoverClient.removeResponseListener();
        }
    }

    @Test
    void createSession_failsFast_whenTheFirstHitsADeterministicDockerError() {
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
            verify(sandbox2, never()).createSession(any());
        }
        finally {
            handler1.shutdown();
            handler2.shutdown();
            failoverClient.removeResponseListener();
        }
    }

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

    private record RelayHarness(RemoteInteractiveSandboxClient client, InteractiveSandboxRelayHandler handler, InteractiveSandboxService localSandbox,
            BuildAgentInformationService informationService) implements AutoCloseable {

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
        BuildAgentInformationService informationService = mock(BuildAgentInformationService.class);
        RemoteInteractiveSandboxClient client = new RemoteInteractiveSandboxClient(clientAccess);
        client.registerResponseListener();

        InteractiveSandboxRelayHandler handler = new InteractiveSandboxRelayHandler(applicationContext(localSandbox), handlerAccess, mock(SharedQueueProcessingService.class),
                informationService);
        ReflectionTestUtils.setField(handler, "buildAgentShortName", AGENT_SHORT_NAME);
        ReflectionTestUtils.setField(handler, "maxGenerationSandboxSlots", maxGenerationSandboxSlots);
        handler.registerRequestListener();
        return new RelayHarness(client, handler, localSandbox, informationService);
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
