package de.tum.in.www1.artemis.service.compass.umlmodel.reachabilitygraph;

import java.util.ArrayList;
import java.util.List;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class ReachabilityGraph extends UMLDiagram {

    private final List<ReachabilityGraphMarking> markings;

    private final List<ReachabilityGraphArc> arcs;

    public ReachabilityGraph(long modelSubmissionId, List<ReachabilityGraphMarking> markings, List<ReachabilityGraphArc> arcs) {
        super(modelSubmissionId);
        this.markings = markings;
        this.arcs = arcs;
    }

    @Override
    public UMLElement getElementByJSONID(String jsonElementId) {
        for (ReachabilityGraphMarking marking : markings) {
            if (marking.getJSONElementID().equals(jsonElementId)) {
                return marking;
            }
        }
        for (ReachabilityGraphArc arc : arcs) {
            if (arc.getJSONElementID().equals(jsonElementId)) {
                return arc;
            }
        }
        return null;
    }

    @Override
    protected List<UMLElement> getModelElements() {
        List<UMLElement> modelElements = new ArrayList<>();
        modelElements.addAll(markings);
        modelElements.addAll(arcs);
        return modelElements;
    }
}
