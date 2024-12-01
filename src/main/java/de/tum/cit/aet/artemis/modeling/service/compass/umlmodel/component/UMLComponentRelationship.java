package de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.component;

import java.util.Objects;

import com.google.common.base.CaseFormat;

import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.Similarity;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.UMLElement;
import de.tum.cit.aet.artemis.modeling.service.compass.utils.CompassConfiguration;

public class UMLComponentRelationship extends UMLElement {

    // NOTE: this is also used in deployment diagrams
    public enum UMLComponentRelationshipType {
        COMPONENT_INTERFACE_PROVIDED, COMPONENT_INTERFACE_REQUIRED, COMPONENT_DEPENDENCY, DEPLOYMENT_ASSOCIATION, DEPLOYMENT_DEPENDENCY, DEPLOYMENT_INTERFACE_PROVIDED,
        DEPLOYMENT_INTERFACE_REQUIRED
    }

    private final UMLElement source;

    private final UMLElement target;

    private final UMLComponentRelationshipType type;

    public UMLComponentRelationship(UMLElement source, UMLElement target, UMLComponentRelationshipType type, String jsonElementID) {
        super(jsonElementID);
        this.source = source;
        this.target = target;
        this.type = type;
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        if (!(reference instanceof UMLComponentRelationship referenceRelationship)) {
            return 0;
        }

        double similarity = 0;

        double sourceTargetSimilarity = 0;
        sourceTargetSimilarity += referenceRelationship.getSource().similarity(source) * CompassConfiguration.COMPONENT_RELATION_ELEMENT_WEIGHT;
        sourceTargetSimilarity += referenceRelationship.getTarget().similarity(target) * CompassConfiguration.COMPONENT_RELATION_ELEMENT_WEIGHT;

        double targetSourceSimilarity = 0;
        targetSourceSimilarity += referenceRelationship.getSource().similarity(target) * CompassConfiguration.COMPONENT_RELATION_ELEMENT_WEIGHT;
        targetSourceSimilarity += referenceRelationship.getTarget().similarity(source) * CompassConfiguration.COMPONENT_RELATION_ELEMENT_WEIGHT;

        if (type == UMLComponentRelationshipType.DEPLOYMENT_ASSOCIATION || type == UMLComponentRelationshipType.DEPLOYMENT_INTERFACE_PROVIDED
                || type == UMLComponentRelationshipType.COMPONENT_INTERFACE_PROVIDED) {
            similarity += Math.max(sourceTargetSimilarity, targetSourceSimilarity);
        }
        else {
            similarity += sourceTargetSimilarity;
        }

        if (referenceRelationship.type == this.type) {
            similarity += CompassConfiguration.RELATION_TYPE_WEIGHT;
        }

        return ensureSimilarityRange(similarity);
    }

    @Override
    public String toString() {
        return "Relationship " + getSource().getName() + " " + type + " " + getTarget().getName() + " (" + getType() + ")";
    }

    @Override
    public String getName() {
        return getType();
    }

    @Override
    public String getType() {
        return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, type.name());
    }

    public UMLComponentRelationshipType getRelationshipType() {
        return type;
    }

    public UMLElement getSource() {
        return source;
    }

    public UMLElement getTarget() {
        return target;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), source, target, type);
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        UMLComponentRelationship otherRelationship = (UMLComponentRelationship) obj;
        return Objects.equals(otherRelationship.getSource(), source) && Objects.equals(otherRelationship.getTarget(), target);
    }
}
