package de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.activity;

import java.io.Serializable;
import java.util.Objects;

import com.google.common.base.CaseFormat;

import de.tum.cit.aet.artemis.modeling.service.compass.strategy.NameSimilarity;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.Similarity;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.UMLElement;

public class UMLActivityNode extends UMLActivityElement implements Serializable {

    public enum UMLActivityNodeType {
        ACTIVITY_INITIAL_NODE, ACTIVITY_FINAL_NODE, ACTIVITY_ACTION_NODE, ACTIVITY_OBJECT_NODE, ACTIVITY_FORK_NODE, ACTIVITY_JOIN_NODE, ACTIVITY_DECISION_NODE, ACTIVITY_MERGE_NODE
    }

    private final UMLActivityNodeType type;

    public UMLActivityNode(String name, String jsonElementID, UMLActivityNodeType type) {
        super(name, jsonElementID);

        this.type = type;
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        if (!(reference instanceof UMLActivityNode referenceNode)) {
            return 0;
        }

        if (!Objects.equals(getType(), referenceNode.getType())) {
            return 0;
        }

        // TODO: take the parent element into account

        return NameSimilarity.levenshteinSimilarity(getName(), referenceNode.getName());
    }

    @Override
    public String getType() {
        return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, type.name());
    }
}
