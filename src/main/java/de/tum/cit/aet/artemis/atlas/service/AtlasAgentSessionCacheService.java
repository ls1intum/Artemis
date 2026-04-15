package de.tum.cit.aet.artemis.atlas.service;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyRelationDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.CompetencyPreviewDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.CompetencyRelationPreviewDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.ExerciseCompetencyMappingDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.RelationGraphPreviewDTO;
import de.tum.cit.aet.artemis.atlas.service.CompetencyExpertToolsService.CompetencyOperation;

/**
 * Service for managing cached session data for the Atlas Agent.
 * Handles caching of pending competency and relation operations during multi-step agent interactions.
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
     * Must be configured in HazelcastConfiguration with appropriate TTL (2 hours recommended).
     */
    public static final String ATLAS_SESSION_PENDING_OPERATIONS_CACHE = "atlas-session-pending-operations";

    /**
     * Cache name for tracking pending relation operations for each session.
     * Must be configured in CacheConfiguration with appropriate TTL (2 hours recommended).
     */
    public static final String ATLAS_SESSION_PENDING_RELATIONS_CACHE = "atlas-session-pending-relations";

    /**
     * Cache name for exercise mapping preview DTOs.
     * Used as distributed fallback when the ThreadLocal preview is not available across nodes.
     */
    public static final String ATLAS_SESSION_EXERCISE_PREVIEW_CACHE = "atlas-session-exercise-preview";

    /**
     * Cache name for per-message preview data history.
     * Stores a map of assistant message index to preview data for each session,
     * enabling history reconstruction without embedding markers in chat memory.
     */
    public static final String ATLAS_SESSION_PREVIEW_HISTORY_CACHE = "atlas-session-preview-history";

    /**
     * Preview data associated with a single assistant message.
     */
    public record MessagePreviewData(@Nullable List<CompetencyPreviewDTO> competencyPreviews, @Nullable List<CompetencyRelationPreviewDTO> relationPreviews,
            @Nullable RelationGraphPreviewDTO relationGraphPreview, @Nullable ExerciseCompetencyMappingDTO exerciseMappingPreview) implements Serializable {
    }

    private final CacheManager cacheManager;

    /**
     * Per-session locks serializing read-modify-write on the preview history cache entry.
     * Prevents concurrent {@link #storePreviewForMessage} calls for the same session from
     * overwriting each other's history maps.
     */
    private final ConcurrentHashMap<String, Object> previewHistoryLocks = new ConcurrentHashMap<>();

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

    /**
     * Get the cached relation operations for a session.
     * Used by Competency Mapper to retrieve previous relation data.
     *
     * @param sessionId the session ID
     * @return the cached relation operations, or null if none exist
     */
    @SuppressWarnings("unchecked")
    public List<CompetencyRelationDTO> getCachedRelationData(String sessionId) {
        Cache cache = cacheManager.getCache(ATLAS_SESSION_PENDING_RELATIONS_CACHE);
        if (cache == null) {
            return null;
        }
        return (List<CompetencyRelationDTO>) cache.get(sessionId, List.class);
    }

    /**
     * Cache relation operations for a session.
     * Called after preview generation to enable tracking.
     *
     * @param sessionId  the session ID
     * @param operations the relation operations to cache
     */
    public void cacheRelationOperations(String sessionId, List<CompetencyRelationDTO> operations) {
        Cache cache = cacheManager.getCache(ATLAS_SESSION_PENDING_RELATIONS_CACHE);
        if (cache != null) {
            cache.put(sessionId, operations);
        }
    }

    /**
     * Clear cached relation operations for a session.
     * Called after successful save or when starting a new relation flow.
     *
     * @param sessionId the session ID
     */
    public void clearCachedRelationOperations(String sessionId) {
        Cache cache = cacheManager.getCache(ATLAS_SESSION_PENDING_RELATIONS_CACHE);
        if (cache != null) {
            cache.evict(sessionId);
        }
    }

    /**
     * Cache the exercise mapping preview for a session.
     * Written alongside the ThreadLocal so cross-node requests can retrieve it.
     *
     * @param sessionId the session ID
     * @param preview   the exercise mapping preview DTO
     */
    public void cacheExerciseMappingPreview(String sessionId, ExerciseCompetencyMappingDTO preview) {
        Cache cache = cacheManager.getCache(ATLAS_SESSION_EXERCISE_PREVIEW_CACHE);
        if (cache != null) {
            cache.put(sessionId, preview);
        }
    }

    /**
     * Retrieve the cached exercise mapping preview for a session.
     *
     * @param sessionId the session ID
     * @return the cached preview, or null if absent
     */
    public ExerciseCompetencyMappingDTO getCachedExerciseMappingPreview(String sessionId) {
        Cache cache = cacheManager.getCache(ATLAS_SESSION_EXERCISE_PREVIEW_CACHE);
        if (cache == null) {
            return null;
        }
        return cache.get(sessionId, ExerciseCompetencyMappingDTO.class);
    }

    /**
     * Evict the cached exercise mapping preview for a session.
     *
     * @param sessionId the session ID
     */
    public void clearCachedExerciseMappingPreview(String sessionId) {
        Cache cache = cacheManager.getCache(ATLAS_SESSION_EXERCISE_PREVIEW_CACHE);
        if (cache != null) {
            cache.evict(sessionId);
        }
    }

    /**
     * Store preview data for a specific assistant message in the session's preview history.
     *
     * @param sessionId    the session ID
     * @param messageIndex the 0-based index of the assistant message
     * @param previewData  the preview data to store
     */
    @SuppressWarnings("unchecked")
    public void storePreviewForMessage(String sessionId, int messageIndex, MessagePreviewData previewData) {
        Cache cache = cacheManager.getCache(ATLAS_SESSION_PREVIEW_HISTORY_CACHE);
        if (cache == null) {
            return;
        }
        // Serialize per-session read-modify-write: without this, concurrent calls for the same session
        // can both observe a null history, each create a fresh map, and the final cache.put leaves only
        // the last writer's map — silently dropping the loser's messageIndex entry.
        Object lock = previewHistoryLocks.computeIfAbsent(sessionId, k -> new Object());
        synchronized (lock) {
            Map<Integer, MessagePreviewData> history = cache.get(sessionId, Map.class);
            if (history == null) {
                history = new ConcurrentHashMap<>();
            }
            history.put(messageIndex, previewData);
            cache.put(sessionId, history);
        }
    }

    /**
     * Retrieve the full preview history for a session.
     *
     * @param sessionId the session ID
     * @return map of assistant message index to preview data, or empty map if none exist
     */
    @SuppressWarnings("unchecked")
    public Map<Integer, MessagePreviewData> getPreviewHistory(String sessionId) {
        Cache cache = cacheManager.getCache(ATLAS_SESSION_PREVIEW_HISTORY_CACHE);
        if (cache == null) {
            return Map.of();
        }
        Map<Integer, MessagePreviewData> history = cache.get(sessionId, Map.class);
        return history != null ? history : Map.of();
    }

    /**
     * Clear the preview history for a session.
     *
     * @param sessionId the session ID
     */
    public void clearPreviewHistory(String sessionId) {
        Cache cache = cacheManager.getCache(ATLAS_SESSION_PREVIEW_HISTORY_CACHE);
        if (cache != null) {
            cache.evict(sessionId);
        }
        previewHistoryLocks.remove(sessionId);
    }
}
