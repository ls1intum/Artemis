package de.tum.cit.aet.artemis.programming.service.localci.scaparser.strategy.sarif;

import java.util.Map;

import de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif.PropertyBag;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif.ReportingDescriptor;

/**
 * Categorizes a rule by its type.
 * <p>
 * Can be TODO, HINT, COMPILE_TIME_ERROR, CHECKED_MODE_COMPILE_TIME_ERROR, STATIC_WARNING, SYNTACTIC_ERROR or LINT.
 */
public class DartAnalyzeCategorizer implements RuleCategorizer {

    @Override
    public String categorizeRule(ReportingDescriptor rule) {
        Map<String, Object> properties = rule.getOptionalProperties().map(PropertyBag::additionalProperties).orElseGet(Map::of);
        return properties.getOrDefault("type", "UNKNOWN").toString();
    }
}
