package de.tum.in.www1.artemis.service.compass.umlmodel.deployment;

import java.util.List;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.Similarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLContainerElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class UMLNode extends UMLContainerElement {

    public final static String UML_NODE_TYPE = "Node";

    private final String name;

    private final String stereotype;

    public UMLNode(String name, List<UMLElement> subElements, String jsonElementID, String stereotype) {
        super(jsonElementID, subElements);
        this.name = name;
        this.stereotype = stereotype;

        for (var subElement : subElements) {
            subElement.setParentElement(this);
        }
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        double similarity = 0;

        if (reference instanceof UMLNode) {
            UMLNode referenceNode = (UMLNode) reference;
            similarity += NameSimilarity.levenshteinSimilarity(name, referenceNode.getName());
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
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        UMLNode otherNode = (UMLNode) obj;

        return otherNode.name.equals(this.name) && otherNode.stereotype.equals(this.stereotype);
    }
}
