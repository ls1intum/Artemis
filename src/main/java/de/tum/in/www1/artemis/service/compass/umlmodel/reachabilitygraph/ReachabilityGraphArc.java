package de.tum.in.www1.artemis.service.compass.umlmodel.reachabilitygraph;

import java.util.Objects;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.Similarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class ReachabilityGraphArc extends UMLElement {

    public static final String REACHABILITY_GRAPH_ARC_TYPE = "ReachabilityGraphArc";

    private final UMLElement source;

    private final UMLElement target;

    private final String transition;

    public ReachabilityGraphArc(String transition, UMLElement source, UMLElement target, String jsonElementID) {
        super(jsonElementID);
        this.transition = transition;
        this.source = source;
        this.target = target;
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        if (!(reference instanceof ReachabilityGraphArc referenceArc)) {
            return 0;
        }

        double similarity = 0;

        double sourceWeight = 0.25;
        double targetWeight = 0.25;
        double transitionWeight = 0.5;

        similarity += referenceArc.getSource().similarity(source) * sourceWeight;
        similarity += referenceArc.getTarget().similarity(target) * targetWeight;

        similarity += NameSimilarity.levenshteinSimilarity(transition, referenceArc.transition) * transitionWeight;

        return ensureSimilarityRange(similarity);
    }

    @Override
    public String toString() {
        return "ReachabilityGraphArc " + getSource().getName() + " -> " + transition + " -> " + getTarget().getName();
    }

    @Override
    public String getName() {
        return getType();
    }

    @Override
    public String getType() {
        return REACHABILITY_GRAPH_ARC_TYPE;
    }

    public UMLElement getSource() {
        return source;
    }

    public UMLElement getTarget() {
        return target;
    }

    public String getTransition() {
        return transition;
    }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o)) {
            return false;
        }
        ReachabilityGraphArc arc = (ReachabilityGraphArc) o;
        return Objects.equals(arc.getSource(), source) && Objects.equals(arc.getTarget(), target);
    }
}
