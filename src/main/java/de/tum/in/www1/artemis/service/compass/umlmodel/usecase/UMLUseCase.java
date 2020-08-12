package de.tum.in.www1.artemis.service.compass.umlmodel.usecase;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.Similarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class UMLUseCase extends UMLElement {

    public final static String UML_USE_CASE_TYPE = "UseCase";

    private String name;

    private UMLSystemBoundary systemBoundary;

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

    /**
     * Calculates the similarity to another UML class by comparing the class names using the Levenshtein distance and checking the UML class types.
     *
     * @param reference the reference element to compare this class with
     * @return the similarity as number [0-1]
     */
    @Override
    public double similarity(Similarity<UMLElement> reference) {
        double similarity = 0;

        if (!(reference instanceof UMLUseCase)) {
            return similarity;
        }
        UMLUseCase referenceObject = (UMLUseCase) reference;

        similarity += NameSimilarity.levenshteinSimilarity(name, referenceObject.getName());

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
        if (!(reference instanceof UMLUseCase)) {
            return 0;
        }

        UMLUseCase referenceObject = (UMLUseCase) reference;

        double similarity = similarity(referenceObject);

        return ensureSimilarityRange(similarity);
    }

    public void setSystemBoundary(UMLSystemBoundary umlSystemBoundary) {
        this.systemBoundary = systemBoundary;
    }
}
