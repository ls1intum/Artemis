package de.tum.cit.aet.artemis.localci.service.ci;

/**
 * Holds back a run that triggers many builds one after the other, such as an instructor's "build all", until the CI
 * system has room for the next build.
 * <p>
 * One instance serves one run on one thread and is not shared, because it remembers what it has already waited. The
 * CI system hands it out with {@link ContinuousIntegrationTriggerService#createBulkTriggerThrottle()}.
 */
@FunctionalInterface
public interface BulkTriggerThrottle {

    /**
     * A throttle that never waits, for CI systems that have no queue to look at.
     */
    BulkTriggerThrottle NONE = () -> {
    };

    /**
     * Blocks until the CI system has room for one more build, or until the throttle gives up waiting.
     * <p>
     * It never throws: a throttle that cannot tell whether there is room, or that has waited long enough, returns and
     * lets the build be queued, because a build that is not triggered is never retried.
     */
    void awaitCapacity();
}
