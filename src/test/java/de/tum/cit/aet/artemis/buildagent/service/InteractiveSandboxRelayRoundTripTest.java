package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
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
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentStatus;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResultDTO;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxOp;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxOpRequestDTO;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxOpResponseDTO;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionContextDTO;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionSpecDTO;
import de.tum.cit.aet.artemis.core.service.distributed.api.topic.DistributedTopic;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalMap;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalTopic;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;

class InteractiveSandboxRelayRoundTripTest {

    private static final String AGENT_SHORT_NAME = "agent-1";

    private static final String CONTAINER_ID = "container-abc";

    private InteractiveSandboxService localSandbox;

    private RemoteInteractiveSandboxClient client;

    private InteractiveSandboxRelayHandler handler;

    private SharedQueueProcessingService queueProcessingService;

    private LocalTopic<SandboxOpRequestDTO> requestsTopic;

    private LocalTopic<SandboxOpResponseDTO> responsesTopic;

    private DistributedDataAccessService clientAccess;

    private DistributedDataAccessService handlerAccess;

    private LocalMap<String, byte[]> payloads;

    @BeforeEach
    void setUp() {
        // One shared request topic and one shared response topic stand in for the cluster-wide distributed topics; LocalTopic delivers synchronously in-JVM. The keyed payload map
        // is shared the same way: the sender stages the bytes under the correlation id and removes them after the terminal response (copy-in: client→agent, copy-out:
        // agent→client).
        requestsTopic = new LocalTopic<>();
        responsesTopic = new LocalTopic<>();
        payloads = new LocalMap<>();

        clientAccess = mock(DistributedDataAccessService.class);
        when(clientAccess.getHyperionSandboxRequestsTopic()).thenReturn(requestsTopic);
        when(clientAccess.getHyperionSandboxResponsesTopic()).thenReturn(responsesTopic);
        when(clientAccess.getHyperionSandboxPayloads()).thenReturn(payloads);
        when(clientAccess.getBuildAgentInformation()).thenReturn(List.of(idleAgent(AGENT_SHORT_NAME, 0, 4)));

        handlerAccess = mock(DistributedDataAccessService.class);
        when(handlerAccess.getHyperionSandboxRequestsTopic()).thenReturn(requestsTopic);
        when(handlerAccess.getHyperionSandboxResponsesTopic()).thenReturn(responsesTopic);
        when(handlerAccess.getHyperionSandboxPayloads()).thenReturn(payloads);
        when(handlerAccess.isConnectedToCluster()).thenReturn(true);

        localSandbox = mock(InteractiveSandboxService.class);

        client = new RemoteInteractiveSandboxClient(clientAccess);
        client.registerResponseListener();

        queueProcessingService = availableQueueProcessingService();
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
            context.registerBean(BuildAgentDockerService.class, () -> mock(BuildAgentDockerService.class));
            context.register(RemoteInteractiveSandboxClient.class, InteractiveSandboxService.class);

            context.refresh();

            assertThat(context.getBean(InteractiveSandbox.class)).isInstanceOf(RemoteInteractiveSandboxClient.class);
            assertThat(context.getBean(InteractiveSandboxService.class)).isNotNull();
        }
    }

    @Test
    void initializeHosting_publishesTheNewSlotStateInsteadOfWaitingForAnUnrelatedRefresh() {
        // The periodic heartbeat only writes this agent's map entry while it is absent, so setting the local slot state without publishing left an agent that had just gained
        // capacity advertising zero — and core nodes rejecting every generation request — until something unrelated happened to republish it.
        BuildAgentInformationService buildAgentInformationService = mock(BuildAgentInformationService.class);
        InteractiveSandboxRelayHandler freshHandler = new InteractiveSandboxRelayHandler(applicationContext(localSandbox), handlerAccess, queueProcessingService,
                buildAgentInformationService);
        ReflectionTestUtils.setField(freshHandler, "buildAgentShortName", "agent-fresh");
        ReflectionTestUtils.setField(freshHandler, "maxGenerationSandboxSlots", 3);

        try {
            freshHandler.registerRequestListener();

            InOrder order = inOrder(buildAgentInformationService);
            order.verify(buildAgentInformationService).updateGenerationSandboxSlotState(0, 3);
            order.verify(buildAgentInformationService).refreshLocalBuildAgentInformationPreservingFailures(false);
        }
        finally {
            freshHandler.shutdown();
        }
    }

    @Test
    void createSession_reportsTheObservedFleetCapacityAndNamesTheOptInProperty() {
        // At the default of zero slots the run simply fails; the message and the warning it accompanies are the only way an administrator learns which property to set.
        when(clientAccess.getBuildAgentInformation())
                .thenReturn(List.of(new BuildAgentInformation(new BuildAgentDTO("no-gen", "127.0.0.1:5701", "no-gen"), 4, 0, List.of(), BuildAgentStatus.IDLE, "", null, 0, 0, 0)));

        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> client.createSession(sessionSpec()))
                .withMessageContaining("artemis.continuous-integration.build-agent.max-generation-sandbox-slots");

        RemoteInteractiveSandboxClient.GenerationSandboxCapacity capacity = client.generationSandboxCapacity();
        assertThat(capacity.reachableAgents()).isEqualTo(1);
        assertThat(capacity.hostingAgents()).isZero();
        assertThat(capacity.noAgentAdvertisesCapacity()).isTrue();
        assertThat(capacity.freeSlots()).isZero();
    }

    @Test
    void generationSandboxCapacity_distinguishesAFullFleetFromOneThatHostsNothing() {
        when(clientAccess.getBuildAgentInformation())
                .thenReturn(List.of(new BuildAgentInformation(new BuildAgentDTO("busy", "127.0.0.1:5701", "busy"), 4, 0, List.of(), BuildAgentStatus.ACTIVE, "", null, 0, 2, 2),
                        new BuildAgentInformation(new BuildAgentDTO("no-gen", "127.0.0.1:5702", "no-gen"), 4, 0, List.of(), BuildAgentStatus.IDLE, "", null, 0, 0, 0),
                        new BuildAgentInformation(new BuildAgentDTO("offline", "127.0.0.1:5703", "offline"), 4, 0, List.of(), BuildAgentStatus.PAUSED, "", null, 0, 0, 8)));

        RemoteInteractiveSandboxClient.GenerationSandboxCapacity capacity = client.generationSandboxCapacity();

        // A paused agent is not reachable capacity, so its eight advertised slots must not make an unrunnable fleet look configured.
        assertThat(capacity.reachableAgents()).isEqualTo(2);
        assertThat(capacity.hostingAgents()).isEqualTo(1);
        assertThat(capacity.totalSlots()).isEqualTo(2);
        assertThat(capacity.reservedSlots()).isEqualTo(2);
        assertThat(capacity.freeSlots()).isZero();
        // Busy is a load state that resolves on its own; "nothing hosts generation" is a configuration state that never does.
        assertThat(capacity.noAgentAdvertisesCapacity()).isFalse();
        assertThat(client.hasAvailableGenerationSandboxSlot()).isFalse();
    }

    @Test
    void createSession_encodesAgentAffinityIntoHandle() {
        when(localSandbox.createSession(any())).thenReturn(CONTAINER_ID);

        String handle = client.createSession(sessionSpec());

        assertThat(handle).isEqualTo(AGENT_SHORT_NAME + "::" + CONTAINER_ID);
    }

    @Test
    void createSessionWithoutObservabilityContextFailsBeforeDockerCreate() {
        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> client.createSession(new SandboxSessionSpecDTO("some-image", null))).withMessageContaining("context");

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
    void closedClientRejectsNewOperationsBeforePublishingThem() {
        ReflectionTestUtils.setField(client, "controlOpTimeout", Duration.ofMillis(100));
        client.removeResponseListener();

        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> client.createSession(sessionSpec())).withMessageContaining("shutting down");
        verify(localSandbox, never()).createSession(any());
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
        SandboxSessionContextDTO context = new SandboxSessionContextDTO("job-42", 123L, "Sorting exercise", 7L, "instructor", "GENERATE");
        SandboxSessionSpecDTO spec = new SandboxSessionSpecDTO("some-image", null, context);
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
        SandboxExecResultDTO agentResult = new SandboxExecResultDTO(0, "hello stdout", "", false);
        when(localSandbox.exec(eq(CONTAINER_ID), any(), eq("echo"), eq("hello"))).thenReturn(agentResult);

        SandboxExecResultDTO result = client.exec(createOwnedHandle(), Duration.ofSeconds(30), "echo", "hello");

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
    void copyFailureAfterTheContainerWasInvalidatedReleasesItsSlot() {
        try (RelayHarness harness = newHarness(1)) {
            String replacementContainer = "replacement-container";
            when(harness.localSandbox().createSession(any())).thenReturn(CONTAINER_ID, replacementContainer);
            String handle = harness.client().createSession(sessionSpec("failed-copy-job"));
            doThrow(new LocalCIException("copy timed out")).when(harness.localSandbox()).copyIn(eq(CONTAINER_ID), eq("/workspace"), any());
            when(harness.localSandbox().sessionExists(CONTAINER_ID)).thenReturn(false);

            assertThatExceptionOfType(LocalCIException.class)
                    .isThrownBy(() -> harness.client().copyIn(handle, "/workspace", new ByteArrayInputStream(tarWithSingleFile("file.txt", "content"))))
                    .withMessageContaining("copy timed out");

            assertThat(harness.client().createSession(sessionSpec("replacement-job"))).isEqualTo(AGENT_SHORT_NAME + "::" + replacementContainer);
        }
    }

    @Test
    void resetSessionRestartsTheOwnedContainerThroughTheRelay() {
        String handle = createOwnedHandle();

        client.resetSession(handle);

        verify(localSandbox).resetSession(CONTAINER_ID);
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
    void shutdown_destroysOwnedSandboxSessions() {
        createOwnedHandle();
        clearInvocations(localSandbox);

        handler.shutdown();

        verify(localSandbox).removeSessionsForCurrentAgent();
    }

    @Test
    void sameHandlerCreateSessionsUseAvailableSlotsConcurrently() throws Exception {
        try (RelayHarness harness = newHarness(2)) {
            CountDownLatch createsStarted = new CountDownLatch(2);
            CountDownLatch releaseCreates = new CountDownLatch(1);
            AtomicInteger containerSequence = new AtomicInteger();
            when(harness.localSandbox().createSession(any())).thenAnswer(invocation -> {
                createsStarted.countDown();
                releaseCreates.await(5, TimeUnit.SECONDS);
                return "container-" + containerSequence.incrementAndGet();
            });

            CompletableFuture<String> first = CompletableFuture.supplyAsync(() -> harness.client().createSession(sessionSpec("job-1")));
            CompletableFuture<String> second = CompletableFuture.supplyAsync(() -> harness.client().createSession(sessionSpec("job-2")));
            try {
                assertThat(createsStarted.await(2, TimeUnit.SECONDS)).isTrue();
            }
            finally {
                releaseCreates.countDown();
            }

            assertThat(first.get(5, TimeUnit.SECONDS)).startsWith(AGENT_SHORT_NAME + "::container-");
            assertThat(second.get(5, TimeUnit.SECONDS)).startsWith(AGENT_SHORT_NAME + "::container-");
        }
    }

    @Test
    void sameHandlerConcurrentDuplicateCreatesForTheSameJobReuseOneContainerAndSlot() throws Exception {
        try (RelayHarness harness = newHarness(2)) {
            CountDownLatch createStarted = new CountDownLatch(1);
            CountDownLatch finishCreate = new CountDownLatch(1);
            when(harness.localSandbox().createSession(any())).thenAnswer(invocation -> {
                createStarted.countDown();
                finishCreate.await(5, TimeUnit.SECONDS);
                return CONTAINER_ID;
            }).thenReturn(CONTAINER_ID + "-2");

            CompletableFuture<String> firstCreate = CompletableFuture.supplyAsync(() -> harness.client().createSession(sessionSpec("job-1")));
            assertThat(createStarted.await(2, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<String> duplicateCreate = CompletableFuture.supplyAsync(() -> harness.client().createSession(sessionSpec("job-1")));
            finishCreate.countDown();

            String first = firstCreate.get(5, TimeUnit.SECONDS);
            String duplicate = duplicateCreate.get(5, TimeUnit.SECONDS);
            String otherJob = harness.client().createSession(sessionSpec("job-2"));

            assertThat(duplicate).isEqualTo(first);
            assertThat(otherJob).isEqualTo(AGENT_SHORT_NAME + "::" + CONTAINER_ID + "-2");
            verify(harness.localSandbox(), times(2)).createSession(any());
        }
    }

    @Test
    void sameHandlerDuplicateCreateWithDifferentSessionSpecFails() {
        when(localSandbox.createSession(any())).thenReturn(CONTAINER_ID);
        String firstHandle = client.createSession(sessionSpec("job-1", "image-1"));

        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> client.createSession(sessionSpec("job-1", "image-2")))
                .withMessageContaining("different sandbox specification");

        assertThat(firstHandle).isEqualTo(AGENT_SHORT_NAME + "::" + CONTAINER_ID);
        verify(localSandbox, times(1)).createSession(any());
    }

    @Test
    void shutdownWaitsForAnInFlightCreateBeforeCleaningUp() throws Exception {
        try (RelayHarness harness = newHarness(1)) {
            CountDownLatch createStarted = new CountDownLatch(1);
            CountDownLatch releaseCreate = new CountDownLatch(1);
            AtomicBoolean createCompleted = new AtomicBoolean();
            AtomicReference<Boolean> cleanupSawCompletedCreate = new AtomicReference<>();
            when(harness.localSandbox().createSession(any())).thenAnswer(invocation -> {
                createStarted.countDown();
                boolean interrupted = false;
                while (true) {
                    try {
                        releaseCreate.await();
                        break;
                    }
                    catch (InterruptedException ignored) {
                        interrupted = true;
                    }
                }
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
                createCompleted.set(true);
                return CONTAINER_ID;
            });
            when(harness.localSandbox().removeSessionsForCurrentAgent()).thenAnswer(invocation -> {
                cleanupSawCompletedCreate.set(createCompleted.get());
                return 0;
            });
            clearInvocations(harness.localSandbox());

            CompletableFuture<String> create = CompletableFuture.supplyAsync(() -> harness.client().createSession(sessionSpec()));
            assertThat(createStarted.await(2, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<Void> shutdown = CompletableFuture.runAsync(harness.handler()::shutdown);

            await().atMost(Duration.ofSeconds(5)).until(() -> ReflectionTestUtils.getField(harness.handler(), "requestListenerId") == null);
            releaseCreate.countDown();
            assertThat(create.get(5, TimeUnit.SECONDS)).isEqualTo(AGENT_SHORT_NAME + "::" + CONTAINER_ID);
            shutdown.get(5, TimeUnit.SECONDS);
            assertThat(cleanupSawCompletedCreate).hasValue(true);
            verify(harness.localSandbox()).removeSessionsForCurrentAgent();
        }
    }

    @Test
    void exec_rejectsAnUnownedContainerId() {
        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> client.exec(handle(), Duration.ofSeconds(1), "echo", "unsafe"))
                .withMessageContaining("is not owned by this relay");

        verify(localSandbox, never()).exec(anyString(), any(), any(String[].class));
    }

    @Test
    void oversizeCopyInPayload_isRejectedBeforeReachingTheWire() {
        byte[] huge = new byte[RemoteInteractiveSandboxClient.MAX_PAYLOAD_BYTES + 1];

        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> client.copyIn(handle(), "/workspace", new ByteArrayInputStream(huge)))
                .withMessageContaining("relay limit");

        verify(localSandbox, never()).copyIn(anyString(), anyString(), any());
    }

    @Test
    void requestForDifferentAgent_isIgnoredByHandler() {
        SandboxOpRequestDTO foreignRequest = SandboxOpRequestDTO.destroy("corr-foreign", "some-other-agent", CONTAINER_ID);
        requestsTopic.publish(foreignRequest);

        verify(localSandbox, never()).destroySession(anyString());
    }

    @Test
    void sameHandlerDuplicateCorrelationIdReplaysResponseWithoutRepeatingCreate() throws Exception {
        String correlationId = "corr-dup";
        CountDownLatch firstResponseReceived = new CountDownLatch(1);
        AtomicInteger matchingResponses = new AtomicInteger();
        responsesTopic.addMessageListener(response -> {
            if (correlationId.equals(response.correlationId())) {
                matchingResponses.incrementAndGet();
                firstResponseReceived.countDown();
            }
        });
        when(localSandbox.createSession(any())).thenReturn(CONTAINER_ID);
        SandboxOpRequestDTO request = SandboxOpRequestDTO.create(correlationId, AGENT_SHORT_NAME, sessionSpec());

        requestsTopic.publish(request);
        assertThat(firstResponseReceived.await(5, TimeUnit.SECONDS)).isTrue();
        requestsTopic.publish(request);

        assertThat(matchingResponses).hasValue(2);
        verify(localSandbox, times(1)).createSession(any());
    }

    @Test
    void drainingHandlerReplaysCompletedCreateInsteadOfReturningAnAmbiguousDecline() throws Exception {
        String correlationId = "corr-completed-before-drain";
        SandboxOpRequestDTO request = SandboxOpRequestDTO.create(correlationId, AGENT_SHORT_NAME, sessionSpec());
        assertThat(handler.claimRequest(request).accepted()).isTrue();
        handler.rememberCompletedResponse(SandboxOpResponseDTO.ok(correlationId, CONTAINER_ID));
        BlockingQueue<SandboxOpResponseDTO> matchingResponses = responsesFor(correlationId);
        AtomicBoolean shuttingDown = (AtomicBoolean) ReflectionTestUtils.getField(handler, "shuttingDown");
        shuttingDown.set(true);
        try {
            requestsTopic.publish(request);
            SandboxOpResponseDTO replayed = matchingResponses.poll(5, TimeUnit.SECONDS);
            assertThat(replayed).isNotNull();
            assertThat(replayed.success()).isTrue();
            assertThat(replayed.sessionId()).isEqualTo(CONTAINER_ID);
        }
        finally {
            shuttingDown.set(false);
        }
    }

    @Test
    void inFlightCorrelationId_isNotEvictedByCompletedResponseHistory() {
        String inFlightId = "corr-in-flight";
        SandboxOpRequestDTO inFlightRequest = SandboxOpRequestDTO.list(inFlightId, AGENT_SHORT_NAME);
        assertThat(handler.claimRequest(inFlightRequest).accepted()).isTrue();

        for (int i = 0; i < InteractiveSandboxRelayHandler.MAX_REMEMBERED_CORRELATION_IDS; i++) {
            String correlationId = "corr-completed-" + i;
            assertThat(handler.claimRequest(SandboxOpRequestDTO.list(correlationId, AGENT_SHORT_NAME)).accepted()).isTrue();
            handler.rememberCompletedResponse(SandboxOpResponseDTO.ok(correlationId, CONTAINER_ID));
        }

        InteractiveSandboxRelayHandler.RequestClaim retryWhileRunning = handler.claimRequest(inFlightRequest);
        assertThat(retryWhileRunning.accepted()).isFalse();
        assertThat(retryWhileRunning.completedResponse()).isNull();

        InteractiveSandboxRelayHandler.RequestClaim overload = handler.claimRequest(SandboxOpRequestDTO.list("corr-over-cap", AGENT_SHORT_NAME));
        assertThat(overload.accepted()).isFalse();
        assertThat(overload.completedResponse().errorMessage()).contains(InteractiveSandboxRelayHandler.OVERLOAD_REFUSAL_MARKER);

        SandboxOpResponseDTO completed = SandboxOpResponseDTO.ok(inFlightId, CONTAINER_ID);
        handler.rememberCompletedResponse(completed);
        assertThat(handler.claimRequest(inFlightRequest).completedResponse()).isEqualTo(completed);
    }

    @Test
    void inFlightCorrelationIdWhoseOperationNeverCompleted_isReclaimedOnceItsRetryDeadlinePasses() {
        String correlationId = "corr-leaked-in-flight";
        SandboxOpRequestDTO request = SandboxOpRequestDTO.list(correlationId, AGENT_SHORT_NAME);
        assertThat(handler.claimRequest(request).accepted()).isTrue();

        // While the operation may genuinely still be running, a retry must not repeat its side effect.
        assertThat(handler.claimRequest(request).accepted()).isFalse();

        // The operation never reaches rememberCompletedResponse (a killed worker, an Error), so nothing removes its entry. Age the stored retry deadline instead of waiting out
        // the real request deadline plus its one-minute grace: past that instant no retry the requester could still send would be honoured anyway.
        inFlightCorrelationIds().put(correlationId, System.currentTimeMillis() - 1);

        InteractiveSandboxRelayHandler.RequestClaim reclaimed = handler.claimRequest(request);

        // Without the sweep the leaked entry survives for the lifetime of the agent and this correlation id stays permanently unclaimable.
        assertThat(reclaimed.accepted()).isTrue();
        assertThat(reclaimed.completedResponse()).isNull();
    }

    @Test
    void expiredRequestIsRejectedWithoutExecution() {
        SandboxOpRequestDTO expired = SandboxOpRequestDTO.list("corr-expired", AGENT_SHORT_NAME).withDeadline(Duration.ofSeconds(-1));

        InteractiveSandboxRelayHandler.RequestClaim claim = handler.claimRequest(expired);

        assertThat(claim.accepted()).isFalse();
        assertThat(claim.completedResponse().errorMessage()).contains("deadline expired");
    }

    @Test
    void sameHandlerCreatesForHashCollidingJobIdsDoNotBlockEachOther() throws Exception {
        // "Aa" and "BB" intentionally collide under String.hashCode(), but represent unrelated jobs on this handler.
        assertThat("Aa".hashCode()).isEqualTo("BB".hashCode());
        CountDownLatch firstCreateStarted = new CountDownLatch(1);
        CountDownLatch finishFirstCreate = new CountDownLatch(1);
        CountDownLatch secondCreateStarted = new CountDownLatch(1);
        when(localSandbox.createSession(any())).thenAnswer(invocation -> {
            SandboxSessionSpecDTO spec = invocation.getArgument(0);
            if (spec.context().jobId().equals("Aa")) {
                firstCreateStarted.countDown();
                finishFirstCreate.await(5, TimeUnit.SECONDS);
                return CONTAINER_ID;
            }
            secondCreateStarted.countDown();
            return CONTAINER_ID + "-2";
        });
        CompletableFuture<String> first = CompletableFuture.supplyAsync(() -> client.createSession(sessionSpec("Aa")));
        assertThat(firstCreateStarted.await(2, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<String> second = CompletableFuture.supplyAsync(() -> client.createSession(sessionSpec("BB")));
        try {
            assertThat(secondCreateStarted.await(2, TimeUnit.SECONDS)).isTrue();
        }
        finally {
            finishFirstCreate.countDown();
        }

        assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(AGENT_SHORT_NAME + "::" + CONTAINER_ID);
        assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(AGENT_SHORT_NAME + "::" + CONTAINER_ID + "-2");
        assertThat((Map<?, ?>) ReflectionTestUtils.getField(handler, "jobCoordinations")).isEmpty();
    }

    @Test
    void sameHandlerCreateDestroysAContainerThatFinishesAfterTheRequestDeadline() throws Exception {
        CountDownLatch createStarted = new CountDownLatch(1);
        CountDownLatch finishCreate = new CountDownLatch(1);
        when(localSandbox.createSession(any())).thenAnswer(invocation -> {
            createStarted.countDown();
            finishCreate.await(5, TimeUnit.SECONDS);
            return CONTAINER_ID;
        });
        SandboxOpRequestDTO request = SandboxOpRequestDTO.create("corr-late-create", AGENT_SHORT_NAME, sessionSpec()).withDeadline(Duration.ofMillis(100));
        BlockingQueue<SandboxOpResponseDTO> matchingResponses = responsesFor(request.correlationId());

        requestsTopic.publish(request);
        assertThat(createStarted.await(2, TimeUnit.SECONDS)).isTrue();
        await().atMost(Duration.ofSeconds(2)).until(() -> System.currentTimeMillis() > request.deadlineEpochMillis());
        finishCreate.countDown();

        SandboxOpResponseDTO response = matchingResponses.poll(2, TimeUnit.SECONDS);
        assertThat(response).isNotNull();
        assertThat(response.success()).isFalse();
        assertThat(response.errorMessage()).contains("deadline expired");
        verify(localSandbox).destroySession(CONTAINER_ID);
        assertThat(handler.ownedSessionIdsSnapshot()).doesNotContain(CONTAINER_ID);
    }

    @Test
    void sameHandlerLateCreateWithFailedRollbackReturnsAndReplaysCreated() throws Exception {
        String correlationId = "corr-late-create-failed-rollback";
        CountDownLatch createStarted = new CountDownLatch(1);
        CountDownLatch finishCreate = new CountDownLatch(1);
        when(localSandbox.createSession(any())).thenAnswer(invocation -> {
            createStarted.countDown();
            finishCreate.await(5, TimeUnit.SECONDS);
            return CONTAINER_ID;
        });
        doThrow(new LocalCIException("rollback failed")).when(localSandbox).destroySession(CONTAINER_ID);
        when(localSandbox.sessionExists(CONTAINER_ID)).thenReturn(true);
        SandboxOpRequestDTO request = SandboxOpRequestDTO.create(correlationId, AGENT_SHORT_NAME, sessionSpec()).withDeadline(Duration.ofMillis(100));
        BlockingQueue<SandboxOpResponseDTO> matchingResponses = responsesFor(correlationId);

        requestsTopic.publish(request);
        assertThat(createStarted.await(2, TimeUnit.SECONDS)).isTrue();
        await().atMost(Duration.ofSeconds(2)).until(() -> System.currentTimeMillis() > request.deadlineEpochMillis());
        finishCreate.countDown();

        SandboxOpResponseDTO firstResponse = matchingResponses.poll(2, TimeUnit.SECONDS);
        assertThat(firstResponse).isNotNull();
        assertThat(firstResponse.success()).isTrue();
        assertThat(firstResponse.sessionId()).isEqualTo(CONTAINER_ID);
        requestsTopic.publish(request);
        SandboxOpResponseDTO replay = matchingResponses.poll(2, TimeUnit.SECONDS);
        assertThat(replay).isEqualTo(firstResponse);
        verify(localSandbox, times(1)).createSession(any());
        verify(localSandbox, times(1)).destroySession(CONTAINER_ID);
        assertThat(handler.ownedSessionIdsSnapshot()).containsExactly(CONTAINER_ID);
        assertThat(((Semaphore) ReflectionTestUtils.getField(handler, "sandboxSlotPermits")).availablePermits()).isEqualTo(1);
    }

    @Test
    void lateCopyOutDoesNotStageAPayloadAfterTheRequestDeadline() throws Exception {
        when(localSandbox.createSession(any())).thenReturn(CONTAINER_ID);
        client.createSession(sessionSpec());
        CountDownLatch copyOutStarted = new CountDownLatch(1);
        CountDownLatch finishCopyOut = new CountDownLatch(1);
        byte[] tar = tarWithSingleFile("result.txt", "late output");
        when(localSandbox.copyOut(CONTAINER_ID, "/workspace/out")).thenAnswer(invocation -> {
            copyOutStarted.countDown();
            finishCopyOut.await(5, TimeUnit.SECONDS);
            return new TarArchiveInputStream(new ByteArrayInputStream(tar));
        });
        SandboxOpRequestDTO request = SandboxOpRequestDTO.copyOut("corr-late-copy-out", AGENT_SHORT_NAME, CONTAINER_ID, "/workspace/out").withDeadline(Duration.ofMillis(100));
        BlockingQueue<SandboxOpResponseDTO> matchingResponses = responsesFor(request.correlationId());

        requestsTopic.publish(request);
        assertThat(copyOutStarted.await(2, TimeUnit.SECONDS)).isTrue();
        await().atMost(Duration.ofSeconds(2)).until(() -> System.currentTimeMillis() > request.deadlineEpochMillis());
        finishCopyOut.countDown();

        SandboxOpResponseDTO response = matchingResponses.poll(2, TimeUnit.SECONDS);
        assertThat(response).isNotNull();
        assertThat(response.success()).isFalse();
        assertThat(response.errorMessage()).contains("deadline expired");
        assertThat(payloads.get(request.correlationId())).isNull();
    }

    @Test
    void copyOutRemovesPayloadWhenDeadlineExpiresDuringDistributedPut() throws Exception {
        when(localSandbox.createSession(any())).thenReturn(CONTAINER_ID);
        client.createSession(sessionSpec());
        CountDownLatch payloadStored = new CountDownLatch(1);
        CountDownLatch finishPut = new CountDownLatch(1);
        LocalMap<String, byte[]> expiringDuringPutPayloads = new LocalMap<>() {

            @Override
            public void put(String key, byte[] value) {
                super.put(key, value);
                payloadStored.countDown();
                try {
                    finishPut.await(5, TimeUnit.SECONDS);
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
        };
        when(handlerAccess.getHyperionSandboxPayloads()).thenReturn(expiringDuringPutPayloads);
        byte[] tar = tarWithSingleFile("result.txt", "late output");
        when(localSandbox.copyOut(CONTAINER_ID, "/workspace/out")).thenReturn(new TarArchiveInputStream(new ByteArrayInputStream(tar)));
        SandboxOpRequestDTO request = SandboxOpRequestDTO.copyOut("corr-expired-during-put", AGENT_SHORT_NAME, CONTAINER_ID, "/workspace/out").withDeadline(Duration.ofMillis(100));
        BlockingQueue<SandboxOpResponseDTO> matchingResponses = responsesFor(request.correlationId());

        requestsTopic.publish(request);
        assertThat(payloadStored.await(2, TimeUnit.SECONDS)).isTrue();
        await().atMost(Duration.ofSeconds(2)).until(() -> System.currentTimeMillis() > request.deadlineEpochMillis());
        finishPut.countDown();

        SandboxOpResponseDTO response = matchingResponses.poll(2, TimeUnit.SECONDS);
        assertThat(response).isNotNull();
        assertThat(response.success()).isFalse();
        assertThat(response.errorMessage()).contains("deadline expired");
        assertThat(expiringDuringPutPayloads.get(request.correlationId())).isNull();
    }

    @Test
    void sameHandlerResponsePublicationFailureIsRecoveredByReplayingTheCompletedResultWithoutRepeatingCreate() {
        LocalTopic<SandboxOpRequestDTO> requests = new LocalTopic<>();
        LocalTopic<SandboxOpResponseDTO> deliveredResponses = new LocalTopic<>();
        @SuppressWarnings("unchecked")
        DistributedTopic<SandboxOpResponseDTO> droppingResponses = mock(DistributedTopic.class);
        AtomicInteger responsePublications = new AtomicInteger();
        doAnswer(invocation -> {
            if (responsePublications.incrementAndGet() == 1) {
                throw new IllegalStateException("response topic unavailable");
            }
            deliveredResponses.publish(invocation.getArgument(0));
            return null;
        }).when(droppingResponses).publish(any());
        when(droppingResponses.addMessageListener(any())).thenAnswer(invocation -> deliveredResponses.addMessageListener(invocation.getArgument(0)));
        doAnswer(invocation -> {
            deliveredResponses.removeMessageListener(invocation.getArgument(0));
            return null;
        }).when(droppingResponses).removeMessageListener(any());

        DistributedDataAccessService access = mock(DistributedDataAccessService.class);
        when(access.getHyperionSandboxRequestsTopic()).thenReturn(requests);
        when(access.getHyperionSandboxResponsesTopic()).thenReturn(droppingResponses);
        when(access.getBuildAgentInformation()).thenReturn(List.of(idleAgent(AGENT_SHORT_NAME, 0, 1)));
        when(access.isConnectedToCluster()).thenReturn(true);
        InteractiveSandboxService sandbox = mock(InteractiveSandboxService.class);
        when(sandbox.createSession(any())).thenReturn(CONTAINER_ID);
        RemoteInteractiveSandboxClient retryingClient = new RemoteInteractiveSandboxClient(access);
        ReflectionTestUtils.setField(retryingClient, "controlOpTimeout", Duration.ofSeconds(2));
        ReflectionTestUtils.setField(retryingClient, "relayRetryInterval", Duration.ofMillis(50));
        retryingClient.registerResponseListener();
        InteractiveSandboxRelayHandler retryingHandler = new InteractiveSandboxRelayHandler(applicationContext(sandbox), access, availableQueueProcessingService(),
                mock(BuildAgentInformationService.class));
        ReflectionTestUtils.setField(retryingHandler, "buildAgentShortName", AGENT_SHORT_NAME);
        ReflectionTestUtils.setField(retryingHandler, "maxGenerationSandboxSlots", 1);
        retryingHandler.registerRequestListener();

        try {
            assertThat(retryingClient.createSession(sessionSpec())).isEqualTo(AGENT_SHORT_NAME + "::" + CONTAINER_ID);
            verify(sandbox, times(1)).createSession(any());
            assertThat(responsePublications).hasValue(2);
        }
        finally {
            retryingHandler.shutdown();
            retryingClient.removeResponseListener();
        }
    }

    @Test
    void unansweredOperation_backsOffItsRePublishesInsteadOfBroadcastingAtAFixedInterval() {
        // A re-publish is a cluster-wide broadcast that every hosting agent deserializes on its event thread, so a healthy but long operation must not keep paying for one every
        // interval: the delay doubles instead of staying at the first interval.
        LocalTopic<SandboxOpRequestDTO> unansweredRequests = new LocalTopic<>();
        AtomicInteger publications = new AtomicInteger();
        unansweredRequests.addMessageListener(request -> publications.incrementAndGet());
        DistributedDataAccessService access = mock(DistributedDataAccessService.class);
        when(access.getHyperionSandboxRequestsTopic()).thenReturn(unansweredRequests);
        when(access.getHyperionSandboxResponsesTopic()).thenReturn(new LocalTopic<>());
        RemoteInteractiveSandboxClient backingOffClient = new RemoteInteractiveSandboxClient(access);
        ReflectionTestUtils.setField(backingOffClient, "relayRetryInterval", Duration.ofMillis(20));
        ReflectionTestUtils.setField(backingOffClient, "controlOpTimeout", Duration.ofSeconds(2));
        backingOffClient.registerResponseListener();

        try {
            assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> backingOffClient.resetSession(handle())).withMessageContaining("timed out");
        }
        finally {
            backingOffClient.removeResponseListener();
        }

        // A fixed 20ms interval would re-publish about a hundred times over this two-second budget; doubling cannot exceed the initial publish plus seven re-publishes.
        assertThat(publications).hasValueBetween(2, 20);
    }

    @Test
    void execRunsOffTheTopicListenerThread() {
        AtomicReference<Thread> execThread = new AtomicReference<>();
        when(localSandbox.exec(eq(CONTAINER_ID), any(), eq("echo"), eq("x"))).thenAnswer(invocation -> {
            execThread.set(Thread.currentThread());
            return new SandboxExecResultDTO(0, "", "", false);
        });

        Thread callerThread = Thread.currentThread();
        client.exec(createOwnedHandle(), Duration.ofSeconds(5), "echo", "x");

        assertThat(execThread.get()).isNotSameAs(callerThread);
        assertThat(execThread.get().getName()).startsWith("hyperion-sandbox-relay-");
    }

    @Test
    void operationThatHitsAFatalErrorStillAnswersTheBlockedCallerWithAFailure() throws Exception {
        // An Error is not an Exception: catching only Exception left the core node — which is blocked on a distributed future — waiting out its entire relay budget with no
        // response and no diagnosis, for a failure the agent already knew about.
        createOwnedHandle();
        when(localSandbox.exec(eq(CONTAINER_ID), any(), any(String[].class))).thenThrow(new StackOverflowError("boom"));
        SandboxOpRequestDTO request = SandboxOpRequestDTO.exec("corr-fatal", AGENT_SHORT_NAME, CONTAINER_ID, new String[] { "true" }, 5);
        BlockingQueue<SandboxOpResponseDTO> matchingResponses = responsesFor(request.correlationId());

        requestsTopic.publish(request);

        SandboxOpResponseDTO response = matchingResponses.poll(5, TimeUnit.SECONDS);
        assertThat(response).isNotNull();
        assertThat(response.success()).isFalse();
        assertThat(response.errorMessage()).contains("boom");
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
            assertThat(harness.client().createSession(sessionSpec("job-1"))).isEqualTo(AGENT_SHORT_NAME + "::" + CONTAINER_ID);
            assertThat(harness.client().createSession(sessionSpec("job-2"))).isEqualTo(AGENT_SHORT_NAME + "::" + CONTAINER_ID + "-2");

            assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> harness.client().createSession(sessionSpec("job-3")))
                    .withMessageContaining("generation sandbox slot capacity");
        }
    }

    @Test
    void createSucceedsWhenPublishingTheUpdatedSlotStateFails() {
        try (RelayHarness harness = newHarness(1)) {
            when(harness.localSandbox().createSession(any())).thenReturn(CONTAINER_ID);
            doThrow(new LocalCIException("state store unavailable")).when(harness.informationService()).updateGenerationSandboxSlotState(1, 1);

            assertThat(harness.client().createSession(sessionSpec())).isEqualTo(AGENT_SHORT_NAME + "::" + CONTAINER_ID);
            assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> harness.client().createSession(sessionSpec("other-job")))
                    .withMessageContaining("generation sandbox slot capacity");
        }
    }

    @Test
    void pausedAgent_refusesNewSession() {
        when(queueProcessingService.tryAcquireGenerationAdmission()).thenReturn(false);

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
    void startupBeforeClusterConnection_defersHostingUntilConnected() {
        DistributedDataAccessService access = mock(DistributedDataAccessService.class);
        when(access.isConnectedToCluster()).thenReturn(false);
        when(access.getHyperionSandboxRequestsTopic()).thenReturn(requestsTopic);
        when(access.getHyperionSandboxResponsesTopic()).thenReturn(responsesTopic);
        InteractiveSandboxService sandbox = mock(InteractiveSandboxService.class);
        InteractiveSandboxRelayHandler deferred = new InteractiveSandboxRelayHandler(applicationContext(sandbox), access, queueProcessingService,
                mock(BuildAgentInformationService.class));
        ReflectionTestUtils.setField(deferred, "buildAgentShortName", AGENT_SHORT_NAME);
        ReflectionTestUtils.setField(deferred, "maxGenerationSandboxSlots", 2);
        ArgumentCaptor<Consumer<Boolean>> connectionListener = ArgumentCaptor.forClass(Consumer.class);

        deferred.registerRequestListener();

        verify(access).addConnectionStateListener(connectionListener.capture());
        assertThat(ReflectionTestUtils.getField(deferred, "requestListenerId")).isNull();
        verify(sandbox, never()).removeSessionsForCurrentAgent();

        when(access.isConnectedToCluster()).thenReturn(true);
        connectionListener.getValue().accept(true);

        assertThat(ReflectionTestUtils.getField(deferred, "requestListenerId")).isNotNull();
        verify(sandbox).removeSessionsForCurrentAgent();
        deferred.shutdown();
    }

    @Test
    void startupCleanupFailure_disablesHostingWithoutSubscribing() {
        InteractiveSandboxService sandbox = mock(InteractiveSandboxService.class);
        when(sandbox.removeSessionsForCurrentAgent()).thenThrow(new LocalCIException("cleanup failed"));
        BuildAgentInformationService informationService = mock(BuildAgentInformationService.class);
        DistributedDataAccessService access = mock(DistributedDataAccessService.class);
        when(access.isConnectedToCluster()).thenReturn(true);
        InteractiveSandboxRelayHandler disabled = new InteractiveSandboxRelayHandler(applicationContext(sandbox), access, queueProcessingService, informationService);
        ReflectionTestUtils.setField(disabled, "buildAgentShortName", AGENT_SHORT_NAME);
        ReflectionTestUtils.setField(disabled, "maxGenerationSandboxSlots", 2);

        disabled.registerRequestListener();

        assertThat(ReflectionTestUtils.getField(disabled, "requestListenerId")).isNull();
        assertThat(ReflectionTestUtils.getField(disabled, "workerExecutor")).isNull();
        verify(informationService).updateGenerationSandboxSlotState(2, 2);
    }

    @Test
    void replacedAgentInstanceIgnoresRequestsForTheSharedShortName() {
        DistributedDataAccessService access = mock(DistributedDataAccessService.class);
        when(access.isConnectedToCluster()).thenReturn(true);
        when(access.getLocalMemberAddress()).thenReturn("old-instance:5701");
        when(access.getBuildAgentInformationMap()).thenReturn(Map.of(AGENT_SHORT_NAME, idleAgent(AGENT_SHORT_NAME, "new-instance:5701", 0, 2)));
        when(access.getHyperionSandboxRequestsTopic()).thenReturn(requestsTopic);
        when(access.getHyperionSandboxResponsesTopic()).thenReturn(responsesTopic);
        InteractiveSandboxService sandbox = mock(InteractiveSandboxService.class);
        InteractiveSandboxRelayHandler replaced = new InteractiveSandboxRelayHandler(applicationContext(sandbox), access, queueProcessingService,
                mock(BuildAgentInformationService.class));
        ReflectionTestUtils.setField(replaced, "buildAgentShortName", AGENT_SHORT_NAME);
        ReflectionTestUtils.setField(replaced, "maxGenerationSandboxSlots", 2);
        replaced.registerRequestListener();

        requestsTopic.publish(SandboxOpRequestDTO.create("correlation", AGENT_SHORT_NAME, sessionSpec()));

        verify(sandbox, never()).createSession(any());
        replaced.shutdown();
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
            assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> harness.client().createSession(sessionSpec("other-job")))
                    .withMessageContaining("generation sandbox slot capacity");
        }
    }

    @Test
    void concurrentDestroy_removesTheContainerExactlyOnce() throws Exception {
        CountDownLatch destroyRequestsPublished = new CountDownLatch(2);
        LocalTopic<SandboxOpRequestDTO> observedRequests = new LocalTopic<>() {

            @Override
            public void publish(SandboxOpRequestDTO request) {
                super.publish(request);
                if (request.op() == SandboxOp.DESTROY) {
                    destroyRequestsPublished.countDown();
                }
            }
        };
        try (RelayHarness harness = newHarness(1, observedRequests); ExecutorService callers = Executors.newFixedThreadPool(2)) {
            when(harness.localSandbox().createSession(any())).thenReturn(CONTAINER_ID);
            String handle = harness.client().createSession(sessionSpec());
            CountDownLatch destroyStarted = new CountDownLatch(1);
            CountDownLatch finishDestroy = new CountDownLatch(1);
            doAnswer(invocation -> {
                destroyStarted.countDown();
                finishDestroy.await();
                return null;
            }).when(harness.localSandbox()).destroySession(CONTAINER_ID);

            CompletableFuture<Void> first = CompletableFuture.runAsync(() -> harness.client().destroySession(handle), callers);
            CompletableFuture<Void> second;
            try {
                assertThat(destroyStarted.await(5, TimeUnit.SECONDS)).isTrue();
                second = CompletableFuture.runAsync(() -> harness.client().destroySession(handle), callers);
                assertThat(destroyRequestsPublished.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> harness.client().listSessions(AGENT_SHORT_NAME)).withMessageContaining("overloaded");
            }
            finally {
                finishDestroy.countDown();
            }

            assertThat(first).succeedsWithin(Duration.ofSeconds(5));
            assertThat(second).succeedsWithin(Duration.ofSeconds(5));
            verify(harness.localSandbox(), times(1)).destroySession(CONTAINER_ID);
        }
    }

    @Test
    void destroyOfOneJobDoesNotBlockDestroyOfAnotherJob() throws Exception {
        try (RelayHarness harness = newHarness(2); ExecutorService callers = Executors.newFixedThreadPool(2)) {
            String otherContainer = "container-2";
            when(harness.localSandbox().createSession(any())).thenReturn(CONTAINER_ID, otherContainer);
            String firstHandle = harness.client().createSession(sessionSpec("job-1"));
            String secondHandle = harness.client().createSession(sessionSpec("job-2"));
            CountDownLatch firstDestroyStarted = new CountDownLatch(1);
            CountDownLatch finishFirstDestroy = new CountDownLatch(1);
            CountDownLatch secondDestroyFinished = new CountDownLatch(1);
            doAnswer(invocation -> {
                if (CONTAINER_ID.equals(invocation.getArgument(0))) {
                    firstDestroyStarted.countDown();
                    finishFirstDestroy.await();
                }
                return null;
            }).when(harness.localSandbox()).destroySession(anyString());

            CompletableFuture<Void> first = CompletableFuture.runAsync(() -> harness.client().destroySession(firstHandle), callers);
            CompletableFuture<Void> second = null;
            boolean secondCompletedWhileFirstWasBlocked;
            try {
                assertThat(firstDestroyStarted.await(5, TimeUnit.SECONDS)).isTrue();
                second = CompletableFuture.runAsync(() -> {
                    harness.client().destroySession(secondHandle);
                    secondDestroyFinished.countDown();
                }, callers);
                secondCompletedWhileFirstWasBlocked = secondDestroyFinished.await(2, TimeUnit.SECONDS);
            }
            finally {
                finishFirstDestroy.countDown();
            }

            assertThat(secondCompletedWhileFirstWasBlocked).isTrue();
            assertThat(first).succeedsWithin(Duration.ofSeconds(5));
            assertThat(second).isNotNull().succeedsWithin(Duration.ofSeconds(5));
            verify(harness.localSandbox()).destroySession(CONTAINER_ID);
            verify(harness.localSandbox()).destroySession(otherContainer);
        }
    }

    @Test
    void createForTheSameJobWaitsUntilDestroyCompletes() throws Exception {
        AtomicInteger createRequests = new AtomicInteger();
        CountDownLatch replacementCreatePublished = new CountDownLatch(1);
        LocalTopic<SandboxOpRequestDTO> observedRequests = new LocalTopic<>() {

            @Override
            public void publish(SandboxOpRequestDTO request) {
                super.publish(request);
                if (request.op() == SandboxOp.CREATE && createRequests.incrementAndGet() == 2) {
                    replacementCreatePublished.countDown();
                }
            }
        };
        try (RelayHarness harness = newHarness(1, observedRequests); ExecutorService callers = Executors.newFixedThreadPool(2)) {
            when(harness.localSandbox().createSession(any())).thenReturn(CONTAINER_ID, "replacement-container");
            String handle = harness.client().createSession(sessionSpec());
            CountDownLatch destroyStarted = new CountDownLatch(1);
            CountDownLatch finishDestroy = new CountDownLatch(1);
            doAnswer(invocation -> {
                destroyStarted.countDown();
                finishDestroy.await();
                return null;
            }).when(harness.localSandbox()).destroySession(CONTAINER_ID);

            CompletableFuture<Void> destroy = CompletableFuture.runAsync(() -> harness.client().destroySession(handle), callers);
            assertThat(destroyStarted.await(5, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<String> replacement = CompletableFuture.supplyAsync(() -> harness.client().createSession(sessionSpec()), callers);
            try {
                assertThat(replacementCreatePublished.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(replacement).isNotDone();
                verify(harness.localSandbox(), times(1)).createSession(any());
            }
            finally {
                finishDestroy.countDown();
            }

            assertThat(destroy).succeedsWithin(Duration.ofSeconds(5));
            assertThat(replacement).succeedsWithin(Duration.ofSeconds(5)).isEqualTo(AGENT_SHORT_NAME + "::replacement-container");
            verify(harness.localSandbox(), times(2)).createSession(any());
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
            assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> harness.client().createSession(sessionSpec("other-job")))
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
    void destroySucceedsAndReleasesThePermitWhenSlotStatePublicationFails() {
        try (RelayHarness harness = newHarness(1)) {
            when(harness.localSandbox().createSession(any())).thenReturn(CONTAINER_ID);
            String handle = harness.client().createSession(sessionSpec());
            doThrow(new RuntimeException("state publication unavailable")).when(harness.informationService()).updateGenerationSandboxSlotState(anyInt(), anyInt());

            assertThatNoException().isThrownBy(() -> harness.client().destroySession(handle));
            assertThat(harness.client().createSession(sessionSpec())).isEqualTo(handle);
        }
    }

    @Test
    void timedOutExec_releasesThePermitForTheContainerDestroyedByTheService() {
        try (RelayHarness harness = newHarness(1)) {
            when(harness.localSandbox().createSession(any())).thenReturn(CONTAINER_ID);
            when(harness.localSandbox().exec(eq(CONTAINER_ID), any(), any(String[].class))).thenReturn(new SandboxExecResultDTO(-1, "", "", true));
            String handle = harness.client().createSession(sessionSpec());

            assertThat(harness.client().exec(handle, Duration.ofSeconds(1), "sleep", "10").timedOut()).isTrue();
            assertThat(harness.client().createSession(sessionSpec())).isEqualTo(handle);
        }
    }

    @Test
    void failedExecReleasesThePermitWhenItsContainerIsAlreadyAbsent() {
        try (RelayHarness harness = newHarness(1)) {
            when(harness.localSandbox().createSession(any())).thenReturn(CONTAINER_ID);
            when(harness.localSandbox().exec(eq(CONTAINER_ID), any(), any(String[].class))).thenThrow(new LocalCIException("Docker response lost"));
            when(harness.localSandbox().sessionExists(CONTAINER_ID)).thenReturn(false);
            String handle = harness.client().createSession(sessionSpec());

            assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> harness.client().exec(handle, Duration.ofSeconds(1), "sleep", "10"))
                    .withMessageContaining("Docker response lost");
            assertThat(harness.client().createSession(sessionSpec())).isEqualTo(handle);
        }
    }

    @Test
    void relayFailsFastInsteadOfQueuingRequestsWithoutBound() throws Exception {
        try (RelayHarness harness = newHarness(1); ExecutorService callers = Executors.newFixedThreadPool(3)) {
            when(harness.localSandbox().createSession(any())).thenReturn(CONTAINER_ID);
            String handle = harness.client().createSession(sessionSpec());
            CountDownLatch workersStarted = new CountDownLatch(2);
            CountDownLatch releaseWorkers = new CountDownLatch(1);
            when(harness.localSandbox().exec(eq(CONTAINER_ID), any(), any(String[].class))).thenAnswer(invocation -> {
                workersStarted.countDown();
                releaseWorkers.await(10, TimeUnit.SECONDS);
                return new SandboxExecResultDTO(0, "", "", false);
            });

            List<CompletableFuture<SandboxExecResultDTO>> running = List.of(
                    CompletableFuture.supplyAsync(() -> harness.client().exec(handle, Duration.ofSeconds(10), "true"), callers),
                    CompletableFuture.supplyAsync(() -> harness.client().exec(handle, Duration.ofSeconds(10), "true"), callers));
            assertThat(workersStarted.await(2, TimeUnit.SECONDS)).isTrue();
            ThreadPoolExecutor executor = (ThreadPoolExecutor) ReflectionTestUtils.getField(harness.handler(), "workerExecutor");
            assertThat(executor.getQueue()).isEmpty();

            CompletableFuture<SandboxExecResultDTO> rejected = CompletableFuture.supplyAsync(() -> harness.client().exec(handle, Duration.ofSeconds(10), "true"), callers);
            assertThat(rejected).failsWithin(Duration.ofSeconds(2)).withThrowableThat().withCauseInstanceOf(LocalCIException.class).withMessageContaining("overloaded");

            releaseWorkers.countDown();
            CompletableFuture.allOf(running.toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void createSession_failsOverToTheNextAgent_whenTheFirstDeclinesAtCapacity() {
        LocalTopic<SandboxOpRequestDTO> requests = new LocalTopic<>();
        LocalTopic<SandboxOpResponseDTO> responses = new LocalTopic<>();
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
        LocalTopic<SandboxOpRequestDTO> requests = new LocalTopic<>();
        LocalTopic<SandboxOpResponseDTO> responses = new LocalTopic<>();
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
        LocalTopic<SandboxOpRequestDTO> requests = new LocalTopic<>();
        LocalTopic<SandboxOpResponseDTO> responses = new LocalTopic<>();
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
            assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> failoverClient.createSession(new SandboxSessionSpecDTO("bogus-image", null, sessionContext())))
                    .withMessageContaining("no such image");
            verify(sandbox2, never()).createSession(any());
        }
        finally {
            handler1.shutdown();
            handler2.shutdown();
            failoverClient.removeResponseListener();
        }
    }

    private static InteractiveSandboxRelayHandler sharedHandler(String shortName, int maxSessions, LocalTopic<SandboxOpRequestDTO> requests,
            LocalTopic<SandboxOpResponseDTO> responses, LocalMap<String, byte[]> payloads, InteractiveSandboxService sandbox) {
        DistributedDataAccessService access = mock(DistributedDataAccessService.class);
        when(access.getHyperionSandboxRequestsTopic()).thenReturn(requests);
        when(access.getHyperionSandboxResponsesTopic()).thenReturn(responses);
        when(access.getHyperionSandboxPayloads()).thenReturn(payloads);
        when(access.isConnectedToCluster()).thenReturn(true);
        InteractiveSandboxRelayHandler handler = new InteractiveSandboxRelayHandler(applicationContext(sandbox), access, availableQueueProcessingService(),
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
        return newHarness(maxGenerationSandboxSlots, new LocalTopic<>());
    }

    private static RelayHarness newHarness(int maxGenerationSandboxSlots, LocalTopic<SandboxOpRequestDTO> requests) {
        LocalTopic<SandboxOpResponseDTO> responses = new LocalTopic<>();
        LocalMap<String, byte[]> payloads = new LocalMap<>();

        DistributedDataAccessService clientAccess = mock(DistributedDataAccessService.class);
        when(clientAccess.getHyperionSandboxRequestsTopic()).thenReturn(requests);
        when(clientAccess.getHyperionSandboxResponsesTopic()).thenReturn(responses);
        when(clientAccess.getHyperionSandboxPayloads()).thenReturn(payloads);
        when(clientAccess.getBuildAgentInformation()).thenReturn(List.of(idleAgent(AGENT_SHORT_NAME, 0, 4)));

        DistributedDataAccessService handlerAccess = mock(DistributedDataAccessService.class);
        when(handlerAccess.getHyperionSandboxRequestsTopic()).thenReturn(requests);
        when(handlerAccess.getHyperionSandboxResponsesTopic()).thenReturn(responses);
        when(handlerAccess.getHyperionSandboxPayloads()).thenReturn(payloads);
        when(handlerAccess.isConnectedToCluster()).thenReturn(true);

        InteractiveSandboxService localSandbox = mock(InteractiveSandboxService.class);
        BuildAgentInformationService informationService = mock(BuildAgentInformationService.class);
        RemoteInteractiveSandboxClient client = new RemoteInteractiveSandboxClient(clientAccess);
        client.registerResponseListener();

        InteractiveSandboxRelayHandler handler = new InteractiveSandboxRelayHandler(applicationContext(localSandbox), handlerAccess, availableQueueProcessingService(),
                informationService);
        ReflectionTestUtils.setField(handler, "buildAgentShortName", AGENT_SHORT_NAME);
        ReflectionTestUtils.setField(handler, "maxGenerationSandboxSlots", maxGenerationSandboxSlots);
        handler.registerRequestListener();
        return new RelayHarness(client, handler, localSandbox, informationService);
    }

    private static SandboxSessionSpecDTO sessionSpec() {
        return sessionSpec("job");
    }

    private static SandboxSessionSpecDTO sessionSpec(String jobId) {
        return sessionSpec(jobId, "some-image");
    }

    private static SandboxSessionSpecDTO sessionSpec(String jobId, String image) {
        return new SandboxSessionSpecDTO(image, null, new SandboxSessionContextDTO(jobId, 1L, "Sorting exercise", 2L, "instructor", "GENERATE"));
    }

    private static SharedQueueProcessingService availableQueueProcessingService() {
        SharedQueueProcessingService service = mock(SharedQueueProcessingService.class);
        when(service.tryAcquireGenerationAdmission()).thenReturn(true);
        return service;
    }

    private static SandboxSessionContextDTO sessionContext() {
        return new SandboxSessionContextDTO("job", 1L, "Sorting exercise", 2L, "instructor", "GENERATE");
    }

    private static String handle() {
        return AGENT_SHORT_NAME + "::" + CONTAINER_ID;
    }

    private String createOwnedHandle() {
        when(localSandbox.createSession(any())).thenReturn(CONTAINER_ID);
        return client.createSession(sessionSpec());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Long> inFlightCorrelationIds() {
        return (Map<String, Long>) ReflectionTestUtils.getField(handler, "inFlightCorrelationIds");
    }

    private BlockingQueue<SandboxOpResponseDTO> responsesFor(String correlationId) {
        BlockingQueue<SandboxOpResponseDTO> responses = new LinkedBlockingQueue<>();
        responsesTopic.addMessageListener(response -> {
            if (correlationId.equals(response.correlationId())) {
                responses.add(response);
            }
        });
        return responses;
    }

    private static BuildAgentInformation idleAgent(String name, int currentJobs, int maxJobs) {
        return idleAgent(name, "127.0.0.1:5701", currentJobs, maxJobs);
    }

    private static BuildAgentInformation idleAgent(String name, String memberAddress, int currentJobs, int maxJobs) {
        return new BuildAgentInformation(new BuildAgentDTO(name, memberAddress, name), maxJobs, currentJobs, List.of(), BuildAgentStatus.IDLE, "", null, 0, 0, 2);
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
