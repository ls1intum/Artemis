package de.tum.in.www1.artemis.service.compass.umlmodel.bpmn;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.Similarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class BPMNFlow extends UMLElement implements Serializable {

    public static final String BPMN_FLOW_TYPE = "BPMNFlow";

    private final String name;

    private final UMLElement source;

    private final UMLElement target;

    private final BPMNFlowType flowType;

    public BPMNFlow(String name, String jsonElementID, BPMNFlowType flowType, UMLElement source, UMLElement target) {
        super(jsonElementID);

        this.name = name;
        this.source = source;
        this.target = target;
        this.flowType = flowType;
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        if (!(reference instanceof BPMNFlow referenceFlow)) {
            return 0;
        }

        double similarity = 0;

        similarity += referenceFlow.getSource().similarity(source) * 0.4;
        similarity += referenceFlow.getTarget().similarity(target) * 0.4;
        similarity += NameSimilarity.levenshteinSimilarity(getName(), referenceFlow.getName()) * 0.2;

        double flowTypeSimilarityFactor = (this.flowType == ((BPMNFlow) reference).flowType) ? 1.0 : 0.5;

        return ensureSimilarityRange(similarity * flowTypeSimilarityFactor);
    }

    @Override
    public String toString() {
        return "Flow " + getSource().getName() + " --> " + getTarget().getName() + " (" + getType() + ")";
    }

    @Override
    public String getName() {
        return this.name;
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

    /**
     * Get the flow type of this BPMN flow
     *
     * @return the flow type of this BPMN flow
     */
    public BPMNFlowType getFlowType() {
        return flowType;
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) {
            return false;
        }

        BPMNFlow otherFlow = (BPMNFlow) object;

        return Objects.equals(otherFlow.getSource(), source) && Objects.equals(otherFlow.getTarget(), target) && otherFlow.flowType == ((BPMNFlow) object).flowType;
    }

    public enum BPMNFlowType {

        SEQUENCE("sequence"), MESSAGE("message"), ASSOCIATION("association"), DATA_ASSOCIATION("data association");

        private final String value;

        BPMNFlowType(String value) {
            this.value = value;
        }

        public static Optional<BPMNFlowType> get(String value) {
            return Arrays.stream(BPMNFlowType.values()).filter(element -> element.value.equals(value)).findFirst();
        }

        public String getValue() {
            return value;
        }
    }
}
