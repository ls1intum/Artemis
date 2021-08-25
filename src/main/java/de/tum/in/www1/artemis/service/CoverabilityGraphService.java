package de.tum.in.www1.artemis.service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.math3.util.Pair;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.petrinet.PetriNet;
import de.tum.in.www1.artemis.service.compass.umlmodel.petrinet.PetriNetArc;
import de.tum.in.www1.artemis.service.compass.umlmodel.petrinet.PetriNetPlace;
import de.tum.in.www1.artemis.service.compass.umlmodel.petrinet.PetriNetTransition;

@Service
public class CoverabilityGraphService {

    private List<Double> initialMarking(PetriNet petriNet) {
        return petriNet.getPlaces().stream().map(place -> Double.parseDouble(place.getAmountOfTokens())).collect(Collectors.toList());
    }

    private List<List<Double>> incidenceMatrix(PetriNet petriNet) {
        List<PetriNetPlace> places = petriNet.getPlaces();
        List<PetriNetTransition> transitions = petriNet.getTransitions();
        List<PetriNetArc> arcs = petriNet.getArcs();

        Double[][] incidenceMatrix = new Double[transitions.size()][places.size()];

        Arrays.stream(incidenceMatrix).forEach(vector -> Arrays.fill(vector, 0d));

        for (PetriNetArc arc : arcs) {
            UMLElement source = arc.getSource();
            UMLElement target = arc.getTarget();

            PetriNetPlace place = null;
            PetriNetTransition transition = null;

            double multiplicity = Double.parseDouble(arc.getMultiplicity());

            if (source instanceof PetriNetPlace && target instanceof PetriNetTransition) {
                place = (PetriNetPlace) source;
                transition = (PetriNetTransition) target;
                multiplicity *= -1;
            }
            else if (source instanceof PetriNetTransition && target instanceof PetriNetPlace) {
                transition = (PetriNetTransition) source;
                place = (PetriNetPlace) target;
            }
            else {
                // invalid arc (either from place to place or transition to transition)
            }

            int transitionIndex = transitions.indexOf(transition);
            int placeIndex = places.indexOf(place);

            incidenceMatrix[transitionIndex][placeIndex] += multiplicity;
        }

        return Arrays.stream(incidenceMatrix).map(Arrays::asList).collect(Collectors.toList());
    }

    private List<Double> capacities(PetriNet petriNet) {
        return petriNet.getPlaces().stream().map(PetriNetPlace::getCapacity).map(capacity -> "Infinity".equals(capacity) ? Double.POSITIVE_INFINITY : Double.parseDouble(capacity))
                .collect(Collectors.toList());
    }

    private List<Pair<PetriNetTransition, List<Double>>> nextMarkings(PetriNet petriNet, List<List<Double>> incidenceMatrix, List<Double> capacities, List<Double> marking) {
        List<Pair<PetriNetTransition, List<Double>>> markings = new ArrayList<>();

        for (int i = 0; i < incidenceMatrix.size(); i++) {
            List<Double> delta = incidenceMatrix.get(i);
            List<Double> nextMarking = new ArrayList<>();

            for (int j = 0; j < marking.size(); j++) {
                nextMarking.add(delta.get(j) + marking.get(j));
            }

            if (isNonNegative(nextMarking) && isLessOrEqual(nextMarking, capacities)) {
                markings.add(new Pair<>(petriNet.getTransitions().get(i), nextMarking));
            }
        }

        return markings;
    }

    private boolean isNonNegative(List<Double> marking) {
        return marking.stream().mapToDouble(d -> d).noneMatch(d -> d < 0);
    }

    private boolean isLessOrEqual(List<Double> left, List<Double> right) {
        return IntStream.range(0, left.size()).allMatch(i -> left.get(i) <= right.get(i));
    }

    public CoverabilityGraph coverabilityGraph(PetriNet petriNet) {
        List<List<Double>> incidenceMatrix = incidenceMatrix(petriNet);
        List<Double> capacities = capacities(petriNet);
        List<Double> initialMarking = initialMarking(petriNet);
        List<Integer> initialMarkingInt = initialMarking.stream().map(Double::intValue).collect(Collectors.toList());

        Set<List<Integer>> vertices = new HashSet<>();
        Map<List<Integer>, Set<Target>> edges = new HashMap<>();
        CoverabilityGraph coverabilityGraph = new CoverabilityGraph(vertices, edges, initialMarkingInt);

        Stack<List<Double>> R = new Stack<>() {

            {
                push(initialMarking);
            }
        };
        Stack<List<Double>> W = new Stack<>();

        HashMap<List<Double>, List<Double>> ancestors = new HashMap<>();

        while (!R.isEmpty()) {
            List<Double> marking = R.pop();
            W.push(marking);

            for (Pair<PetriNetTransition, List<Double>> nextMarking : nextMarkings(petriNet, incidenceMatrix, capacities, marking)) {
                List<Double> runner = marking;

                while (runner != null && !isLessOrEqual(runner, nextMarking.getSecond())) {
                    runner = ancestors.get(runner);
                }

                if (runner != null) {
                    for (int i = 0; i < nextMarking.getSecond().size(); i++) {
                        if (nextMarking.getSecond().get(i) - runner.get(i) > 0 && capacities.get(i) == Double.POSITIVE_INFINITY) {
                            // If we were to compute the actual coverability graph with omega symbols we would use the
                            // line below. However, we only want to work with finite reachability graphs and hence if an
                            // omega symbol is found (here it is represented by Double.POSITIVE_INFINITY) we can stop
                            // the execution of the algorithm immediately, implying there is no finite reachability graph.
                            // nextMarking.getSecond().set(i, Double.POSITIVE_INFINITY);
                            return null;
                        }
                    }
                }

                System.out.println(marking + " ->" + nextMarking.getFirst() + "-> " + nextMarking.getSecond());

                List<Integer> source = marking.stream().map(Double::intValue).collect(Collectors.toList());
                List<Integer> target = nextMarking.getSecond().stream().map(Double::intValue).collect(Collectors.toList());

                vertices.add(source);
                vertices.add(target);

                edges.putIfAbsent(source, new HashSet<>());
                edges.get(source).add(new Target(target, nextMarking.getFirst()));

                if (!W.contains(nextMarking.getSecond()) && !R.contains(nextMarking.getSecond())) {
                    R.push(nextMarking.getSecond());
                    ancestors.put(nextMarking.getSecond(), marking);
                }
            }
        }

        return coverabilityGraph;
    }

    public static final class CoverabilityGraph {

        private final Set<List<Integer>> vertices;

        private final Map<List<Integer>, Set<Target>> edges;

        private final List<Integer> initialMarking;

        public CoverabilityGraph(Set<List<Integer>> vertices, Map<List<Integer>, Set<Target>> edges, List<Integer> initialMarking) {
            this.vertices = vertices;
            this.edges = edges;
            this.initialMarking = initialMarking;
        }

        public Set<List<Integer>> getVertices() {
            return vertices;
        }

        public Map<List<Integer>, Set<Target>> getEdges() {
            return edges;
        }

        public List<Integer> getInitialMarking() {
            return initialMarking;
        }
    }

    public static final class Target {

        private final List<Integer> vertex;

        private final PetriNetTransition edge;

        public Target(List<Integer> vertex, PetriNetTransition edge) {
            this.vertex = vertex;
            this.edge = edge;
        }

        public List<Integer> getVertex() {
            return vertex;
        }

        public PetriNetTransition getEdge() {
            return edge;
        }
    }
}
