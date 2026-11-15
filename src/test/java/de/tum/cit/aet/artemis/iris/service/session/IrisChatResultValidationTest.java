package de.tum.cit.aet.artemis.iris.service.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import tools.jackson.databind.node.ObjectNode;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.iris.domain.message.IrisJsonMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ChatJob;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;

class IrisChatResultValidationTest {

    private static final String VALID_MCQ = """
            {"type":"mcq","question":"What is {x}?","options":[{"text":"A","correct":true},{"text":"B","correct":false}],"explanation":"Use braces."}
            """.strip();

    private IrisSessionRepository sessions;

    private IrisMessageService messages;

    private IrisChatWebsocketService websocket;

    private de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository messageRepository;

    private de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService jobs;

    private de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService usage;

    private AbstractIrisChatSessionService<IrisChatSession> service;

    private IrisChatSession session;

    private final ChatJob job = new ChatJob("job", 1L, 2L, null, null, null, null);

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        sessions = mock(IrisSessionRepository.class);
        messages = mock(IrisMessageService.class);
        websocket = mock(IrisChatWebsocketService.class);
        messageRepository = mock(de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository.class);
        jobs = mock(de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService.class);
        usage = mock(de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService.class);
        service = mock(AbstractIrisChatSessionService.class,
                withSettings().useConstructor(sessions, null, null, JsonObjectMapper.get(), messages, messageRepository, websocket, usage, jobs).defaultAnswer(CALLS_REAL_METHODS));
        session = new IrisChatSession();
        session.setId(2L);
    }

    @ParameterizedTest
    @MethodSource("malformedMcqs")
    void malformedQuizPayloadBecomesHelpfulTextInsteadOfBrokenInteractiveContent(String payload) {
        IrisMessage message = handleIntermediate(payload);
        assertThat(message.getContent()).singleElement().isInstanceOfSatisfying(IrisTextMessageContent.class,
                content -> assertThat(content.getTextContent()).isEqualTo("Sorry, I tried to generate a quiz question but the response was malformed. Please try again."));
        assertThat(message.getIntermediate()).isTrue();
    }

    static Stream<String> malformedMcqs() {
        var mapper = JsonObjectMapper.get();
        var payloads = new java.util.ArrayList<String>();
        for (String field : List.of("question", "options", "explanation")) {
            ObjectNode absent = (ObjectNode) mapper.readTree(VALID_MCQ);
            absent.remove(field);
            payloads.add(absent.toString());
            ObjectNode wrongType = (ObjectNode) mapper.readTree(VALID_MCQ);
            wrongType.put(field, 7);
            payloads.add(wrongType.toString());
            ObjectNode blank = (ObjectNode) mapper.readTree(VALID_MCQ);
            blank.put(field, " ");
            payloads.add(blank.toString());
        }
        payloads.add(VALID_MCQ.replace("{\"text\":\"B\",\"correct\":false}", "7"));
        payloads.add(VALID_MCQ.replace("\"correct\":false", "\"correct\":true"));
        payloads.add(VALID_MCQ.replace("\"correct\":true", "\"correct\":false"));
        payloads.add(VALID_MCQ.replace("{\"text\":\"B\",\"correct\":false}", "{}"));
        payloads.add(VALID_MCQ.replace("\"text\":\"B\"", "\"text\":2"));
        payloads.add(VALID_MCQ.replace("\"text\":\"B\"", "\"text\":\" \""));
        payloads.add(VALID_MCQ.replace("\"correct\":false", "\"other\":false"));
        payloads.add(VALID_MCQ.replace("\"correct\":false", "\"correct\":\"false\""));
        payloads.add(VALID_MCQ.replace(",{\"text\":\"B\",\"correct\":false}", ""));
        payloads.add("{\"type\":\"mcq-set\"}");
        payloads.add("{\"type\":\"mcq-set\",\"questions\":[]}");
        payloads.add("{\"type\":\"mcq-set\",\"questions\":{}}");
        payloads.add("{\"type\":\"mcq-set\",\"questions\":[{}]}");
        payloads.add("Prefix {\"type\":\"mcq\",\"question\":\"\"} suffix");
        return payloads.stream();
    }

    @Test
    void validMixedQuizSetRetainsTextAndDoesNotDuplicateNestedQuizObjects() {
        String set = "{\"type\":\"mcq-set\",\"questions\":[" + VALID_MCQ + "]}";
        IrisMessage message = handleIntermediate("Before\n" + set + "\nAfter");
        assertThat(message.getContent()).hasSize(3);
        assertThat(message.getContent().get(0)).isInstanceOfSatisfying(IrisTextMessageContent.class, text -> assertThat(text.getTextContent()).isEqualTo("Before"));
        assertThat(message.getContent().get(1)).isInstanceOfSatisfying(IrisJsonMessageContent.class,
                json -> assertThat(JsonObjectMapper.get().readTree(json.getContentAsString()).get("questions")).hasSize(1));
        assertThat(message.getContent().get(2)).isInstanceOfSatisfying(IrisTextMessageContent.class, text -> assertThat(text.getTextContent()).isEqualTo("After"));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = { "{\"other\":true}", "{\"type\":\"other\"}", "Prefix {\"type\":\"mcq\",broken}",
            "Prefix {\"type\":\"mcq\",\"question\":\"unfinished" })
    void nonQuizOrUnparseableEmbeddedJsonRemainsPlainText(String input) {
        assertThat(handleIntermediate(input).getContent()).singleElement().isInstanceOfSatisfying(IrisTextMessageContent.class,
                text -> assertThat(text.getTextContent()).isEqualTo(input));
    }

    @Test
    void whitespaceAroundEmbeddedQuizDoesNotCreateEmptyTextSegments() {
        IrisMessage message = handleIntermediate("  " + VALID_MCQ + "  ");
        assertThat(message.getContent()).singleElement().isInstanceOf(IrisJsonMessageContent.class);
    }

    @Test
    void deletedSessionDropsStatusAndPartialUpdates() {
        when(sessions.findById(2L)).thenReturn(Optional.empty());
        var status = new PyrisChatStatusUpdateDTO(null, PyrisRunState.RUNNING, null, null, null, null);
        assertThat(service.handleStatusUpdate(job, status)).isNull();
        assertThat(service.handlePartialStatusUpdate(job, status)).isFalse();
        org.mockito.Mockito.verifyNoInteractions(messages, websocket);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = { " ", "Summary" })
    void compactionPersistsOnlyNonblankSummariesAsHiddenJsonMessages(String summary) {
        when(sessions.findById(2L)).thenReturn(Optional.of(session));
        var compaction = new de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisCompactionDTO(summary, 7L);
        var status = new PyrisChatStatusUpdateDTO(null, PyrisRunState.RUNNING, null, null, List.of(), List.of(), null, null, null, null, null, null, compaction);
        assertThat(service.handleStatusUpdate(job, status)).isEqualTo(job);
        var saved = ArgumentCaptor.forClass(IrisMessage.class);
        verify(messages, org.mockito.Mockito.times(summary != null && !summary.isBlank() ? 1 : 0)).saveMessage(saved.capture(), eq(session), eq(IrisMessageSender.SUMMARY));
        if (summary != null && !summary.isBlank()) {
            assertThat(saved.getValue().getContent()).singleElement().isInstanceOfSatisfying(IrisJsonMessageContent.class, json -> {
                assertThat(JsonObjectMapper.get().readTree(json.getContentAsString()).get("summary").asString()).isEqualTo("Summary");
                assertThat(JsonObjectMapper.get().readTree(json.getContentAsString()).get("coversThroughMessageId").asLong()).isEqualTo(7L);
            });
        }
    }

    @Test
    void statusOnlyTokensAppendExistingTraceAndSuggestionsUseScalarUpdate() {
        when(sessions.findById(2L)).thenReturn(Optional.of(session));
        var trace = new de.tum.cit.aet.artemis.admin.domain.LLMTokenUsageTrace();
        trace.setId(9L);
        when(usage.findLLMTokenUsageTraceById(9L)).thenReturn(Optional.of(trace));
        var tokens = List.of(new de.tum.cit.aet.artemis.admin.domain.LLMRequest("model", 10, 1f, 5, 1f, "pipeline"));
        var tracedJob = job.withTraceId(9L);
        var status = new PyrisChatStatusUpdateDTO(null, PyrisRunState.RUNNING, null, null, List.of("Try again"), tokens);
        assertThat(service.handleStatusUpdate(tracedJob, status)).isEqualTo(tracedJob);
        verify(usage).appendRequestsToTrace(tokens, trace);
        assertThat(session.getLatestSuggestions()).isEqualTo("[\"Try again\"]");
        verify(sessions).updateLatestSuggestions(2L, "[\"Try again\"]");
        verify(sessions, org.mockito.Mockito.never()).save(any());
    }

    private IrisMessage handleIntermediate(String result) {
        when(sessions.findByIdWithMessagesAndContents(2L)).thenReturn(session);
        when(messages.saveMessage(any(), eq(session), eq(IrisMessageSender.LLM))).thenAnswer(call -> call.getArgument(0));
        var status = new PyrisChatStatusUpdateDTO(result, PyrisRunState.RUNNING, null, null, List.of(), List.of(), null, null, null, null, false);
        assertThat(service.handleStatusUpdate(job, status)).isEqualTo(job);
        var saved = ArgumentCaptor.forClass(IrisMessage.class);
        verify(messages).saveMessage(saved.capture(), eq(session), eq(IrisMessageSender.LLM));
        return saved.getValue();
    }
}
