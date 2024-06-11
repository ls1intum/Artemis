package de.tum.in.www1.artemis.web.rest.dto.competency;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.competency.CompetencyProgress;
import de.tum.in.www1.artemis.service.competency.CompetencyProgressService;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyGraphNodeDTO(String id, String label, ZonedDateTime softDueDate, Double progress, Double confidence, Double mastery) {

    public static CompetencyGraphNodeDTO of(@NotNull CompetencyProgress competencyProgress) {
        return new CompetencyGraphNodeDTO(competencyProgress.getCompetency().getId().toString(), competencyProgress.getCompetency().getTitle(),
                competencyProgress.getCompetency().getSoftDueDate(), competencyProgress.getProgress(), competencyProgress.getConfidence(),
                CompetencyProgressService.getMastery(competencyProgress));
    }
}
