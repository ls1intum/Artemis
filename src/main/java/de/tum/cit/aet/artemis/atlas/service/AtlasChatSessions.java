package de.tum.cit.aet.artemis.atlas.service;

import java.util.List;
import java.util.Set;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.session.CreateSessionRequest;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionService;

/**
 * Stores the Atlas chat transcript and prepares the recent conversation text for a new turn.
 */
final class AtlasChatSessions {

    /**
     * The prompt window size. Spring AI extends the window to the beginning of a turn when necessary.
     */
    private static final int PROMPT_HISTORY_MESSAGES = 20;

    private static final Set<MessageType> CONVERSATION_TYPES = Set.of(MessageType.USER, MessageType.ASSISTANT);

    private AtlasChatSessions() {
        // utility class
    }

    /**
     * Reads the prompt history once and records the incoming user message before invoking the model.
     *
     * @param sessionService the session memory
     * @param sessionId      the required id of the conversation
     * @param text           the incoming user message
     * @return the recent conversation as plain text messages, in chronological order
     */
    static List<Message> startTurn(SessionService sessionService, String sessionId, String text) {
        sessionService.findOrCreate(CreateSessionRequest.builder().id(sessionId).build());
        // The JDBC repository applies lastN in SQL for the log view. The active view loads the entire uncompacted log.
        EventFilter filter = EventFilter.builder().messageTypes(CONVERSATION_TYPES).lastN(PROMPT_HISTORY_MESSAGES).build();
        List<Message> history = conversation(sessionService, sessionId, filter).stream().map(AtlasChatSessions::plainText).toList();
        if (!text.isBlank()) {
            sessionService.appendMessage(sessionId, new UserMessage(text));
        }
        return history;
    }

    /**
     * The conversation of a session in the order it happened: the messages of the user and the replies of the assistant, without the tool calls and their results.
     *
     * @param sessionService the session memory
     * @param sessionId      the id of the session
     * @return the messages, empty when the session does not exist
     */
    static List<Message> conversation(SessionService sessionService, String sessionId) {
        return conversation(sessionService, sessionId, EventFilter.builder().messageTypes(CONVERSATION_TYPES).build());
    }

    private static List<Message> conversation(SessionService sessionService, String sessionId, EventFilter filter) {
        return sessionService.getEvents(sessionId, filter).stream().filter(SessionEvent::hasMessage).map(SessionEvent::getMessage).filter(AtlasChatSessions::isConversationMessage)
                .toList();
    }

    /**
     * Appends a reply of the assistant to the session and creates the session when it does not exist yet.
     *
     * @param sessionService the session memory
     * @param sessionId      the id of the session
     * @param text           the text of the reply
     */
    static void appendAssistantMessage(SessionService sessionService, String sessionId, String text) {
        sessionService.findOrCreate(CreateSessionRequest.builder().id(sessionId).build());
        sessionService.appendMessage(sessionId, new AssistantMessage(text));
    }

    private static boolean isConversationMessage(Message message) {
        // An assistant message that asks for tool calls is part of the tool loop, not of what the user reads
        return !(message instanceof AssistantMessage assistantMessage && assistantMessage.hasToolCalls());
    }

    private static Message plainText(Message message) {
        // A session written by an earlier build can contain provider-specific metadata that JDBC decodes as plain maps.
        return message instanceof AssistantMessage ? new AssistantMessage(message.getText()) : new UserMessage(message.getText());
    }
}
