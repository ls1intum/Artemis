package de.tum.cit.aet.artemis.atlas.dto.atlasml;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation;
import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;

/**
 * DTO for AtlasML API communication representing a competency relation.
 * This contains only IDs and matches the Python AtlasML API structure.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AtlasMLCompetencyRelationDTO(@JsonProperty("tail_id") @NotNull Long tailId, @JsonProperty("head_id") @NotNull Long headId,
        @JsonProperty("relation_type") @NotNull String relationType) {

    /**
     * Convert from domain CompetencyRelation to AtlasML DTO.
     */
    @Nullable
    public static AtlasMLCompetencyRelationDTO fromDomain(@Nullable CompetencyRelation relation) {
        if (relation == null) {
            return null;
        }

        @NotNull
        Long tailId = relation.getTailCompetency().getId();
        @NotNull
        Long headId = relation.getHeadCompetency().getId();
        @NotNull
        String relationType = relation.getType() != null ? relation.getType().name() : RelationType.ASSUMES.name();

        return new AtlasMLCompetencyRelationDTO(tailId, headId, relationType);
    }

    /**
     * Convert to domain CompetencyRelation.
     * Note: This creates a basic relation without full competency objects.
     */
    @NotNull
    public CompetencyRelation toDomain() {
        @NotNull
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
