package de.tum.in.www1.artemis.service.compass.umlmodel.component;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.Similarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class UMLComponentInterface extends UMLElement {

    public final static String UML_COMPONENT_INTERFACE_TYPE = "ComponentInterface";

    private final String name;

    public UMLComponentInterface(String name, String jsonElementID) {
        super(jsonElementID);
        this.name = name;
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
        UMLComponentInterface referenceComponentInterface = (UMLComponentInterface) reference;

        similarity += NameSimilarity.levenshteinSimilarity(name, referenceComponentInterface.getName());

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
        return "Component Interface " + name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return UML_COMPONENT_INTERFACE_TYPE;
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        UMLComponentInterface otherComponentInterface = (UMLComponentInterface) obj;

        return otherComponentInterface.name.equals(this.name);
    }
}
