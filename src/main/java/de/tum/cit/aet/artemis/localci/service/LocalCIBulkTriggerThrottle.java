package de.tum.cit.aet.artemis.localci.service;

import java.time.Duration;
import java.util.function.LongSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentStatus;
import de.tum.cit.aet.artemis.localci.service.ci.BulkTriggerThrottle;

/**
 * Holds back a run that queues many builds, such as an instructor's "build all", while the build queue is longer than the
 * build agents can work off.
 * <p>
 * The queue may hold {@code maxQueuedJobsPerBuildSlot} jobs for every build slot, a slot being one build a build agent
 * runs at the same time. Agents take jobs from the queue whenever they have a free slot, so a queue of that length
 * keeps every slot busy without a run adding thousands of jobs that only sit there. Jobs of other runs and of students'
 * pushes count towards the length, because they compete for the same slots.
 * <p>
 * The throttle looks at the queue only. It does not slow down the builds that are already running, and it cannot make
 * agents that are overloaded by their own concurrency setting any less busy.
 * <p>
 * It is meant for one run on one thread and is not thread safe.
 */
final class LocalCIBulkTriggerThrottle implements BulkTriggerThrottle {

    private static final Logger log = LoggerFactory.getLogger(LocalCIBulkTriggerThrottle.class);

    /**
     * How long to wait before looking at the queue again. Short enough to notice free room within a few builds'
     * time, long enough not to hammer the distributed queue while the run waits.
     */
    static final Duration POLL_INTERVAL = Duration.ofSeconds(5);

    /**
     * How long the number of build slots is trusted before it is read again. Reading it means reading the information of
     * every build agent, including the jobs each one runs, which is much heavier than asking for the size of the queue,
     * and the number only changes when an agent joins, leaves or is paused.
     */
    static final Duration BUILD_SLOTS_REFRESH_INTERVAL = Duration.ofSeconds(30);

    /**
     * The limit while no build slot is known to be available, in which case there is nothing to wait for.
     */
    private static final int NO_LIMIT = Integer.MAX_VALUE;

    /**
     * What {@link Thread#sleep(Duration)} does, so that a test does not have to wait.
     */
    @FunctionalInterface
    interface Sleeper {

        void sleep(Duration duration) throws InterruptedException;
    }

    private final DistributedDataAccessService distributedDataAccessService;

    private final int maxQueuedJobsPerBuildSlot;

    private final long maxTotalWaitNanos;

    private final LongSupplier nanoClock;

    private final Sleeper sleeper;

    /**
     * The longest queue this run adds to, valid from {@link #limitReadAtNanos} on, and only if {@link #limitKnown}.
     */
    private int limit;

    private boolean limitKnown;

    private long limitReadAtNanos;

    private long totalWaitedNanos;

    /**
     * Set once the run stopped waiting for good, because it waited too long or was interrupted.
     */
    private boolean givenUp;

    private boolean readFailureLogged;

    /**
     * Creates a throttle that waits in real time.
     *
     * @param distributedDataAccessService the access to the build queue and the build agents
     * @param maxQueuedJobsPerBuildSlot    how many queued jobs one build slot is allowed to have, at least 1
     * @param maxTotalWait                 the longest time the run waits in total before it queues the rest without waiting
     */
    LocalCIBulkTriggerThrottle(DistributedDataAccessService distributedDataAccessService, int maxQueuedJobsPerBuildSlot, Duration maxTotalWait) {
        this(distributedDataAccessService, maxQueuedJobsPerBuildSlot, maxTotalWait, System::nanoTime, Thread::sleep);
    }

    LocalCIBulkTriggerThrottle(DistributedDataAccessService distributedDataAccessService, int maxQueuedJobsPerBuildSlot, Duration maxTotalWait, LongSupplier nanoClock,
            Sleeper sleeper) {
        this.distributedDataAccessService = distributedDataAccessService;
        this.maxQueuedJobsPerBuildSlot = maxQueuedJobsPerBuildSlot;
        this.maxTotalWaitNanos = maxTotalWait.toNanos();
        this.nanoClock = nanoClock;
        this.sleeper = sleeper;
    }

