package de.tum.in.www1.artemis.service.compass.umlmodel;

import de.tum.in.www1.artemis.service.compass.assessment.CompassResult;

import java.util.List;

public abstract class UMLDiagram implements SimilarElement<UMLDiagram> {

    private long modelSubmissionId;

    private CompassResult lastAssessmentCompassResult = null;

    public UMLDiagram(long modelSubmissionId) {
        this.modelSubmissionId = modelSubmissionId;
    }

    /**
     * Gets an UML element of the UML model with the given id.
     *
     * @param jsonElementId the id of the UML element
     * @return the UML element if one could be found for the given id, null otherwise
     */
    public abstract UMLElement getElementByJSONID(String jsonElementId);

    /**
     * Get the list of connectable elements of the diagram, i.e. all elements that can be connected by relations in the respective diagram type.
     *
     * @return the list of connectable elements of the diagram
     */
    protected abstract List<UMLElement> getConnectableElements();

    /**
     * Get the list of relations of the diagram, i.e. all elements that can connect connectable elements in the respective diagram type.
     *
     * @return the list of relations of the diagram
     */
    protected abstract List<UMLElement> getRelations();

    /**
     * Get the list of container elements of the diagram, i.e. all elements that can contain other elements in the respective diagram type.
     *
     * @return the list of container elements of the diagram
     */
    protected abstract List<UMLElement> getContainerElements();

    @Override
    public final double similarity(UMLDiagram reference) {
        if (reference == null || reference.getClass() != this.getClass()) {
            return 0;
        }

        double sim1 = reference.similarityScore(this);
        double sim2 = similarityScore(reference);

        // TODO CZ: is the double calculation + multiplication necessary?
        return sim1 * sim2;
    }

    /**
     * Compares this with another diagram to calculate the similarity. It iterates over all elements of the three element classes (connectables, relations, containers) and
     * calculates the max similarity to elements of the reference diagram. The sum of the single element similarity scores is the total similarity score between the two diagrams.
     *
     * @param reference the reference UML diagram to compare this diagram with
     * @return the similarity of the diagrams as number [0-1]
     */
    protected double similarityScore(UMLDiagram reference) {
        List<UMLElement> connectableElements = getConnectableElements();
        List<UMLElement> relations = getRelations();
        List<UMLElement> containerElements = getContainerElements();

        double similarity = 0;

        // For calculating the weight of the similarity of every element, we consider the max. element count of both diagrams to reflect missing elements on either side in the
        // total similarity score. E.g. if we compare two diagrams, diagramA with one class and diagramB with two classes, the highest possible similarity should be 0.5, so
        // the weight should be 1/2, no matter if we do diagramA.similarityScore(diagramB) or diagramB.similarityScore(diagramA).
        int maxElementCount = Math.max(connectableElements.size(), reference.getConnectableElements().size()) + Math.max(relations.size(), reference.getRelations().size()) +
            Math.max(containerElements.size(), reference.getContainerElements().size());
        double weight = 1.0 / maxElementCount;

        for (UMLElement connectableElement : connectableElements) {
            double similarityValue = reference.similarConnectableElementScore(connectableElement);
            similarity += weight * similarityValue;
        }

        for (UMLElement relation : relations) {
            double similarityValue = reference.similarRelationScore(relation);
            similarity += weight * similarityValue;
        }

        for (UMLElement containerElement : containerElements) {
            double similarityValue = reference.similarContainerElementScore(containerElement);
            similarity += weight * similarityValue;
        }

        if (similarity < 0) {
            similarity = 0;
        }
        else if (similarity > 1) {
            similarity = 1;
        }

        return similarity;
    }

    /**
     * Compares a reference connectable element to the list of connectable elements of this diagram and returns the maximum similarity score, i.e. the similarity between the
     * reference element and the most similar element of the connectable element list.
     *
     * @param referenceConnectable the reference connectable element that should be compared to the connectable elements of this diagram
     * @return the maximum similarity score between the reference element and the list of connectable elements of this diagram
     */
    protected double similarConnectableElementScore(UMLElement referenceConnectable) {
        return similarElementScore(getConnectableElements(), referenceConnectable);
    }

    /**
     * Compares a reference relation element to the list of relation elements of this diagram and returns the maximum similarity score, i.e. the similarity between the reference
     * element and the most similar element of the relation list.
     *
     * @param referenceRelation the reference relation element that should be compared to the relations of this diagram
     * @return the maximum similarity score between the reference element and the list of relations of this diagram
     */
    protected double similarRelationScore(UMLElement referenceRelation) {
        return similarElementScore(getRelations(), referenceRelation);
    }

    /**
     * Compares a reference container element to the list of container elements of this diagram and returns the maximum similarity score, i.e. the similarity between the reference
     * element and the most similar element of the container element list.
     *
     * @param referenceContainer the reference container element that should be compared to the container elements of this diagram
     * @return the maximum similarity score between the reference element and the list of container elements of this diagram
     */
    protected double similarContainerElementScore(UMLElement referenceContainer) {
        return similarElementScore(getContainerElements(), referenceContainer);
    }

    /**
     * Compares a reference element to a list of elements and returns the maximum similarity score, i.e. the similarity between the reference element and the most similar element
     * of the list.
     *
     * @param elements the list of elements that should be compared to the reference element
     * @param referenceElement the reference element that should be compared to the elements of the list
     * @return the maximum similarity score between the reference element and the list of elements
     */
    private double similarElementScore(List<UMLElement> elements, UMLElement referenceElement) {
        return elements.stream().mapToDouble(element -> element.similarity(referenceElement)).max().orElse(0);
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
     * Set the lastAssessmentCompassResult that represents the most recent automatic assessment calculated by Compass for this diagram.
     *
     * @param compassResult the most recent Compass result for this diagram
     */
    public void setLastAssessmentCompassResult(CompassResult compassResult) {
        lastAssessmentCompassResult = compassResult;
    }

    /**
     * Returns the lastAssessmentCompassResult that represents the most recent automatic assessment calculated by Compass for this diagram.
     *
     * @return the most recent Compass result for this diagram
     */
    public CompassResult getLastAssessmentCompassResult() {
        return lastAssessmentCompassResult;
    }

    /**
     * Indicates if this diagram already has an automatic assessment calculated by Compass or not.
     *
     * @return true if Compass has not already calculated an automatic assessment for this diagram, false otherwise
     */
    public boolean isUnassessed() {
        return lastAssessmentCompassResult == null;
    }

    /**
     * Get the confidence of the last compass result, i.e. the most recent automatic assessment calculated by Compass for this diagram.
     *
     * @return The confidence of the last compass result
     */
    public double getLastAssessmentConfidence() {
        if (isUnassessed()) {
            return -1;
        }

        return lastAssessmentCompassResult.getConfidence();
    }

    /**
     * Get the coverage for the last assessed compass result, i.e. the most recent automatic assessment calculated by Compass for this diagram.
     *
     * @return The coverage of the last compass results
     */
    public double getLastAssessmentCoverage() {
        if (isUnassessed()) {
            return -1;
        }

        return lastAssessmentCompassResult.getCoverage();
    }

    /**
     * Get a human readable name of this diagram in the form "Model <submissionId>".
     *
     * @return a human readable name of this diagram in the form "Model <submissionId>"
     */
    public String getName() {
        return "Model " + modelSubmissionId;
    }
}
