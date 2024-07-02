package de.tum.in.www1.artemis.web.rest.dto.competency;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.CompetencyProgress;
import de.tum.in.www1.artemis.service.competency.CompetencyProgressService;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyNameDTO(long id, String title, double masteryProgress) {

    public static CompetencyNameDTO of(Competency competency) {
        Optional<CompetencyProgress> optionalProgress = competency.getUserProgress().stream().findFirst();
        return new CompetencyNameDTO(competency.getId(), competency.getTitle(), optionalProgress.map(CompetencyProgressService::getMasteryProgress).orElse(0.0));
    }
}
