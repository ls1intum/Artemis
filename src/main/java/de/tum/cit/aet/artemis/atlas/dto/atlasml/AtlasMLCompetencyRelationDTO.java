package de.tum.cit.aet.artemis.atlas.dto.atlasml;

import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation;
import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;

/**
 * DTO for AtlasML API communication representing a competency relation.
 * This maps the AtlasML relation types to the domain relation types.
 */
public class AtlasMLCompetencyRelationDTO {

    @JsonProperty("tail_competency_id")
    private String tailCompetencyId;

    @JsonProperty("head_competency_id")
    private String headCompetencyId;

    @JsonProperty("relation_type")
    private String relationType;

    // Default constructor for Jackson
    public AtlasMLCompetencyRelationDTO() {
    }

    public AtlasMLCompetencyRelationDTO(String tailCompetencyId, String headCompetencyId, String relationType) {
        this.tailCompetencyId = tailCompetencyId;
        this.headCompetencyId = headCompetencyId;
        this.relationType = relationType;
    }

    public String getTailCompetencyId() {
        return tailCompetencyId;
    }

    public void setTailCompetencyId(String tailCompetencyId) {
        this.tailCompetencyId = tailCompetencyId;
    }

    public String getHeadCompetencyId() {
        return headCompetencyId;
    }

    public void setHeadCompetencyId(String headCompetencyId) {
        this.headCompetencyId = headCompetencyId;
    }

    public String getRelationType() {
        return relationType;
    }

    public void setRelationType(String relationType) {
        this.relationType = relationType;
    }

    /**
     * Convert from domain CompetencyRelation to AtlasML DTO.
     * Maps domain relation types to AtlasML relation types.
     */
    public static AtlasMLCompetencyRelationDTO fromDomain(CompetencyRelation relation) {
        if (relation == null) {
            return null;
        }

        String atlasMLRelationType = mapRelationTypeToAtlasML(relation.getType());
        String tailId = relation.getTailCompetency() != null ? relation.getTailCompetency().getId().toString() : null;
        String headId = relation.getHeadCompetency() != null ? relation.getHeadCompetency().getId().toString() : null;

        return new AtlasMLCompetencyRelationDTO(tailId, headId, atlasMLRelationType);
    }

    /**
     * Convert to domain CompetencyRelation.
     * Note: This creates a basic relation without the full domain objects.
     */
    public CompetencyRelation toDomain() {
        RelationType domainRelationType = mapAtlasMLToRelationType(this.relationType);

        CompetencyRelation relation = new CompetencyRelation();
        relation.setType(domainRelationType);
        // Note: tailCompetency and headCompetency would need to be set with actual domain objects
        return relation;
    }

    /**
     * Maps domain RelationType to AtlasML relation type string.
     */
    private static String mapRelationTypeToAtlasML(RelationType domainType) {
        if (domainType == null) {
            return "SUPERSET"; // Default
        }

        return switch (domainType) {
            case ASSUMES -> "SUPERSET"; // Assumes means tail requires head, so tail is superset of head
            case EXTENDS -> "SUBSET";   // Extends means tail builds on head, so tail is subset of head
            case MATCHES -> "SUPERSET"; // Matches could be considered superset for AtlasML
        };
    }

    /**
     * Maps AtlasML relation type string to domain RelationType.
     */
    private static RelationType mapAtlasMLToRelationType(String atlasMLType) {
        if (atlasMLType == null) {
            return RelationType.ASSUMES; // Default
        }

        return switch (atlasMLType.toUpperCase()) {
            case "SUPERSET" -> RelationType.ASSUMES;
            case "SUBSET" -> RelationType.EXTENDS;
            default -> RelationType.ASSUMES; // Default fallback
        };
    }

    @Override
    public String toString() {
        return "AtlasMLCompetencyRelationDTO{" + "tailCompetencyId='" + tailCompetencyId + '\'' + ", headCompetencyId='" + headCompetencyId + '\'' + ", relationType='"
                + relationType + '\'' + '}';
    }
}
