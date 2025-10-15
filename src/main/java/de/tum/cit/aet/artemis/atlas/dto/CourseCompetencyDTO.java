package de.tum.cit.aet.artemis.atlas.dto;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseCompetencyDTO(long id, @NotNull String title, @Nullable String description, @Nullable CompetencyTaxonomy taxonomy) {

    public static CourseCompetencyDTO of(CourseCompetency competency) {
        return new CourseCompetencyDTO(competency.getId(), competency.getTitle(), competency.getDescription(), competency.getTaxonomy());
    }
}
