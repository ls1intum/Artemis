package de.tum.in.www1.artemis.web.rest.dto.competency;

import de.tum.in.www1.artemis.domain.competency.Competency;

public record CompetencyNameDTO(long id, String title) {

    public static CompetencyNameDTO of(Competency competency) {
        return new CompetencyNameDTO(competency.getId(), competency.getTitle());
    }
}
