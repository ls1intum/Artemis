package de.tum.cit.aet.artemis.service.compass.umlmodel.bpmn;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import de.tum.cit.aet.artemis.service.compass.strategy.NameSimilarity;
import de.tum.cit.aet.artemis.service.compass.umlmodel.Similarity;
import de.tum.cit.aet.artemis.service.compass.umlmodel.UMLElement;

/**
 * Represents a BPMN flow
 */
public class BPMNFlow extends UMLElement implements Serializable {

    public static final String BPMN_FLOW_TYPE = "BPMNFlow";

    private final String name;

    private final UMLElement source;

    private final UMLElement target;

    private final BPMNFlowType flowType;

    /**
     * Construct an instance of the BPMNFlow class
     *
     * @param name          The name of the constructed flow
     * @param jsonElementID The JSON element ID of the constructed flow
     */
    public BPMNFlow(String name, String jsonElementID, BPMNFlowType flowType, UMLElement source, UMLElement target) {
        super(jsonElementID);

        this.name = name;
        this.source = source;
        this.target = target;
        this.flowType = flowType;
    }

    /**
     * Calculate the similarity between the element and another given UML Element
     *
     * @param reference the reference object that should be compared to this object
     * @return A similarity score between 0 and 1
     */
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

    /**
     * Get a string representation for the flow
     *
     * @return A string representation of the flow
     */
    @Override
    public String toString() {
        return "Flow " + getSource().getName() + " --> " + getTarget().getName() + " (" + getType() + ")";
    }

    /**
     * Get the name of the element
     *
     * @return The name of the element
     */
    @Override
    public String getName() {
        return this.name;
    }

    /**
     * Get the type of the BPMN element
     *
     * @return The type of BPMN element
     */
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
    public int hashCode() {
        return Objects.hash(super.hashCode(), name, source, target, flowType);
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) {
            return false;
        }

        BPMNFlow otherFlow = (BPMNFlow) object;

        return Objects.equals(otherFlow.getSource(), source) && Objects.equals(otherFlow.getTarget(), target) && otherFlow.flowType == ((BPMNFlow) object).flowType;
    }

    /**
     * Represents the different types of BPMN flows
     */
    public enum BPMNFlowType {

        SEQUENCE("sequence"), MESSAGE("message"), ASSOCIATION("association"), DATA_ASSOCIATION("data association");

        private final String value;

        /**
         * Construct an instance of the BPMNFlowType enum
         *
         * @param value The raw value of the entry
         */
        BPMNFlowType(String value) {
            this.value = value;
        }

        /**
         * Get the enum key corresponding to the given value
         *
         * @param value The value to retrieve the key for
         * @return The enum key corresponding to the given value
         */
        public static Optional<BPMNFlowType> fromValue(String value) {
            return Arrays.stream(BPMNFlowType.values()).filter(element -> element.value.equals(value)).findFirst();
        }

        /**
         * Get the value of an enum key
         *
         * @return The value of the enum key
         */
        public String getValue() {
            return value;
        }
    }
}
