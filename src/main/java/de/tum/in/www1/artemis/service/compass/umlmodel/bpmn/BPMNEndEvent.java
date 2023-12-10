package de.tum.in.www1.artemis.service.compass.umlmodel.bpmn;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.Similarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class BPMNEndEvent extends UMLElement implements Serializable {

    public static final String BPMN_END_EVENT_TYPE = "BPMNEndEvent";

    private final String name;

    private final BPMNEndEventType eventType;

    public BPMNEndEvent(String name, String jsonElementID, BPMNEndEventType endEventType) {
        super(jsonElementID);

        this.name = name;
        this.eventType = endEventType;
    }

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

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getType() {
        return BPMN_END_EVENT_TYPE;
    }

    @Override
    public String toString() {
        return getName();
    }

    public BPMNEndEventType getEventType() {
        return this.eventType;
    }

    public enum BPMNEndEventType {

        DEFAULT("default"), MESSAGE("message"), ESCALATION("escalation"), ERROR("error"), COMPENSATION("compensation"), SIGNAL("signal"), TERMINATE("terminate");

        private final String value;

        BPMNEndEventType(String value) {
            this.value = value;
        }

        public static Optional<BPMNEndEventType> get(String value) {
            return Arrays.stream(BPMNEndEventType.values()).filter(element -> element.value.equals(value)).findFirst();
        }

        public String getValue() {
            return value;
        }
    }
}
