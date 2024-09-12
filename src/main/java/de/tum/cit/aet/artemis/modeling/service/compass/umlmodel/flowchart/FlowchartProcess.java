package de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.flowchart;

import de.tum.cit.aet.artemis.modeling.service.compass.strategy.NameSimilarity;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.Similarity;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.UMLElement;

public class FlowchartProcess extends UMLElement {

    public static final String FLOWCHART_PROCESS_TYPE = "FlowchartProcess";

    private final String name;

    public FlowchartProcess(String name, String jsonElementID) {
        super(jsonElementID);
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return FLOWCHART_PROCESS_TYPE;
    }

    @Override
    public String toString() {
        return "FlowchartProcess " + name;
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        double similarity = 0;

        if (!(reference instanceof FlowchartProcess referenceProcess)) {
            return similarity;
        }

        similarity += NameSimilarity.levenshteinSimilarity(getName(), referenceProcess.getName());

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
        if (!(reference instanceof FlowchartProcess referenceProcess)) {
            return 0;
        }

        double similarity = similarity(referenceProcess);

        return ensureSimilarityRange(similarity);
    }
}
