package de.tum.in.www1.artemis.service.compass.umlmodel;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration;

import java.util.List;

public class UMLClass extends UMLElement {

    // TODO move activity diagram types into its own class
    public enum UMLClassType {
        CLASS, ABSTRACT_CLASS, ENUMERATION, INTERFACE,
        ACTIVITY_CONTROL_INITIAL_NODE, ACTIVITY_CONTROL_FINAL_NODE, ACTIVITY_ACTION_NODE, ACTIVITY_OBJECT, ACTIVITY_MERGE_NODE, ACTIVITY_FORK_NODE, ACTIVITY_FORK_NODE_HORIZONTAL
    }

    private String name;
    private UMLClassType type;

    List<UMLAttribute> attributeList;
    List<UMLMethod> methodList;

    public UMLClass(String name, List<UMLAttribute> attributeList, List<UMLMethod> methodList, String jsonElementID, String type) {
        this.name = name;
        this.attributeList = attributeList;
        this.methodList = methodList;
        this.jsonElementID = jsonElementID;
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

        int elementCount = attributeList.size() + methodList.size() + 1;

        double weight = 1.0 / elementCount;

        // count of items with similarity = 0
        int missingCount = 0;

        // check name
        if (reference.name.equals(this.name)) {
            similarity += weight;
        }

        // check attributes
        for (UMLAttribute attribute : attributeList) {
            double similarityValue = reference.similarAttributeScore(attribute);
            similarity += weight * similarityValue;

            if (similarityValue < CompassConfiguration.NO_MATCH_THRESHOLD) {
                missingCount++;
            }
        }

        // check methods
        for (UMLMethod method : methodList) {
            double similarityValue = reference.similarMethodScore(method);
            similarity += weight * similarityValue;

            if (similarityValue < CompassConfiguration.NO_MATCH_THRESHOLD) {
                missingCount++;
            }
        }

        // Penalty for missing attributes and methods
        int referenceMissingCount = Math.max(reference.attributeList.size() - attributeList.size(), 0);
        referenceMissingCount += Math.max(reference.methodList.size() - methodList.size(), 0);

        missingCount += referenceMissingCount;

        // make sure: 0.0 <= similarity <= simulation.0
        if (missingCount > 0 ) {
            double penaltyWeight = 1 / missingCount;
            similarity -= penaltyWeight * CompassConfiguration.MISSING_ELEMENT_PENALTY * missingCount;
        }

        if (similarity < 0) {
            similarity = 0;
        }

        return similarity;
    }

    private double similarAttributeScore(UMLAttribute otherAttribute) {
        return this.similarScore(otherAttribute, attributeList);
    }

    private double similarMethodScore(UMLMethod otherMethod) {
        return this.similarScore(otherMethod, methodList);
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
    public String getName () {
        return "Class " + name;
    }

    @Override
    public String getValue() {
        return name;
    }

    UMLElement getElementByJSONID(String jsonID) {
        if (this.jsonElementID.equals(jsonID)) {
            return this;
        }

        for (UMLAttribute umlAttribute : attributeList) {
            if (umlAttribute.jsonElementID.equals(jsonID)) {
                return umlAttribute;
            }
        }

        for (UMLMethod umlMethod : methodList) {
            if (umlMethod.jsonElementID.equals(jsonID)) {
                return umlMethod;
            }
        }

        return null;
    }


    public List<UMLAttribute> getAttributeList() {
        return attributeList;
    }

    public List<UMLMethod> getMethodList() {
        return methodList;
    }

    public int getElementCount() {
        return attributeList.size() + methodList.size() + 1;
    }
}
