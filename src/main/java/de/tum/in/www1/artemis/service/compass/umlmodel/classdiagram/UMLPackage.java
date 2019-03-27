package de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

import java.util.List;

public class UMLPackage extends UMLElement {

    public final static String UML_PACKAGE_TYPE = "Package";

    private String name;

    private List<UMLClass> classes;

    public UMLPackage(String name, List<UMLClass> classes, String jsonElementID) {
        this.classes = classes;
        this.name = name;
        this.setJsonElementID(jsonElementID);
    }

    @Override
    public double similarity(UMLElement element) {
        double similarity = 0;
        if (element.getClass() == UMLPackage.class) {
            similarity += NameSimilarity.nameContainsSimilarity(name, element.getName());
        }
        return similarity;
    }

    @Override
    public String getName() {
        return "Package " + name;
    }

    @Override
    public String getValue() {
        return name;
    }

    @Override
    public String getType() { return UML_PACKAGE_TYPE; }

    public void addClass(UMLClass umlClass) {
        this.classes.add(umlClass);
    }

    public void removeClass(UMLClass umlClass) {
        this.classes.remove(umlClass);
    }
}
