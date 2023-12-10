package de.tum.in.www1.artemis.service.compass.umlmodel.bpmn;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.Similarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class BPMNTask extends UMLElement implements Serializable {

    public static final String BPMN_TASK_TYPE = "BPMNTask";

    private final String name;

    private final BPMNTaskType taskType;

    private final BPMNMarker marker;

    public BPMNTask(String name, String jsonElementID, BPMNTaskType taskType, BPMNMarker marker) {
        super(jsonElementID);

        this.name = name;
        this.taskType = taskType;
        this.marker = marker;
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        if (!(reference instanceof BPMNTask referenceNode)) {
            return 0;
        }

        if (!Objects.equals(getType(), referenceNode.getType())) {
            return 0;
        }

        double taskTypeSimilarityFactor = (this.taskType == ((BPMNTask) reference).taskType) ? 1.0 : 0.5;
        double markerSimilarityFactor = (this.marker == ((BPMNTask) reference).marker) ? 1.0 : 0.5;

        return NameSimilarity.levenshteinSimilarity(getName(), referenceNode.getName()) * taskTypeSimilarityFactor * markerSimilarityFactor;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getType() {
        return BPMN_TASK_TYPE;
    }

    @Override
    public String toString() {
        return getName();
    }

    public BPMNTaskType getTaskType() {
        return this.taskType;
    }

    public BPMNMarker getMarker() {
        return this.marker;
    }

    public enum BPMNTaskType {

        DEFAULT("default"), USER("user"), SEND("send"), RECEIVE("receive"), MANUAL("manual"), BUSINESS_RULE("business rule"), SCRIPT("script");

        private final String value;

        BPMNTaskType(String value) {
            this.value = value;
        }

        public static Optional<BPMNTaskType> get(String value) {
            return Arrays.stream(BPMNTaskType.values()).filter(element -> element.value.equals(value)).findFirst();
        }

        public String getValue() {
            return value;
        }
    }

    public enum BPMNMarker {

        NONE("none"), PARALLEL_MULTI_INSTANCE("parallel multi instance"), SEQUENTIAL_MULTI_INSTANCE("sequential multi instance"), LOOP("loop");

        private final String value;

        BPMNMarker(String value) {
            this.value = value;
        }

        public static Optional<BPMNMarker> get(String value) {
            return Arrays.stream(BPMNMarker.values()).filter(element -> element.value.equals(value)).findFirst();
        }

        public String getValue() {
            return value;
        }
    }

}
