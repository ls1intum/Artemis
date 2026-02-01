package de.tum.cit.aet.artemis.atlas.service;

import java.util.List;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.service.CompetencyExpertToolsService.CompetencyOperation;

/**
 * Service for managing cached session data for the Atlas Agent.
 * Handles caching of pending competency operations during multi-step agent interactions.
 * <p>
 * This service is extracted from AtlasAgentService to break the circular dependency between
 * AtlasAgentService and CompetencyExpertToolsService. Both services can now depend on this
 * cache service without creating a cycle.
 */
@Lazy
@Service
@Conditional(AtlasEnabled.class)
public class AtlasAgentSessionCacheService {

    /**
     * Cache name for tracking pending competency operations for each session.
     * Must be configured in CacheConfiguration with appropriate TTL (2 hours recommended).
     */
    public static final String ATLAS_SESSION_PENDING_OPERATIONS_CACHE = "atlas-session-pending-operations";

    private final CacheManager cacheManager;

    public AtlasAgentSessionCacheService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * Get the cached pending competency operations for a session.
     * Used by Competency Expert to retrieve previous preview data for refinement.
     *
     * @param sessionId the session ID
     * @return the cached pending competency operations, or null if none exist
     */
    @SuppressWarnings("unchecked")
    public List<CompetencyOperation> getCachedPendingCompetencyOperations(String sessionId) {
        Cache cache = cacheManager.getCache(ATLAS_SESSION_PENDING_OPERATIONS_CACHE);
        if (cache == null) {
            return null;
        }
        return (List<CompetencyOperation>) cache.get(sessionId, List.class);
    }

    /**
     * Cache pending competency operations for a session.
     * Called after preview generation to enable deterministic refinements.
     *
     * @param sessionId  the session ID
     * @param operations the competency operations to cache
     */
    public void cachePendingCompetencyOperations(String sessionId, List<CompetencyOperation> operations) {
        Cache cache = cacheManager.getCache(ATLAS_SESSION_PENDING_OPERATIONS_CACHE);
        if (cache != null) {
            cache.put(sessionId, operations);
        }
    }

    /**
     * Clear cached pending competency operations for a session.
     * Called after successful save or when starting a new competency flow.
     *
     * @param sessionId the session ID
     */
    public void clearCachedPendingCompetencyOperations(String sessionId) {
        Cache cache = cacheManager.getCache(ATLAS_SESSION_PENDING_OPERATIONS_CACHE);
        if (cache != null) {
            cache.evict(sessionId);
        }
    }
}
