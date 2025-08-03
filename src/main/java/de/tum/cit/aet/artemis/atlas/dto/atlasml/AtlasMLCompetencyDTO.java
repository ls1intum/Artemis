package de.tum.cit.aet.artemis.atlas.dto.atlasml;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;

/**
 * DTO for AtlasML API communication representing a competency.
 * This matches the Python AtlasML API structure with single-letter taxonomy codes.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AtlasMLCompetencyDTO(@JsonProperty("id") String id, @JsonProperty("title") String title, @JsonProperty("description") String description,
        @JsonProperty("taxonomy") String taxonomy) {

    /**
     * Convert from domain Competency to AtlasML DTO.
     * Uses single-letter taxonomy codes as expected by AtlasML API.
     */
    public static AtlasMLCompetencyDTO fromDomain(Competency competency) {
        if (competency == null) {
            return null;
        }

        // Convert to single-letter taxonomy code using the converter
        String taxonomyCode = "U"; // Default to UNDERSTAND
        if (competency.getTaxonomy() != null) {
            taxonomyCode = new CompetencyTaxonomy.TaxonomyConverter().convertToDatabaseColumn(competency.getTaxonomy());
        }

        return new AtlasMLCompetencyDTO(competency.getId().toString(), competency.getTitle(), competency.getDescription(), taxonomyCode);
    }

    /**
     * Convert to domain Competency.
     * Note: This creates a basic competency without all the domain-specific fields.
     * Handles single-letter taxonomy codes from AtlasML API.
     */
    public Competency toDomain() {
        CompetencyTaxonomy taxonomy = CompetencyTaxonomy.UNDERSTAND; // Default
        if (this.taxonomy != null) {
            try {
                // Use the converter to handle single-letter codes
                taxonomy = new CompetencyTaxonomy.TaxonomyConverter().convertToEntityAttribute(this.taxonomy);
            }
            catch (IllegalArgumentException e) {
                // Use default if taxonomy code is invalid
            }
        }

        return new Competency(title, description, null, null, taxonomy, false);
    }
}
