package de.tum.cit.aet.artemis.localci.service.scaparser.strategy.sarif;

import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.localci.service.scaparser.format.sarif.PropertyBag;
import de.tum.cit.aet.artemis.localci.service.scaparser.format.sarif.ReportingDescriptor;

/**
 * Categorizes a rule of the .NET compiler platform (Roslyn) by the category the analyzer declares for it.
 * <p>
 * The compiler diagnostics ({@code CS0000}) are reported in the category "Compiler".
 *
 * @see <a href="https://learn.microsoft.com/en-us/dotnet/fundamentals/code-analysis/categories">Code analysis rule categories</a>
 */
public class RoslynCategorizer implements RuleCategorizer {

    private static final String COMPILER_CATEGORY = "Compiler";

    /**
     * The category assigned to a rule whose category is not one of the known ones, e.g. a rule of a third-party analyzer.
     */
    public static final String OTHER_CATEGORY = "Other";

    public static final List<String> CATEGORY_NAMES = List.of(COMPILER_CATEGORY, "Design", "Documentation", "Globalization", "Interoperability", "Maintainability", "Naming",
            "Performance", "Reliability", "Security", "Style", "Usage", OTHER_CATEGORY);

    @Override
    public String categorizeRule(ReportingDescriptor rule) {
        Map<String, Object> properties = rule.getOptionalProperties().map(PropertyBag::additionalProperties).orElseGet(Map::of);
        Object category = properties.get("category");
        if (category != null && CATEGORY_NAMES.contains(category.toString())) {
            return category.toString();
        }
        if (category == null && rule.id().startsWith("CS")) {
            return COMPILER_CATEGORY;
        }
        return OTHER_CATEGORY;
    }
}
