package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

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
    void startTurnReplaysPlainTextFromEarlierSessions() {
        appendTurnWithToolCall("List the competencies", "call-1", "Here are the competencies");

        List<Message> history = AtlasChatSessions.startTurn(sessionService, SESSION_ID, "Follow-up");

        assertThat(history).extracting(Message::getText).containsExactly("List the competencies", "Here are the competencies");
        assertThat(history.getLast().getMetadata()).doesNotContainKey("provider-output");
        assertThat(sessionService.getMessages(SESSION_ID).getLast().getText()).isEqualTo("Follow-up");
    }

    @Test
    void startTurnReadsTheMostRecentMessagesInOrder() {
        for (int turn = 0; turn < 15; turn++) {
            sessionService.appendMessage(SESSION_ID, new UserMessage("question " + turn));
            sessionService.appendMessage(SESSION_ID, new AssistantMessage("answer " + turn));
        }

        List<Message> history = AtlasChatSessions.startTurn(sessionService, SESSION_ID, "Next question");

        assertThat(history).hasSize(20);
        assertThat(history.getFirst().getText()).isEqualTo("question 5");
        assertThat(history.getLast().getText()).isEqualTo("answer 14");
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

    private void appendTurnWithToolCall(String question, String callId, String answer) {
        sessionService.appendMessage(SESSION_ID, new UserMessage(question));
        sessionService.appendMessage(SESSION_ID,
                AssistantMessage.builder().toolCalls(List.of(new AssistantMessage.ToolCall(callId, "function", "getCourseCompetencies", "{}"))).build());
        sessionService.appendMessage(SESSION_ID,
                ToolResponseMessage.builder().responses(List.of(new ToolResponseMessage.ToolResponse(callId, "getCourseCompetencies", "[]"))).build());
        sessionService.appendMessage(SESSION_ID, AssistantMessage.builder().content(answer).properties(Map.of("provider-output", List.of(Map.of("text", answer)))).build());
    }
}
