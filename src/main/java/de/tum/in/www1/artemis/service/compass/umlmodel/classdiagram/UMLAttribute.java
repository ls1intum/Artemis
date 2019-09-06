package de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration;

public class UMLAttribute extends UMLElement {

    public final static String UML_ATTRIBUTE_TYPE = "ClassAttribute";

    private UMLClass parentClass;

    private String name;

    private String attributeType;

    public UMLAttribute(String name, String attributeType, String jsonElementID) {
        this.name = name;
        this.attributeType = attributeType;
        this.setJsonElementID(jsonElementID);
    }

    public void setParentClass(UMLClass parentClass) {
        this.parentClass = parentClass;
    }

    /**
     * Compare this with another element to calculate the similarity
     *
     * @param element the element to compare with
     * @return the similarity as number [0-1]
     */
    @Override
    public double similarity(UMLElement element) {
        double similarity = 0;

        if (element.getClass() != UMLAttribute.class) {
            return similarity;
        }

        UMLAttribute other = (UMLAttribute) element;

        similarity += NameSimilarity.namePartiallyEqualsSimilarity(name, other.name) * CompassConfiguration.ATTRIBUTE_NAME_WEIGHT;

        similarity += NameSimilarity.nameEqualsSimilarity(attributeType, other.attributeType) * CompassConfiguration.ATTRIBUTE_TYPE_WEIGHT;

        return similarity;
    }

    @Override
    public String toString() {
        return "Attribute " + name + (attributeType != null && !attributeType.equals("") ? ": " + attributeType : "") + " in class " + parentClass.getName();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return UML_ATTRIBUTE_TYPE;
    }
}
