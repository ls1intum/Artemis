package de.tum.in.www1.artemis.util.parallelTestExecution;

import static org.assertj.core.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.validation.constraints.NotNull;

import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import de.tum.in.www1.artemis.*;
import de.tum.in.www1.artemis.util.AbstractArtemisIntegrationTest;

public class ParallelConsoleAppender extends AppenderBase<ILoggingEvent> {

    private PatternLayoutEncoder encoder;

    private static final InheritableThreadLocal<Class<?>> localTestGroup = new InheritableThreadLocal<>();

    private static final ConcurrentMap<Class<?>, List<byte[]>> testGroupToEncodedLogs = new ConcurrentHashMap<>();

    @Override
    protected synchronized void append(ILoggingEvent loggingEvent) {
        Class<?> testClass = localTestGroup.get();

        // Add the logging Event to the corresponding List in the Map
        if (testClass != null && !loggingEvent.getThreadName().contains("event")) {
            List<byte[]> logs = testGroupToEncodedLogs.putIfAbsent(testClass, new ArrayList<>());
            if (logs == null) {
                logs = testGroupToEncodedLogs.get(testClass);
            }
            logs.add(encoder.encode(loggingEvent));
            return;
        }

        // If the thread id is not assigned to a TestGroup, we add the logging event for all active TestGroups
        for (var logs : testGroupToEncodedLogs.values()) {
            logs.add(encoder.encode(loggingEvent));
        }
    }

    public static synchronized void printLogsForGroup(Class<?> testClass) {
        Class<?> testGroupClass = TestGroup.fromClass(testClass);
        List<byte[]> logs = testGroupToEncodedLogs.remove(testGroupClass);
        if (logs == null) {
            return;
        }

        for (byte[] log : logs) {
            System.out.writeBytes(log);
        }
        System.out.flush();
    }

    public static synchronized void addStringToLogsForGroup(String string) {
        Class<?> testClass = localTestGroup.get();

        // Add the logging Event to the corresponding List in the Map
        if (testClass != null) {
            List<byte[]> logs = testGroupToEncodedLogs.putIfAbsent(testClass, new ArrayList<>());
            if (logs == null) {
                logs = testGroupToEncodedLogs.get(testClass);
            }
            logs.add(string.getBytes());
            return;
        }

        // If the thread id is not assigned to a TestGroup, we add the logging event for all active TestGroups
        for (var logs : testGroupToEncodedLogs.values()) {
            logs.add(string.getBytes());
        }

    }

    public static void registerActiveTestGroup(Class<?> testClass) {
        localTestGroup.set(TestGroup.fromClass(testClass));
    }

    public static void unregisterActiveTestGroup(Class<?> testClass) {
        testGroupToEncodedLogs.remove(testClass);
        localTestGroup.remove();
    }

    // public Encoder<ILoggingEvent> getEncoder() {
    // return encoder;
    // }

    // Used by logback
    public void setEncoder(PatternLayoutEncoder encoder) {
        this.encoder = encoder;
    }

    private enum TestGroup {

        BAMBOO_INTEGRATION_TEST(AbstractSpringIntegrationBambooBitbucketJiraTest.class), GITLAB_INTEGRATION_TEST(AbstractSpringIntegrationGitlabCIGitlabSamlTest.class),
        JENKINS_INTEGRATION_TEST(AbstractSpringIntegrationJenkinsGitlabTest.class), LOCAL_INTEGRATION_TEST(AbstractSpringIntegrationLocalCILocalVCTest.class),
        MIN_INTEGRATION_TEST(AbstractSpringIntegrationTest.class);

        @NotNull
        private final Class<?> clazz;

        TestGroup(Class<?> clazz) {
            this.clazz = clazz;
        }

        public static Class<?> fromClass(Class<?> clazz) {
            if (clazz == null) {
                return null;
            }

            for (TestGroup group : values()) {
                if (group.clazz.isAssignableFrom(clazz)) {
                    return group.clazz;
                }
            }

            if (AbstractArtemisIntegrationTest.class.isAssignableFrom(clazz)) {
                fail("Test class " + clazz.getName() + " extends ArtemisIntegrationTest but is not assigned to a test group");
            }

            return clazz;
        }
    }
}
