package de.tum.cit.aet.artemis.localci.service.scaparser.strategy.sarif;

import de.tum.cit.aet.artemis.localci.service.scaparser.format.sarif.ReportingDescriptor;

/**
 * A {@link RuleCategorizer} that uses the rule id as its own category.
 * <p>
 * No tool is parsed this way: every {@link RuleCategorizer} the parser is wired with in production derives a category
 * from the rule instead. This is the neutral categorizer {@link SarifParserTest} passes when the test is about the
 * parser rather than about categorization, which is why it lives in the test sources.
 */
class IdCategorizer implements RuleCategorizer {

    @Override
    public String categorizeRule(ReportingDescriptor rule) {
        return rule.id();
    }
}
