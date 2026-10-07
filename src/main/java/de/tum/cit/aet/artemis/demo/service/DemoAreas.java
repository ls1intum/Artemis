package de.tum.cit.aet.artemis.demo.service;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Seeds one area of the demo course at a time, so that a failing area is logged and skipped instead of keeping the remaining areas from being seeded or escaping into the startup
 * of the instance.
 * <p>
 * Whatever an area created before it failed stays in place and counts as seeded on the next startup, like after any interrupted run.
 */
final class DemoAreas {

    private static final Logger log = LoggerFactory.getLogger(DemoAreas.class);

    private DemoAreas() {
        // static helper, do not instantiate
    }

    /**
     * Seeds one area of the demo course.
     *
     * @param area the name of the area, used in the log when it fails.
     * @param step the seeding of the area.
     */
    static void seed(String area, Runnable step) {
        seed(area, () -> {
            step.run();
            return null;
        }, null);
    }

    /**
     * Seeds one area of the demo course whose result later areas build on.
     *
     * @param area     the name of the area, used in the log when it fails.
     * @param step     the seeding of the area.
     * @param fallback the result later areas continue with when this area fails.
     * @param <T>      the type of the result.
     * @return the result of the area, or the fallback if it failed.
     */
    static <T> @Nullable T seed(String area, Supplier<T> step, @Nullable T fallback) {
        try {
            return step.get();
        }
        catch (RuntimeException exception) {
            log.error("Could not seed the demo {}", area, exception);
            return fallback;
        }
    }
}
