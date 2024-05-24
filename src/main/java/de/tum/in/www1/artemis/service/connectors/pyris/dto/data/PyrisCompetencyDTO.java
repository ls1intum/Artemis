package de.tum.in.www1.artemis.service.connectors.pyris.dto.data;

import static de.tum.in.www1.artemis.service.util.ZonedDateTimeUtil.toInstant;

import java.time.Instant;

import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.CompetencyTaxonomy;

public record PyrisCompetencyDTO(long id, String title, String description, CompetencyTaxonomy taxonomy, Instant softDueDate, boolean optional) {

    public static PyrisCompetencyDTO of(Competency competency) {
        return new PyrisCompetencyDTO(competency.getId(), competency.getTitle(), competency.getDescription(), competency.getTaxonomy(), toInstant(competency.getSoftDueDate()),
                competency.isOptional());
    }
}
