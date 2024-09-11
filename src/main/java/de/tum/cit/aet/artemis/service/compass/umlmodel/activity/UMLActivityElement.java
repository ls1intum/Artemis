package de.tum.cit.aet.artemis.service.compass.umlmodel.activity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.annotation.Nullable;

import de.tum.cit.aet.artemis.service.compass.umlmodel.UMLElement;

public abstract class UMLActivityElement extends UMLElement implements Serializable {

    // TODO: use UML Container Element instead

    protected String name;

    @Nullable
    private UMLActivity parentActivity;

    /**
     * to make mockito happy
     */
    public UMLActivityElement() {
    }

    public UMLActivityElement(String name, String jsonElementID) {
        super(jsonElementID);
        this.name = name;
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
    public void setParentActivity(@Nullable UMLActivity parentActivity) {
        this.parentActivity = parentActivity;
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

        UMLActivityElement otherActivityElement = (UMLActivityElement) obj;

        if (otherActivityElement.getParentActivity() == null && parentActivity == null) {
            return true;
        }
        else if (otherActivityElement.getParentActivity() != null && parentActivity != null) {
            return Objects.equals(otherActivityElement.getParentActivity().getName(), parentActivity.getName());
        }

        return false;
    }
}
