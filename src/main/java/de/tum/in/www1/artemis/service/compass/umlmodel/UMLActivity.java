package de.tum.in.www1.artemis.service.compass.umlmodel;

public class UMLActivity extends UMLElement {

    public enum UMLActivityType {
        ACTIVITY_CONTROL_INITIAL_NODE,
        ACTIVITY_CONTROL_FINAL_NODE,
        ACTIVITY_ACTION_NODE,
        ACTIVITY_OBJECT,
        ACTIVITY_MERGE_NODE,
        ACTIVITY_FORK_NODE,
        ACTIVITY_FORK_NODE_HORIZONTAL
    }

    @Override
    public double similarity(UMLElement element) {
        //TODO implement
        return 0;
    }

    @Override
    public String getName() {
        //TODO implement
        return null;
    }

    @Override
    public String getValue() {
        //TODO implement
        return null;
    }
}
