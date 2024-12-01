package de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.bpmn;

import java.io.Serializable;
import java.util.Objects;

import de.tum.cit.aet.artemis.modeling.service.compass.strategy.NameSimilarity;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.Similarity;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.UMLElement;

/**
 * Represents a BPMN data store
 */
public class BPMNDataStore extends UMLElement implements Serializable {

    public static final String BPMN_DATA_STORE_TYPE = "BPMNDataStore";

    private final String name;

    /**
     * Construct an instance of the BPMNDataStore class
     *
     * @param name          The name of the constructed data store
     * @param jsonElementID The JSON element ID of the constructed data store
     */
    public BPMNDataStore(String name, String jsonElementID) {
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
        if (!(reference instanceof BPMNDataStore referenceNode)) {
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
        return BPMN_DATA_STORE_TYPE;
    }

    /**
     * Get a string representation for the data store
     *
     * @return A string representation of the data store
     */
    @Override
    public String toString() {
        return getName();
    }
}
