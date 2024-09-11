package de.tum.cit.aet.artemis.service.compass.umlmodel.bpmn;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import de.tum.cit.aet.artemis.service.compass.strategy.NameSimilarity;
import de.tum.cit.aet.artemis.service.compass.umlmodel.Similarity;
import de.tum.cit.aet.artemis.service.compass.umlmodel.UMLElement;

/**
 * Represents a BPMN intermediate event
 */
public class BPMNIntermediateEvent extends UMLElement implements Serializable {

    public static final String BPMN_INTERMEDIATE_EVENT_TYPE = "BPMNIntermediateEvent";

    private final String name;

    private final BPMNIntermediateEventType eventType;

    /**
     * Construct an instance of the BPMNIntermediateEvent class
     *
     * @param name          The name of the constructed intermediate event
     * @param jsonElementID The JSON element ID of the constructed intermediate event
     */
    public BPMNIntermediateEvent(String name, String jsonElementID, BPMNIntermediateEventType eventType) {
        super(jsonElementID);

        this.name = name;
        this.eventType = eventType;
    }

    /**
     * Calculate the similarity between the element and another given UML Element
     *
     * @param reference the reference object that should be compared to this object
     * @return A similarity score between 0 and 1
     */
    @Override
    public double similarity(Similarity<UMLElement> reference) {
        if (!(reference instanceof BPMNIntermediateEvent referenceNode)) {
            return 0;
        }

        if (!Objects.equals(getType(), referenceNode.getType())) {
            return 0;
        }

        double eventTypeSimilarityFactor = (this.eventType == ((BPMNIntermediateEvent) reference).eventType) ? 1.0 : 0.5;

        return NameSimilarity.levenshteinSimilarity(getName(), referenceNode.getName()) * eventTypeSimilarityFactor;
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
        return BPMN_INTERMEDIATE_EVENT_TYPE;
    }

    /**
     * Get a string representation for the intermediate event
     *
     * @return A string representation of the intermediate event
     */
    @Override
    public String toString() {
        return getName();
    }

    /**
     * Get the event type of the intermediate event
     *
     * @return The event type of the intermediate event
     */
    public BPMNIntermediateEventType getEventType() {
        return this.eventType;
    }

    /**
     * Represents the different types of BPMN intermediate events
     */
    public enum BPMNIntermediateEventType {

        DEFAULT("default"), MESSAGE_CATCH("message-catch"), MESSAGE_THROW("message-throw"), TIMER_CATCH("timer-catch"), ESCALATION_THROW("escalation-throw"),
        CONDITIONAL_CATCH("conditional-catch"), LINK_CATCH("link-catch"), LINK_THROW("link-throw"), COMPENSATION_THROW("compensation-throw"), SIGNAL_CATCH("signal-catch"),
        SIGNAL_THROW("signal-throw");

        private final String value;

        /**
         * Construct an instance of the BPMNIntermediateEventType enum
         *
         * @param value The raw value of the entry
         */
        BPMNIntermediateEventType(String value) {
            this.value = value;
        }

        /**
         * Get the enum key corresponding to the given value
         *
         * @param value The value to retrieve the key for
         * @return The enum key corresponding to the given value
         */
        public static Optional<BPMNIntermediateEventType> fromValue(String value) {
            return Arrays.stream(BPMNIntermediateEventType.values()).filter(element -> element.value.equals(value)).findFirst();
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
