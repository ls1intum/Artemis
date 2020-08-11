package de.tum.in.www1.artemis.service.compass.umlmodel.usecase;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.Similarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class UMLActor extends UMLElement {

    public final static String UML_OBJECT_TYPE = "ObjectName";

    private String name;

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
        return UML_OBJECT_TYPE;
    }

    @Override
    public String toString() {
        return "Object " + name;
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

        if (!(reference instanceof UMLActor)) {
            return similarity;
        }
        UMLActor referenceObject = (UMLActor) reference;

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
        if (!(reference instanceof UMLActor)) {
            return 0;
        }

        UMLActor referenceObject = (UMLActor) reference;

        double similarity = similarity(referenceObject);

        return ensureSimilarityRange(similarity);
    }
}
