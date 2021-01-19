package de.tum.in.www1.artemis.service.compass.umlmodel.component;

import java.util.Objects;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.Similarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration;

public class UMLComponentInterface extends UMLElement {

    public static final String UML_COMPONENT_INTERFACE_TYPE = "ComponentInterface";

    public static final String UML_DEPLOYMENT_INTERFACE_TYPE = "DeploymentInterface";

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
        if (!(reference instanceof UMLComponentInterface)) {
            return 0;
        }

        double similarity = 0;

        UMLComponentInterface referenceComponentInterface = (UMLComponentInterface) reference;
        similarity += NameSimilarity.levenshteinSimilarity(getName(), referenceComponentInterface.getName()) * CompassConfiguration.COMPONENT_NAME_WEIGHT;

        if (Objects.equals(getParentElement(), referenceComponentInterface.getParentElement())) {
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
        if (!(reference instanceof UMLComponentInterface)) {
            return 0;
        }

        UMLComponentInterface referenceClass = (UMLComponentInterface) reference;

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
    // TODO: in case of deployment diagrams, this is not really correct, it should then be UML_DEPLOYMENT_INTERFACE_TYPE
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
