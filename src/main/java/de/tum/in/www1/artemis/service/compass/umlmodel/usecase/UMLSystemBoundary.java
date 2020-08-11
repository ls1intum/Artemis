package de.tum.in.www1.artemis.service.compass.umlmodel.usecase;

import java.util.List;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.Similarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class UMLSystemBoundary extends UMLElement {

    public final static String UML_PACKAGE_TYPE = "Package";

    private String name;

    private List<UMLUseCase> useCases;

    public UMLSystemBoundary(String name, List<UMLUseCase> classes, String jsonElementID) {
        super(jsonElementID);

        this.useCases = useCases;
        this.name = name;

        setPackageOfClasses();
    }

    /**
     * Sets the package attribute of all classes contained in this package.
     */
    private void setPackageOfClasses() {
        for (UMLUseCase useCase : useCases) {
            useCase.setSystemBoundary(this);
        }
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        double similarity = 0;

        if (reference instanceof UMLSystemBoundary) {
            UMLSystemBoundary referencePackage = (UMLSystemBoundary) reference;
            similarity += NameSimilarity.levenshteinSimilarity(name, referencePackage.getName());
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

    public void addClass(UMLUseCase umlUseCase) {
        this.useCases.add(umlUseCase);
    }

    public void removeClass(UMLUseCase umlUseCase) {
        this.useCases.remove(umlUseCase);
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        UMLSystemBoundary otherPackage = (UMLSystemBoundary) obj;

        return otherPackage.useCases.size() == useCases.size() && otherPackage.useCases.containsAll(useCases) && useCases.containsAll(otherPackage.useCases);
    }
}
