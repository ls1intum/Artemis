package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import static de.tum.cit.aet.artemis.core.util.TimeUtil.toInstant;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;

/**
 * Pyris DTO mapping for a {@link Competency}.
 */
// @formatter:off
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisCompetencyDTO(
        long id,
        String title,
        String description,
        CompetencyTaxonomy taxonomy,
        Instant softDueDate,
        boolean optional
// @formatter:on
) {

    /**
     * Create a {@link PyrisCompetencyDTO} from a {@link Competency}.
     */
    public static PyrisCompetencyDTO from(Competency competency) {
        // @formatter:off
        return new PyrisCompetencyDTO(
                competency.getId(),
                competency.getTitle(),
                competency.getDescription(),
                competency.getTaxonomy(),
                toInstant(competency.getSoftDueDate()),
                competency.isOptional()
        );
        // @formatter:on
    }
}
