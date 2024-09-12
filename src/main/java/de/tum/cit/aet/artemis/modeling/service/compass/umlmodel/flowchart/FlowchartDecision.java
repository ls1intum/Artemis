package de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.flowchart;

import de.tum.cit.aet.artemis.modeling.service.compass.strategy.NameSimilarity;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.Similarity;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.UMLElement;

public class FlowchartDecision extends UMLElement {

    public static final String FLOWCHART_DECISION_TYPE = "FlowchartDecision";

    private final String name;

    public FlowchartDecision(String name, String jsonElementID) {
        super(jsonElementID);
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return FLOWCHART_DECISION_TYPE;
    }

    @Override
    public String toString() {
        return "FlowchartDecision " + name;
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        double similarity = 0;

        if (!(reference instanceof FlowchartDecision referenceDecision)) {
            return similarity;
        }

        similarity += NameSimilarity.levenshteinSimilarity(getName(), referenceDecision.getName());

        return ensureSimilarityRange(similarity);
    }

    /**
     * Checks for overall similarity including attributes and methods.
     *
     * @param reference the reference element to compare this object with
     * @return the similarity as number [0-1]
     */
    @Override
    public double overallSimilarity(Similarity<UMLElement> reference) {
        if (!(reference instanceof FlowchartDecision referenceDecision)) {
            return 0;
        }

        double similarity = similarity(referenceDecision);

        return ensureSimilarityRange(similarity);
    }
}
