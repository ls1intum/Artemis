package de.tum.cit.aet.artemis.service.compass.umlmodel.classdiagram;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

import de.tum.cit.aet.artemis.service.compass.strategy.NameSimilarity;
import de.tum.cit.aet.artemis.service.compass.umlmodel.Similarity;
import de.tum.cit.aet.artemis.service.compass.umlmodel.UMLContainerElement;
import de.tum.cit.aet.artemis.service.compass.umlmodel.UMLElement;

public class UMLPackage extends UMLContainerElement implements Serializable {

    public static final String UML_PACKAGE_TYPE = "Package";

    private final String name;

    public UMLPackage(String name, List<UMLElement> elements, String jsonElementID) {
        super(jsonElementID, elements);
        this.name = name;
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        double similarity = 0;

        if (reference instanceof UMLPackage referencePackage) {
            similarity += NameSimilarity.levenshteinSimilarity(getName(), referencePackage.getName());
        }

        return ensureSimilarityRange(similarity);
    }

    @Override
    public String toString() {
        return "Package " + name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return UML_PACKAGE_TYPE;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), name);
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }
        UMLPackage otherPackage = (UMLPackage) obj;
        return otherPackage.getName().equals(this.getName());
    }
}
