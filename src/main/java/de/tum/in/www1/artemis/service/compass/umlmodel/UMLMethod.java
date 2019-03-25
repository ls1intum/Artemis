package de.tum.in.www1.artemis.service.compass.umlmodel;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;

import java.util.List;

public class UMLMethod extends UMLElement {

    private UMLClass parentClass;
    private String completeName;
    private String name;
    private String returnType;
    private List<String> parameters;

    public UMLMethod(String completeName, String name, String returnType, List<String> parameter, String jsonElementID) {
        this.completeName = completeName;
        this.name = name;
        this.returnType = returnType;
        this.parameters = parameter;
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
        return "Method " + completeName + " in class " + parentClass.getValue();
    }

    @Override
    public String getValue() {
        return name;
    }
}
