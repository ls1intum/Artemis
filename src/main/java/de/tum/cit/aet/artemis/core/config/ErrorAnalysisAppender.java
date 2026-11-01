package de.tum.cit.aet.artemis.core.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import de.tum.cit.aet.artemis.core.domain.ErrorLocation;
import de.tum.cit.aet.artemis.core.domain.ErrorSeverity;
import de.tum.cit.aet.artemis.core.service.ClientErrorReportService;
import de.tum.cit.aet.artemis.core.service.erroranalysis.ErrorAnalysisBuffer;

/**
 * A Logback appender that hands every warning and error that the server logs to the error analysis. It only adds to a buffer in memory, and the error analysis writes the buffer to
 * the cluster in batches, so logging never waits for the cluster.
 * <p>
 * Logback creates the appender from {@code logback-spring.xml}, before and outside of Spring, which is why it reaches the
 * analysis through {@link ErrorAnalysisBuffer#shared()}. It never fails, so a defect of the analysis cannot stop the server from
 * logging. The entry that writes a report of a client to the log is skipped, because the report is not an error of the server
 * and the analysis counts it as an error of the client.
 */
public class ErrorAnalysisAppender extends AppenderBase<ILoggingEvent> {

    private static final String CLIENT_REPORT_LOGGER = ClientErrorReportService.class.getName();

    @Override
    protected void append(ILoggingEvent event) {
        if (!event.getLevel().isGreaterOrEqual(Level.WARN) || CLIENT_REPORT_LOGGER.equals(event.getLoggerName())) {
            return;
        }
        try {
            ErrorSeverity severity = event.getLevel().isGreaterOrEqual(Level.ERROR) ? ErrorSeverity.ERROR : ErrorSeverity.WARN;
            ErrorAnalysisBuffer.shared().record(ErrorLocation.SERVER, severity, describe(event));
        }
        catch (RuntimeException exception) {
            // nothing to do: the analysis must not make logging fail, and an error here could not be logged without recursion
        }
    }

    /**
     * The logger without its package, the message and the type of the exception, if there is one.
     */
    private static String describe(ILoggingEvent event) {
        String logger = event.getLoggerName();
        String message = logger.substring(logger.lastIndexOf('.') + 1) + ": " + event.getFormattedMessage();
        IThrowableProxy throwable = event.getThrowableProxy();
        return throwable == null ? message : message + " (" + throwable.getClassName() + ")";
    }
}
