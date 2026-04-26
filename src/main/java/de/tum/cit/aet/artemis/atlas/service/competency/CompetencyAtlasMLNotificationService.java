package de.tum.cit.aet.artemis.atlas.service.competency;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.api.AtlasMLApi;
import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SaveCompetencyRequestDTO.OperationTypeDTO;

/**
 * Shared helper that pushes competency mutations to AtlasML so its ML index stays in sync with
 * the database. Used by both the REST controller ({@link de.tum.cit.aet.artemis.atlas.web.CompetencyResource})
 * and the orchestrator's write tools ({@link de.tum.cit.aet.artemis.atlas.service.OrchestratorToolsService})
 * — every mutation path that writes a {@link Competency} must call this so the index does not
 * silently desync.
 * <p>
 * AtlasML failures are swallowed with a warn-log to keep the primary mutation flow non-blocking;
 * the index can be reconciled separately if needed.
 */
@Lazy
@Service
@Conditional(AtlasEnabled.class)
public class CompetencyAtlasMLNotificationService {

    private static final Logger log = LoggerFactory.getLogger(CompetencyAtlasMLNotificationService.class);

    private final Optional<AtlasMLApi> atlasMLApi;

    public CompetencyAtlasMLNotificationService(Optional<AtlasMLApi> atlasMLApi) {
        this.atlasMLApi = atlasMLApi;
    }

    /**
     * Notify AtlasML about competency changes with consistent error handling.
     *
     * @param competencies         the competencies to save
     * @param operationType        the operation type (UPDATE or DELETE)
     * @param operationDescription the description of the operation for logging purposes
     */
    public void notifyAtlasML(List<Competency> competencies, @NonNull OperationTypeDTO operationType, String operationDescription) {
        if (competencies == null || competencies.isEmpty()) {
            return;
        }
        try {
            atlasMLApi.ifPresent(api -> api.saveCompetencies(competencies, operationType));
        }
        catch (Exception e) {
            log.warn("Failed to notify AtlasML about {}: {}", operationDescription, e.getMessage());
        }
    }
}
