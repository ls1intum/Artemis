package de.tum.in.www1.artemis.util.parallelTestExecution;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.sift.Discriminator;

/**
 * Discriminator that allows us to write the logs of each test method to a separate file.
 */
public class LoggingDiscriminator implements Discriminator<ILoggingEvent> {

    private static final String KEY = "logFileName";

    private static String value = "unknown-test";

    private boolean isStarted;

    @Override
    public String getDiscriminatingValue(ILoggingEvent iLoggingEvent) {
        return value;
    }

    @Override
    public String getKey() {
        return KEY;
    }

    @Override
    public void start() {
        isStarted = true;
    }

    @Override
    public void stop() {
        isStarted = false;
    }

    @Override
    public boolean isStarted() {
        return isStarted;
    }

    /**
     * Sets the value of the discriminator.
     *
     * @param newValue usually a combination of the test class name and test method name
     */
    static void setValue(String newValue) {
        value = newValue;
    }
}
