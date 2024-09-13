package de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.usecase;

import de.tum.cit.aet.artemis.modeling.service.compass.strategy.NameSimilarity;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.Similarity;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.UMLElement;

public class UMLActor extends UMLElement {

    public static final String UML_ACTOR_TYPE = "UseCaseActor";

    private final String name;

    public UMLActor(String name, String jsonElementID) {
        super(jsonElementID);
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return UML_ACTOR_TYPE;
    }

    @Override
    public String toString() {
        return "Actor " + name;
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        double similarity = 0;

        if (!(reference instanceof UMLActor referenceObject)) {
            return similarity;
        }

        similarity += NameSimilarity.levenshteinSimilarity(getName(), referenceObject.getName());

        // NOTE: even if it is possible in Apollon, a parent element does not really make sense here and would simply be wrong

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
        if (!(reference instanceof UMLActor referenceObject)) {
            return 0;
        }

        double similarity = similarity(referenceObject);

        return ensureSimilarityRange(similarity);
    }
}