    @Override
    public void awaitCapacity() {
        if (givenUp) {
            return;
        }
        try {
            waitForRoom();
        }
        catch (InterruptedException e) {
            // The interrupt status is deliberately not restored: the caller sleeps between batches too, and a restored flag
            // would make each of those sleeps fail at once. The run goes on without waiting, because stopping it would leave
            // the remaining participations unbuilt for good.
            givenUp = true;
            log.warn("Interrupted while waiting for room in the build queue, queueing the rest of this run without waiting");
        }
        catch (RuntimeException e) {
            // A queue that cannot be read must not stop the run: the trigger itself reports what it cannot do per
            // participation, and the next call looks again.
            if (!readFailureLogged) {
                readFailureLogged = true;
                log.warn("Could not read the build queue, queueing without waiting for room", e);
            }
        }
    }

    private void waitForRoom() throws InterruptedException {
        long waitedHereNanos = 0;
        boolean waiting = false;
        while (true) {
            int currentLimit = queueLimit();
            if (currentLimit == NO_LIMIT) {
                return;
            }
            int queuedJobs = distributedDataAccessService.getQueuedJobsSize();
            if (queuedJobs < currentLimit) {
                if (waiting) {
                    log.info("The build queue has room again after {} s ({} jobs queued, limit {}), continuing to queue builds", Duration.ofNanos(waitedHereNanos).toSeconds(),
                            queuedJobs, currentLimit);
                }
                return;
            }
            if (totalWaitedNanos >= maxTotalWaitNanos) {
                givenUp = true;
                log.warn("The build queue still holds {} jobs (limit {}) after this run waited {} min in total, queueing the rest of this run without waiting", queuedJobs,
                        currentLimit, Duration.ofNanos(totalWaitedNanos).toMinutes());
                return;
            }
            if (!waiting) {
                waiting = true;
                log.info("The build queue holds {} jobs, which is the limit of {} for the available build slots, waiting before queueing more builds", queuedJobs, currentLimit);
            }
            long sleepStartedNanos = nanoClock.getAsLong();
            sleeper.sleep(POLL_INTERVAL);
            long sleptNanos = Math.max(0, nanoClock.getAsLong() - sleepStartedNanos);
            waitedHereNanos += sleptNanos;
            totalWaitedNanos += sleptNanos;
        }
    }

    /**
     * The number of queued jobs from which the run waits, read again once the last reading is old.
     */
    private int queueLimit() {
        long nowNanos = nanoClock.getAsLong();
        if (!limitKnown || nowNanos - limitReadAtNanos >= BUILD_SLOTS_REFRESH_INTERVAL.toNanos()) {
            limit = limitFor(availableBuildSlots());
            limitKnown = true;
            limitReadAtNanos = nowNanos;
        }
        return limit;
    }

    private int limitFor(int buildSlots) {
        // Without a build agent nothing is taken from the queue, and waiting for it to shrink would only wait for an agent
        // to join. The jobs stay queued either way and are built once an agent is there, as they were before the throttle.
        if (buildSlots <= 0) {
            return NO_LIMIT;
        }
        return (int) Math.min(NO_LIMIT - 1L, (long) buildSlots * maxQueuedJobsPerBuildSlot);
    }

    /**
     * The builds the build agents can run at the same time. A paused agent takes no job, so its slots do not count.
     */
    private int availableBuildSlots() {
        return distributedDataAccessService.getBuildAgentInformation().stream().filter(LocalCIBulkTriggerThrottle::takesJobs)
                .mapToInt(BuildAgentInformation::maxNumberOfConcurrentBuildJobs).sum();
    }

    private static boolean takesJobs(BuildAgentInformation agent) {
        return agent.status() != BuildAgentStatus.PAUSED && agent.status() != BuildAgentStatus.SELF_PAUSED;
    }
}
