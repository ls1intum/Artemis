package de.tum.cit.aet.artemis.service.compass.umlmodel.bpmn;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import de.tum.cit.aet.artemis.service.compass.strategy.NameSimilarity;
import de.tum.cit.aet.artemis.service.compass.umlmodel.Similarity;
import de.tum.cit.aet.artemis.service.compass.umlmodel.UMLElement;

/**
 * Represents a BPMN end event
 */
public class BPMNEndEvent extends UMLElement implements Serializable {

    public static final String BPMN_END_EVENT_TYPE = "BPMNEndEvent";

    private final String name;

    private final BPMNEndEventType eventType;

    /**
     * Construct an instance of the BPMNEndEvent class
     *
     * @param name          The name of the constructed end event
     * @param jsonElementID The JSON element ID of the constructed end event
     * @param endEventType  The event type of the constructed end event
     */
    public BPMNEndEvent(String name, String jsonElementID, BPMNEndEventType endEventType) {
        super(jsonElementID);

        this.name = name;
        this.eventType = endEventType;
    }

    /**
     * Calculate the similarity between the element and another given UML Element
     *
     * @param reference the reference object that should be compared to this object
     * @return A similarity score between 0 and 1
     */
    @Override
    public double similarity(Similarity<UMLElement> reference) {
        if (!(reference instanceof BPMNEndEvent referenceNode)) {
            return 0;
        }

        if (!Objects.equals(getType(), referenceNode.getType())) {
            return 0;
        }

        double eventTypeSimilarityFactor = (this.eventType == ((BPMNEndEvent) reference).eventType) ? 1.0 : 0.5;

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
        return BPMN_END_EVENT_TYPE;
    }

    /**
     * Get a string representation for the end event
     *
     * @return A string representation of the end event
     */
    @Override
    public String toString() {
        return getName();
    }

    /**
     * Get the event type of the end event
     *
     * @return The event type of the end event
     */
    public BPMNEndEventType getEventType() {
        return this.eventType;
    }

    /**
     * Represents the different types of BPMN end events
     */
    public enum BPMNEndEventType {

        DEFAULT("default"), MESSAGE("message"), ESCALATION("escalation"), ERROR("error"), COMPENSATION("compensation"), SIGNAL("signal"), TERMINATE("terminate");

        private final String value;

        /**
         * Construct an instance of the BPMNEndEventType enum
         *
         * @param value The raw value of the entry
         */
        BPMNEndEventType(String value) {
            this.value = value;
        }

        /**
         * Get the enum key corresponding to the given value
         *
         * @param value The value to retrieve the key for
         * @return The enum key corresponding to the given value
         */
        public static Optional<BPMNEndEventType> fromValue(String value) {
            return Arrays.stream(BPMNEndEventType.values()).filter(element -> element.value.equals(value)).findFirst();
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
