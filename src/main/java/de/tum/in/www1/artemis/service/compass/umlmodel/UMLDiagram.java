package de.tum.in.www1.artemis.service.compass.umlmodel;

import java.io.Serializable;
import java.util.List;

public abstract class UMLDiagram implements Similarity<UMLDiagram>, Serializable {

    private long modelSubmissionId;

    /**
     * to make mockito happy
     */
    public UMLDiagram() {
    }

    public UMLDiagram(long modelSubmissionId) {
        this.modelSubmissionId = modelSubmissionId;
    }

    /**
     * Gets a UML element of the UML model with the given id.
     *
     * @param jsonElementId the id of the UML element
     * @return the UML element if one could be found for the given id, null otherwise
     */
    public abstract UMLElement getElementByJSONID(String jsonElementId);

    /**
     * Get the list of first level model elements of the diagram (e.g. classes, relationships and packages of UML class diagrams, but no Attributes and Methods).
     *
     * @return the list of first level model elements of the diagram
     */
    public abstract List<UMLElement> getModelElements();

    /**
     * Get the list of model elements of the diagram including child elements (e.g. classes, relationships and packages of UML class diagrams, including attributes and methods of
     * classes). The default behavior is to return the elements returned by getModelElements(). If the specific diagram type has elements with child elements (i.e. that are not
     * returned by getModelElements()) that need to be handled separately, the diagram type has to implement this method.
     *
     * @return the list of all model elements of the diagram including child elements
     */
    public List<UMLElement> getAllModelElements() {
        return getModelElements();
    }

    /**
     * Compares this with another diagram to calculate the similarity. It iterates over all model elements and calculates the max. similarity to elements of the reference diagram.
     * The sum of the weighted single element similarity scores is the total similarity score of the two diagrams.
     *
     * @param reference the reference UML diagram to compare this diagram with
     * @return the similarity of the diagrams as number [0-1]
     */
    @Override
    public double similarity(Similarity<UMLDiagram> reference) {
        if (reference == null || !reference.getClass().isInstance(this)) {
            return 0;
        }

        UMLDiagram diagramReference = (UMLDiagram) reference;

        List<UMLElement> modelElements = getModelElements();
        List<UMLElement> referenceModelElements = diagramReference.getModelElements();

        // To ensure symmetry (i.e. A.similarity(B) = B.similarity(A)) we make sure that this diagram always has less or equally many elements than the reference diagram.
        if (modelElements.size() > referenceModelElements.size()) {
            return diagramReference.similarity(this);
        }

        double similarity = 0;

        // For calculating the weight of the similarity of every element, we consider the max. element count to reflect missing elements, i.e. it should not be possible to get a
        // similarity of 1 if the amount of elements differs. As we know that the reference diagram has at least as many elements as this diagram, we take the element count of the
        // reference.
        int maxElementCount = referenceModelElements.size();
        double weight = 1.0 / maxElementCount;

        for (Similarity<UMLElement> element : modelElements) {
            double similarityValue = diagramReference.similarElementScore(element);
            similarity += weight * similarityValue;
        }

        // Make sure that the similarity value is between 0 and 1.
        return Math.min(Math.max(similarity, 0), 1);
    }

    /**
     * Compares a reference element to the list of model elements of this diagram and returns the maximum similarity score, i.e. the similarity between the reference element and
     * the most similar element of this diagram.
     *
     * @param referenceElement the reference element that should be compared to the model elements of this diagram
     * @return the maximum similarity score of the reference element and the list of model elements of this diagram
     */
    private double similarElementScore(Similarity<UMLElement> referenceElement) {
        return getModelElements().stream().mapToDouble(element -> element.overallSimilarity(referenceElement)).max().orElse(0);
    }

    /**
     * Return the submissionId of the UML diagram.
     *
     * @return the submissionId of the UML diagram
     */
    public long getModelSubmissionId() {
        return modelSubmissionId;
    }

    /**
     * Get a human-readable name of this diagram in the form "Model <submissionId>".
     *
     * @return a human-readable name of this diagram in the form "Model <submissionId>"
     */
    public String getName() {
        return "Model " + modelSubmissionId;
    }
}
