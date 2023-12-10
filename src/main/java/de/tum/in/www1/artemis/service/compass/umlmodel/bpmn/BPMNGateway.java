package de.tum.in.www1.artemis.service.compass.umlmodel.bpmn;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import com.google.common.base.CaseFormat;

import de.tum.in.www1.artemis.service.compass.strategy.NameSimilarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.Similarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class BPMNGateway extends UMLElement implements Serializable {

    public static final String BPMN_GATEWAY_TYPE = "BPMNGateway";

    private final String name;

    private final BPMNGatewayType gatewayType;

    public BPMNGateway(String name, String jsonElementID, BPMNGatewayType gatewayType) {
        super(jsonElementID);

        this.name = name;
        this.gatewayType = gatewayType;
    }

    @Override
    public double similarity(Similarity<UMLElement> reference) {
        if (!(reference instanceof BPMNGateway referenceNode)) {
            return 0;
        }

        if (!Objects.equals(getType(), referenceNode.getType())) {
            return 0;
        }

        double gatewayTypeSimilarityFactor = (this.gatewayType == ((BPMNGateway) reference).gatewayType) ? 1.0 : 0.5;

        return NameSimilarity.levenshteinSimilarity(getName(), referenceNode.getName()) * gatewayTypeSimilarityFactor;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getType() {
        return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, BPMN_GATEWAY_TYPE);
    }

    @Override
    public String toString() {
        return getName();
    }

    public BPMNGatewayType getGatewayType() {
        return gatewayType;
    }

    public enum BPMNGatewayType {

        COMPLEX("complex"), EVENT_BASED("event-based"), EXCLUSIVE("exclusive"), INCLUSIVE("inclusive"), PARALLEL("parallel");

        private final String value;

        BPMNGatewayType(String value) {
            this.value = value;
        }

        public static Optional<BPMNGatewayType> get(String value) {
            return Arrays.stream(BPMNGatewayType.values()).filter(element -> element.value.equals(value)).findFirst();
        }

        public String getValue() {
            return value;
        }
    }
}
