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
 * Pushes competency mutations to AtlasML so its ML index stays in sync with the database; every
 * write path on a {@link Competency} must call this. Failures are swallowed with a warn-log so the
 * primary mutation flow stays non-blocking.
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
     * Notifies AtlasML about competency changes with consistent error handling.
     *
     * @param competencies         the competencies to save
     * @param operationType        UPDATE or DELETE
     * @param operationDescription used in the warn-log on failure
     */
    public void notifyAtlasML(List<Competency> competencies, @NonNull OperationTypeDTO operationType, String operationDescription) {
        if (competencies == null || competencies.isEmpty()) {
            return;
        }
        atlasMLApi.ifPresent(api -> {
            try {
                if (!api.saveCompetencies(competencies, operationType)) {
                    log.warn("AtlasML reported failure for {}", operationDescription);
                }
            }
            catch (Exception e) {
                log.warn("Failed to notify AtlasML about {}: {}", operationDescription, e.getMessage());
            }
        });
    }
}
