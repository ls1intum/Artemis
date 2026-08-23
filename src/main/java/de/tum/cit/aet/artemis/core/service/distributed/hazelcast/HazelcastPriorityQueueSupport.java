package de.tum.cit.aet.artemis.core.service.distributed.hazelcast;

import com.hazelcast.client.impl.clientside.HazelcastClientProxy;
import com.hazelcast.config.QueueConfig;
import com.hazelcast.core.HazelcastInstance;

/**
 * Guards the priority-queue contract of the Hazelcast backend.
 *
 * <p>
 * Unlike the Redisson and local backends, Hazelcast does not order a queue by the items' natural ordering on its own.
 * Ordering comes from a {@link QueueConfig#setPriorityComparatorClassName(String)} entry that is bound to a specific
 * queue <em>name</em> and has to exist before the queue proxy is created. Artemis configures exactly one such entry,
 * for {@code buildJobQueue}.
 *
 * <p>
 * Without this guard, requesting a priority queue under any other name silently returns a plain FIFO queue: the call
 * succeeds, no warning is logged, and jobs are simply dispatched in the wrong order. Failing fast turns that silent
 * misbehaviour into an actionable error at the call site.
 */
final class HazelcastPriorityQueueSupport {

    private HazelcastPriorityQueueSupport() {
    }

    /**
     * Verifies that the given queue name has a priority comparator configured.
     *
     * @param hazelcastInstance the Hazelcast instance backing the queue
     * @param name              the queue name to check
     * @throws UnsupportedOperationException if no priority comparator is configured for the name
     */
    static void verifyPriorityOrderingConfigured(HazelcastInstance hazelcastInstance, String name) {
        // Clients (build agents) cannot read cluster configuration; ordering is governed by the owning member's config.
        if (hazelcastInstance instanceof HazelcastClientProxy) {
            return;
        }

        // Exact lookup: getQueueConfig(name) would fall back to the "default" config and mask a missing entry.
        QueueConfig queueConfig = hazelcastInstance.getConfig().getQueueConfigs().get(name);
        if (queueConfig != null && queueConfig.getPriorityComparatorClassName() != null) {
            return;
        }

        throw new UnsupportedOperationException("No Hazelcast priority comparator is configured for queue '" + name
                + "', so it would silently behave as a FIFO queue. Add a QueueConfig with a priorityComparatorClassName for this queue name in HazelcastConfiguration, "
                + "or use getQueue(name) if FIFO ordering is intended.");
    }
}
