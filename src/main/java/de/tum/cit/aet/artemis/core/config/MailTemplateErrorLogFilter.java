package de.tum.cit.aet.artemis.core.config;

import org.slf4j.Marker;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Suppresses Thymeleaf's duplicate mail-template failure log, whose message and cause can contain recipient data.
 * The mail services report these failures with safe operation details and exception types instead.
 */
public class MailTemplateErrorLogFilter extends TurboFilter {

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable throwable) {
        // Thymeleaf formats the outer template and current thread name into its message before calling the logger.
        // Match that prefix exactly so a non-mail failure whose exception mentions a mail template remains visible.
        if ("org.thymeleaf.TemplateEngine".equals(logger.getName()) && Level.ERROR.equals(level) && format != null
                && format.startsWith("[THYMELEAF][" + Thread.currentThread().getName() + "] Exception processing template \"mail/")) {
            return FilterReply.DENY;
        }
        return FilterReply.NEUTRAL;
    }
}
