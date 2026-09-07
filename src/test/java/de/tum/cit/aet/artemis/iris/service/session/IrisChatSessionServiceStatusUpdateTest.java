package de.tum.cit.aet.artemis.iris.service.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.MessageSource;

import de.tum.cit.aet.artemis.account.service.UserAiPreferenceService;
import de.tum.cit.aet.artemis.admin.domain.LLMRequest;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.domain.LLMTokenUsageTrace;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.dto.IrisMessageResponseDTO;
import de.tum.cit.aet.artemis.iris.dto.MemirisMemoryDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisCitationService;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisActivityDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisActivityKind;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisActivityState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ChatJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.PyrisJob;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.lecture.api.LectureRepositoryApi;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;

class IrisChatSessionServiceStatusUpdateTest {

    private IrisSessionRepository irisSessionRepository;

    private IrisMessageService irisMessageService;

    private IrisMessageRepository irisMessageRepository;

    private IrisChatWebsocketService irisChatWebsocketService;

    private LLMTokenUsageService llmTokenUsageService;

    private PyrisJobService pyrisJobService;

    private IrisChatSessionService irisChatSessionService;

    @BeforeEach
    void setUp() {
        irisSessionRepository = mock(IrisSessionRepository.class);
        irisMessageService = mock(IrisMessageService.class);
        irisMessageRepository = mock(IrisMessageRepository.class);
        irisChatWebsocketService = mock(IrisChatWebsocketService.class);
        llmTokenUsageService = mock(LLMTokenUsageService.class);
        pyrisJobService = mock(PyrisJobService.class);

        irisChatSessionService = new IrisChatSessionService(irisMessageService, irisMessageRepository, llmTokenUsageService, mock(IrisSettingsService.class),
                irisChatWebsocketService, mock(AuthorizationCheckService.class), irisSessionRepository, mock(IrisChatSessionRepository.class),
                mock(ProgrammingExerciseStudentParticipationRepository.class), mock(ProgrammingSubmissionRepository.class), mock(IrisRateLimitService.class),
                JsonObjectMapper.get(), mock(ExerciseRepository.class), mock(SubmissionRepository.class), mock(CourseRepository.class), Optional.<LectureRepositoryApi>empty(),
                mock(IrisCitationService.class), mock(MessageSource.class), mock(IrisChatPipelineExecutionService.class), pyrisJobService, mock(UserAiPreferenceService.class));
    }

    @Test
    void duplicateResultStatusUpdatesCreateAndEmitAnswerOnlyOnce() throws Exception {
        var session = new IrisChatSession();
        session.setId(2L);
        session.setUserId(5L);
        session.setCourseId(1L);
        when(irisSessionRepository.findByIdWithMessagesAndContents(2L)).thenReturn(session);

        var initialJob = new ChatJob("run-1", 1L, 2L, 3L, null, null, null);
        var jobMapEntry = new AtomicReference<PyrisJob>(initialJob);
        var jobLock = new ReentrantLock();
        when(pyrisJobService.getJob("run-1")).thenAnswer(invocation -> jobMapEntry.get());
        when(pyrisJobService.runWithJobLock(eq("run-1"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<?> supplier = invocation.getArgument(1, Supplier.class);
            jobLock.lock();
            try {
                return supplier.get();
            }
            finally {
                jobLock.unlock();
            }
        });
        doAnswer(invocation -> {
            var job = invocation.getArgument(0, PyrisJob.class);
            jobMapEntry.set(job);
            return null;
        }).when(pyrisJobService).updateJob(any(PyrisJob.class));

        var savedAssistantMessage = new IrisMessage();
        savedAssistantMessage.setId(100L);
        var firstSaveStarted = new CountDownLatch(1);
        var releaseFirstSave = new CountDownLatch(1);
        when(irisMessageService.saveMessage(any(IrisMessage.class), eq(session), eq(IrisMessageSender.LLM))).thenAnswer(invocation -> {
            firstSaveStarted.countDown();
            assertThat(releaseFirstSave.await(2, TimeUnit.SECONDS)).isTrue();
            var message = invocation.getArgument(0, IrisMessage.class);
            message.setId(100L);
            return message;
        });
        when(irisMessageRepository.findById(100L)).thenReturn(Optional.of(savedAssistantMessage));

        var tokens = List.of(new LLMRequest("gpt-test", 10, 1.0F, 20, 2.0F, "IRIS_CHAT_TEST"));
        var trace = new LLMTokenUsageTrace();
        trace.setId(200L);
        when(llmTokenUsageService.saveLLMTokenUsage(eq(tokens), eq(LLMServiceType.IRIS), any())).thenReturn(trace);

        var createdMemory = new MemirisMemoryDTO("mem-1", "Memory", "content", List.of(), List.of(), false, false);
        var statusUpdate = new PyrisChatStatusUpdateDTO("answer", PyrisRunState.RUNNING, null, "Locked title", List.of("suggestion"), tokens, null, List.of(createdMemory));

        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> irisChatSessionService.handleStatusUpdate(initialJob, statusUpdate));
            assertThat(firstSaveStarted.await(2, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> irisChatSessionService.handleStatusUpdate(initialJob, statusUpdate));
            releaseFirstSave.countDown();

            var firstResult = first.get(2, TimeUnit.SECONDS);
            var secondResult = second.get(2, TimeUnit.SECONDS);

            assertThat(firstResult.assistantMessageId()).isEqualTo(100L);
            assertThat(secondResult.assistantMessageId()).isEqualTo(100L);
            assertThat(jobMapEntry.get()).isInstanceOfSatisfying(ChatJob.class, job -> {
                assertThat(job.assistantMessageId()).isEqualTo(100L);
                assertThat(job.traceId()).isEqualTo(200L);
            });
        }
        finally {
            executor.shutdownNow();
        }

        verify(irisMessageService, times(1)).saveMessage(any(IrisMessage.class), eq(session), eq(IrisMessageSender.LLM));
        verify(irisChatWebsocketService, times(1)).sendMessage(eq(session), any(IrisMessage.class), eq(PyrisRunState.RUNNING), isNull(), eq("Locked title"), anyList(), eq("run-1"),
                isNull(), isNull(), isNull());
        verify(llmTokenUsageService, times(1)).saveLLMTokenUsage(eq(tokens), eq(LLMServiceType.IRIS), any());
        verify(llmTokenUsageService, never()).appendRequestsToTrace(anyList(), any());

        verify(irisChatWebsocketService, times(1)).sendStatusUpdate(eq(session), eq("run-1"), eq(PyrisRunState.RUNNING), isNull(), eq("Locked title"), eq(List.of("suggestion")),
                eq(tokens), isNull(), isNull());
        verify(irisMessageRepository).findById(100L);
        verify(irisMessageRepository).save(savedAssistantMessage);
        assertThat(savedAssistantMessage.getCreatedMemories()).containsExactly(createdMemory);
        assertThat(session.getTitle()).isEqualTo("Locked title");
    }

