package de.tum.cit.aet.artemis.programming.service.localci.scaparser.strategy.sarif;

public class RuffCategorizer implements RuleCategorizer {

    @Override
    public String categorizeRule(ReportingDescriptor rule) {
        return rule.getProperties().getAdditionalProperties().getOrDefault("kind", "Unknown").toString();
    }
}
