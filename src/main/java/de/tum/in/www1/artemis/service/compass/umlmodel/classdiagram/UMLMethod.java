package de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.Similarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

import java.util.List;

public class UMLMethod extends UMLElement {

    public final static String UML_METHOD_TYPE = "ClassMethod";

    private UMLClass parentClass;

    private String completeName;

    private String name;

    private String returnType;

    private List<String> parameters;

    public UMLMethod(String completeName, String name, String returnType, List<String> parameter, String jsonElementID) {
        super(jsonElementID);

        this.completeName = completeName;
        this.name = name;
        this.returnType = returnType;
        this.parameters = parameter;
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

        if (reference == null || reference.getClass() != UMLMethod.class) {
            return similarity;
        }

        UMLMethod referenceMethod = (UMLMethod) reference;

        int elementCount = parameters.size() + 2;

        double weight = 1.0 / elementCount;

        similarity += NameSimilarity.nameEqualsSimilarity(name, referenceMethod.name) * weight;

        similarity += NameSimilarity.nameEqualsSimilarity(returnType, referenceMethod.returnType) * weight;

        for (String oParameter : referenceMethod.parameters) {
            if (parameters.contains(oParameter)) {
                similarity += weight;
            }
        }

        return ensureSimilarityRange(similarity);
    }

    @Override
    public String toString() {
        return "Method " + completeName + " in class " + parentClass.getName();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return UML_METHOD_TYPE;
    }
}
