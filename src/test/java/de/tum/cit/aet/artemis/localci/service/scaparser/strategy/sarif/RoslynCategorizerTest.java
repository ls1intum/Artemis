package de.tum.cit.aet.artemis.localci.service.scaparser.strategy.sarif;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.localci.service.scaparser.format.sarif.PropertyBag;
import de.tum.cit.aet.artemis.localci.service.scaparser.format.sarif.ReportingDescriptor;

class RoslynCategorizerTest {

    private final RoslynCategorizer categorizer = new RoslynCategorizer();

    private String categorize(String ruleId, Map<String, Object> properties) {
        var rule = new ReportingDescriptor(ruleId, null, null, null, null, null, null, null, null, null, null, null, properties == null ? null : new PropertyBag(properties));
        return categorizer.categorizeRule(rule);
    }

    @Test
    void shouldUseTheCategoryTheAnalyzerDeclares() {
        assertThat(categorize("CA1822", Map.of("category", "Performance"))).isEqualTo("Performance");
        assertThat(categorize("CS8600", Map.of("category", "Compiler"))).isEqualTo("Compiler");
    }

    @Test
    void shouldReportCompilerDiagnosticsWithoutCategoryAsCompiler() {
        assertThat(categorize("CS0168", null)).isEqualTo("Compiler");
        assertThat(categorize("CS0168", Map.of())).isEqualTo("Compiler");
    }

    @Test
    void shouldFallBackToOtherForAnUnknownCategory() {
        assertThat(categorize("XY0001", Map.of("category", "ThirdParty"))).isEqualTo(RoslynCategorizer.OTHER_CATEGORY);
        // a compiler-like id with an unknown declared category is not a compiler diagnostic
        assertThat(categorize("CS0168", Map.of("category", "ThirdParty"))).isEqualTo(RoslynCategorizer.OTHER_CATEGORY);
    }

    @Test
    void shouldFallBackToOtherForARuleWithoutCategory() {
        assertThat(categorize("XY0001", null)).isEqualTo(RoslynCategorizer.OTHER_CATEGORY);
    }
}
