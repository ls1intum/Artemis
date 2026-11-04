package de.tum.cit.aet.artemis.localci.service.scaparser.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.config.StaticCodeAnalysisConfigurer;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisTool;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisReportDTO;

class SwiftLintParserTest {

    private static final String CUSTOM_RULE_REPORT = """
            <?xml version="1.0" encoding="utf-8"?>
            <checkstyle version="4.3">
                <file name="/var/tmp/testing-dir/assignment/Sources/Lib/Client.swift">
                    <error line="2" column="5" severity="warning" message="Custom message" source="swiftlint.rules.my_custom_rule"/>
                    <error line="3" column="1" severity="error" message="No source"/>
                </file>
                <file name="/var/tmp/testing-dir/assignment/Sources/Lib/Empty.swift"></file>
            </checkstyle>
            """;

    private final SwiftLintParser parser = new SwiftLintParser();

    @Test
    void shouldAssignTheKindOfTheRuleAsCategory() {
        assertThat(SwiftLintRuleKinds.kindOf("trailing_whitespace")).isEqualTo(SwiftLintRuleKinds.KIND_STYLE);
        assertThat(SwiftLintRuleKinds.kindOf("force_try")).isEqualTo(SwiftLintRuleKinds.KIND_IDIOMATIC);
        assertThat(SwiftLintRuleKinds.kindOf("mark")).isEqualTo(SwiftLintRuleKinds.KIND_LINT);
        assertThat(SwiftLintRuleKinds.kindOf("cyclomatic_complexity")).isEqualTo(SwiftLintRuleKinds.KIND_METRICS);
        assertThat(SwiftLintRuleKinds.kindOf("empty_count")).isEqualTo(SwiftLintRuleKinds.KIND_PERFORMANCE);
    }

    @Test
    void shouldAssignMiscellaneousToUnknownRules() {
        assertThat(SwiftLintRuleKinds.kindOf("my_custom_rule")).isEqualTo(SwiftLintRuleKinds.KIND_MISCELLANEOUS);

        StaticCodeAnalysisReportDTO report = parser.parse(CUSTOM_RULE_REPORT);

        assertThat(report.tool()).isEqualTo(StaticCodeAnalysisTool.SWIFTLINT);
        assertThat(report.issues()).hasSize(2);
        assertThat(report.issues().getFirst().rule()).isEqualTo("my_custom_rule");
        assertThat(report.issues().getFirst().category()).isEqualTo(SwiftLintRuleKinds.KIND_MISCELLANEOUS);
        assertThat(report.issues().get(1).rule()).isEqualTo("Unknown");
        assertThat(report.issues().get(1).category()).isEqualTo(SwiftLintRuleKinds.KIND_MISCELLANEOUS);
    }

    @Test
    void shouldParseAReportWithoutViolations() {
        StaticCodeAnalysisReportDTO report = parser.parse("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<checkstyle version=\"4.3\">\n</checkstyle>\n");

        assertThat(report.tool()).isEqualTo(StaticCodeAnalysisTool.SWIFTLINT);
        assertThat(report.issues()).isEmpty();
    }

    @Test
    void shouldRejectInvalidXml() {
        assertThatThrownBy(() -> parser.parse("<checkstyle><file")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldSelectTheParserByTheReportFileName() {
        assertThat(new ParserPolicy().configure("swiftlint-result.xml")).isInstanceOf(SwiftLintParser.class);
    }

    @Test
    void shouldMapEveryKindToADefaultCategoryOfSwift() {
        Set<String> mappedToolCategories = StaticCodeAnalysisConfigurer.staticCodeAnalysisConfiguration().get(ProgrammingLanguage.SWIFT).stream()
                .flatMap(category -> category.categoryMappings().stream()).filter(mapping -> mapping.tool() == StaticCodeAnalysisTool.SWIFTLINT).map(mapping -> mapping.category())
                .collect(Collectors.toSet());

        assertThat(mappedToolCategories).contains(SwiftLintRuleKinds.KIND_STYLE, SwiftLintRuleKinds.KIND_IDIOMATIC, SwiftLintRuleKinds.KIND_LINT, SwiftLintRuleKinds.KIND_METRICS,
                SwiftLintRuleKinds.KIND_PERFORMANCE, SwiftLintRuleKinds.KIND_MISCELLANEOUS);
    }
}
