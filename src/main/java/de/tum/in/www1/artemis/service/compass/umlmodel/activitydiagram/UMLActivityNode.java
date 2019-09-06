package de.tum.in.www1.artemis.service.compass.umlmodel.activitydiagram;

import com.google.common.base.CaseFormat;
import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class UMLActivityNode extends UMLElement {

    public enum UMLActivityNodeType {
        ACTIVITY_INITIAL_NODE, ACTIVITY_FINAL_NODE, ACTIVITY_ACTION_NODE, ACTIVITY_OBJECT_NODE, ACTIVITY_FORK_NODE, ACTIVITY_JOIN_NODE, ACTIVITY_DECISION_NODE, ACTIVITY_MERGE_NODE;

        public static List<String> getTypesAsList() {
            return Arrays.stream(UMLActivityNode.UMLActivityNodeType.values()).map(umlClassType -> CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, umlClassType.name()))
                    .collect(Collectors.toList());
        }
    }

    private String name;

    private UMLActivityNodeType type;

    @Nullable
    private UMLActivity parentActivity;

    public UMLActivityNode(String name, String jsonElementID, String type) {
        this.name = name;
        this.setJsonElementID(jsonElementID);
        this.type = UMLActivityNodeType.valueOf(type);
    }

    @Override
    public double similarity(UMLElement element) {
        if (element.getClass() != UMLActivityNode.class) {
            return 0;
        }

        return NameSimilarity.levenshteinSimilarity(name, element.getName());
    }

    @Override
    public String toString() {
        return getType() + " " + getName();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, type.name());
    }

    @Nullable
    public UMLActivity getParentActivity() {
        return parentActivity;
    }

    public void setParentActivity(@Nullable UMLActivity parentActivity) {
        this.parentActivity = parentActivity;
    }
}
