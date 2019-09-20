package de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.Similarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration;

public class UMLAttribute extends UMLElement {

    public final static String UML_ATTRIBUTE_TYPE = "ClassAttribute";

    private UMLClass parentClass;

    private String name;

    private String attributeType;

    public UMLAttribute(String name, String attributeType, String jsonElementID) {
        super(jsonElementID);

        this.name = name;
        this.attributeType = attributeType;
    }

    /**
     * Set the parent class of this attribute, i.e. the UML class that contains it.
     *
     * @param parentClass the UML class that contains this attribute
     */
    public void setParentClass(UMLClass parentClass) {
        this.parentClass = parentClass;
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        double similarity = 0;

        if (reference == null || reference.getClass() != UMLAttribute.class) {
            return similarity;
        }

        UMLAttribute referenceAttribute = (UMLAttribute) reference;

        similarity += NameSimilarity.namePartiallyEqualsSimilarity(name, referenceAttribute.name) * CompassConfiguration.ATTRIBUTE_NAME_WEIGHT;

        similarity += NameSimilarity.nameEqualsSimilarity(attributeType, referenceAttribute.attributeType) * CompassConfiguration.ATTRIBUTE_TYPE_WEIGHT;

        return ensureSimilarityRange(similarity);
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
