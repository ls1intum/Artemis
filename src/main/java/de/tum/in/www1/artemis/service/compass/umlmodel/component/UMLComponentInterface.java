package de.tum.in.www1.artemis.service.compass.umlmodel.component;

import de.tum.in.www1.artemis.service.compass.umlmodel.Similarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class UMLComponentInterface extends UMLElement {

    /**
     * empty constructor used to make mockito happy
     */
    public UMLComponentInterface() {
        super();
    }

    public UMLComponentInterface(String jsonElementID) {
        super(jsonElementID);
    }

    /**
     * Calculates the similarity to another UML component interface by comparing the type and relations.
     *
     * @param reference the reference element to compare this class with
     * @return the similarity as number [0-1]
     */
    @Override
    public double similarity(Similarity<UMLElement> reference) {
        double similarity = 0;

        if (!(reference instanceof UMLComponentInterface)) {
            return similarity;
        }
        UMLComponentInterface referenceClass = (UMLComponentInterface) reference;

        // TODO: how should we compare this? (not really possible)

        return ensureSimilarityRange(similarity);
    }

    /**
     * Checks for overall similarity.
     *
     * @param reference the reference element to compare this class with
     * @return the similarity as number [0-1]
     */
    @Override
    public double overallSimilarity(Similarity<UMLElement> reference) {
        if (!(reference instanceof UMLComponentInterface)) {
            return 0;
        }

        UMLComponentInterface referenceClass = (UMLComponentInterface) reference;

        double similarity = similarity(referenceClass);

        return ensureSimilarityRange(similarity);
    }

    @Override
    public String toString() {
        return "Component Interface ";
    }

    @Override
    public String getName() {
        return "";
    }

    @Override
    public String getType() {
        return null;
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        UMLComponentInterface otherComponentInterface = (UMLComponentInterface) obj;
        // TODO: without a name this does not really make sense

        return true;
    }
}
