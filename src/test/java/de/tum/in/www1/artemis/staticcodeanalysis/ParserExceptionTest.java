package de.tum.in.www1.artemis.staticcodeanalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.service.connectors.localci.scaparser.ReportParser;
import de.tum.in.www1.artemis.service.connectors.localci.scaparser.exception.ParserException;

public class ParserExceptionTest {

    @Test
    void testParserExceptionThrown() {
        ReportParser parser = new ReportParser();
        ParserException parserException = catchThrowableOfType(() -> parser.transformToJSONReport(null), ParserException.class);
        assertThat(parserException).isNotNull();
    }
}
