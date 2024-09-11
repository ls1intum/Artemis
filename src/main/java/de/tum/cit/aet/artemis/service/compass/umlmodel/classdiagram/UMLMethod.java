package de.tum.cit.aet.artemis.service.compass.umlmodel.classdiagram;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import jakarta.validation.constraints.NotNull;

import de.tum.cit.aet.artemis.service.compass.strategy.NameSimilarity;
import de.tum.cit.aet.artemis.service.compass.umlmodel.Similarity;
import de.tum.cit.aet.artemis.service.compass.umlmodel.UMLElement;
import de.tum.cit.aet.artemis.service.compass.utils.CompassConfiguration;

public class UMLMethod extends UMLElement implements Serializable {

    public static final String UML_METHOD_TYPE = "ClassMethod";

    private UMLElement parentElement;

    private String completeName;

    private String name;

    private String returnType;

    private List<String> parameters;

    /**
     * empty constructor used to make mockito happy
     */
    public UMLMethod() {
        // default empty constructor
    }

    public UMLMethod(String completeName, String name, String returnType, List<String> parameters, String jsonElementID) {
        super(jsonElementID);

        this.completeName = completeName;
        this.name = name;
        this.returnType = returnType;
        this.parameters = parameters;
    }

    /**
     * Get the parent element of this method, i.e. the UML class that contains it.
     *
     * @return the UML class that contains this method
     */
    @NotNull
    @Override
    public UMLElement getParentElement() {
        return parentElement;
    }

    /**
     * Set the parent class of this method, i.e. the UML class that contains it.
     *
     * @param parentElement the UML class that contains this method
     */
    @Override
    public void setParentElement(@NotNull UMLElement parentElement) {
        this.parentElement = parentElement;
    }

    /**
     * Get the return type of this method.
     *
     * @return the return type of this method as String
     */
    public String getReturnType() {
        return returnType;
    }

    /**
     * Get the parameter list of this method.
     *
     * @return the list of parameters (as Strings) of this method
     */
    public List<String> getParameters() {
        return parameters;
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        double similarity = 0;

        if (!(reference instanceof UMLMethod referenceMethod)) {
            return similarity;
        }

        if (!parentsSimilar(referenceMethod)) {
            return similarity;
        }

        int elementCount = getParameters().size() + 2;
        double weight = 1.0 / elementCount;

        similarity += NameSimilarity.levenshteinSimilarity(getName(), referenceMethod.getName()) * weight;
        similarity += NameSimilarity.nameEqualsSimilarity(getReturnType(), referenceMethod.getReturnType()) * weight;

        List<String> referenceParameters = referenceMethod.getParameters() != null ? referenceMethod.getParameters() : Collections.emptyList();
        for (String referenceParameter : referenceParameters) {
            if (getParameters().contains(referenceParameter)) {
                similarity += weight;
            }
        }

        return ensureSimilarityRange(similarity);
    }

    /**
     * Checks if the parent classes of this method and the given reference method are similar/equal by comparing the similarity IDs of both parent classes. If the similarity
     * IDs are not set, it calculates the similarity of the parent classes itself and checks against the configured equality threshold.
     *
     * @param referenceMethod the reference method of which the parent class is compared against the parent class of this method
     * @return true if the parent classes are similar/equal, false otherwise
     */
    private boolean parentsSimilar(UMLMethod referenceMethod) {
        if (referenceMethod.getParentElement() != null) {
            if (parentElement.getSimilarityID() != -1 && referenceMethod.getParentElement().getSimilarityID() != -1) {
                return parentElement.getSimilarityID() == referenceMethod.getParentElement().getSimilarityID();
            }
        }

        return parentElement.similarity(referenceMethod.getParentElement()) > CompassConfiguration.EQUALITY_THRESHOLD;
    }

    @Override
    public String toString() {
        return "Method " + completeName + " in class " + parentElement.getName();
    }

    @Override
    public String getName() {
        return name;
    }

    public String getCompleteName() {
        return completeName;
    }

    @Override
    public String getType() {
        return UML_METHOD_TYPE;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), completeName, name, returnType, parameters);
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        UMLMethod otherMethod = (UMLMethod) obj;

        if (otherMethod.getParameters().size() != getParameters().size() || !otherMethod.getParameters().containsAll(getParameters())
                || !getParameters().containsAll(otherMethod.getParameters())) {
            return false;
        }

        return Objects.equals(otherMethod.getReturnType(), getReturnType()) && Objects.equals(otherMethod.getParentElement().getName(), getParentElement().getName());
    }
}
