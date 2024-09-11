package de.tum.cit.aet.artemis.service.compass.umlmodel.classdiagram;

import static de.tum.cit.aet.artemis.service.compass.strategy.NameSimilarity.nameEqualsSimilarity;
import static de.tum.cit.aet.artemis.service.compass.utils.CompassConfiguration.RELATION_MULTIPLICITY_WEIGHT;
import static de.tum.cit.aet.artemis.service.compass.utils.CompassConfiguration.RELATION_ROLE_WEIGHT;

import java.io.Serializable;
import java.util.Objects;

import com.google.common.base.CaseFormat;

import de.tum.cit.aet.artemis.service.compass.umlmodel.Similarity;
import de.tum.cit.aet.artemis.service.compass.umlmodel.UMLElement;
import de.tum.cit.aet.artemis.service.compass.utils.CompassConfiguration;

public class UMLRelationship extends UMLElement implements Serializable {

    public enum UMLRelationshipType {

        CLASS_BIDIRECTIONAL, CLASS_UNIDIRECTIONAL, CLASS_INHERITANCE, CLASS_REALIZATION, CLASS_DEPENDENCY, CLASS_AGGREGATION, CLASS_COMPOSITION;

        /**
         * Converts the UMLRelationship to a representing human-readable string.
         *
         * @return the String which represents the relationship
         */
        public String toSymbol() {
            return switch (this) {
                case CLASS_DEPENDENCY -> " ╌╌> ";
                case CLASS_AGGREGATION -> " --◇ ";
                case CLASS_INHERITANCE -> " --▷ ";
                case CLASS_REALIZATION -> " ╌╌▷ ";
                case CLASS_COMPOSITION -> " --◆ ";
                case CLASS_UNIDIRECTIONAL -> " --> ";
                case CLASS_BIDIRECTIONAL -> " <-> ";
            };
        }
    }

    private UMLClass source;

    private UMLClass target;

    private String sourceRole;

    private String targetRole;

    private String sourceMultiplicity;

    private String targetMultiplicity;

    private UMLRelationshipType relationshipType;

    /**
     * to make mockito happy
     */
    public UMLRelationship() {
    }

    public UMLRelationship(UMLClass source, UMLClass target, UMLRelationshipType relationshipType, String jsonElementID, String sourceRole, String targetRole,
            String sourceMultiplicity, String targetMultiplicity) {
        super(jsonElementID);

        this.source = source;
        this.target = target;
        this.relationshipType = relationshipType;
        this.sourceMultiplicity = sourceMultiplicity;
        this.targetMultiplicity = targetMultiplicity;
        this.sourceRole = sourceRole;
        this.targetRole = targetRole;
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        if (!(reference instanceof UMLRelationship referenceRelationship)) {
            return 0;
        }

        double similarity = 0;

        similarity += referenceRelationship.getSource().similarity(getSource()) * CompassConfiguration.RELATION_ELEMENT_WEIGHT;
        similarity += referenceRelationship.getTarget().similarity(getTarget()) * CompassConfiguration.RELATION_ELEMENT_WEIGHT;

        similarity += nameEqualsSimilarity(referenceRelationship.getSourceRole(), getSourceRole()) * RELATION_ROLE_WEIGHT;
        similarity += nameEqualsSimilarity(referenceRelationship.getTargetRole(), getTargetRole()) * RELATION_ROLE_WEIGHT;
        similarity += nameEqualsSimilarity(referenceRelationship.getSourceMultiplicity(), getSourceMultiplicity()) * RELATION_MULTIPLICITY_WEIGHT;
        similarity += nameEqualsSimilarity(referenceRelationship.getTargetMultiplicity(), getTargetMultiplicity()) * RELATION_MULTIPLICITY_WEIGHT;

        // bidirectional associations can be swapped
        if (getRelationshipType() == UMLRelationshipType.CLASS_BIDIRECTIONAL) {
            double similarityReverse = 0;

            similarityReverse += referenceRelationship.getSource().similarity(getTarget()) * CompassConfiguration.RELATION_ELEMENT_WEIGHT;
            similarityReverse += referenceRelationship.getTarget().similarity(getSource()) * CompassConfiguration.RELATION_ELEMENT_WEIGHT;

            similarityReverse += nameEqualsSimilarity(referenceRelationship.getTargetRole(), getSourceRole()) * RELATION_ROLE_WEIGHT;
            similarityReverse += nameEqualsSimilarity(referenceRelationship.getSourceRole(), getTargetRole()) * RELATION_ROLE_WEIGHT;
            similarityReverse += nameEqualsSimilarity(referenceRelationship.getTargetMultiplicity(), getSourceMultiplicity()) * RELATION_MULTIPLICITY_WEIGHT;
            similarityReverse += nameEqualsSimilarity(referenceRelationship.getSourceMultiplicity(), getTargetMultiplicity()) * RELATION_MULTIPLICITY_WEIGHT;

            similarity = Math.max(similarity, similarityReverse);
        }

        if (Objects.equals(referenceRelationship.getRelationshipType(), getRelationshipType())) {
            similarity += CompassConfiguration.RELATION_TYPE_WEIGHT;
        }

        return ensureSimilarityRange(similarity);
    }

    @Override
    public String toString() {
        return "Relationship " + getSource().getName() + getRelationshipType().toSymbol() + getTarget().getName() + " (" + getType() + ")";
    }

    @Override
    public String getName() {
        return getType();
    }

    @Override
    public String getType() {
        return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, getRelationshipType().name());
    }

    public String getSourceRole() {
        return sourceRole;
    }

    public String getTargetRole() {
        return targetRole;
    }

    public String getSourceMultiplicity() {
        return sourceMultiplicity;
    }

    public String getTargetMultiplicity() {
        return targetMultiplicity;
    }

    public UMLRelationshipType getRelationshipType() {
        return relationshipType;
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
    public int hashCode() {
        return Objects.hash(super.hashCode(), source, target, sourceRole, targetRole, sourceMultiplicity, targetMultiplicity, relationshipType);
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        UMLRelationship otherRelationship = (UMLRelationship) obj;

        return Objects.equals(otherRelationship.getSource(), getSource()) && Objects.equals(otherRelationship.getTarget(), getTarget())
                && Objects.equals(otherRelationship.getSourceRole(), getSourceRole()) && Objects.equals(otherRelationship.getTargetRole(), getTargetRole())
                && Objects.equals(otherRelationship.getSourceMultiplicity(), getSourceMultiplicity())
                && Objects.equals(otherRelationship.getTargetMultiplicity(), getTargetMultiplicity());
    }
}
