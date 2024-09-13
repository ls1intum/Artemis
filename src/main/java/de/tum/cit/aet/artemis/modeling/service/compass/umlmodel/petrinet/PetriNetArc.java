package de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.petrinet;

import java.util.Objects;

import de.tum.cit.aet.artemis.modeling.service.compass.strategy.NameSimilarity;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.Similarity;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.UMLElement;

public class PetriNetArc extends UMLElement {

    public static final String PETRI_NET_ARC_TYPE = "PetriNetArc";

    private final UMLElement source;

    private final UMLElement target;

    private final String multiplicity;

    public PetriNetArc(String multiplicity, UMLElement source, UMLElement target, String jsonElementID) {
        super(jsonElementID);
        this.multiplicity = multiplicity;
        this.source = source;
        this.target = target;
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        if (!(reference instanceof PetriNetArc referenceArc)) {
            return 0;
        }

        double similarity = 0;

        double sourceWeight = 0.25;
        double targetWeight = 0.25;
        double multiplicityWeight = 0.5;

        similarity += referenceArc.getSource().similarity(source) * sourceWeight;
        similarity += referenceArc.getTarget().similarity(target) * targetWeight;

        similarity += NameSimilarity.levenshteinSimilarity(multiplicity, referenceArc.multiplicity) * multiplicityWeight;

        return ensureSimilarityRange(similarity);
    }

    @Override
    public String toString() {
        return "PetriNetArc " + getSource().getName() + " -> " + getTarget().getName() + "(" + multiplicity + ")";
    }

    @Override
    public String getName() {
        return getType();
    }

    @Override
    public String getType() {
        return PETRI_NET_ARC_TYPE;
    }

    public UMLElement getSource() {
        return source;
    }

    public UMLElement getTarget() {
        return target;
    }

    public String getMultiplicity() {
        return multiplicity;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), source, target, multiplicity);
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }
        PetriNetArc otherRelationship = (PetriNetArc) obj;
        return Objects.equals(otherRelationship.getSource(), source) && Objects.equals(otherRelationship.getTarget(), target);
    }
}
