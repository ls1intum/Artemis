package de.tum.cit.aet.artemis.service.compass.umlmodel.bpmn;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import de.tum.cit.aet.artemis.service.compass.strategy.NameSimilarity;
import de.tum.cit.aet.artemis.service.compass.umlmodel.Similarity;
import de.tum.cit.aet.artemis.service.compass.umlmodel.UMLElement;

/**
 * Represents a BPMN gateway
 */
public class BPMNGateway extends UMLElement implements Serializable {

    public static final String BPMN_GATEWAY_TYPE = "BPMNGateway";

    private final String name;

    private final BPMNGatewayType gatewayType;

    /**
     * Construct an instance of the BPMNGateway class
     *
     * @param name          The name of the constructed gateway
     * @param jsonElementID The JSON element ID of the constructed gateway
     */
    public BPMNGateway(String name, String jsonElementID, BPMNGatewayType gatewayType) {
        super(jsonElementID);

        this.name = name;
        this.gatewayType = gatewayType;
    }

    /**
     * Calculate the similarity between the element and another given UML Element
     *
     * @param reference the reference object that should be compared to this object
     * @return A similarity score between 0 and 1
     */
    @Override
    public double similarity(Similarity<UMLElement> reference) {
        if (!(reference instanceof BPMNGateway referenceNode)) {
            return 0;
        }

        if (!Objects.equals(getType(), referenceNode.getType())) {
            return 0;
        }

        double gatewayTypeSimilarityFactor = (this.gatewayType == ((BPMNGateway) reference).gatewayType) ? 1.0 : 0.5;

        return NameSimilarity.levenshteinSimilarity(getName(), referenceNode.getName()) * gatewayTypeSimilarityFactor;
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
        return BPMN_GATEWAY_TYPE;
    }

    /**
     * Get a string representation for the gateway
     *
     * @return A string representation of the gateway
     */
    @Override
    public String toString() {
        return getName();
    }

    /**
     * Get the gateway type of the gateway
     *
     * @return The gateway type of the gateway
     */
    public BPMNGatewayType getGatewayType() {
        return gatewayType;
    }

    /**
     * Represents the different types of BPMN gateways
     */
    public enum BPMNGatewayType {

        COMPLEX("complex"), EVENT_BASED("event-based"), EXCLUSIVE("exclusive"), INCLUSIVE("inclusive"), PARALLEL("parallel");

        private final String value;

        /**
         * Construct an instance of the BPMNGatewayType enum
         *
         * @param value The raw value of the entry
         */
        BPMNGatewayType(String value) {
            this.value = value;
        }

        /**
         * Get the enum key corresponding to the given value
         *
         * @param value The value to retrieve the key for
         * @return The enum key corresponding to the given value
         */
        public static Optional<BPMNGatewayType> fromValue(String value) {
            return Arrays.stream(BPMNGatewayType.values()).filter(element -> element.value.equals(value)).findFirst();
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
