package de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.bpmn;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import de.tum.cit.aet.artemis.modeling.service.compass.strategy.NameSimilarity;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.Similarity;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.UMLElement;

/**
 * Represents a BPMN task
 */
public class BPMNTask extends UMLElement implements Serializable {

    public static final String BPMN_TASK_TYPE = "BPMNTask";

    private final String name;

    private final BPMNTaskType taskType;

    private final BPMNMarker marker;

    /**
     * Construct an instance of the BPMNTask class
     *
     * @param name          The name of the constructed task
     * @param jsonElementID The JSON element ID of the constructed task
     * @param taskType      The task type of the constructed task
     * @param marker        The marker of the constructed task
     */
    public BPMNTask(String name, String jsonElementID, BPMNTaskType taskType, BPMNMarker marker) {
        super(jsonElementID);

        this.name = name;
        this.taskType = taskType;
        this.marker = marker;
    }

    /**
     * Calculate the similarity between the element and another given UML Element
     *
     * @param reference the reference object that should be compared to this object
     * @return A similarity score between 0 and 1
     */
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
        return BPMN_TASK_TYPE;
    }

    /**
     * Get a string representation for the task
     *
     * @return A string representation of the task
     */
    @Override
    public String toString() {
        return getName();
    }

    /**
     * Get the task type of the task
     *
     * @return The task type of the task
     */
    public BPMNTaskType getTaskType() {
        return this.taskType;
    }

    /**
     * Get the marker of the task
     *
     * @return The marker of the task
     */
    public BPMNMarker getMarker() {
        return this.marker;
    }

    /**
     * Represents the different types of BPMN tasks
     */
    public enum BPMNTaskType {

        DEFAULT("default"), USER("user"), SEND("send"), RECEIVE("receive"), MANUAL("manual"), BUSINESS_RULE("business rule"), SCRIPT("script");

        private final String value;

        /**
         * Construct an instance of the BPMNTaskType enum
         *
         * @param value The raw value of the entry
         */
        BPMNTaskType(String value) {
            this.value = value;
        }

        /**
         * Get the enum key corresponding to the given value
         *
         * @param value The value to retrieve the key for
         * @return The enum key corresponding to the given value
         */
        public static Optional<BPMNTaskType> fromValue(String value) {
            return Arrays.stream(BPMNTaskType.values()).filter(element -> element.value.equals(value)).findFirst();
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

    /**
     * Represents the different types of BPMN markers
     */
    public enum BPMNMarker {

        NONE("none"), PARALLEL_MULTI_INSTANCE("parallel multi instance"), SEQUENTIAL_MULTI_INSTANCE("sequential multi instance"), LOOP("loop");

        private final String value;

        /**
         * Construct an instance of the BPMNMarker enum
         *
         * @param value The raw value of the entry
         */
        BPMNMarker(String value) {
            this.value = value;
        }

        /**
         * Get the enum key corresponding to the given value
         *
         * @param value The value to retrieve the key for
         * @return The enum key corresponding to the given value
         */
        public static Optional<BPMNMarker> fromValue(String value) {
            return Arrays.stream(BPMNMarker.values()).filter(element -> element.value.equals(value)).findFirst();
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
