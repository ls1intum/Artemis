package de.tum.in.www1.artemis.service.compass.umlmodel.reachabilitygraph;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.Similarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class ReachabilityGraphMarking extends UMLElement {

    public static final String REACHABILITY_GRAPH_MARKING_TYPE = "ReachabilityGraphMarking";

    private final String name;

    private final boolean isInitialMarking;

    public ReachabilityGraphMarking(String name, String jsonElementID, boolean isInitialMarking) {
        super(jsonElementID);
        this.name = name;
        this.isInitialMarking = isInitialMarking;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return REACHABILITY_GRAPH_MARKING_TYPE;
    }

    public boolean isInitialMarking() {
        return isInitialMarking;
    }

    @Override
    public String toString() {
        return "ReachabilityGraphMarking{" + "name='" + name + '\'' + ", isInitialMarking=" + isInitialMarking + '}';
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        double similarity = 0;

        if (!(reference instanceof ReachabilityGraphMarking referenceMarking)) {
            return similarity;
        }

        similarity += NameSimilarity.levenshteinSimilarity(name, referenceMarking.name);

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
        if (!(reference instanceof ReachabilityGraphMarking referencePlace)) {
            return 0;
        }

        double similarity = similarity(referencePlace);

        return ensureSimilarityRange(similarity);
    }
}
