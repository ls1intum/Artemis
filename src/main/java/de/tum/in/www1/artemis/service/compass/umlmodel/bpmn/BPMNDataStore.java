package de.tum.in.www1.artemis.service.compass.umlmodel.bpmn;

import java.io.Serializable;
import java.util.Objects;

import com.google.common.base.CaseFormat;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.Similarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class BPMNDataStore extends UMLElement implements Serializable {

    public static final String BPMN_DATA_STORE_TYPE = "BPMNDataStore";

    private final String name;

    public BPMNDataStore(String name, String jsonElementID) {
        super(jsonElementID);

        this.name = name;
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        if (!(reference instanceof BPMNDataStore referenceNode)) {
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
        return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, BPMN_DATA_STORE_TYPE);
    }

    @Override
    public String toString() {
        return getName();
    }
}
