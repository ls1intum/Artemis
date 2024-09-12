package de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.petrinet;

import de.tum.cit.aet.artemis.modeling.service.compass.strategy.NameSimilarity;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.Similarity;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.UMLElement;

public class PetriNetPlace extends UMLElement {

    public static final String PETRI_NET_PLACE_TYPE = "PetriNetPlace";

    private final String name;

    private final String amountOfTokens;

    private final String capacity;

    public PetriNetPlace(String name, String amountOfTokens, String capacity, String jsonElementID) {
        super(jsonElementID);
        this.name = name;
        this.amountOfTokens = amountOfTokens;
        this.capacity = capacity;
    }

    public String getAmountOfTokens() {
        return amountOfTokens;
    }

    public String getCapacity() {
        return capacity;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return PETRI_NET_PLACE_TYPE;
    }

    @Override
    public String toString() {
        return "PetriNetPlace " + name + "(" + amountOfTokens + " of " + capacity + ")";
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        double similarity = 0;

        if (!(reference instanceof PetriNetPlace referencePlace)) {
            return similarity;
        }

        double nameWeight = 0.5;
        double amountOfTokensWeight = 0.25;
        double capacityWeight = 0.25;

        similarity += NameSimilarity.levenshteinSimilarity(name, referencePlace.name) * nameWeight;
        similarity += NameSimilarity.levenshteinSimilarity(amountOfTokens, referencePlace.amountOfTokens) * amountOfTokensWeight;
        similarity += NameSimilarity.levenshteinSimilarity(capacity, referencePlace.capacity) * capacityWeight;

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
        if (!(reference instanceof PetriNetPlace referencePlace)) {
            return 0;
        }

        double similarity = similarity(referencePlace);

        return ensureSimilarityRange(similarity);
    }
}
