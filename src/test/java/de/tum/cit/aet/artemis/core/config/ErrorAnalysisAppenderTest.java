package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import de.tum.cit.aet.artemis.core.domain.ErrorAnalysisSortField;
import de.tum.cit.aet.artemis.core.domain.ErrorAnalysisSpan;
import de.tum.cit.aet.artemis.core.domain.ErrorLocation;
import de.tum.cit.aet.artemis.core.domain.ErrorSeverity;
import de.tum.cit.aet.artemis.core.dto.ErrorGroupDTO;
import de.tum.cit.aet.artemis.core.service.ClientErrorReportService;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.core.service.erroranalysis.ErrorAnalysisService;

class ErrorAnalysisAppenderTest {

    private final LoggerContext context = new LoggerContext();

    private final ErrorAnalysisAppender appender = new ErrorAnalysisAppender();

    /** The analysis is shared by every test of the JVM, so each test marks its messages to find them again. */
    private String marker;

    @BeforeEach
    void setUp() {
        // letters only, as the analysis replaces digits by placeholders
        marker = "appendertest" + ThreadLocalRandom.current().ints(16, 'a', 'z' + 1).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append);
        appender.setContext(context);
        appender.start();
    }

    private void log(String loggerName, Level level, String message, Throwable throwable) {
        var event = new LoggingEvent("fqcn", context.getLogger(loggerName), level, message, throwable, null);
        appender.doAppend(event);
    }

    private ErrorGroupDTO groupOf(String text) {
        // a node answers with what it collected and has not handed to the cluster yet, which is all that this test produces
        return new ErrorAnalysisService(new LocalDataProviderService(), "10.6")
                .getGroups(ErrorAnalysisSpan.DAY, null, ErrorSeverity.WARN, ErrorAnalysisSortField.COUNT, false, 0, Integer.MAX_VALUE).groups().stream()
                .filter(group -> group.message().contains(marker + text)).findFirst().orElse(null);
    }

    @Test
    void countsAnErrorOfTheServerWithTheNameOfItsLoggerAndWithoutThePackage() {
        log("de.tum.cit.aet.artemis.exercise.service.ExerciseService", Level.ERROR, marker + "-error", null);

        assertThat(groupOf("-error")).satisfies(group -> {
            assertThat(group.location()).isEqualTo(ErrorLocation.SERVER);
            assertThat(group.severity()).isEqualTo(ErrorSeverity.ERROR);
            assertThat(group.message()).startsWith("ExerciseService: ");
        });
    }

    @Test
    void countsAWarningAsAWarning() {
        log("de.tum.cit.aet.artemis.exercise.service.ExerciseService", Level.WARN, marker + "-warning", null);

        assertThat(groupOf("-warning")).extracting(ErrorGroupDTO::severity).isEqualTo(ErrorSeverity.WARN);
    }

    @Test
    void addsTheTypeOfTheExceptionToTheMessage() {
        log("de.tum.cit.aet.artemis.Any", Level.ERROR, marker + "-exception", new IllegalStateException("details are not part of the group"));

        assertThat(groupOf("-exception")).extracting(ErrorGroupDTO::message).asString().endsWith("(java.lang.IllegalStateException)");
    }

    @Test
    void ignoresWhatIsLessSeriousThanAWarning() {
        log("de.tum.cit.aet.artemis.Any", Level.INFO, marker + "-info", null);
        log("de.tum.cit.aet.artemis.Any", Level.DEBUG, marker + "-debug", null);

        assertThat(groupOf("-info")).isNull();
        assertThat(groupOf("-debug")).isNull();
    }

    @Test
    void ignoresTheEntryThatWritesAReportOfAClientToTheLog() {
        log(ClientErrorReportService.class.getName(), Level.ERROR, marker + "-client-report", null);

        assertThat(groupOf("-client-report")).isNull();
    }
}
