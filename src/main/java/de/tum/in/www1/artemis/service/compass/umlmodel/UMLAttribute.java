package de.tum.in.www1.artemis.service.compass.umlmodel;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration;

public class UMLAttribute extends UMLElement {

    private String name;
    private String type;

    public UMLAttribute(String name, String type, String jsonElementID) {
        this.name = name;
        this.type = type;
        this.jsonElementID = jsonElementID;
    }

    public double similarity(UMLElement element) {
        double similarity = 0;

        if (element.getClass() != UMLAttribute.class) {
            return similarity;
        }

        UMLAttribute other = (UMLAttribute) element;

        similarity += NameSimilarity.namePartiallyEqualsSimilarity(name, other.name) * CompassConfiguration.ATTRIBUTE_NAME_WEIGHT;

        similarity += NameSimilarity.nameEqualsSimilarity(type, other.type) * CompassConfiguration.ATTRIBUTE_TYPE_WEIGHT;

        return similarity;
    }

    @Override
    public String getName () {
        return "Attribute " + name;
    }
}
