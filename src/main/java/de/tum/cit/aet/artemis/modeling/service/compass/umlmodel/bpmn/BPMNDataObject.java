package de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.bpmn;

import java.io.Serializable;
import java.util.Objects;

import de.tum.cit.aet.artemis.modeling.service.compass.strategy.NameSimilarity;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.Similarity;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.UMLElement;

/**
 * Represents a BPMN data object
 */
public class BPMNDataObject extends UMLElement implements Serializable {

    public static final String BPMN_DATA_OBJECT_TYPE = "BPMNDataObject";

    private final String name;

    /**
     * Construct an instance of the BPMNDataObject class
     *
     * @param name          The name of the constructed data object
     * @param jsonElementID The JSON element ID of the constructed data object
     */
    public BPMNDataObject(String name, String jsonElementID) {
        super(jsonElementID);

        this.name = name;
    }

    /**
     * Calculate the similarity between the element and another given UML Element
     *
     * @param reference the reference object that should be compared to this object
     * @return A similarity score between 0 and 1
     */
    @Override
    public double similarity(Similarity<UMLElement> reference) {
        if (!(reference instanceof BPMNDataObject referenceNode)) {
            return 0;
        }

        if (!Objects.equals(getType(), referenceNode.getType())) {
            return 0;
        }

        return NameSimilarity.levenshteinSimilarity(getName(), referenceNode.getName());
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
        return BPMN_DATA_OBJECT_TYPE;
    }

    /**
     * Get a string representation for the data object
     *
     * @return A string representation of the data object
     */
    @Override
    public String toString() {
        return getName();
    }
}
