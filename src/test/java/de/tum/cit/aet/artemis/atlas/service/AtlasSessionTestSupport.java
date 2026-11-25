package de.tum.cit.aet.artemis.atlas.service;

import java.util.List;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.session.CreateSessionRequest;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.InMemorySessionRepository;
import org.springframework.ai.session.SessionService;

/**
 * Session memory for the Atlas unit tests: the real Spring AI session service on the in-memory repository, so that the writers and readers of the chat agent run
 * against a real conversation store instead of a mock.
 */
final class AtlasSessionTestSupport {

    private AtlasSessionTestSupport() {
        // utility class
    }

    /**
     * @return a session service on an empty in-memory repository
     */
    static SessionService newSessionService() {
        return DefaultSessionService.builder().sessionRepository(InMemorySessionRepository.builder().build()).build();
    }

    /**
     * Puts the messages into the session, in order, and creates the session when it does not exist yet.
     *
     * @param sessionService the session memory
     * @param sessionId      the id of the session
     * @param messages       the messages of the conversation
     */
    static void givenConversation(SessionService sessionService, String sessionId, List<? extends Message> messages) {
        sessionService.findOrCreate(CreateSessionRequest.builder().id(sessionId).build());
        messages.forEach(message -> sessionService.appendMessage(sessionId, message));
    }
}
