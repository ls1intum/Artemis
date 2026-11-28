package de.tum.cit.aet.artemis.atlas.service;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.session.SessionService;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasLLMEnabled;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.BatchRelationPreviewResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.CompetencyRelationPreviewDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.SingleRelationPreviewResponseDTO;

/**
 * Service for managing preview data utilities in Atlas Agent chat responses.
 * Provides relation preview conversion and chat memory helpers.
 */
@Lazy
@Service
@Conditional(AtlasLLMEnabled.class)
public class AtlasAgentPreviewService {

    private final SessionService sessionService;

    public AtlasAgentPreviewService(@Nullable SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /**
     * Converts single/batch relation preview DTOs to a unified list of CompetencyRelationPreviewDTO.
     *
     * @param singleRelationPreview Optional single relation preview
     * @param batchRelationPreview  Optional batch relation preview
     * @return Unified list of relation previews, or null if no previews exist
     */
    public List<CompetencyRelationPreviewDTO> convertToRelationPreviewsList(@Nullable SingleRelationPreviewResponseDTO singleRelationPreview,
            @Nullable BatchRelationPreviewResponseDTO batchRelationPreview) {
        List<CompetencyRelationPreviewDTO> result = new ArrayList<>();

        if (singleRelationPreview != null && singleRelationPreview.preview() && singleRelationPreview.relation() != null) {
            result.add(singleRelationPreview.relation());
        }

        if (batchRelationPreview != null && batchRelationPreview.batchPreview() && batchRelationPreview.relations() != null) {
            result.addAll(batchRelationPreview.relations());
        }

        return result.isEmpty() ? null : result;
    }

    /**
     * Adds an assistant message directly to chat memory.
     * Used for plan continuation responses that are generated with saveToMemory=false.
     *
     * @param sessionId the session ID
     * @param message   the assistant message text
     */
    public void addAssistantMessageToMemory(String sessionId, String message) {
        if (sessionService != null) {
            AtlasChatSessions.appendAssistantMessage(sessionService, sessionId, message);
        }
    }
}
