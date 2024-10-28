package de.tum.cit.aet.artemis.programming.service.localci.scaparser.strategy.sarif;

public interface RuleCategorizer {

    /**
     * Categorizes a SARIF rule using a tool specific strategy.
     *
     * @param rule The reporting descriptor containing the rule details
     * @return The identifier of the resulting category
     */
    String categorizeRule(ReportingDescriptor rule);
}
