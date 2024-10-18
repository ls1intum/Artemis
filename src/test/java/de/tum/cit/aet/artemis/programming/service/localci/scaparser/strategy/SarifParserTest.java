package de.tum.cit.aet.artemis.programming.service.localci.scaparser.strategy;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.google.common.io.Resources;

import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisReportDTO;

class SarifParserTest {

    SarifParser sarifParser = new SarifParser();

    @Test
    void testParse() throws IOException {
        URL resource = SarifParserTest.class.getResource("/test-data/static-code-analysis/ruff.sarif");
        assert resource != null;
        String reportContents = Resources.toString(resource, StandardCharsets.UTF_8);

        StaticCodeAnalysisReportDTO parsed = sarifParser.parse(reportContents);

        System.out.println(parsed);
    }
}
