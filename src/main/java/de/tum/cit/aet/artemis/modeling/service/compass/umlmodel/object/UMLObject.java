package de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.object;

import java.util.List;

import de.tum.cit.aet.artemis.modeling.service.compass.strategy.NameSimilarity;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.Similarity;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.UMLElement;

public class UMLObject extends UMLElement {

    public static final String UML_OBJECT_TYPE = "ObjectName";

    private final String name;

    private final List<UMLObjectAttribute> attributes;

    private final List<UMLObjectMethod> methods;

    public UMLObject(String name, List<UMLObjectAttribute> attributes, List<UMLObjectMethod> methods, String jsonElementID) {
        super(jsonElementID);
        this.name = name;
        this.attributes = attributes;
        this.methods = methods;

        setParentForChildElements();
    }

    /**
     * Sets the parent of all child elements. The child elements of a UML object are its attributes and methods.
     */
    private void setParentForChildElements() {
        for (UMLObjectAttribute attribute : attributes) {
            attribute.setParentElement(this);
        }

        for (UMLObjectMethod method : methods) {
            method.setParentElement(this);
        }
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        double similarity = 0;

        if (!(reference instanceof UMLObject referenceObject)) {
            return similarity;
        }

        similarity += NameSimilarity.levenshteinSimilarity(getName(), referenceObject.getName());

        return ensureSimilarityRange(similarity);
    }

    /**
     * Checks for overall similarity including attributes and methods.
     *
     * @param reference the reference element to compare this object with
     * @return the similarity as number [0-1]
     */
    @Override
    public double overallSimilarity(Similarity<UMLElement> reference) {
        if (!(reference instanceof UMLObject referenceObject)) {
            return 0;
        }

        // To ensure symmetry (i.e. A.similarity(B) = B.similarity(A)) we make sure that this class always has less or equally many elements than the reference class.
        if (getElementCount() > referenceObject.getElementCount()) {
            return referenceObject.overallSimilarity(this);
        }

        double similarity = 0;

        // For calculating the weight of the similarity of every element, we consider the max. element count to reflect missing elements, i.e. it should not be possible to get a
        // similarity of 1 if the amount of elements differs. E.g. if we compare two classes, classA with one attribute and classB with two attributes, the highest possible
        // similarity between these classes should be 2/3 (name/type + one attribute can be similar), so the weight should be 1/3, no matter if we do
        // classA.overallSimilarity(classB) or classB.overallSimilarity(classA). As we know that the reference class has at least as many elements as this class, we take the
        // element count of the reference.
        double weight = 1.0 / referenceObject.getElementCount();

        // check similarity of class name and type
        similarity += weight * similarity(referenceObject);

        // check attributes
        for (UMLObjectAttribute attribute : attributes) {
            double similarityValue = referenceObject.similarAttributeScore(attribute);
            similarity += weight * similarityValue;
        }

        // check methods
        for (UMLObjectMethod method : methods) {
            double similarityValue = referenceObject.similarMethodScore(method);
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
    private double similarAttributeScore(UMLObjectAttribute referenceAttribute) {
        return similarScore(referenceAttribute, attributes);
    }

    /**
     * Returns the maximum similarity score of the given method and the list of methods of this class, i.e. the similarity between the reference method and the most
     * similar method of this class.
     *
     * @param referenceMethod the reference method that should be compared to the methods of this class
     * @return the maximum similarity score of the reference method and the list of method of this class
     */
    private double similarMethodScore(UMLObjectMethod referenceMethod) {
        return similarScore(referenceMethod, methods);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return UML_OBJECT_TYPE;
    }

    @Override
    public String toString() {
        return "Object " + name;
    }

    /**
     * Get the list of UML attributes of this object.
     *
     * @return the list of attributes
     */
    public List<UMLObjectAttribute> getAttributes() {
        return attributes;
    }

    /**
     * Get the list of UML methods of this object.
     *
     * @return the list of methods
     */
    public List<UMLObjectMethod> getMethods() {
        return methods;
    }

    /**
     * Get the number of elements of this UML object. The total number includes the number of child elements (i.e. the attributes and methods of the object) and the UML object
     * itself.
     *
     * @return the number of elements of the object
     */
    public int getElementCount() {
        return attributes.size() + methods.size() + 1;
    }

}
