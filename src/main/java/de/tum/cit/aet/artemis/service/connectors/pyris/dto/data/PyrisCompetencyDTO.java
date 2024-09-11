package de.tum.cit.aet.artemis.service.connectors.pyris.dto.data;

import static de.tum.cit.aet.artemis.service.util.TimeUtil.toInstant;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisCompetencyDTO(long id, String title, String description, CompetencyTaxonomy taxonomy, Instant softDueDate, boolean optional) {

    public static PyrisCompetencyDTO of(Competency competency) {
        return new PyrisCompetencyDTO(competency.getId(), competency.getTitle(), competency.getDescription(), competency.getTaxonomy(), toInstant(competency.getSoftDueDate()),
                competency.isOptional());
    }
}
