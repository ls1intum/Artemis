package de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.object;

import java.util.Objects;

import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.Similarity;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.UMLElement;

public class UMLObjectLink extends UMLElement {

    public static final String UML_OBJECT_LINK_TYPE = "ObjectLink";

    private final UMLObject source;

    private final UMLObject target;

    public UMLObjectLink(UMLObject source, UMLObject target, String jsonElementID) {
        super(jsonElementID);

        this.source = source;
        this.target = target;
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        if (!(reference instanceof UMLObjectLink referenceLink)) {
            return 0;
        }

        double similarity = 0;

        double sourceWeight = 0.5;
        double targetWeight = 0.5;

        similarity += referenceLink.getSource().similarity(source) * sourceWeight;
        similarity += referenceLink.getTarget().similarity(target) * targetWeight;

        double similarityReverse = 0;

        similarityReverse += referenceLink.getSource().similarity(target) * sourceWeight;
        similarityReverse += referenceLink.getTarget().similarity(source) * targetWeight;

        similarity = Math.max(similarity, similarityReverse);

        return ensureSimilarityRange(similarity);
    }

    @Override
    public String toString() {
        return "ObjectLink " + getSource().getName() + " -> " + getTarget().getName();
    }

    @Override
    public String getName() {
        return getType();
    }

    @Override
    public String getType() {
        return UML_OBJECT_LINK_TYPE;
    }

    public UMLObject getSource() {
        return source;
    }

    public UMLObject getTarget() {
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

        UMLObjectLink otherRelationship = (UMLObjectLink) obj;

        // this is bidirectional, so we need to take the other way round into account as well
        return Objects.equals(otherRelationship.getSource(), source) && Objects.equals(otherRelationship.getTarget(), target)
                || Objects.equals(otherRelationship.getSource(), target) && Objects.equals(otherRelationship.getTarget(), source);
    }
}
