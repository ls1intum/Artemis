package de.tum.in.www1.artemis.service.compass.umlmodel.usecase;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.Similarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLContainerElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class UMLSystemBoundary extends UMLContainerElement {

    public static final String UML_SYSTEM_BOUNDARY_TYPE = "UseCaseSystem";

    private final String name;

    public UMLSystemBoundary(String name, String jsonElementID) {
        super(jsonElementID);
        this.name = name;
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        double similarity = 0;

        if (reference instanceof UMLSystemBoundary referencePackage) {
            similarity += NameSimilarity.levenshteinSimilarity(getName(), referencePackage.getName());
        }

        // NOTE: even if it is possible in Apollon, a parent element does not really make sense here and would simply be wrong

        return ensureSimilarityRange(similarity);
    }

    @Override
    public String toString() {
        return "System Boundary " + name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return UML_SYSTEM_BOUNDARY_TYPE;
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        UMLSystemBoundary systemBoundary = (UMLSystemBoundary) obj;

        return name.equals(systemBoundary.name);
    }
}
