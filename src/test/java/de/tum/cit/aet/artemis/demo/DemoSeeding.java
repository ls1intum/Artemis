package de.tum.cit.aet.artemis.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.tum.cit.aet.artemis.core.DeferredEagerBeanInitializationCompletedEvent;
import de.tum.cit.aet.artemis.demo.service.DemoDataSeedingService;

/**
 * Seeds the demo data in tests and reports the areas that failed.
 * <p>
 * The seeding logs a failing area instead of letting its exception escape, so that one area cannot keep the others from being seeded. A test that only seeds and then looks at
 * the data would therefore pass even if the area it checks failed and left the data of an earlier run in place.
 */
final class DemoSeeding {

    /**
     * The package of the seeding services, whose loggers report the failing areas.
     */
    private static final String SEEDING_LOGGER = "de.tum.cit.aet.artemis.demo.service";

    private DemoSeeding() {
        // static helper, do not instantiate
    }

    /**
     * Seeds the demo data and fails the test if an area of it failed.
     *
     * @param demoDataSeedingService the seeding to run.
     */
    static void seed(DemoDataSeedingService demoDataSeedingService) {
        assertThat(seedAndCollectErrors(demoDataSeedingService)).as("every area of the demo data is seeded without an error").isEmpty();
    }

    /**
     * Seeds the demo data and collects the errors the seeding logged, for tests in which an area fails on purpose.
     *
     * @param demoDataSeedingService the seeding to run.
     * @return the messages of the errors the seeding logged, in the order they were logged.
     */
    static List<String> seedAndCollectErrors(DemoDataSeedingService demoDataSeedingService) {
        Logger seedingLogger = (Logger) LoggerFactory.getLogger(SEEDING_LOGGER);
        // Seeding logs a failing area on the calling thread, while demo tests of other test contexts may seed concurrently in the same JVM.
        String seedingThread = Thread.currentThread().getName();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        seedingLogger.addAppender(appender);
        try {
            demoDataSeedingService.seedDemoData(new DeferredEagerBeanInitializationCompletedEvent());
        }
        finally {
            seedingLogger.detachAppender(appender);
        }
        return appender.list.stream().filter(event -> event.getLevel() == Level.ERROR && seedingThread.equals(event.getThreadName())).map(ILoggingEvent::getFormattedMessage)
                .toList();
    }
}
