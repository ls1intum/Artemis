package de.tum.cit.aet.artemis.programming.service.localci.scaparser.strategy.sarif;

import de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif.ReportingDescriptor;

/**
 * Categorizes a rule by its associated Rubocop Department.
 * <p>
 * Rule IDs are structured like {@code Department/CopName}.
 */
public class RubocopCategorizer implements RuleCategorizer {

    @Override
    public String categorizeRule(ReportingDescriptor rule) {
        return rule.id().substring(0, rule.id().indexOf('/'));
    }
}
