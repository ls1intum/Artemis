package de.tum.in.www1.artemis.service.compass.umlmodel.component;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.Similarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLContainerElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class UMLComponent extends UMLContainerElement {

    public final static String UML_COMPONENT_TYPE = "Component";

    private final String name;

    public UMLComponent(String name, String jsonElementID) {
        super(jsonElementID);
        this.name = name;
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        double similarity = 0;

        if (reference instanceof UMLComponent) {
            UMLComponent referencePackage = (UMLComponent) reference;
            similarity += NameSimilarity.levenshteinSimilarity(name, referencePackage.getName());
        }

        return ensureSimilarityRange(similarity);
    }

    @Override
    public String toString() {
        return "Package " + name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return UML_COMPONENT_TYPE;
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        UMLComponent otherComponent = (UMLComponent) obj;

        return otherComponent.name.equals(this.name);
    }
}
