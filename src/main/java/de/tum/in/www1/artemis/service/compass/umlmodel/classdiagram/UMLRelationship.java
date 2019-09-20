package de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram;

import com.google.common.base.CaseFormat;
import de.tum.in.www1.artemis.service.compass.umlmodel.Similarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration;

public class UMLRelationship extends UMLElement {

    public enum UMLRelationshipType {
        CLASS_BIDIRECTIONAL, CLASS_UNIDIRECTIONAL, CLASS_INHERITANCE, CLASS_REALIZATION, CLASS_DEPENDENCY, CLASS_AGGREGATION, CLASS_COMPOSITION;

        /**
         * Converts the UMLRelationship to a representing human-readable string.
         *
         * @return the String which represents the relationship
         */
        public String toSymbol() {
            switch (this) {
            case CLASS_DEPENDENCY:
                return " ╌╌> ";
            case CLASS_AGGREGATION:
                return " --◇ ";
            case CLASS_INHERITANCE:
                return " --▷ ";
            case CLASS_REALIZATION:
                return " ╌╌▷ ";
            case CLASS_COMPOSITION:
                return " --◆ ";
            case CLASS_UNIDIRECTIONAL:
                return " --> ";
            case CLASS_BIDIRECTIONAL:
                return " <-> ";
            default:
                return " --- ";
            }
        }
    }

    private UMLClass source;

    private UMLClass target;

    private String sourceRole;

    private String targetRole;

    private String sourceMultiplicity;

    private String targetMultiplicity;

    private UMLRelationshipType type;

    public UMLRelationship(UMLClass source, UMLClass target, UMLRelationshipType type, String jsonElementID, String sourceRole, String targetRole, String sourceMultiplicity,
                           String targetMultiplicity) {
        super(jsonElementID);

        this.source = source;
        this.target = target;
        this.type = type;
        this.sourceMultiplicity = sourceMultiplicity;
        this.targetMultiplicity = targetMultiplicity;
        this.sourceRole = sourceRole;
        this.targetRole = targetRole;
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        if (reference == null || reference.getClass() != UMLRelationship.class) {
            return 0;
        }

        UMLRelationship referenceRelationship = (UMLRelationship) reference;

        double similarity = 0;
        double weight = 1;

        similarity += referenceRelationship.source.similarity(source) * CompassConfiguration.RELATION_ELEMENT_WEIGHT;
        similarity += referenceRelationship.target.similarity(target) * CompassConfiguration.RELATION_ELEMENT_WEIGHT;

        if (!referenceRelationship.sourceRole.isEmpty() || !this.sourceRole.isEmpty()) {
            if (referenceRelationship.sourceRole.equals(this.sourceRole)) {
                similarity += CompassConfiguration.RELATION_ROLE_OPTIONAL_WEIGHT;
            }
            weight += CompassConfiguration.RELATION_ROLE_OPTIONAL_WEIGHT;
        }
        if (!referenceRelationship.targetRole.isEmpty() || !this.targetRole.isEmpty()) {
            if (referenceRelationship.targetRole.equals(this.targetRole)) {
                similarity += CompassConfiguration.RELATION_ROLE_OPTIONAL_WEIGHT;
            }
            weight += CompassConfiguration.RELATION_ROLE_OPTIONAL_WEIGHT;
        }
        if (!referenceRelationship.sourceMultiplicity.isEmpty() || !this.sourceMultiplicity.isEmpty()) {
            if (referenceRelationship.sourceMultiplicity.equals(this.sourceMultiplicity)) {
                similarity += CompassConfiguration.RELATION_MULTIPLICITY_OPTIONAL_WEIGHT;
            }
            weight += CompassConfiguration.RELATION_MULTIPLICITY_OPTIONAL_WEIGHT;
        }
        if (!referenceRelationship.targetMultiplicity.isEmpty() || !this.targetMultiplicity.isEmpty()) {
            if (referenceRelationship.targetMultiplicity.equals(this.targetMultiplicity)) {
                similarity += CompassConfiguration.RELATION_MULTIPLICITY_OPTIONAL_WEIGHT;
            }
            weight += CompassConfiguration.RELATION_MULTIPLICITY_OPTIONAL_WEIGHT;
        }

        // bidirectional associations can be swapped
        if (type == UMLRelationshipType.CLASS_BIDIRECTIONAL) {
            double similarityReverse = 0;

            if (referenceRelationship.targetRole.equals(this.sourceRole)) {
                similarityReverse += CompassConfiguration.RELATION_ROLE_OPTIONAL_WEIGHT;
            }
            if (referenceRelationship.sourceRole.equals(this.targetRole)) {
                similarityReverse += CompassConfiguration.RELATION_ROLE_OPTIONAL_WEIGHT;
            }
            if (referenceRelationship.targetMultiplicity.equals(this.sourceMultiplicity)) {
                similarityReverse += CompassConfiguration.RELATION_MULTIPLICITY_OPTIONAL_WEIGHT;
            }
            if (referenceRelationship.sourceMultiplicity.equals(this.targetMultiplicity)) {
                similarityReverse += CompassConfiguration.RELATION_MULTIPLICITY_OPTIONAL_WEIGHT;
            }

            similarityReverse += referenceRelationship.source.similarity(target) * CompassConfiguration.RELATION_ELEMENT_WEIGHT;
            similarityReverse += referenceRelationship.target.similarity(source) * CompassConfiguration.RELATION_ELEMENT_WEIGHT;

            similarity = Math.max(similarity, similarityReverse);
        }

        if (referenceRelationship.type == this.type) {
            similarity += CompassConfiguration.RELATION_TYPE_WEIGHT;
        }

        return ensureSimilarityRange(similarity / weight);
    }

    @Override
    public String toString() {
        return "Relationship " + getSource().getName() + type.toSymbol() + getTarget().getName() + " (" + getType() + ")";
    }

    @Override
    public String getName() {
        return getType();
    }

    @Override
    public String getType() {
        return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, type.name());
    }

    /**
     * Get the source of this UML relationship, i.e. the UML class where the relationship starts.
     *
     * @return the source UML class of this relationship
     */
    public UMLClass getSource() {
        return source;
    }

    /**
     * Get the target of this UML relationship, i.e. the UML class where the relationship ends.
     *
     * @return the target UML class of this relationship
     */
    public UMLClass getTarget() {
        return target;
    }

}
