package de.tum.cit.aet.artemis.atlas.repository;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;

/**
 * Repository wrapper for Spring AI's ChatMemoryRepository.
 * Provides transactional access to chat memory operations.
 */
@Conditional(AtlasEnabled.class)
@Lazy
@Repository
public class AtlasChatMemoryRepository {

    private final ChatMemoryRepository chatMemoryRepository;

    public AtlasChatMemoryRepository(@Autowired(required = false) ChatMemoryRepository chatMemoryRepository) {
        this.chatMemoryRepository = chatMemoryRepository;
    }

    /**
     * Deletes all messages for a given conversation/session.
     *
     * @param conversationId the conversation/session ID
     */
    @Transactional
    public void deleteByConversationId(String conversationId) {
        if (chatMemoryRepository != null) {
            chatMemoryRepository.deleteByConversationId(conversationId);
        }
    }
}
