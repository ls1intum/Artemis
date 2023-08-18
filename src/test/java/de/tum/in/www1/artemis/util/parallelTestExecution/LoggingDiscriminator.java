package de.tum.in.www1.artemis.util.parallelTestExecution;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.sift.Discriminator;

/**
 * Discriminator that allows us to write the logs of each test method to a separate file.
 */
public class LoggingDiscriminator implements Discriminator<ILoggingEvent> {

    private static final String KEY = "logFileName";

    private static final String DEFAULT = "unknown";

    private static final InheritableThreadLocal<String> value = new InheritableThreadLocal<>();

    private static final InheritableThreadLocal<Long> threadId = new InheritableThreadLocal<>();

    private static final InheritableThreadLocal<TestGroup> testGroup = new InheritableThreadLocal<>();

    private boolean isStarted;

    private Long threadIdOnStart;

    public LoggingDiscriminator() {
        threadIdOnStart = Thread.currentThread().getId();
        System.out.println("Parallel Debug Info: LoggingDiscriminator created in " + threadIdOnStart);
    }

    @Override
    public String getDiscriminatingValue(ILoggingEvent iLoggingEvent) {
        System.out.println("Parallel Debug Info: LoggingDiscriminator.getDiscriminatingValue() called for " + threadIdOnStart);
        if (value.get() == null) {
            return DEFAULT;
        }
        return value.get();
    }

    @Override
    public String getKey() {
        return KEY;
    }

    @Override
    public void start() {
        isStarted = true;
        System.out.println("Parallel Debug Info: LoggingDiscriminator started in " + threadIdOnStart);
    }

    @Override
    public void stop() {
        isStarted = false;
        System.out.println("Parallel Debug Info: LoggingDiscriminator stopped in " + threadIdOnStart);
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
        value.set(newValue);
    }

    static void setThreadId(Long newValue) {
        threadId.set(newValue);
    }

    static void setTestGroup(TestGroup newValue) {
        testGroup.set(newValue);
    }

    static void clear() {
        threadId.remove();
        value.remove();
        testGroup.remove();
    }

    static Long getThreadId() {
        return threadId.get();
    }

    static TestGroup getTestGroup() {
        return testGroup.get();
    }

    @Override
    protected void finalize() throws Throwable {
        System.out.println("Parallel Debug Info: LoggingDiscriminator finalized in " + threadIdOnStart);
        super.finalize();
    }
}
