package de.tum.cit.aet.artemis.atlas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseCompetencyDTO(long id, String title, CompetencyTaxonomy taxonomy) {

    public static CourseCompetencyDTO of(CourseCompetency competency) {
        return new CourseCompetencyDTO(competency.getId(), competency.getTitle(), competency.getTaxonomy());
    }
}
