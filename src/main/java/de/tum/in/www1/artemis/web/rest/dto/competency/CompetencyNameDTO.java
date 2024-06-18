package de.tum.in.www1.artemis.web.rest.dto.competency;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.competency.Competency;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyNameDTO(long id, String title) {

    public static CompetencyNameDTO of(Competency competency) {
        return new CompetencyNameDTO(competency.getId(), competency.getTitle());
    }
}