    @Test
    void resultStatusUpdatePersistsActivityTrailOnAssistantMessage() {
        var session = new IrisChatSession();
        session.setId(2L);
        session.setUserId(5L);
        session.setCourseId(1L);
        when(irisSessionRepository.findByIdWithMessagesAndContents(2L)).thenReturn(session);

        var job = new ChatJob("run-1", 1L, 2L, 3L, null, null, null);
        when(pyrisJobService.getJob("run-1")).thenReturn(job);
        when(pyrisJobService.runWithJobLock(eq("run-1"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<?> supplier = invocation.getArgument(1, Supplier.class);
            return supplier.get();
        });
        when(irisMessageService.saveMessage(any(IrisMessage.class), eq(session), eq(IrisMessageSender.LLM))).thenAnswer(invocation -> {
            var message = invocation.getArgument(0, IrisMessage.class);
            message.setId(101L);
            return message;
        });

        var activity = new PyrisActivityDTO("activity-1", PyrisActivityKind.TOOL, "lecture_content_retrieval", PyrisActivityState.FINISHED, "Lecture 1", "2 chunks", 120L);
        var statusUpdate = new PyrisChatStatusUpdateDTO("answer", PyrisRunState.FINISHED, null, null, null, null, null, null, null, null, List.of(activity), 3);

        var updatedJob = irisChatSessionService.handleStatusUpdate(job, statusUpdate);

        var messageCaptor = ArgumentCaptor.forClass(IrisMessage.class);
        verify(irisMessageService).saveMessage(messageCaptor.capture(), eq(session), eq(IrisMessageSender.LLM));
        var savedMessage = messageCaptor.getValue();
        assertThat(savedMessage.getToolActivity()).containsExactly(activity);
        assertThat(IrisMessageResponseDTO.of(savedMessage).activities()).containsExactly(activity);
        assertThat(updatedJob.assistantMessageId()).isEqualTo(101L);
        verify(irisChatWebsocketService).sendMessage(eq(session), eq(savedMessage), eq(PyrisRunState.RUNNING), isNull(), isNull(), anyList(), eq("run-1"), eq(List.of(activity)),
                eq(3), isNull());
    }

    @Test
    void intermediateResultPersistsWithoutJobLockAssistantClaimOrTrail() {
        var session = new IrisChatSession();
        session.setId(2L);
        session.setUserId(5L);
        session.setCourseId(1L);
        when(irisSessionRepository.findByIdWithMessagesAndContents(2L)).thenReturn(session);

        var job = new ChatJob("run-1", 1L, 2L, 3L, null, null, null);
        when(irisMessageService.saveMessage(any(IrisMessage.class), eq(session), eq(IrisMessageSender.LLM))).thenAnswer(invocation -> {
            var message = invocation.getArgument(0, IrisMessage.class);
            message.setId(102L);
            return message;
        });

        var activity = new PyrisActivityDTO("activity-1", PyrisActivityKind.TOOL, "lecture_content_retrieval", PyrisActivityState.RUNNING, "Lecture 1", null, null);
        var statusUpdate = new PyrisChatStatusUpdateDTO("Let me check first", PyrisRunState.RUNNING, null, null, null, null, null, null, null, null, List.of(activity), 3, false);

        var updatedJob = irisChatSessionService.handleStatusUpdate(job, statusUpdate);

        assertThat(updatedJob).isSameAs(job);
        verify(pyrisJobService, never()).runWithJobLock(eq("run-1"), any());
        verify(pyrisJobService, never()).updateJob(any(PyrisJob.class));

        var messageCaptor = ArgumentCaptor.forClass(IrisMessage.class);
        verify(irisMessageService).saveMessage(messageCaptor.capture(), eq(session), eq(IrisMessageSender.LLM));
        var savedMessage = messageCaptor.getValue();
        assertThat(savedMessage.getIntermediate()).isTrue();
        assertThat(savedMessage.getToolActivity()).isNull();
        assertThat(IrisMessageResponseDTO.of(savedMessage).finalResult()).isFalse();

        verify(irisChatWebsocketService).sendMessage(eq(session), eq(savedMessage), eq(PyrisRunState.RUNNING), isNull(), isNull(), anyList(), eq("run-1"), isNull(), isNull(),
                eq(false));
    }
}
