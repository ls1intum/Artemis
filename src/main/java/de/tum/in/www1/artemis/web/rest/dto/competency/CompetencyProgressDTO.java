package de.tum.in.www1.artemis.web.rest.dto.competency;

import de.tum.in.www1.artemis.domain.competency.CompetencyProgress;
import de.tum.in.www1.artemis.service.competency.CompetencyProgressService;

public record CompetencyProgressDTO(long id, String title, Double progress, Double confidence, Double mastery) {

    public static CompetencyProgressDTO of(CompetencyProgress competencyProgress) {
        return new CompetencyProgressDTO(competencyProgress.getCompetency().getId(), competencyProgress.getCompetency().getTitle(), competencyProgress.getProgress(),
                competencyProgress.getConfidence(), CompetencyProgressService.getMastery(competencyProgress));
    }
}
