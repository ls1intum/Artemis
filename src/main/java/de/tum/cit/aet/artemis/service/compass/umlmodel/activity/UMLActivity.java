package de.tum.cit.aet.artemis.service.compass.umlmodel.activity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import de.tum.cit.aet.artemis.service.compass.strategy.NameSimilarity;
import de.tum.cit.aet.artemis.service.compass.umlmodel.Similarity;
import de.tum.cit.aet.artemis.service.compass.umlmodel.UMLElement;

public class UMLActivity extends UMLActivityElement implements Serializable {

    public static final String UML_ACTIVITY_TYPE = "Activity";

    private final List<UMLActivityElement> childElements;

    /**
     * empty constructor used to make mockito happy
     */
    public UMLActivity() {
        childElements = new ArrayList<>();
    }

    public UMLActivity(String name, List<UMLActivityElement> childElements, String jsonElementID) {
        super(name, jsonElementID);
        this.childElements = childElements;
        setActivityOfContainedElements();
    }

    /**
     * Sets the parent activity of all activity elements contained in this UML activity.
     */
    private void setActivityOfContainedElements() {
        for (UMLActivityElement childElement : childElements) {
            childElement.setParentActivity(this);
        }
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        if (!(reference instanceof UMLActivity referenceActivity)) {
            return 0;
        }

        return NameSimilarity.levenshteinSimilarity(getName(), referenceActivity.getName());
    }

    @Override
    public String getType() {
        return UML_ACTIVITY_TYPE;
    }

    /**
     * Add an activity element to the list of child elements contained in this UML activity.
     *
     * @param childElement the activity element that should be added
     */
    public void addChildElement(UMLActivityElement childElement) {
        childElements.add(childElement);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), childElements);
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        UMLActivity otherActivity = (UMLActivity) obj;

        return otherActivity.childElements.size() == childElements.size() && otherActivity.childElements.containsAll(childElements)
                && childElements.containsAll(otherActivity.childElements);
    }
}
