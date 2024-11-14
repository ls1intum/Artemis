package de.tum.cit.aet.artemis.programming.service.localci.scaparser.strategy.sarif;

import de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif.ReportingDescriptor;

class IdCategorizer implements RuleCategorizer {

    @Override
    public String categorizeRule(ReportingDescriptor rule) {
        return rule.id();
    }
}
