package de.tum.in.www1.artemis.service.compass.umlmodel.bpmn;

import java.io.Serializable;
import java.util.Objects;

import de.tum.in.www1.artemis.service.compass.umlmodel.Similarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class BPMNFlow extends UMLElement implements Serializable {

    public static final String BPMN_FLOW_TYPE = "BPMNFlow";

    private final UMLElement source;

    private final UMLElement target;

    public BPMNFlow(UMLElement source, UMLElement target, String jsonElementID) {
        super(jsonElementID);

        this.source = source;
        this.target = target;
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        if (!(reference instanceof BPMNFlow referenceControlFlow)) {
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
        return "Flow " + getSource().getName() + " --> " + getTarget().getName() + " (" + getType() + ")";
    }

    @Override
    public String getName() {
        return getType();
    }

    @Override
    public String getType() {
        return BPMN_FLOW_TYPE;
    }

    /**
     * Get the source of this BPMN flow element, i.e. the BPMN task element where the flow starts.
     *
     * @return the source UML activity element of this control flow element
     */
    public UMLElement getSource() {
        return source;
    }

    /**
     * Get the target of this BPMN flow element, i.e. the BPMN task element where the flow ends.
     *
     * @return the target BPMN element of this flow element
     */
    public UMLElement getTarget() {
        return target;
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        BPMNFlow otherFlow = (BPMNFlow) obj;

        return Objects.equals(otherFlow.getSource(), source) && Objects.equals(otherFlow.getTarget(), target);
    }
}
