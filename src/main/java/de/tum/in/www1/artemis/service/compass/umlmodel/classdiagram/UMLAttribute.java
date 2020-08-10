package de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram;

import java.util.Objects;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.Similarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration;

public class UMLAttribute extends UMLElement {

    public final static String UML_ATTRIBUTE_TYPE = "ClassAttribute";

    private UMLElement parentElement;

    private String name;

    private String attributeType;

    public UMLAttribute(String name, String attributeType, String jsonElementID) {
        super(jsonElementID);

        this.name = name;
        this.attributeType = attributeType;
    }

    /**
     * Get the parent element of this attribute, i.e. the UML class that contains it.
     *
     * @return the UML element that contains this attribute
     */
    public UMLElement getParentElement() {
        return parentElement;
    }

    /**
     * Set the parent element of this attribute, i.e. the UML class that contains it.
     *
     * @param parentElement the UML element that contains this attribute
     */
    public void setParentElement(UMLElement parentElement) {
        this.parentElement = parentElement;
    }

    /**
     * Get the type of this attribute.
     *
     * @return the attribute type as String
     */
    String getAttributeType() {
        return attributeType;
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        double similarity = 0;

        if (!(reference instanceof UMLAttribute)) {
            return similarity;
        }

        UMLAttribute referenceAttribute = (UMLAttribute) reference;

        if (!parentsSimilar(referenceAttribute)) {
            return similarity;
        }

        similarity += NameSimilarity.levenshteinSimilarity(name, referenceAttribute.getName()) * CompassConfiguration.ATTRIBUTE_NAME_WEIGHT;

        similarity += NameSimilarity.nameEqualsSimilarity(attributeType, referenceAttribute.getAttributeType()) * CompassConfiguration.ATTRIBUTE_TYPE_WEIGHT;

        return ensureSimilarityRange(similarity);
    }

    /**
     * Checks if the parent classes of this attribute and the given reference attribute are similar/equal by comparing the similarity IDs of both parent classes. If the similarity
     * IDs are not set, it calculates the similarity of the parent classes itself and checks against the configured equality threshold.
     *
     * @param referenceAttribute the reference attribute of which the parent class is compared against the parent class of this attribute
     * @return true if the parent classes are similar/equal, false otherwise
     */
    private boolean parentsSimilar(UMLAttribute referenceAttribute) {
        if (parentElement.getSimilarityID() != -1 && referenceAttribute.getParentElement().getSimilarityID() != -1) {
            return parentElement.getSimilarityID() == referenceAttribute.getParentElement().getSimilarityID();
        }

        return parentElement.similarity(referenceAttribute.getParentElement()) > CompassConfiguration.EQUALITY_THRESHOLD;
    }

    @Override
    public String toString() {
        return "Attribute " + name + (attributeType != null && !attributeType.equals("") ? ": " + attributeType : "") + " in class " + parentElement.getName();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return UML_ATTRIBUTE_TYPE;
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        UMLAttribute otherAttribute = (UMLAttribute) obj;

        return Objects.equals(otherAttribute.getAttributeType(), attributeType) && Objects.equals(otherAttribute.getParentElement().getName(), parentElement.getName());
    }
}
