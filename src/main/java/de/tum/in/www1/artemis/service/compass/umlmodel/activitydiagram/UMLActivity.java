package de.tum.in.www1.artemis.service.compass.umlmodel.activitydiagram;

import java.util.List;

public class UMLActivity extends UMLActivityElement {

    public final static String UML_ACTIVITY_TYPE = "Activity";

    private List<UMLActivityElement> activityElements;

    public UMLActivity(String name, List<UMLActivityElement> activityElements, String jsonElementID) {
        super(name, jsonElementID);

        this.activityElements = activityElements;

        setActivityOfContainedElements();
    }

    /**
     * Sets the parent activity of all activity elements contained in this UML activity.
     */
    private void setActivityOfContainedElements() {
        for (UMLActivityElement activityElement : activityElements) {
            activityElement.setParentActivity(this);
        }
    }

    @Override
    public String getType() {
        return UML_ACTIVITY_TYPE;
    }

    /**
     * Add an activity element to the list of activity elements contained in this UML activity.
     *
     * @param activityElement the activity element that should be added
     */
    public void addActivityElement(UMLActivityElement activityElement) {
        activityElements.add(activityElement);
    }
}
