package de.tum.in.www1.artemis.service.compass.umlmodel.component;

import java.util.Objects;

import com.google.common.base.CaseFormat;

import de.tum.in.www1.artemis.service.compass.umlmodel.Similarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration;

public class UMLComponentRelationship extends UMLElement {

    public enum UMLComponentInterfaceType {
        PROVIDED, REQUIRED, DEPENDENCY
    }

    private UMLElement source;

    private UMLElement target;

    private UMLComponentInterfaceType type;

    public UMLComponentRelationship(UMLElement source, UMLElement target, UMLComponentInterfaceType type, String jsonElementID) {
        super(jsonElementID);
        this.source = source;
        this.target = target;
        this.type = type;
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        if (!(reference instanceof UMLComponentRelationship)) {
            return 0;
        }

        UMLComponentRelationship referenceRelationship = (UMLComponentRelationship) reference;

        double similarity = 0;

        similarity += referenceRelationship.getSource().similarity(source) * CompassConfiguration.RELATION_ELEMENT_WEIGHT;
        similarity += referenceRelationship.getTarget().similarity(target) * CompassConfiguration.RELATION_ELEMENT_WEIGHT;

        if (referenceRelationship.type == this.type) {
            similarity += CompassConfiguration.RELATION_TYPE_WEIGHT;
        }

        return ensureSimilarityRange(similarity);
    }

    @Override
    public String toString() {
        return "Relationship " + getSource().getName() + type + getTarget().getName() + " (" + getType() + ")";
    }

    @Override
    public String getName() {
        return getType();
    }

    @Override
    public String getType() {
        return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, type.name());
    }

    public UMLElement getSource() {
        return source;
    }

    public UMLElement getTarget() {
        return target;
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
