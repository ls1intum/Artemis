package de.tum.in.www1.artemis.service.compass.umlmodel;

import de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration;


public class UMLAssociation extends UMLElement {

    // PLAIN is legacy
    // TODO move activity diagram types into its own class
    public enum UMLRelationType {
        PLAIN,
        DEPENDENCY,
        AGGREGATION,
        INHERITANCE,
        REALIZATION,
        COMPOSITION,
        ASSOCIATION_UNIDIRECTIONAL,
        ASSOCIATION_BIDIRECTIONAL,
        ACTIVITY_CONTROL_FLOW;

        public String toReadableString() {
            switch (this) {
                case DEPENDENCY:
                    return "Dependency";
                case AGGREGATION:
                    return "Aggregation";
                case INHERITANCE:
                    return "Inheritance";
                case REALIZATION:
                    return "Realization";
                case COMPOSITION:
                    return "Composition";
                case ASSOCIATION_UNIDIRECTIONAL:
                    return "Unidirectional";
                case ASSOCIATION_BIDIRECTIONAL:
                    return "Bidirectional";
                case ACTIVITY_CONTROL_FLOW:
                    return "Control Flow";
                default:
                    return "Other";
            }
        }

        public String toSymbol() {
            switch (this) {
                case DEPENDENCY:
                    return " ╌╌> ";
                case AGGREGATION:
                    return " --◇ ";
                case INHERITANCE:
                    return " --▷ ";
                case REALIZATION:
                    return " ╌╌▷ ";
                case COMPOSITION:
                    return " --◆ ";
                case ASSOCIATION_UNIDIRECTIONAL:
                    return " --> ";
                case ASSOCIATION_BIDIRECTIONAL:
                    return " <-> ";
                case ACTIVITY_CONTROL_FLOW:
                    return " --> ";
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

    public UMLAssociation(UMLClass source, UMLClass target, String type, String jsonElementID, String sourceRole, String targetRole,
                          String sourceMultiplicity, String targetMultiplicity) {
        this.source = source;
        this.target = target;
        this.sourceMultiplicity = sourceMultiplicity;
        this.targetMultiplicity = targetMultiplicity;
        this.sourceRole = sourceRole;
        this.targetRole = targetRole;

        this.jsonElementID = jsonElementID;

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
        if (element.getClass() != UMLAssociation.class) {
            return 0;
        }

        UMLAssociation reference = (UMLAssociation) element;

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
        if (type == UMLRelationType.ASSOCIATION_BIDIRECTIONAL) {
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
        return "Association " + getSource().getValue() + type.toSymbol() + getTarget().getValue() + " (" + type.toReadableString() + ")";
    }

    @Override
    public String getValue() {
        return type.toReadableString();
    }

    public UMLClass getSource() {
        return source;
    }

    public UMLClass getTarget() {
        return target;
    }

}
