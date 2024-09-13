package de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.component;

import java.util.Objects;

import de.tum.cit.aet.artemis.modeling.service.compass.strategy.NameSimilarity;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.Similarity;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.UMLElement;
import de.tum.cit.aet.artemis.modeling.service.compass.utils.CompassConfiguration;
import de.tum.cit.aet.artemis.modeling.service.compass.utils.SimilarityUtils;

public class UMLComponentInterface extends UMLElement {

    public static final String UML_COMPONENT_INTERFACE_TYPE = "ComponentInterface";

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
        if (!(reference instanceof UMLComponentInterface referenceComponentInterface)) {
            return 0;
        }

        double similarity = 0;

        similarity += NameSimilarity.levenshteinSimilarity(getName(), referenceComponentInterface.getName()) * CompassConfiguration.COMPONENT_NAME_WEIGHT;

        if (SimilarityUtils.parentsSimilarOrEqual(getParentElement(), referenceComponentInterface.getParentElement())) {
            similarity += CompassConfiguration.COMPONENT_PARENT_WEIGHT;
        }

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
        if (!(reference instanceof UMLComponentInterface referenceClass)) {
            return 0;
        }

        double similarity = similarity(referenceClass);

        return ensureSimilarityRange(similarity);
    }

    @Override
    // TODO: in case of deployment diagrams, this is not really correct, it should then be Deployment Interface
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
    public int hashCode() {
        return Objects.hash(super.hashCode(), name);
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
