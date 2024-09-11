package de.tum.cit.aet.artemis.service.compass.umlmodel.activity;

import java.io.Serializable;
import java.util.Objects;

import de.tum.cit.aet.artemis.service.compass.umlmodel.Similarity;
import de.tum.cit.aet.artemis.service.compass.umlmodel.UMLElement;

public class UMLControlFlow extends UMLElement implements Serializable {

    public static final String UML_CONTROL_FLOW_TYPE = "ActivityControlFlow";

    private final UMLActivityElement source;

    private final UMLActivityElement target;

    public UMLControlFlow(UMLActivityElement source, UMLActivityElement target, String jsonElementID) {
        super(jsonElementID);

        this.source = source;
        this.target = target;
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        if (!(reference instanceof UMLControlFlow referenceControlFlow)) {
            return 0;
        }

        double similarity = 0;
        double weight = 0.5;

        similarity += referenceControlFlow.getSource().similarity(source) * weight;
        similarity += referenceControlFlow.getTarget().similarity(target) * weight;

        return ensureSimilarityRange(similarity);
    }

    @Override
    public String toString() {
        return "Control Flow " + getSource().getName() + " --> " + getTarget().getName() + " (" + getType() + ")";
    }

    @Override
    public String getName() {
        return getType();
    }

    @Override
    public String getType() {
        return UML_CONTROL_FLOW_TYPE;
    }

    /**
     * Get the source of this UML control flow element, i.e. the UML activity element where the control flow starts.
     *
     * @return the source UML activity element of this control flow element
     */
    public UMLActivityElement getSource() {
        return source;
    }

    /**
     * Get the target of this UML control flow element, i.e. the UML activity element where the control flow ends.
     *
     * @return the target UML activity element of this control flow element
     */
    public UMLActivityElement getTarget() {
        return target;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), source, target);
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        UMLControlFlow otherControlFlow = (UMLControlFlow) obj;

        return Objects.equals(otherControlFlow.getSource(), source) && Objects.equals(otherControlFlow.getTarget(), target);
    }
}
