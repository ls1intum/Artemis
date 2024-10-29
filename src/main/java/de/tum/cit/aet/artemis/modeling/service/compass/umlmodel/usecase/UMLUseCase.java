package de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.usecase;

import de.tum.cit.aet.artemis.modeling.service.compass.strategy.NameSimilarity;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.Similarity;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.UMLElement;
import de.tum.cit.aet.artemis.modeling.service.compass.utils.CompassConfiguration;
import de.tum.cit.aet.artemis.modeling.service.compass.utils.SimilarityUtils;

public class UMLUseCase extends UMLElement {

    public static final String UML_USE_CASE_TYPE = "UseCase";

    private final String name;

    public UMLUseCase(String name, String jsonElementID) {
        super(jsonElementID);
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return UML_USE_CASE_TYPE;
    }

    @Override
    public String toString() {
        return "Use Case " + name;
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        if (!(reference instanceof UMLUseCase referenceUseCase)) {
            return 0;
        }

        double similarity = 0;

        similarity += NameSimilarity.levenshteinSimilarity(getName(), referenceUseCase.getName()) * CompassConfiguration.COMPONENT_NAME_WEIGHT;

        if (SimilarityUtils.parentsSimilarOrEqual(getParentElement(), referenceUseCase.getParentElement())) {
            similarity += CompassConfiguration.COMPONENT_PARENT_WEIGHT;
        }

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
        if (!(reference instanceof UMLUseCase referenceObject)) {
            return 0;
        }

        double similarity = similarity(referenceObject);

        return ensureSimilarityRange(similarity);
    }
}
