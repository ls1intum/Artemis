package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.session.CreateSessionRequest;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.InMemorySessionRepository;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionService;

class AtlasChatSessionsTest {

    private static final String SESSION_ID = "course_1_user_2";

    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionService = DefaultSessionService.builder().sessionRepository(InMemorySessionRepository.builder().build()).build();
        sessionService.findOrCreate(CreateSessionRequest.builder().id(SESSION_ID).build());
    }

    @Test
    void promptHistoryKeepsAToolCallTogetherWithItsResult() {
        appendTurnWithToolCall("List the competencies", "call-1", "Here are the competencies");

        List<MessageType> types = promptHistoryTypes();

        assertThat(types).containsExactly(MessageType.USER, MessageType.ASSISTANT, MessageType.TOOL, MessageType.ASSISTANT);
    }

    @Test
    void promptHistoryWindowCountsEveryEventAndNeverSplitsAToolCall() {
        for (int turn = 0; turn < 10; turn++) {
            appendTurnWithToolCall("question " + turn, "call-" + turn, "answer " + turn);
        }

        List<MessageType> types = promptHistoryTypes();

        assertThat(types).hasSizeLessThanOrEqualTo(AtlasChatSessions.PROMPT_HISTORY_EVENTS);
        assertThat(types.stream().filter(type -> type == MessageType.TOOL).count()).isEqualTo(types.stream().filter(type -> type == MessageType.USER).count());
        assertThat(types.getFirst()).isEqualTo(MessageType.USER);
        assertThat(types.getLast()).isEqualTo(MessageType.ASSISTANT);
    }

    @Test
    void promptHistoryOfAConversationWithoutToolCallsKeepsItsLastMessages() {
        for (int turn = 0; turn < 15; turn++) {
            sessionService.appendMessage(SESSION_ID, new UserMessage("question " + turn));
            sessionService.appendMessage(SESSION_ID, new AssistantMessage("answer " + turn));
        }

        assertThat(promptHistoryTypes()).hasSize(AtlasChatSessions.PROMPT_HISTORY_EVENTS);
    }

    @Test
    void conversationLeavesToolCallsAndTheirResultsOut() {
        appendTurnWithToolCall("List the competencies", "call-1", "Here are the competencies");

        List<Message> conversation = AtlasChatSessions.conversation(sessionService, SESSION_ID);

        assertThat(conversation).extracting(Message::getMessageType).containsExactly(MessageType.USER, MessageType.ASSISTANT);
        assertThat(conversation).extracting(Message::getText).containsExactly("List the competencies", "Here are the competencies");
    }

    @Test
    void appendAssistantMessageCreatesTheSessionWhenItDoesNotExist() {
        AtlasChatSessions.appendAssistantMessage(sessionService, "course_9_user_9", "Hello");

        assertThat(AtlasChatSessions.conversation(sessionService, "course_9_user_9")).extracting(Message::getText).containsExactly("Hello");
    }

    private List<MessageType> promptHistoryTypes() {
        return sessionService.getEvents(SESSION_ID, AtlasChatSessions.promptHistory()).stream().filter(SessionEvent::hasMessage).map(event -> event.getMessage().getMessageType())
                .toList();
    }

    private void appendTurnWithToolCall(String question, String callId, String answer) {
        sessionService.appendMessage(SESSION_ID, new UserMessage(question));
        sessionService.appendMessage(SESSION_ID,
                AssistantMessage.builder().toolCalls(List.of(new AssistantMessage.ToolCall(callId, "function", "getCourseCompetencies", "{}"))).build());
        sessionService.appendMessage(SESSION_ID,
                ToolResponseMessage.builder().responses(List.of(new ToolResponseMessage.ToolResponse(callId, "getCourseCompetencies", "[]"))).build());
        sessionService.appendMessage(SESSION_ID, new AssistantMessage(answer));
    }
}
