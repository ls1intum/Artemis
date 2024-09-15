package de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.usecase;

import java.util.Objects;

import com.google.common.base.CaseFormat;

import de.tum.cit.aet.artemis.modeling.service.compass.strategy.NameSimilarity;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.Similarity;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.UMLElement;
import de.tum.cit.aet.artemis.modeling.service.compass.utils.CompassConfiguration;

public class UMLUseCaseAssociation extends UMLElement {

    // NOTE: this is also used in deployment diagrams
    public enum UMLUseCaseAssociationType {
        USE_CASE_ASSOCIATION, USE_CASE_GENERALIZATION, USE_CASE_INCLUDE, USE_CASE_EXTEND
    }

    private final String name;

    private final UMLElement source;

    private final UMLElement target;

    private final UMLUseCaseAssociationType type;

    public UMLUseCaseAssociation(String name, UMLElement source, UMLElement target, UMLUseCaseAssociationType type, String jsonElementID) {
        super(jsonElementID);
        this.name = name;
        this.source = source;
        this.target = target;
        this.type = type;
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        if (!(reference instanceof UMLUseCaseAssociation referenceAssociation)) {
            return 0;
        }

        double similarity = 0;

        double sourceWeight = CompassConfiguration.USE_CASE_ASSOCIATION_ELEMENT_WEIGHT;
        double targetWeight = CompassConfiguration.USE_CASE_ASSOCIATION_ELEMENT_WEIGHT;

        // only use case associations can have names where it would make sense to compare them
        if (type == UMLUseCaseAssociationType.USE_CASE_ASSOCIATION) {
            similarity += NameSimilarity.levenshteinSimilarity(getName(), referenceAssociation.getName()) * CompassConfiguration.USE_CASE_ASSOCIATION_NAME_WEIGHT;
        }
        else {
            // increase weight in case the name is not taken into account, so that we can still reach a max of 1.0
            sourceWeight += CompassConfiguration.USE_CASE_ASSOCIATION_NAME_WEIGHT / 2;
            targetWeight += CompassConfiguration.USE_CASE_ASSOCIATION_NAME_WEIGHT / 2;
        }

        similarity += referenceAssociation.getSource().similarity(source) * sourceWeight;
        similarity += referenceAssociation.getTarget().similarity(target) * targetWeight;

        if (referenceAssociation.type == this.type) {
            similarity += CompassConfiguration.RELATION_TYPE_WEIGHT;
        }

        return ensureSimilarityRange(similarity);
    }

    @Override
    public String toString() {
        return "UseCaseAssociation " + getSource().getName() + "->" + getTarget().getName() + " (" + getType() + ")";
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, type.name());
    }

    public UMLElement getSource() {
        return source;
    }

    public UMLElement getTarget() {
        return target;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), name, source, target, type);
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        UMLUseCaseAssociation otherRelationship = (UMLUseCaseAssociation) obj;

        return Objects.equals(otherRelationship.getSource(), source) && Objects.equals(otherRelationship.getTarget(), target) && Objects.equals(otherRelationship.name, name)
                && Objects.equals(otherRelationship.type, type);
    }
}
