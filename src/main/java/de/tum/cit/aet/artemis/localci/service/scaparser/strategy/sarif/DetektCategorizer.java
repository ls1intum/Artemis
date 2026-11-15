package de.tum.cit.aet.artemis.localci.service.scaparser.strategy.sarif;

import java.util.List;

import de.tum.cit.aet.artemis.localci.service.scaparser.format.sarif.ReportingDescriptor;

/**
 * Categorizes a detekt rule by its rule set.
 * <p>
 * detekt reports the rule set as part of the rule id, which has the form {@code detekt.<rule set>.<rule>}, e.g. {@code detekt.naming.MemberNameEqualsClassName}.
 *
 * @see <a href="https://detekt.dev/docs/rules/complexity">detekt rule sets</a>
 */
public class DetektCategorizer implements RuleCategorizer {

    private static final String RULE_ID_PREFIX = "detekt.";

    /**
     * The category assigned to a rule that does not belong to one of the known rule sets, e.g. a rule of a detekt plugin.
     */
    public static final String OTHER_CATEGORY = "other";

    public static final List<String> CATEGORY_NAMES = List.of("complexity", "coroutines", "empty-blocks", "exceptions", "naming", "performance", "potential-bugs", "style",
            OTHER_CATEGORY);

    @Override
    public String categorizeRule(ReportingDescriptor rule) {
        String ruleId = rule.id();
        if (!ruleId.startsWith(RULE_ID_PREFIX)) {
            return OTHER_CATEGORY;
        }
        int ruleSetEnd = ruleId.indexOf('.', RULE_ID_PREFIX.length());
        if (ruleSetEnd == -1) {
            return OTHER_CATEGORY;
        }
        String ruleSet = ruleId.substring(RULE_ID_PREFIX.length(), ruleSetEnd);
        return CATEGORY_NAMES.contains(ruleSet) ? ruleSet : OTHER_CATEGORY;
    }
}
