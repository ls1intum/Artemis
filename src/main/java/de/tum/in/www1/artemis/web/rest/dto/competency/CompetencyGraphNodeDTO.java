package de.tum.in.www1.artemis.web.rest.dto.competency;

import java.time.ZonedDateTime;
import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.CompetencyProgress;
import de.tum.in.www1.artemis.service.competency.CompetencyProgressService;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyGraphNodeDTO(String id, String label, ZonedDateTime softDueDate, Double progress, Double confidence, Double masteryProgress) {

    public static CompetencyGraphNodeDTO of(@NotNull Competency competency, @NotNull Optional<CompetencyProgress> competencyProgress) {
        return new CompetencyGraphNodeDTO(competency.getId().toString(), competency.getTitle(), competency.getSoftDueDate(),
                competencyProgress.map(CompetencyProgress::getProgress).orElse(0.0), competencyProgress.map(CompetencyProgress::getConfidence).orElse(1.0),
                competencyProgress.map(CompetencyProgressService::getMasteryProgress).orElse(0.0));
    }
}
