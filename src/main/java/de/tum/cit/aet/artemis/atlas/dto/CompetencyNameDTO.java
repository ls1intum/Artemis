package de.tum.cit.aet.artemis.atlas.dto;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyProgress;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyProgressService;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyNameDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id, String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double masteryProgress) {

    public static CompetencyNameDTO of(CourseCompetency competency) {
        Optional<CompetencyProgress> optionalProgress = competency.getUserProgress().stream().findFirst();
        return new CompetencyNameDTO(competency.getId(), competency.getTitle(), optionalProgress.map(CompetencyProgressService::getMasteryProgress).orElse(0.0));
    }
}
