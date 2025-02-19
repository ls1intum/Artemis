package de.tum.cit.aet.artemis.programming.service.localci.scaparser.strategy.sarif;

import de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif.ReportingDescriptor;

public class SingleCategoryCategorizer implements RuleCategorizer {

    private final String category;

    public SingleCategoryCategorizer(String category) {
        this.category = category;
    }

    @Override
    public String categorizeRule(ReportingDescriptor rule) {
        return category;
    }
}
