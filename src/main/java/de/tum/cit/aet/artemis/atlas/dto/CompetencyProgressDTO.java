package de.tum.cit.aet.artemis.atlas.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.CompetencyProgressConfidenceReason;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyProgress;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyProgressDTO(@Nullable Double progress, @Nullable Double confidence, @Nullable CompetencyProgressConfidenceReason confidenceReason) {

    public static @Nullable CompetencyProgressDTO of(@Nullable CompetencyProgress progress) {
        if (progress == null) {
            return null;
        }
        return new CompetencyProgressDTO(progress.getProgress(), progress.getConfidence(), progress.getConfidenceReason());
    }
}
