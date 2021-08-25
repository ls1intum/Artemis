package de.tum.in.www1.artemis.service.compass.umlmodel.petrinet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class PetriNet extends UMLDiagram {

    private final List<PetriNetPlace> places;

    private final List<PetriNetTransition> transitions;

    private final List<PetriNetArc> arcs;

    public PetriNet(long modelSubmissionId, List<PetriNetPlace> places, List<PetriNetTransition> transitions, List<PetriNetArc> arcs) {
        super(modelSubmissionId);
        this.places = new ArrayList<>(places);
        this.transitions = new ArrayList<>(transitions);
        this.arcs = new ArrayList<>(arcs);

        this.places.sort(Comparator.comparing(PetriNetPlace::getName));
        this.transitions.sort(Comparator.comparing(PetriNetTransition::getName));
    }

    @Override
    public UMLElement getElementByJSONID(String jsonElementId) {
        for (PetriNetPlace place : getPlaces()) {
            if (place.getJSONElementID().equals(jsonElementId)) {
                return place;
            }
        }
        for (PetriNetTransition transition : getTransitions()) {
            if (transition.getJSONElementID().equals(jsonElementId)) {
                return transition;
            }
        }
        for (PetriNetArc link : getArcs()) {
            if (link.getJSONElementID().equals(jsonElementId)) {
                return link;
            }
        }
        return null;
    }

    public List<PetriNetPlace> getPlaces() {
        return places;
    }

    public List<PetriNetTransition> getTransitions() {
        return transitions;
    }

    public List<PetriNetArc> getArcs() {
        return arcs;
    }

    @Override
    protected List<UMLElement> getModelElements() {
        List<UMLElement> modelElements = new ArrayList<>();
        modelElements.addAll(places);
        modelElements.addAll(transitions);
        modelElements.addAll(arcs);
        return modelElements;
    }
}
