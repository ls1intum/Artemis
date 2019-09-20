package de.tum.in.www1.artemis.service.compass.umlmodel.activitydiagram;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.Similarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

import javax.annotation.Nullable;

public abstract class UMLActivityElement extends UMLElement {

    protected String name;

    @Nullable
    private UMLActivity parentActivity;

    public UMLActivityElement(String name, String jsonElementID) {
        super(jsonElementID);

        this.name = name;
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        if (reference == null || reference.getClass() != UMLActivityElement.class) {
            return 0;
        }

        UMLActivityElement referenceElement = (UMLActivityElement) reference;

        return NameSimilarity.levenshteinSimilarity(name, referenceElement.getName());
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return getType() + " " + getName();
    }

    /**
     * Get the parent activity of this UML activity element, i.e. the UML activity that contains this element. If the activity element is not contained in any package, the parent
     * activity field is null.
     *
     * @return the parent activity that contains this activity element
     */
    @Nullable
    public UMLActivity getParentActivity() {
        return parentActivity;
    }

    /**
     * Set the parent activity of this UML activity element, i.e. the UML activity that contains this element. If the activity element is not contained in any package, the parent
     * activity field is null.
     *
     * @param parentActivity the parent activity that contains this activity element
     */
    protected void setParentActivity(UMLActivity parentActivity) {
        this.parentActivity = parentActivity;
    }
}
