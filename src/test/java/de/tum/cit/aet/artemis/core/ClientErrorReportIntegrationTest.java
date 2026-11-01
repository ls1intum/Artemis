package de.tum.cit.aet.artemis.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.tum.cit.aet.artemis.core.dto.ClientErrorReportDTO;
import de.tum.cit.aet.artemis.core.service.ClientErrorReportService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class ClientErrorReportIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String ENDPOINT = "/api/core/public/client-errors";

    private Logger logger;

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void captureLog() {
        logger = (Logger) LoggerFactory.getLogger(ClientErrorReportService.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void releaseLog() {
        logger.detachAppender(appender);
        appender.stop();
    }

    private static Map<String, Object> validReport() {
        Map<String, Object> report = new HashMap<>();
        report.put("severity", "ERROR");
        report.put("message", "Unknown question type: foo");
        report.put("stack", "Error: Unknown question type: foo\n    at a (main.js:1:1)");
        report.put("url", "/courses/1/exams/2?token=secret");
        report.put("clientVersion", "9.8.0");
        report.put("extra", Map.of("exerciseId", "42"));
        report.put("tags", Map.of("feature", "quiz"));
        return report;
    }

    @Test
    void acceptsAReportWithoutAuthenticationAndLogsIt() throws Exception {
        request.postWithoutResponseBody(ENDPOINT, validReport(), HttpStatus.OK);

        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).contains("version=9.8.0", "url=/courses/1/exams/2,", "authenticated=false", "tags={feature=quiz}", "extra={exerciseId=42}",
                    "]: Unknown question type: foo\n\tError: Unknown question type: foo\n\t    at a (main.js:1:1)").doesNotContain("token=secret");
        });
    }

    @Test
    @WithMockUser(username = "clienterrorstudent1", roles = "USER")
    void marksAReportOfAnAuthenticatedUserWithoutLoggingWho() throws Exception {
        request.postWithoutResponseBody(ENDPOINT, validReport(), HttpStatus.OK);

        assertThat(appender.list).singleElement().satisfies(event -> assertThat(event.getFormattedMessage()).contains("authenticated=true").doesNotContain("clienterrorstudent1"));
    }

    @ParameterizedTest
    @MethodSource("severities")
    void logsAtTheLevelOfTheSeverity(String severity, Level expectedLevel) throws Exception {
        Map<String, Object> report = validReport();
        report.put("severity", severity);

        request.postWithoutResponseBody(ENDPOINT, report, HttpStatus.OK);

        assertThat(appender.list).singleElement().satisfies(event -> assertThat(event.getLevel()).isEqualTo(expectedLevel));
    }

    private static Stream<Arguments> severities() {
        return Stream.of(Arguments.of("INFO", Level.INFO), Arguments.of("WARN", Level.WARN), Arguments.of("ERROR", Level.ERROR), Arguments.of("CRITICAL", Level.ERROR));
    }

    @ParameterizedTest
    @MethodSource("invalidReports")
    void rejectsAnInvalidReportAndLogsNothing(String description, Map<String, Object> report) throws Exception {
        request.postWithoutResponseBody(ENDPOINT, report, HttpStatus.BAD_REQUEST);

        assertThat(appender.list).as(description).isEmpty();
    }

    private static Stream<Arguments> invalidReports() {
        return Stream.of(Arguments.of("no severity", without("severity")), Arguments.of("unknown severity", with("severity", "FATAL")),
                Arguments.of("no message", without("message")), Arguments.of("blank message", with("message", "  ")),
                Arguments.of("message too long", with("message", StringUtils.repeat('a', ClientErrorReportDTO.MAX_MESSAGE_LENGTH + 1))),
                Arguments.of("stack too long", with("stack", StringUtils.repeat('a', ClientErrorReportDTO.MAX_STACK_LENGTH + 1))),
                Arguments.of("url too long", with("url", StringUtils.repeat('a', ClientErrorReportDTO.MAX_URL_LENGTH + 1))),
                Arguments.of("version too long", with("clientVersion", StringUtils.repeat('a', ClientErrorReportDTO.MAX_VERSION_LENGTH + 1))),
                Arguments.of("too many extra entries", with("extra", entries(ClientErrorReportDTO.MAX_ENTRIES + 1, "v"))),
                Arguments.of("too many tags", with("tags", entries(ClientErrorReportDTO.MAX_ENTRIES + 1, "v"))),
                Arguments.of("extra value too long", with("extra", Map.of("k", StringUtils.repeat('a', ClientErrorReportDTO.MAX_VALUE_LENGTH + 1)))),
                Arguments.of("tag key too long", with("tags", Map.of(StringUtils.repeat('a', ClientErrorReportDTO.MAX_KEY_LENGTH + 1), "v"))));
    }

    private static Map<String, Object> with(String field, Object value) {
        Map<String, Object> report = validReport();
        report.put(field, value);
        return report;
    }

    private static Map<String, Object> without(String field) {
        Map<String, Object> report = validReport();
        report.remove(field);
        return report;
    }

    private static Map<String, String> entries(int count, String value) {
        Map<String, String> entries = new HashMap<>();
        IntStream.range(0, count).forEach(index -> entries.put("key" + index, value));
        return entries;
    }
}
