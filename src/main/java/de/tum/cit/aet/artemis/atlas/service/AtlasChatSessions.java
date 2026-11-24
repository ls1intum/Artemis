package de.tum.cit.aet.artemis.atlas.service;

import java.util.List;
import java.util.Set;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.session.CreateSessionRequest;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionService;

/**
 * How the Atlas chat agent keeps its conversations in the Spring AI session memory.
 * <p>
 * A session holds everything the model saw and said, including every tool call and its result. The conversation that a user sees consists of the messages of the user and the
 * replies of the assistant only, so the readers below leave the rest out. The prompt gets the last {@link #PROMPT_HISTORY_MESSAGES} of them.
 */
final class AtlasChatSessions {

    /**
     * How many messages of the conversation go into the prompt of the chat agent. It is the size of the window that the chat memory kept before.
     */
    static final int PROMPT_HISTORY_MESSAGES = 20;

    private static final Set<MessageType> CONVERSATION_TYPES = Set.of(MessageType.USER, MessageType.ASSISTANT);

    private AtlasChatSessions() {
        // utility class
    }

    /**
     * The history that goes into the prompt: the last messages of the user and the assistant. The tool calls and results are not part of it. A call that is left without
     * its result, or the other way round, is kept out of the prompt by Spring AI, so the model never sees one without the other.
     *
     * @return the filter of the history that the memory advisor reads
     */
    static EventFilter promptHistory() {
        return EventFilter.builder().messageTypes(CONVERSATION_TYPES).lastN(PROMPT_HISTORY_MESSAGES).build();
    }

    /**
     * The conversation of a session in the order it happened: the messages of the user and the replies of the assistant, without the tool calls and their results.
     *
     * @param sessionService the session memory
     * @param sessionId      the id of the session
     * @return the messages, empty when the session does not exist
     */
    static List<Message> conversation(SessionService sessionService, String sessionId) {
        EventFilter filter = EventFilter.builder().messageTypes(CONVERSATION_TYPES).build();
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
}
