package de.tum.cit.aet.artemis.iris.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;

/**
 * API for exporting Iris (AI tutor) data for GDPR data export.
 * Provides access to all chat sessions and messages for a user.
 */
@Controller
@Profile(PROFILE_IRIS)
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
