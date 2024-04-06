package de.tum.in.www1.artemis.util.junit_parallel_logging;

import static org.assertj.core.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import de.tum.in.www1.artemis.AbstractArtemisIntegrationTest;
import de.tum.in.www1.artemis.AbstractSpringIntegrationGitlabCIGitlabSamlTest;
import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;

/**
 * This custom appender is used to capture the logs of multiple tests running in parallel.
 * <p>
 * It captures the logs for each test group separately and prints them to the console at the end of the test class.
 * The test group is determined by the test's class. Each test class can only be assigned to one test group.
 * If a log cannot be assigned to a test group, the logs are printed for all active test groups.
 */
public class ParallelConsoleAppender extends AppenderBase<ILoggingEvent> {

    private PatternLayoutEncoder encoder;

    private static final InheritableThreadLocal<Class<?>> LOCAL_TEST_GROUP = new InheritableThreadLocal<>();

    private static final ConcurrentMap<Class<?>, ByteArrayOutputStream> TEST_GROUP_TO_ENCODED_LOGS = new ConcurrentHashMap<>();

    private static final Set<Class<?>> TEST_GROUPS = Set.of(AbstractSpringIntegrationGitlabCIGitlabSamlTest.class, AbstractSpringIntegrationJenkinsGitlabTest.class,
            AbstractSpringIntegrationLocalCILocalVCTest.class, AbstractSpringIntegrationIndependentTest.class);

    @Override
    protected synchronized void append(ILoggingEvent loggingEvent) {
        Class<?> testClass = LOCAL_TEST_GROUP.get();

        // Add the logging Event to the corresponding List in the Map
        if (testClass != null && !loggingEvent.getThreadName().contains("event")) {
            if (TEST_GROUP_TO_ENCODED_LOGS.containsKey(testClass)) {
                TEST_GROUP_TO_ENCODED_LOGS.get(testClass).writeBytes(encoder.encode(loggingEvent));
            }
            else {
                ByteArrayOutputStream logs = new ByteArrayOutputStream();
                logs.writeBytes(encoder.encode(loggingEvent));
                TEST_GROUP_TO_ENCODED_LOGS.put(testClass, logs);
            }
            return;
        }

        // If the thread id is not assigned to a TestGroup, we add the logging event for all active TestGroups
        for (ByteArrayOutputStream logs : TEST_GROUP_TO_ENCODED_LOGS.values()) {
            logs.writeBytes(encoder.encode(loggingEvent));
        }
    }

    /**
     * Prints the logs for the given test group to the console and removes them.
     * This method should be called at the end of a test class.
     *
     * @param testClass the test's class for which the logs should be printed
     */
    public static synchronized void printLogsForGroup(Class<?> testClass) {
        Class<?> testGroupClass = groupFromClass(testClass);
        ByteArrayOutputStream logs = TEST_GROUP_TO_ENCODED_LOGS.remove(testGroupClass);
        if (logs == null) {
            return;
        }

        System.out.writeBytes(logs.toByteArray());
        logs.reset();
        System.out.flush();
    }

    /**
     * Adds the given string to the logs for the current test group.
     *
     * @param string the string to add to the logs
     */
    public static synchronized void addStringToLogsForGroup(String string) {
        Class<?> testClass = LOCAL_TEST_GROUP.get();

        // Add the logging Event to the corresponding List in the Map
        if (testClass != null) {
            if (TEST_GROUP_TO_ENCODED_LOGS.containsKey(testClass)) {
                TEST_GROUP_TO_ENCODED_LOGS.get(testClass).writeBytes(string.getBytes());
            }
            else {
                ByteArrayOutputStream logs = new ByteArrayOutputStream();
                logs.writeBytes(string.getBytes());
                TEST_GROUP_TO_ENCODED_LOGS.put(testClass, logs);
            }
            return;
        }

        // If the thread id is not assigned to a TestGroup, we add the logging event for all active TestGroups
        for (ByteArrayOutputStream logs : TEST_GROUP_TO_ENCODED_LOGS.values()) {
            logs.writeBytes(string.getBytes());
        }

    }

    /**
     * Registers the test group for the given test class.
     * This method should be called at the beginning of a test class.
     *
     * @param testClass the test's class
     */
    public static void registerActiveTestGroup(Class<?> testClass) {
        LOCAL_TEST_GROUP.set(groupFromClass(testClass));
    }

    /**
     * Unregisters the test group for the given test class.
     * This method should be called at the end of a test class.
     *
     * @param testClass the test's class
     */
    public static void unregisterActiveTestGroup(Class<?> testClass) {
        TEST_GROUP_TO_ENCODED_LOGS.remove(testClass);
        LOCAL_TEST_GROUP.remove();
    }

    /**
     * Sets the encoder for this appender. This method is used by logback and should not be removed.
     * This method is used to set the encoder's pattern in the logback.xml file.
     *
     * @param encoder the encoder used to encode the logging events
     */
    @SuppressWarnings("unused")
    public void setEncoder(PatternLayoutEncoder encoder) {
        this.encoder = encoder;
    }

    /**
     * Returns the test group's class for the given class.
     * If none of the groups' classes is assignable from the given class, the class itself is returned.
     *
     * @param clazz the class for which the test group's class should be returned
     * @return the test group's class for the given class
     */
    private static Class<?> groupFromClass(Class<?> clazz) {
        if (clazz == null) {
            return null;
        }

        for (Class<?> group : TEST_GROUPS) {
            if (group.isAssignableFrom(clazz)) {
                return group;
            }
        }

        if (AbstractArtemisIntegrationTest.class.isAssignableFrom(clazz)) {
            fail("Test class " + clazz.getName() + " extends ArtemisIntegrationTest but is not assigned to a test group");
        }

        return clazz;
    }
}
