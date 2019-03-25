package de.tum.in.www1.artemis.service.compass.umlmodel;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration;

import java.util.List;

public class UMLClass extends UMLElement {

    public enum UMLClassType {
        CLASS,
        ABSTRACT_CLASS,
        ENUMERATION,
        INTERFACE,
    }

    private String name;
    private UMLClassType type;
    private List<UMLAttribute> attributes;
    private List<UMLMethod> methods;

    public UMLClass() {
    }

    public UMLClass(String name, List<UMLAttribute> attributes, List<UMLMethod> methodList, String jsonElementID, String type) {
        this.name = name;
        this.attributes = attributes;
        this.methods = methodList;
        this.setJsonElementID(jsonElementID);
        this.type = UMLClassType.valueOf(type);
    }

    /**
     * checks for name similarity
     *
     * @param element the element to compare with
     * @return the similarity as number [0-1]
     */
    @Override
    public double similarity(UMLElement element) {
        double similarity = 0;

        if (element.getClass() == UMLClass.class) {

            similarity += NameSimilarity.nameContainsSimilarity(name, element.getName()) * CompassConfiguration.CLASS_NAME_WEIGHT;

            //TODO: we could distinguish that abstract class and interface is more similar than e.g. class and enumeration
            if (this.type == ((UMLClass) element).type) {
                similarity += CompassConfiguration.CLASS_TYPE_WEIGHT;
            }
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
    public String getName() {
        return "Class " + name;
    }

    @Override
    public String getValue() {
        return name;
    }

    UMLElement getElementByJSONID(String jsonID) {
        if (this.getJSONElementID().equals(jsonID)) {
            return this;
        }

        for (UMLAttribute umlAttribute : attributes) {
            if (umlAttribute.getJSONElementID().equals(jsonID)) {
                return umlAttribute;
            }
        }

        for (UMLMethod umlMethod : methods) {
            if (umlMethod.getJSONElementID().equals(jsonID)) {
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

    public int getElementCount() {
        return attributes.size() + methods.size() + 1;
    }
}
