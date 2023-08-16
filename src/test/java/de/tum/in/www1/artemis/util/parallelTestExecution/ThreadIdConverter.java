package de.tum.in.www1.artemis.util.parallelTestExecution;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

public class ThreadIdConverter extends ClassicConverter {

    @Override
    public String convert(ILoggingEvent iLoggingEvent) {
        return "" + Thread.currentThread().getId();
    }
}
