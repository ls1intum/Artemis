package de.tum.in.www1.artemis.service.compass.umlmodel.component;

import java.util.List;

import javax.annotation.Nullable;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.Similarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class UMLComponent extends UMLElement {

    public final static String UML_COMPONENT_TYPE = "Component";

    private final String name;

    @Nullable
    private UMLComponent parentComponent;

    private final List<UMLComponent> subComponents;

    public UMLComponent(String name, List<UMLComponent> subComponents, String jsonElementID) {
        super(jsonElementID);

        this.subComponents = subComponents;
        this.name = name;

        for (var subComponent : subComponents) {
            subComponent.setParentComponent(this);
        }
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        double similarity = 0;

        if (reference instanceof UMLComponent) {
            UMLComponent referencePackage = (UMLComponent) reference;
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
        return UML_COMPONENT_TYPE;
    }

    public UMLComponent getParentComponent() {
        return parentComponent;
    }

    public void setParentComponent(UMLComponent parentComponent) {
        this.parentComponent = parentComponent;
    }

    /**
     * Add a UML component to the list of sub components in this component
     *
     * @param umlComponent the new UML component that should be added to this component
     */
    public void addSubComponent(UMLComponent umlComponent) {
        this.subComponents.add(umlComponent);
        umlComponent.setParentComponent(umlComponent);
    }

    /**
     * Add a UML class from the list of classes contained in this package.
     *
     * @param umlComponent the UML class that should be removed from this package
     */
    public void removeSubComponent(UMLComponent umlComponent) {
        this.subComponents.remove(umlComponent);
        umlComponent.setParentComponent(null);
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        UMLComponent otherPackage = (UMLComponent) obj;

        return otherPackage.subComponents.size() == subComponents.size() && otherPackage.subComponents.containsAll(subComponents)
                && subComponents.containsAll(otherPackage.subComponents);
    }
}
