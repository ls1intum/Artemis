package de.tum.cit.aet.artemis.service.compass.umlmodel.deployment;

import java.util.Objects;

import de.tum.cit.aet.artemis.service.compass.strategy.NameSimilarity;
import de.tum.cit.aet.artemis.service.compass.umlmodel.Similarity;
import de.tum.cit.aet.artemis.service.compass.umlmodel.UMLContainerElement;
import de.tum.cit.aet.artemis.service.compass.umlmodel.UMLElement;
import de.tum.cit.aet.artemis.service.compass.utils.CompassConfiguration;
import de.tum.cit.aet.artemis.service.compass.utils.SimilarityUtils;

public class UMLNode extends UMLContainerElement {

    public static final String UML_NODE_TYPE = "DeploymentNode";

    private final String name;

    private final String stereotype;

    public UMLNode(String name, String stereotype, String jsonElementID) {
        super(jsonElementID);
        this.name = name;
        this.stereotype = stereotype;
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        if (!(reference instanceof UMLNode referenceNode)) {
            return 0;
        }
        double similarity = 0;

        similarity += NameSimilarity.levenshteinSimilarity(getName(), referenceNode.getName()) * CompassConfiguration.NODE_NAME_WEIGHT;
        similarity += NameSimilarity.levenshteinSimilarity(stereotype, referenceNode.getStereotype()) * CompassConfiguration.NODE_STEREOTYPE_WEIGHT;

        if (SimilarityUtils.parentsSimilarOrEqual(getParentElement(), referenceNode.getParentElement())) {
            similarity += CompassConfiguration.NODE_PARENT_WEIGHT;
        }

        return ensureSimilarityRange(similarity);
    }

    @Override
    public String toString() {
        return "Node " + name;
    }

    @Override
    public String getName() {
        return name;
    }

    public String getStereotype() {
        return stereotype;
    }

    @Override
    public String getType() {
        return UML_NODE_TYPE;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), name, stereotype);
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        UMLNode otherNode = (UMLNode) obj;

        return otherNode.name.equals(this.name) && otherNode.stereotype.equals(this.stereotype);
    }
}
