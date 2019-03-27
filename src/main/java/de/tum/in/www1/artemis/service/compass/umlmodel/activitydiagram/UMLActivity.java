package de.tum.in.www1.artemis.service.compass.umlmodel.activitydiagram;

import com.google.common.base.CaseFormat;
import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

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

    private String name;
    private UMLActivityType type;

    public UMLActivity(String name, String jsonElementID, String type) {
        this.name = name;
        this.setJsonElementID(jsonElementID);
        this.type = UMLActivityType.valueOf(type);
    }

    @Override
    public double similarity(UMLElement element) {
        double similarity = 0;
        if (element.getClass() == UMLActivity.class) {
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
    public String getType() {
        return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, type.name());
    }
}
