package de.tum.cit.aet.artemis.localci.service.scaparser.strategy.sarif;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.localci.service.scaparser.format.sarif.ReportingDescriptor;

class DetektCategorizerTest {

    private final DetektCategorizer categorizer = new DetektCategorizer();

    private String categorize(String ruleId) {
        return categorizer.categorizeRule(new ReportingDescriptor(ruleId));
    }

    @Test
    void shouldUseTheRuleSetOfTheRuleId() {
        assertThat(categorize("detekt.naming.MemberNameEqualsClassName")).isEqualTo("naming");
        assertThat(categorize("detekt.empty-blocks.EmptyFunctionBlock")).isEqualTo("empty-blocks");
        assertThat(categorize("detekt.potential-bugs.UnsafeCallOnNullableType")).isEqualTo("potential-bugs");
    }

    @Test
    void shouldFallBackToOtherForAnUnknownRuleSet() {
        assertThat(categorize("detekt.formatting.Indentation")).isEqualTo(DetektCategorizer.OTHER_CATEGORY);
    }

    @Test
    void shouldFallBackToOtherForARuleIdWithoutRuleSet() {
        assertThat(categorize("detekt.Indentation")).isEqualTo(DetektCategorizer.OTHER_CATEGORY);
    }

    @Test
    void shouldFallBackToOtherForARuleOfAnotherTool() {
        assertThat(categorize("MyPluginRule")).isEqualTo(DetektCategorizer.OTHER_CATEGORY);
    }
}
