package de.tum.in.www1.artemis.service.compass.umlmodel.component;

import java.util.Objects;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.Similarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLContainerElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration;

public class UMLComponent extends UMLContainerElement {

    public final static String UML_COMPONENT_TYPE = "Component";

    private final String name;

    public UMLComponent(String name, String jsonElementID) {
        super(jsonElementID);
        this.name = name;
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        if (!(reference instanceof UMLComponent)) {
            return 0;
        }

        double similarity = 0;

        UMLComponent referenceComponent = (UMLComponent) reference;
        similarity += NameSimilarity.levenshteinSimilarity(getName(), referenceComponent.getName()) * CompassConfiguration.COMPONENT_NAME_WEIGHT;

        if (Objects.equals(getParentElement(), referenceComponent.getParentElement())) {
            similarity += CompassConfiguration.COMPONENT_PARENT_WEIGHT;
        }

        return ensureSimilarityRange(similarity);
    }

    @Override
    public String toString() {
        return "Component " + name;
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
