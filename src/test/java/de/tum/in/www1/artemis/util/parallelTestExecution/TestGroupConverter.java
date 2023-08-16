package de.tum.in.www1.artemis.util.parallelTestExecution;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

public class TestGroupConverter extends ClassicConverter {

    @Override
    public String convert(ILoggingEvent iLoggingEvent) {
        TestGroup group = LoggingDiscriminator.getTestGroup();
        if (group == null) {
            return "";
        }
        return "" + LoggingDiscriminator.getTestGroup().name();
    }
}
