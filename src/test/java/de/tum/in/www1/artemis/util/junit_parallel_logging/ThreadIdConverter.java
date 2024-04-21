package de.tum.in.www1.artemis.util.junit_parallel_logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * A custom Logback converter that can be used to display the thread id in the logs.
 * <p>
 * This converter is used to distinguish logs from different threads when running tests in parallel.
 */
public class ThreadIdConverter extends ClassicConverter {

    @Override
    public String convert(ILoggingEvent iLoggingEvent) {
        return String.valueOf(Thread.currentThread().threadId());
    }
}
