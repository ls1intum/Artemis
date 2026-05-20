package de.tum.cit.aet.artemis.programming.service.localci.scaparser.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisTool;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisReportDTO;

class GccParserTest {

    private final GccParser parser = new GccParser();

    @Test
    void testParseWarnings() {
        String report = """
                <?xml version="1.0" encoding="UTF-8" ?>
                <root>
                assignment/helloWorld.c:10:5: warning: unused variable &apos;x&apos; [-Wunused-variable]
                assignment/helloWorld.c:20:1: warning: implicit declaration of function &apos;foo&apos; [-Wimplicit-function-declaration]
                </root>
                """;

        StaticCodeAnalysisReportDTO result = parser.parse(report);

        assertThat(result.tool()).isEqualTo(StaticCodeAnalysisTool.GCC);
        assertThat(result.issues()).hasSize(2);

        var issue1 = result.issues().get(0);
        assertThat(issue1.filePath()).isEqualTo("assignment/helloWorld.c");
        assertThat(issue1.startLine()).isEqualTo(10);
        assertThat(issue1.startColumn()).isEqualTo(5);
        assertThat(issue1.message()).isEqualTo("unused variable 'x'");
        assertThat(issue1.rule()).isEqualTo("-Wunused-variable");
        assertThat(issue1.category()).isEqualTo("BadPractice");
        assertThat(issue1.priority()).isEqualTo("warning");

        var issue2 = result.issues().get(1);
        assertThat(issue2.filePath()).isEqualTo("assignment/helloWorld.c");
        assertThat(issue2.startLine()).isEqualTo(20);
        assertThat(issue2.rule()).isEqualTo("-Wimplicit-function-declaration");
        assertThat(issue2.category()).isEqualTo("UndefinedBehavior");
    }

    @Test
    void testParseEmpty() {
        String report = """
                <?xml version="1.0" encoding="UTF-8" ?>
                <root>
                </root>
                """;

        StaticCodeAnalysisReportDTO result = parser.parse(report);

        assertThat(result.tool()).isEqualTo(StaticCodeAnalysisTool.GCC);
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void testParseErrors() {
        String report = """
                <?xml version="1.0" encoding="UTF-8" ?>
                <root>
                assignment/main.c:5:10: error: expected &apos;;&apos; before &apos;}&apos; token
                </root>
                """;

        StaticCodeAnalysisReportDTO result = parser.parse(report);

        assertThat(result.issues()).hasSize(1);
        var issue = result.issues().getFirst();
        assertThat(issue.priority()).isEqualTo("error");
        assertThat(issue.rule()).isEqualTo("compiler-error");
        assertThat(issue.category()).isEqualTo("Misc");
    }
}
