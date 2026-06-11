package de.tum.cit.aet.artemis.localci.service.scaparser.strategy.sarif;

import de.tum.cit.aet.artemis.localci.service.scaparser.format.sarif.ReportingDescriptor;

public interface RuleCategorizer {

    /**
     * Categorizes a SARIF rule using a tool specific strategy.
     *
     * @param rule The reporting descriptor containing the rule details
     * @return The identifier of the resulting category
     */
    String categorizeRule(ReportingDescriptor rule);
}
