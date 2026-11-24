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
 * replies of the assistant only, so the readers below leave the rest out. The prompt gets a window of the last {@link #PROMPT_HISTORY_EVENTS} of its events, see there.
 */
final class AtlasChatSessions {

    /**
     * The size of the window of the history that goes into the prompt of the chat agent: 20 was the size of the window of the chat memory before. The window of Spring AI counts
     * events, every message of the user and every message of the assistant, an assistant message that asks for a tool call included, and it never cuts a turn in two. A
     * conversation without tool calls therefore keeps its last 20 messages, and one with tool calls keeps fewer messages that a user reads: a turn with one tool call takes
     * three events for two such messages. The filter has no criterion to leave the tool calls out of the count.
     */
    static final int PROMPT_HISTORY_EVENTS = 20;

    private static final Set<MessageType> CONVERSATION_TYPES = Set.of(MessageType.USER, MessageType.ASSISTANT);

    private AtlasChatSessions() {
        // utility class
    }

    /**
     * The history that goes into the prompt: a window of the last {@link #PROMPT_HISTORY_EVENTS} events of the user and the assistant, the tool calls without their results
     * among them. A call that is left without its result, or the other way round, is kept out of the prompt by Spring AI, so the model never sees one without the other.
     *
     * @return the filter of the history that the memory advisor reads
     */
    static EventFilter promptHistory() {
        return EventFilter.builder().messageTypes(CONVERSATION_TYPES).lastN(PROMPT_HISTORY_EVENTS).build();
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
