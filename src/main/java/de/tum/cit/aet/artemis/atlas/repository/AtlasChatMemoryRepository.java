package de.tum.cit.aet.artemis.atlas.repository;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;

/**
 * Repository for transactional chat memory operations.
 * Wraps Spring AI's ChatMemoryRepository to provide proper transaction management.
 */
@Conditional(AtlasEnabled.class)
@Lazy
@Repository
public class AtlasChatMemoryRepository {

    private final ChatMemoryRepository chatMemoryRepository;

    public AtlasChatMemoryRepository(@Autowired(required = false) ChatMemoryRepository chatMemoryRepository) {
        this.chatMemoryRepository = chatMemoryRepository;
    }

    @Transactional
    public void deleteByConversationId(String conversationId) {
        if (chatMemoryRepository != null) {
            chatMemoryRepository.deleteByConversationId(conversationId);
        }
    }
}
