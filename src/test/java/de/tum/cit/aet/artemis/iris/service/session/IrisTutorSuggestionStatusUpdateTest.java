package de.tum.cit.aet.artemis.iris.service.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.admin.domain.LLMRequest;
import de.tum.cit.aet.artemis.admin.domain.LLMTokenUsageTrace;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.session.IrisTutorSuggestionSession;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.TutorSuggestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ChatJob;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;

class IrisTutorSuggestionStatusUpdateTest {

    private IrisSessionRepository sessions;

    private IrisMessageService messages;

    private IrisChatWebsocketService websocket;

    private LLMTokenUsageService usage;

    private IrisTutorSuggestionSessionService service;

    private IrisTutorSuggestionSession session;

    private final ChatJob job = new ChatJob("job", 1L, 2L, null, null, null, null);

    @BeforeEach
    void setUp() {
        sessions = mock(IrisSessionRepository.class);
        messages = mock(IrisMessageService.class);
        websocket = mock(IrisChatWebsocketService.class);
        usage = mock(LLMTokenUsageService.class);
        service = new IrisTutorSuggestionSessionService(sessions, null, JsonObjectMapper.get(), messages, websocket, usage, null, null, null, null, null, null, null, null, null,
                null, null);
        var user = new User();
        user.setId(3L);
        session = new IrisTutorSuggestionSession(7L, user);
        session.setId(2L);
    }

    @ParameterizedTest
    @CsvSource({ "Artifact,Answer,2", "Artifact,,1", ",Answer,1", ",'',0", ",,0" })
    void statusSeparatesArtifactAndAssistantMessagesAndSkipsEmptyAnswers(String artifact, String result, int count) {
        when(sessions.findByIdWithMessagesAndContents(2L)).thenReturn(session);
        if (count > 0) {
            when(messages.saveMessage(any(), eq(session), any())).thenAnswer(call -> call.getArgument(0));
        }
        var status = new TutorSuggestionStatusUpdateDTO(artifact, result, PyrisRunState.RUNNING, null, List.of());
        assertThat(service.handleStatusUpdate(job, status)).isEqualTo(job);
        var captured = ArgumentCaptor.forClass(IrisMessage.class);
        var sender = ArgumentCaptor.forClass(IrisMessageSender.class);
        verify(messages, org.mockito.Mockito.times(count)).saveMessage(captured.capture(), eq(session), sender.capture());
        if (artifact != null) {
            assertThat(sender.getAllValues().getFirst()).isEqualTo(IrisMessageSender.ARTIFACT);
            assertThat(captured.getAllValues().getFirst().getContent().getFirst().getContentAsString()).isEqualTo(artifact);
        }
        if (result != null && !result.isEmpty()) {
            assertThat(sender.getAllValues().getLast()).isEqualTo(IrisMessageSender.LLM);
            assertThat(captured.getAllValues().getLast().getContent().getFirst().getContentAsString()).isEqualTo(result);
        }
        org.mockito.Mockito.verifyNoInteractions(usage);
    }

    @Test
    void tokenOnlyUpdateAppendsExistingTraceInsteadOfCreatingAnother() {
        when(sessions.findByIdWithMessagesAndContents(2L)).thenReturn(session);
        var existing = new LLMTokenUsageTrace();
        existing.setId(9L);
        when(usage.findLLMTokenUsageTraceById(9L)).thenReturn(Optional.of(existing));
        var tokens = List.of(new LLMRequest("model", 10, 1f, 5, 2f, "pipeline"));
        var traced = job.withTraceId(9L);
        assertThat(service.handleStatusUpdate(traced, new TutorSuggestionStatusUpdateDTO(null, null, PyrisRunState.RUNNING, null, tokens))).isEqualTo(traced);
        verify(usage).appendRequestsToTrace(tokens, existing);
    }

    @Test
    void tokenOnlyUpdateCreatesTraceWhenNoPreviousTraceExists() {
        when(sessions.findByIdWithMessagesAndContents(2L)).thenReturn(session);
        var trace = new LLMTokenUsageTrace();
        trace.setId(9L);
        when(usage.saveLLMTokenUsage(any(), any(), any())).thenReturn(trace);
        var tokens = List.of(new LLMRequest("model", 10, 1f, 5, 2f, "pipeline"));
        var result = service.handleStatusUpdate(job, new TutorSuggestionStatusUpdateDTO(null, null, PyrisRunState.RUNNING, null, tokens));
        assertThat(result.traceId()).isEqualTo(9L);
        assertThat(result.sessionId()).isEqualTo(2L);
    }

    @Test
    void deletedSessionDropsResultWithoutSavingOrSending() {
        assertThat(service.handleStatusUpdate(job, new TutorSuggestionStatusUpdateDTO("Artifact", "Answer", PyrisRunState.FINISHED, null, null))).isNull();
        org.mockito.Mockito.verifyNoInteractions(messages, websocket, usage);
    }
}
