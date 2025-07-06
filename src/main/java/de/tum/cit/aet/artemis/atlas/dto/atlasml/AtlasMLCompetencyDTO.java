package de.tum.cit.aet.artemis.atlas.dto.atlasml;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;

/**
 * DTO for AtlasML API communication representing a competency.
 * This is a simplified version that matches the Python AtlasML API structure.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AtlasMLCompetencyDTO(@JsonProperty("title") String title, @JsonProperty("description") String description, @JsonProperty("taxonomy") String taxonomy) {

    /**
     * Convert from domain Competency to AtlasML DTO.
     */
    public static AtlasMLCompetencyDTO fromDomain(Competency competency) {
        if (competency == null) {
            return null;
        }

        String taxonomyCode = competency.getTaxonomy() != null ? competency.getTaxonomy().name() : CompetencyTaxonomy.UNDERSTAND.name();

        return new AtlasMLCompetencyDTO(competency.getTitle(), competency.getDescription(), taxonomyCode);
    }

    /**
     * Convert to domain Competency.
     * Note: This creates a basic competency without all the domain-specific fields.
     */
    public Competency toDomain() {
        CompetencyTaxonomy taxonomy = CompetencyTaxonomy.UNDERSTAND; // Default
        if (this.taxonomy != null) {
            try {
                taxonomy = CompetencyTaxonomy.valueOf(this.taxonomy);
            }
            catch (IllegalArgumentException e) {
                // Use default if taxonomy is invalid
            }
        }

        return new Competency(title, description, null, null, taxonomy, false);
    }
}
