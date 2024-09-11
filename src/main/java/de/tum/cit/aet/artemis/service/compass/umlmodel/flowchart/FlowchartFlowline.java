package de.tum.cit.aet.artemis.service.compass.umlmodel.flowchart;

import java.util.Objects;

import de.tum.cit.aet.artemis.service.compass.umlmodel.Similarity;
import de.tum.cit.aet.artemis.service.compass.umlmodel.UMLElement;

public class FlowchartFlowline extends UMLElement {

    public static final String FLOWCHART_FLOWLINE_TYPE = "FlowchartFlowline";

    private final UMLElement source;

    private final UMLElement target;

    public FlowchartFlowline(UMLElement source, UMLElement target, String jsonElementID) {
        super(jsonElementID);

        this.source = source;
        this.target = target;
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        if (!(reference instanceof FlowchartFlowline referenceFlowline)) {
            return 0;
        }

        double similarity = 0;

        double sourceWeight = 0.5;
        double targetWeight = 0.5;

        similarity += referenceFlowline.getSource().similarity(source) * sourceWeight;
        similarity += referenceFlowline.getTarget().similarity(target) * targetWeight;

        double similarityReverse = 0;

        similarityReverse += referenceFlowline.getSource().similarity(target) * sourceWeight;
        similarityReverse += referenceFlowline.getTarget().similarity(source) * targetWeight;

        similarity = Math.max(similarity, similarityReverse);

        return ensureSimilarityRange(similarity);
    }

    @Override
    public String toString() {
        return "FlowchartFlowline " + getSource().getName() + " -> " + getTarget().getName();
    }

    @Override
    public String getName() {
        return getType();
    }

    @Override
    public String getType() {
        return FLOWCHART_FLOWLINE_TYPE;
    }

    public UMLElement getSource() {
        return source;
    }

    public UMLElement getTarget() {
        return target;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), source, target);
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        FlowchartFlowline otherRelationship = (FlowchartFlowline) obj;

        // this is bidirectional, so we need to take the other way round into account as well
        return Objects.equals(otherRelationship.getSource(), source) && Objects.equals(otherRelationship.getTarget(), target)
                || Objects.equals(otherRelationship.getSource(), target) && Objects.equals(otherRelationship.getTarget(), source);
    }
}
