package de.tum.in.www1.artemis.service.compass.umlmodel.activitydiagram;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

import java.util.List;

public class UMLActivity extends UMLElement {

    public final static String UML_ACTIVITY_TYPE = "Activity";

    private String name;

    private List<UMLActivityNode> activityNodes;

    public UMLActivity(String name, List<UMLActivityNode> activityNodes, String jsonElementID) {
        this.name = name;
        this.activityNodes = activityNodes;
        this.setJsonElementID(jsonElementID);
    }

    @Override
    public double similarity(UMLElement element) {
        if (element.getClass() != UMLActivity.class) {
            return 0;
        }

        return NameSimilarity.levenshteinSimilarity(name, element.getName());
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return UML_ACTIVITY_TYPE;
    }

    @Override
    public String toString() {
        return "Activity " + getName();
    }

    public void addActivityNode(UMLActivityNode activityNode) {
        this.activityNodes.add(activityNode);
    }
}
