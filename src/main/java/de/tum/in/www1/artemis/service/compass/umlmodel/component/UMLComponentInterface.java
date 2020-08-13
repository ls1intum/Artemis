package de.tum.in.www1.artemis.service.compass.umlmodel.component;

import com.google.common.base.CaseFormat;

import de.tum.in.www1.artemis.service.compass.umlmodel.Similarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration;

public class UMLComponentInterface extends UMLElement {

    public enum UMLComponentInterfaceType {
        PROVIDED, REQUIRED, DEPENDENCY
    }

    private UMLComponentInterfaceType type;

    /**
     * empty constructor used to make mockito happy
     */
    public UMLComponentInterface() {
        super();
    }

    public UMLComponentInterface(String jsonElementID, UMLComponentInterfaceType type) {
        super(jsonElementID);
        this.type = type;
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

        if (type == referenceClass.type) {
            similarity += CompassConfiguration.CLASS_TYPE_WEIGHT;
        }

        // TODO: take all relations with other elements into account to determine the similarity

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
        return "Component Interface " + type;
    }

    @Override
    public String getName() {
        return "";
    }

    @Override
    public String getType() {
        return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, type.name());
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        UMLComponentInterface otherComponentInterface = (UMLComponentInterface) obj;
        // TODO: without a name this does not really make sense
        if (otherComponentInterface.type.equals(this.type)) {
            return true;
        }

        return true;
    }
}
