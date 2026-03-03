package de.tum.cit.aet.artemis.iris.api;

import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;

/**
 * API for exporting Iris (AI tutor) data for GDPR data export.
 * Provides access to all chat sessions and messages for a user.
 */
@Controller
@Conditional(IrisEnabled.class)
@Lazy
public class IrisDataExportApi extends AbstractIrisApi {

    private final IrisChatSessionRepository irisChatSessionRepository;

    public IrisDataExportApi(IrisChatSessionRepository irisChatSessionRepository) {
        this.irisChatSessionRepository = irisChatSessionRepository;
    }

    /**
     * Finds all chat sessions for a user with their messages loaded.
     * Uses a two-query approach to avoid PostgreSQL JSON equality comparison issues
     * that occur when using DISTINCT with JSON columns in a single query.
     *
     * @param userId the ID of the user
     * @return a set of all chat sessions with messages for the user
     */
    public Set<IrisChatSession> findAllChatSessionsWithMessagesByUserId(long userId) {
        Set<Long> sessionIds = irisChatSessionRepository.findSessionIdsByUserId(userId);
        if (sessionIds.isEmpty()) {
            return Set.of();
        }
        return irisChatSessionRepository.findAllWithMessagesByIds(sessionIds);
    }
}
