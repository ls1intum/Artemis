package de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.petrinet;

import de.tum.cit.aet.artemis.modeling.service.compass.strategy.NameSimilarity;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.Similarity;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.UMLElement;

public class PetriNetTransition extends UMLElement {

    public static final String PETRI_NET_TRANSITION_TYPE = "PetriNetTransition";

    private final String name;

    public PetriNetTransition(String name, String jsonElementID) {
        super(jsonElementID);
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return PETRI_NET_TRANSITION_TYPE;
    }

    @Override
    public String toString() {
        return "PetriNetTransition " + name;
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        double similarity = 0;

        if (!(reference instanceof PetriNetTransition referenceTransition)) {
            return similarity;
        }

        similarity += NameSimilarity.levenshteinSimilarity(getName(), referenceTransition.getName());

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
        if (!(reference instanceof PetriNetTransition referenceTransition)) {
            return 0;
        }

        double similarity = similarity(referenceTransition);

        return ensureSimilarityRange(similarity);
    }
}
