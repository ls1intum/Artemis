package de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram;

import java.util.List;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.Similarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class UMLPackage extends UMLElement {

    public final static String UML_PACKAGE_TYPE = "Package";

    private String name;

    private List<UMLClass> classes;

    public UMLPackage(String name, List<UMLClass> classes, String jsonElementID) {
        super(jsonElementID);

        this.classes = classes;
        this.name = name;

        setPackageOfClasses();
    }

    /**
     * Sets the package attribute of all classes contained in this package.
     */
    private void setPackageOfClasses() {
        for (UMLClass umlClass : classes) {
            umlClass.setUmlPackage(this);
        }
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        double similarity = 0;

        if (reference instanceof UMLPackage) {
            UMLPackage referencePackage = (UMLPackage) reference;
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

    /**
     * Add a UML class to the list of classes contained in this package.
     *
     * @param umlClass the new UML class that should be added to this package
     */
    public void addClass(UMLClass umlClass) {
        this.classes.add(umlClass);
    }

    /**
     * Add a UML class from the list of classes contained in this package.
     *
     * @param umlClass the UML class that should be removed from this package
     */
    public void removeClass(UMLClass umlClass) {
        this.classes.remove(umlClass);
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        UMLPackage otherPackage = (UMLPackage) obj;

        return otherPackage.classes.size() == classes.size() && otherPackage.classes.containsAll(classes) && classes.containsAll(otherPackage.classes);
    }
}
