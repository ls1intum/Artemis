package de.tum.in.www1.artemis.service.compass.umlmodel.bpmn;

import java.io.Serializable;
import java.util.Objects;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.Similarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class BPMNTransaction extends UMLElement implements Serializable {

    public static final String BPMN_TRANSACTION_TYPE = "BPMNTransaction";

    private final String name;

    public BPMNTransaction(String name, String jsonElementID) {
        super(jsonElementID);

        this.name = name;
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        if (!(reference instanceof BPMNTransaction referenceNode)) {
            return 0;
        }

        if (!Objects.equals(getType(), referenceNode.getType())) {
            return 0;
        }

        return NameSimilarity.levenshteinSimilarity(getName(), referenceNode.getName());
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getType() {
        return BPMN_TRANSACTION_TYPE;
    }

    @Override
    public String toString() {
        return this.getName();
    }
}
