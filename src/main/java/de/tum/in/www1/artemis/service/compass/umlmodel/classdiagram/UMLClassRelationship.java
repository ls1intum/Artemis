package de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram;

import com.google.common.base.CaseFormat;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


public class UMLClassRelationship extends UMLElement {

    public enum UMLRelationType {
        // class diagram relations
        CLASS_BIDIRECTIONAL,
        CLASS_UNIDIRECTIONAL,
        CLASS_INHERITANCE,
        CLASS_REALIZATION,
        CLASS_DEPENDENCY,
        CLASS_AGGREGATION,
        CLASS_COMPOSITION;

        public static List<String> getTypesAsList() {
            return Arrays.stream(UMLRelationType.values())
                .map(umlRelationType -> CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, umlRelationType.name()))
                .collect(Collectors.toList());
        }

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

    private UMLRelationType type;

    public UMLClassRelationship(UMLClass source, UMLClass target, String type, String jsonElementID, String sourceRole, String targetRole,
                                String sourceMultiplicity, String targetMultiplicity) {
        this.source = source;
        this.target = target;
        this.sourceMultiplicity = sourceMultiplicity;
        this.targetMultiplicity = targetMultiplicity;
        this.sourceRole = sourceRole;
        this.targetRole = targetRole;

        this.setJsonElementID(jsonElementID);

        this.type = UMLRelationType.valueOf(type.toUpperCase());
    }

    /**
     * Compare this with another element to calculate the similarity
     *
     * @param element the element to compare with
     * @return the similarity as number [0-1]
     */
    @Override
    public double similarity(UMLElement element) {
        if (element.getClass() != UMLClassRelationship.class) {
            return 0;
        }

        UMLClassRelationship reference = (UMLClassRelationship) element;

        double similarity = 0;
        double weight = 1;

        similarity += reference.source.similarity(source) * CompassConfiguration.RELATION_ELEMENT_WEIGHT;
        similarity += reference.target.similarity(target) * CompassConfiguration.RELATION_ELEMENT_WEIGHT;

        if (!reference.sourceRole.isEmpty() || !this.sourceRole.isEmpty()) {
            if (reference.sourceRole.equals(this.sourceRole)) {
                similarity += CompassConfiguration.RELATION_ROLE_OPTIONAL_WEIGHT;
            }
            weight += CompassConfiguration.RELATION_ROLE_OPTIONAL_WEIGHT;
        }
        if (!reference.targetRole.isEmpty() || !this.targetRole.isEmpty()) {
            if (reference.targetRole.equals(this.targetRole)) {
                similarity += CompassConfiguration.RELATION_ROLE_OPTIONAL_WEIGHT;
            }
            weight += CompassConfiguration.RELATION_ROLE_OPTIONAL_WEIGHT;
        }
        if (!reference.sourceMultiplicity.isEmpty() || !this.sourceMultiplicity.isEmpty()) {
            if (reference.sourceMultiplicity.equals(this.sourceMultiplicity)) {
                similarity += CompassConfiguration.RELATION_MULTIPLICITY_OPTIONAL_WEIGHT;
            }
            weight += CompassConfiguration.RELATION_MULTIPLICITY_OPTIONAL_WEIGHT;
        }
        if (!reference.targetMultiplicity.isEmpty() || !this.targetMultiplicity.isEmpty()) {
            if (reference.targetMultiplicity.equals(this.targetMultiplicity)) {
                similarity += CompassConfiguration.RELATION_MULTIPLICITY_OPTIONAL_WEIGHT;
            }
            weight += CompassConfiguration.RELATION_MULTIPLICITY_OPTIONAL_WEIGHT;
        }

        // bidirectional associations can be swapped
        if (type == UMLRelationType.CLASS_BIDIRECTIONAL) {
            double similarityReverse = 0;

            if (reference.targetRole.equals(this.sourceRole)) {
                similarityReverse += CompassConfiguration.RELATION_ROLE_OPTIONAL_WEIGHT;
            }
            if (reference.sourceRole.equals(this.targetRole)) {
                similarityReverse += CompassConfiguration.RELATION_ROLE_OPTIONAL_WEIGHT;
            }
            if (reference.targetMultiplicity.equals(this.sourceMultiplicity)) {
                similarityReverse += CompassConfiguration.RELATION_MULTIPLICITY_OPTIONAL_WEIGHT;
            }
            if (reference.sourceMultiplicity.equals(this.targetMultiplicity)) {
                similarityReverse += CompassConfiguration.RELATION_MULTIPLICITY_OPTIONAL_WEIGHT;
            }

            similarityReverse += reference.source.similarity(target) * CompassConfiguration.RELATION_ELEMENT_WEIGHT;
            similarityReverse += reference.target.similarity(source) * CompassConfiguration.RELATION_ELEMENT_WEIGHT;

            similarity = Math.max(similarity, similarityReverse);
        }

        if (reference.type == this.type) {
            similarity += CompassConfiguration.RELATION_TYPE_WEIGHT;
        }

        return similarity / weight;
    }

    @Override
    public String getName() {
        return "Association " + getSource().getValue() + type.toSymbol() + getTarget().getValue() + " (" + getType() + ")";
    }

    @Override
    public String getValue() {
        return getType();
    }

    @Override
    public String getType() {
        return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, type.name());
    }

    public UMLClass getSource() {
        return source;
    }

    public UMLClass getTarget() {
        return target;
    }

}
