package de.tum.cit.aet.artemis.atlas.dto.atlasml;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation;
import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;

/**
 * DTO for AtlasML API communication representing a competency relation.
 * This contains only IDs and matches the Python AtlasML API structure.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AtlasMLCompetencyRelationDTO(@JsonProperty("tail_id") String tailId, @JsonProperty("head_id") String headId, @JsonProperty("relation_type") String relationType) {

    /**
     * Convert from domain CompetencyRelation to AtlasML DTO.
     */
    public static AtlasMLCompetencyRelationDTO fromDomain(CompetencyRelation relation) {
        if (relation == null) {
            return null;
        }

        String tailId = relation.getTailCompetency() != null ? relation.getTailCompetency().getId().toString() : null;
        String headId = relation.getHeadCompetency() != null ? relation.getHeadCompetency().getId().toString() : null;
        String relationType = relation.getType() != null ? relation.getType().name() : RelationType.ASSUMES.name();

        return new AtlasMLCompetencyRelationDTO(tailId, headId, relationType);
    }

    /**
     * Convert to domain CompetencyRelation.
     * Note: This creates a basic relation without full competency objects.
     */
    public CompetencyRelation toDomain() {
        RelationType type = RelationType.ASSUMES; // Default
        if (this.relationType != null) {
            try {
                type = RelationType.valueOf(this.relationType);
            }
            catch (IllegalArgumentException e) {
                // Use default if relation type is invalid
            }
        }

        CompetencyRelation relation = new CompetencyRelation();
        relation.setType(type);
        // Note: tailCompetency and headCompetency would need to be set separately
        // as they require full CourseCompetency objects

        return relation;
    }
}
