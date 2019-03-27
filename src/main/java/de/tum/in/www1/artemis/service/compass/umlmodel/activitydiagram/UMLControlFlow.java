package de.tum.in.www1.artemis.service.compass.umlmodel.activitydiagram;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration;


public class UMLControlFlow extends UMLElement {

    public static final String UML_CONTROL_FLOW_TYPE = "ActivityControlFlow";
    private final String CONTROL_FLOW_SYMBOL = " --> ";

    private UMLActivity source;
    private UMLActivity target;

    public UMLControlFlow(UMLActivity source, UMLActivity target, String jsonElementID) {
        this.source = source;
        this.target = target;

        this.setJsonElementID(jsonElementID);
    }

    /**
     * Compare this with another element to calculate the similarity
     *
     * @param element the element to compare with
     * @return the similarity as number [0-1]
     */
    @Override
    public double similarity(UMLElement element) {
        if (element.getClass() != UMLControlFlow.class) {
            return 0;
        }

        UMLControlFlow reference = (UMLControlFlow) element;

        double similarity = 0;
        double weight = 1;

        similarity += reference.source.similarity(source) * CompassConfiguration.RELATION_ELEMENT_WEIGHT;
        similarity += reference.target.similarity(target) * CompassConfiguration.RELATION_ELEMENT_WEIGHT;

        return similarity / weight;
    }

    @Override
    public String getName() {
        return "Control Flow " + getSource().getValue() + CONTROL_FLOW_SYMBOL + getTarget().getValue() + " (" + getType() + ")";
    }

    @Override
    public String getValue() {
        return getType();
    }

    @Override
    public String getType() {
        return UML_CONTROL_FLOW_TYPE;
    }

    public UMLActivity getSource() {
        return source;
    }

    public UMLActivity getTarget() {
        return target;
    }

}
