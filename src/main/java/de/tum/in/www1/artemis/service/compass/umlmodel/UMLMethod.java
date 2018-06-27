package de.tum.in.www1.artemis.service.compass.umlmodel;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;

import java.util.List;

public class UMLMethod extends UMLElement {

    private String name;
    private String returnType;
    private List<String> parameters;

    public UMLMethod(String name, String returnType, List<String> parameter, String jsonElementID) {
        this.name = name;
        this.returnType = returnType;
        this.parameters = parameter;

        this.jsonElementID = jsonElementID;
    }

    @Override
    public double similarity(UMLElement element) {
        double similarity = 0;

        if (element.getClass() != UMLMethod.class) {
            return similarity;
        }

        UMLMethod other = (UMLMethod) element;

        int elementCount = parameters.size() + 2;

        double weight = 1.0 / elementCount;

        similarity += NameSimilarity.nameEqualsSimilarity(name, other.name) * weight;

        similarity += NameSimilarity.nameEqualsSimilarity(returnType, other.returnType) * weight;

        for (String oParameter : other.parameters) {
            if (parameters.contains(oParameter)) {
                similarity += weight;
            }
        }

        return similarity;
    }

    @Override
    public String getName() {
        return "Method " + name;
    }
}
