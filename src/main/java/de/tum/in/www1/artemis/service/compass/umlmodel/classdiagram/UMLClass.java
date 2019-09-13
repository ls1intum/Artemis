package de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.google.common.base.CaseFormat;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration;

public class UMLClass extends UMLElement {

    public enum UMLClassType {
        CLASS, ABSTRACT_CLASS, ENUMERATION, INTERFACE;

        public static List<String> getTypesAsList() {
            return Arrays.stream(UMLClassType.values()).map(umlClassType -> CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, umlClassType.name()))
                    .collect(Collectors.toList());
        }
    }

    private String name;

    private UMLClassType type;

    @Nullable
    private UMLPackage umlPackage;

    private List<UMLAttribute> attributes;

    private List<UMLMethod> methods;

    public UMLClass(String name, List<UMLAttribute> attributes, List<UMLMethod> methods, String jsonElementID, String type) {
        this.name = name;
        this.attributes = attributes;
        this.methods = methods;
        this.setJsonElementID(jsonElementID);
        this.type = UMLClassType.valueOf(type);
    }

    /**
     * Calculates the similarity to another UML class by comparing the class names using the Levenshtein distance and checking the UML class types.
     *
     * @param other the UML class to compare this class with
     * @return the similarity as number [0-1]
     */
    @Override
    public double similarity(UMLElement other) {
        double similarity = 0;

        if (other.getClass() != UMLClass.class) {
            return similarity;
        }
        UMLClass otherClass = (UMLClass) other;

        similarity += NameSimilarity.levenshteinSimilarity(name, otherClass.name) * CompassConfiguration.CLASS_NAME_WEIGHT;
        // TODO: we could distinguish that abstract class and interface is more similar than e.g. class and enumeration
        if (this.type == otherClass.type) {
            similarity += CompassConfiguration.CLASS_TYPE_WEIGHT;
        }
        return similarity;
    }

    /**
     * checks for overall similarity including attributes and methods
     *
     * @param element the element to compare with
     * @return the similarity as number [0-1]
     */
    double overallSimilarity(UMLElement element) {
        double similarity = 0;

        if (element.getClass() != UMLClass.class) {
            return 0;
        }

        UMLClass reference = (UMLClass) element;

        int elementCount = attributes.size() + methods.size() + 1;

        double weight = 1.0 / elementCount;

        // count of items with similarity = 0
        int missingCount = 0;

        // check name
        // TODO: use similarity() method here instead? (it considers the Leivenshtein distance of the names and the type of the classes)
        if (reference.name.equals(this.name)) {
            similarity += weight;
        }

        // check attributes
        for (UMLAttribute attribute : attributes) {
            double similarityValue = reference.similarAttributeScore(attribute);
            similarity += weight * similarityValue;

            if (similarityValue < CompassConfiguration.NO_MATCH_THRESHOLD) {
                missingCount++;
            }
        }

        // check methods
        for (UMLMethod method : methods) {
            double similarityValue = reference.similarMethodScore(method);
            similarity += weight * similarityValue;

            if (similarityValue < CompassConfiguration.NO_MATCH_THRESHOLD) {
                missingCount++;
            }
        }

        // Penalty for missing attributes and methods
        int referenceMissingCount = Math.max(reference.attributes.size() - attributes.size(), 0);
        referenceMissingCount += Math.max(reference.methods.size() - methods.size(), 0);

        missingCount += referenceMissingCount;

        // make sure: 0.0 <= similarity <= simulation.0
        if (missingCount > 0) {
            // TODO: the two lines below are equal to "similarity -= CompassConfiguration.MISSING_ELEMENT_PENALTY;"
            double penaltyWeight = 1 / missingCount;
            similarity -= penaltyWeight * CompassConfiguration.MISSING_ELEMENT_PENALTY * missingCount;
        }

        if (similarity < 0) {
            similarity = 0;
        }

        return similarity;
    }

    private double similarAttributeScore(UMLAttribute otherAttribute) {
        return this.similarScore(otherAttribute, attributes);
    }

    private double similarMethodScore(UMLMethod otherMethod) {
        return this.similarScore(otherMethod, methods);
    }

    private double similarScore(UMLElement otherMethod, List<? extends UMLElement> elementList) {
        double similarity = 0;

        for (UMLElement element : elementList) {
            double curr_sim = element.similarity(otherMethod);

            if (curr_sim > similarity) {
                similarity = curr_sim;
            }

            // found perfect match
            if (curr_sim == 1) {
                break;
            }
        }

        return similarity;
    }

    @Override
    public String toString() {
        return "Class " + name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, type.name());
    }

    /**
     * Checks if the UML class or one of its methods/attributes has the given element ID and returns the corresponding element. Otherwise, it returns null.
     *
     * @param jsonElementId the id of the UML element that should be returned
     * @return the UML element if one could be found for the given id, null otherwise
     */
    UMLElement getElementByJSONID(String jsonElementId) {
        if (this.getJSONElementID().equals(jsonElementId)) {
            return this;
        }

        for (UMLAttribute umlAttribute : attributes) {
            if (umlAttribute.getJSONElementID().equals(jsonElementId)) {
                return umlAttribute;
            }
        }

        for (UMLMethod umlMethod : methods) {
            if (umlMethod.getJSONElementID().equals(jsonElementId)) {
                return umlMethod;
            }
        }

        return null;
    }

    public List<UMLAttribute> getAttributes() {
        return attributes;
    }

    public List<UMLMethod> getMethods() {
        return methods;
    }

    @Nullable
    public UMLPackage getUmlPackage() {
        return umlPackage;
    }

    public void setUmlPackage(@Nullable UMLPackage umlPackage) {
        this.umlPackage = umlPackage;
    }

    public int getElementCount() {
        return attributes.size() + methods.size() + 1;
    }
}
