package de.tum.cit.aet.artemis.programming.service.localci.scaparser.strategy.sarif;

class IdCategorizer implements RuleCategorizer {

    @Override
    public String categorizeRule(ReportingDescriptor rule) {
        return rule.getId();
    }
}
