package de.tum.cit.aet.artemis.localci.service.scaparser.strategy.sarif;

import de.tum.cit.aet.artemis.localci.service.scaparser.format.sarif.ReportingDescriptor;

public class IdCategorizer implements RuleCategorizer {

    @Override
    public String categorizeRule(ReportingDescriptor rule) {
        return rule.id();
    }
}
