package de.tum.in.www1.artemis.service.connectors.pyris.dto.data;

import java.time.Instant;

import de.tum.in.www1.artemis.domain.competency.CompetencyTaxonomy;

public record PyrisCompetencyDTO(long id, String title, String description, CompetencyTaxonomy taxonomy, Instant softDueDate, boolean optional) {
}
