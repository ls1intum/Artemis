package de.tum.in.www1.artemis.service.compass.umlmodel.bpmn;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.Similarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class BPMNIntermediateEvent extends UMLElement implements Serializable {

    public static final String BPMN_INTERMEDIATE_EVENT_TYPE = "BPMNIntermediateEvent";

    private final String name;

    private final BPMNIntermediateEventType eventType;

    public BPMNIntermediateEvent(String name, String jsonElementID, BPMNIntermediateEventType eventType) {
        super(jsonElementID);

        this.name = name;
        this.eventType = eventType;
    }

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

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getType() {
        return BPMN_INTERMEDIATE_EVENT_TYPE;
    }

    @Override
    public String toString() {
        return getName();
    }

    public BPMNIntermediateEventType getEventType() {
        return this.eventType;
    }

    public enum BPMNIntermediateEventType {

        DEFAULT("default"), MESSAGE_CATCH("message-catch"), MESSAGE_THROW("message-throw"), TIMER_CATCH("timer-catch"), ESCALATION_THROW("escalation-throw"),
        CONDITIONAL_CATCH("conditional-catch"), LINK_CATCH("link-catch"), LINK_THROW("link-throw"), COMPENSATION_THROW("compensation-throw"), SIGNAL_CATCH("signal-catch"),
        SIGNAL_THROW("signal-throw");

        private final String value;

        BPMNIntermediateEventType(String value) {
            this.value = value;
        }

        public static Optional<BPMNIntermediateEventType> get(String value) {
            return Arrays.stream(BPMNIntermediateEventType.values()).filter(element -> element.value.equals(value)).findFirst();
        }

        public String getValue() {
            return value;
        }
    }

}
