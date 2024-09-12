package de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.flowchart;

import de.tum.cit.aet.artemis.modeling.service.compass.strategy.NameSimilarity;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.Similarity;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.UMLElement;

public class FlowchartTerminal extends UMLElement {

    public static final String FLOWCHART_TERMINAL_TYPE = "FlowchartTerminal";

    private final String name;

    public FlowchartTerminal(String name, String jsonElementID) {
        super(jsonElementID);
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return FLOWCHART_TERMINAL_TYPE;
    }

    @Override
    public String toString() {
        return "FlowchartTerminal " + name;
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        double similarity = 0;

        if (!(reference instanceof FlowchartTerminal referenceTerminal)) {
            return similarity;
        }

        similarity += NameSimilarity.levenshteinSimilarity(getName(), referenceTerminal.getName());

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
        if (!(reference instanceof FlowchartTerminal referenceTerminal)) {
            return 0;
        }

        double similarity = similarity(referenceTerminal);

        return ensureSimilarityRange(similarity);
    }
}
