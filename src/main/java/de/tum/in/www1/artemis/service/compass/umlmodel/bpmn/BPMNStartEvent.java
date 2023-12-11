package de.tum.in.www1.artemis.service.compass.umlmodel.bpmn;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.Similarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class BPMNStartEvent extends UMLElement implements Serializable {

    public static final String BPMN_START_EVENT_TYPE = "BPMNStartEvent";

    private final String name;

    private final BPMNStartEventType eventType;

    /**
     * Construct an instance of the BPMNStartEvent class
     *
     * @param name          The name of the constructed start event
     * @param jsonElementID The JSON element ID of the constructed start event
     * @param eventType     The event type of the constructed start event
     */
    public BPMNStartEvent(String name, String jsonElementID, BPMNStartEventType eventType) {
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
        if (!(reference instanceof BPMNStartEvent referenceNode)) {
            return 0;
        }

        if (!Objects.equals(getType(), referenceNode.getType())) {
            return 0;
        }

        double eventTypeSimilarityFactor = (this.eventType == ((BPMNStartEvent) reference).eventType) ? 1.0 : 0.5;

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
        return BPMN_START_EVENT_TYPE;
    }

    /**
     * Get a string representation for the start event
     *
     * @return A string representation of the start event
     */
    @Override
    public String toString() {
        return getName();
    }

    public BPMNStartEventType getEventType() {
        return this.eventType;
    }

    public enum BPMNStartEventType {

        DEFAULT("default"), MESSAGE("message"), TIMER("timer"), CONDITIONAL("conditional"), SIGNAL("signal");

        private final String value;

        BPMNStartEventType(String value) {
            this.value = value;
        }

        public static Optional<BPMNStartEventType> get(String value) {
            return Arrays.stream(BPMNStartEventType.values()).filter(element -> element.value.equals(value)).findFirst();
        }

        public String getValue() {
            return value;
        }
    }

}
