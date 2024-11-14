package de.tum.cit.aet.artemis.programming.service.localci.scaparser.strategy.sarif;

import java.util.Map;

import de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif.PropertyBag;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif.ReportingDescriptor;

public class RuffCategorizer implements RuleCategorizer {

    @Override
    public String categorizeRule(ReportingDescriptor rule) {
        Map<String, Object> properties = rule.getOptionalProperties().map(PropertyBag::additionalProperties).orElseGet(Map::of);
        return properties.getOrDefault("kind", "Unknown").toString();
    }
}
