package de.tum.in.www1.artemis.service.compass.umlmodel.activitydiagram;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration;

public class UMLControlFlow extends UMLElement {

    public static final String UML_CONTROL_FLOW_TYPE = "ActivityControlFlow";

    private final String CONTROL_FLOW_SYMBOL = " --> ";

    private UMLActivityNode source; // TODO CZ: should UMLActivity be possible source and target as well?

    private UMLActivityNode target;

    public UMLControlFlow(UMLActivityNode source, UMLActivityNode target, String jsonElementID) {
        this.source = source;
        this.target = target;

        this.setJsonElementID(jsonElementID);
    }

    @Override
    public double similarity(UMLElement element) {
        if (element.getClass() != UMLControlFlow.class) {
            return 0;
        }

        UMLControlFlow reference = (UMLControlFlow) element;

        double similarity = 0;

        similarity += reference.source.similarity(source) * CompassConfiguration.RELATION_ELEMENT_WEIGHT;
        similarity += reference.target.similarity(target) * CompassConfiguration.RELATION_ELEMENT_WEIGHT;

        return similarity;
    }

    @Override
    public String toString() {
        return "Control Flow " + getSource().getName() + CONTROL_FLOW_SYMBOL + getTarget().getName() + " (" + getType() + ")";
    }

    @Override
    public String getName() {
        return getType();
    }

    @Override
    public String getType() {
        return UML_CONTROL_FLOW_TYPE;
    }

    public UMLActivityNode getSource() {
        return source;
    }

    public UMLActivityNode getTarget() {
        return target;
    }

}
