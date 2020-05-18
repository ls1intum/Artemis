package de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram;

import static de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity.nameEqualsSimilarity;
import static de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration.RELATION_MULTIPLICITY_WEIGHT;
import static de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration.RELATION_ROLE_WEIGHT;

import java.util.Objects;

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
        if (!(reference instanceof UMLRelationship)) {
            return 0;
        }

        UMLRelationship referenceRelationship = (UMLRelationship) reference;

        double similarity = 0;

        similarity += referenceRelationship.getSource().similarity(source) * CompassConfiguration.RELATION_ELEMENT_WEIGHT;
        similarity += referenceRelationship.getTarget().similarity(target) * CompassConfiguration.RELATION_ELEMENT_WEIGHT;

        // if (isNotEmpty(referenceRelationship.sourceRole) || isNotEmpty(sourceRole)) {
        similarity += nameEqualsSimilarity(referenceRelationship.sourceRole, sourceRole) * RELATION_ROLE_WEIGHT;
        // }
        // if (isNotEmpty(referenceRelationship.targetRole) || isNotEmpty(targetRole)) {
        similarity += nameEqualsSimilarity(referenceRelationship.targetRole, targetRole) * RELATION_ROLE_WEIGHT;
        // }
        // if (isNotEmpty(referenceRelationship.sourceMultiplicity) || isNotEmpty(sourceMultiplicity)) {
        similarity += nameEqualsSimilarity(referenceRelationship.sourceMultiplicity, sourceMultiplicity) * RELATION_MULTIPLICITY_WEIGHT;
        // }
        // if (isNotEmpty(referenceRelationship.targetMultiplicity) || isNotEmpty(targetMultiplicity)) {
        similarity += nameEqualsSimilarity(referenceRelationship.targetMultiplicity, targetMultiplicity) * RELATION_MULTIPLICITY_WEIGHT;
        // }

        // bidirectional associations can be swapped
        if (type == UMLRelationshipType.CLASS_BIDIRECTIONAL) {
            double similarityReverse = 0;

            similarityReverse += nameEqualsSimilarity(referenceRelationship.targetRole, sourceRole) * RELATION_ROLE_WEIGHT;
            similarityReverse += nameEqualsSimilarity(referenceRelationship.sourceRole, targetRole) * RELATION_ROLE_WEIGHT;
            similarityReverse += nameEqualsSimilarity(referenceRelationship.targetMultiplicity, sourceMultiplicity) * RELATION_MULTIPLICITY_WEIGHT;
            similarityReverse += nameEqualsSimilarity(referenceRelationship.sourceMultiplicity, targetMultiplicity) * RELATION_MULTIPLICITY_WEIGHT;

            similarityReverse += referenceRelationship.getSource().similarity(target) * CompassConfiguration.RELATION_ELEMENT_WEIGHT;
            similarityReverse += referenceRelationship.getTarget().similarity(source) * CompassConfiguration.RELATION_ELEMENT_WEIGHT;

            similarity = Math.max(similarity, similarityReverse);
        }

        if (referenceRelationship.type == this.type) {
            similarity += CompassConfiguration.RELATION_TYPE_WEIGHT;
        }

        return ensureSimilarityRange(similarity);
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

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        UMLRelationship otherRelationship = (UMLRelationship) obj;

        return Objects.equals(otherRelationship.getSource(), source) && Objects.equals(otherRelationship.getTarget(), target)
                && Objects.equals(otherRelationship.sourceRole, sourceRole) && Objects.equals(otherRelationship.targetRole, targetRole)
                && Objects.equals(otherRelationship.sourceMultiplicity, sourceMultiplicity) && Objects.equals(otherRelationship.targetMultiplicity, targetMultiplicity);
    }
}
