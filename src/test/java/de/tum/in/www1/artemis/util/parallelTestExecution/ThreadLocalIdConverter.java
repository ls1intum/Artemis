package de.tum.in.www1.artemis.util.parallelTestExecution;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

public class ThreadLocalIdConverter extends ClassicConverter {

    @Override
    public String convert(ILoggingEvent iLoggingEvent) {
        return "" + LoggingDiscriminator.getThreadId();
    }
}
