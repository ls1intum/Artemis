package de.tum.cit.aet.artemis.atlas.dto.atlasml;

import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;

/**
 * DTO for AtlasML API communication representing a competency.
 * This is a simplified version that matches the Python AtlasML API structure.
 */
public class AtlasMLCompetencyDTO {

    @JsonProperty("title")
    private String title;

    @JsonProperty("description")
    private String description;

    @JsonProperty("taxonomy")
    private String taxonomy;

    // Default constructor for Jackson
    public AtlasMLCompetencyDTO() {
    }

    public AtlasMLCompetencyDTO(String title, String description, String taxonomy) {
        this.title = title;
        this.description = description;
        this.taxonomy = taxonomy;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTaxonomy() {
        return taxonomy;
    }

    public void setTaxonomy(String taxonomy) {
        this.taxonomy = taxonomy;
    }

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

    @Override
    public String toString() {
        return "AtlasMLCompetencyDTO{" + "title='" + title + '\'' + ", description='" + description + '\'' + ", taxonomy='" + taxonomy + '\'' + '}';
    }
}
