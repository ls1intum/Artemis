package de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram;

import java.io.Serializable;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.base.CaseFormat;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.Similarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration;

public class UMLClass extends UMLElement implements Serializable {

    public enum UMLClassType {
        CLASS, ABSTRACT_CLASS, ENUMERATION, INTERFACE
    }

    private String name;

    private UMLClassType classType;

    @Nullable
    private UMLPackage umlPackage;

    private List<UMLAttribute> attributes;

    private List<UMLMethod> methods;

    /**
     * empty constructor used to make mockito happy
     */
    public UMLClass() {
        super();
    }

    public UMLClass(String name, List<UMLAttribute> attributes, List<UMLMethod> methods, String jsonElementID, UMLClassType classType) {
        super(jsonElementID);

        this.name = name;
        this.attributes = attributes;
        this.methods = methods;
        this.classType = classType;

        setParentForChildElements();
    }

    /**
     * Sets the parent of all child elements of this class. The child elements of a UML class are its attributes and methods.
     */
    private void setParentForChildElements() {
        for (UMLAttribute attribute : attributes) {
            attribute.setParentElement(this);
        }

        for (UMLMethod method : methods) {
            method.setParentElement(this);
        }
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        double similarity = 0;

        if (!(reference instanceof UMLClass referenceClass)) {
            return similarity;
        }

        // TODO: take the parent element into account

        similarity += NameSimilarity.levenshteinSimilarity(getName(), referenceClass.getName()) * CompassConfiguration.CLASS_NAME_WEIGHT;
        // TODO: we could distinguish that abstract class and interface is more similar than e.g. class and enumeration
        if (getClassType() == referenceClass.getClassType()) {
            similarity += CompassConfiguration.CLASS_TYPE_WEIGHT;
        }

        return ensureSimilarityRange(similarity);
    }

    /**
     * Checks for overall similarity including attributes and methods.
     *
     * @param reference the reference element to compare this class with
     * @return the similarity as number [0-1]
     */
    @Override
    public double overallSimilarity(Similarity<UMLElement> reference) {
        if (!(reference instanceof UMLClass referenceClass)) {
            return 0;
        }

        // To ensure symmetry (i.e. A.similarity(B) = B.similarity(A)) we make sure that this class always has less or equally many elements than the reference class.
        if (getElementCount() > referenceClass.getElementCount()) {
            return referenceClass.overallSimilarity(this);
        }

        double similarity = 0;

        // For calculating the weight of the similarity of every element, we consider the max. element count to reflect missing elements, i.e. it should not be possible to get a
        // similarity of 1 if the amount of elements differs. E.g. if we compare two classes, classA with one attribute and classB with two attributes, the highest possible
        // similarity between these classes should be 2/3 (name/type + one attribute can be similar), so the weight should be 1/3, no matter if we do
        // classA.overallSimilarity(classB) or classB.overallSimilarity(classA). As we know that the reference class has at least as many elements as this class, we take the
        // element count of the reference.
        double weight = 1.0 / referenceClass.getElementCount();

        // check similarity of class name and type
        similarity += weight * similarity(referenceClass);

        // check attributes
        for (UMLAttribute attribute : getAttributes()) {
            double similarityValue = referenceClass.similarAttributeScore(attribute);
            similarity += weight * similarityValue;
        }

        // check methods
        for (UMLMethod method : getMethods()) {
            double similarityValue = referenceClass.similarMethodScore(method);
            similarity += weight * similarityValue;
        }

        return ensureSimilarityRange(similarity);
    }

    /**
     * Returns the maximum similarity score of the given attribute and the list of attributes of this class, i.e. the similarity between the reference attribute and the most
     * similar attribute of this class.
     *
     * @param referenceAttribute the reference attribute that should be compared to the attributes of this class
     * @return the maximum similarity score of the reference attribute and the list of attributes of this class
     */
    private double similarAttributeScore(UMLAttribute referenceAttribute) {
        return similarScore(referenceAttribute, getAttributes());
    }

    /**
     * Returns the maximum similarity score of the given method and the list of methods of this class, i.e. the similarity between the reference method and the most
     * similar method of this class.
     *
     * @param referenceMethod the reference method that should be compared to the methods of this class
     * @return the maximum similarity score of the reference method and the list of method of this class
     */
    private double similarMethodScore(UMLMethod referenceMethod) {
        return similarScore(referenceMethod, getMethods());
    }

    @Override
    public String toString() {
        return "Class " + getName();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, classType.name());
    }

    /**
     * Checks if the UML class or one of its methods/attributes has the given element ID and returns the corresponding element. Otherwise, it returns null.
     *
     * @param jsonElementId the id of the UML element that should be returned
     * @return the UML element if one could be found for the given id, null otherwise
     */
    public UMLElement getElementByJSONID(String jsonElementId) {
        if (getJSONElementID().equals(jsonElementId)) {
            return this;
        }

        for (UMLAttribute umlAttribute : getAttributes()) {
            if (umlAttribute.getJSONElementID().equals(jsonElementId)) {
                return umlAttribute;
            }
        }

        for (UMLMethod umlMethod : getMethods()) {
            if (umlMethod.getJSONElementID().equals(jsonElementId)) {
                return umlMethod;
            }
        }

        return null;
    }

    /**
     * Get the list of UML attributes of this class.
     *
     * @return the list of attributes
     */
    public List<UMLAttribute> getAttributes() {
        return attributes;
    }

    /**
     * Get the list of UML methods of this class.
     *
     * @return the list of methods
     */
    public List<UMLMethod> getMethods() {
        return methods;
    }

    /**
     * Get the UML package that contains this UML class. If the class is not contained in any package, the umlPackage field is null.
     *
     * @return the UML package that contains this UML class, null if this class is not contained in any package
     */
    @Nullable
    public UMLPackage getUmlPackage() {
        return umlPackage;
    }

    public UMLClassType getClassType() {
        return classType;
    }

    /**
     * Set the UML package that contains this UML class. If the class is not contained in any package, the umlPackage field is null.
     *
     * @param umlPackage the UML package that contains this UML class
     */
    public void setUmlPackage(@Nullable UMLPackage umlPackage) {
        this.umlPackage = umlPackage;
    }

    /**
     * Get the number of elements of this UML class. The total number includes the number of child elements (i.e. the attributes and methods of the class) and the UML class itself.
     *
     * @return the number of elements of the class
     */
    public int getElementCount() {
        return getAttributes().size() + getMethods().size() + 1;
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        UMLClass otherClass = (UMLClass) obj;

        if (otherClass.getAttributes().size() != getAttributes().size() || otherClass.getMethods().size() != getMethods().size()) {
            return false;
        }

        if (!otherClass.getAttributes().containsAll(getAttributes()) || !getAttributes().containsAll(otherClass.getAttributes())) {
            return false;
        }

        return otherClass.getMethods().containsAll(getMethods()) && getMethods().containsAll(otherClass.getMethods());
    }
}
