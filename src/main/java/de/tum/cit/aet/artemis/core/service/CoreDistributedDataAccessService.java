package de.tum.cit.aet.artemis.core.service;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

@Lazy
@Service
public class CoreDistributedDataAccessService {

    private final HazelcastInstance hazelcastInstance;

    private IMap<String, Instant> lastTypingTracker;

    private IMap<String, String> destinationTracker;

    private IMap<String, Instant> lastActionTracker;

    public CoreDistributedDataAccessService(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    /**
     * Lazy Init: Returns the last typing tracker map which keeps track of the last typing date for each user in a participation.
     * This is used to determine which team members are currently typing.
     *
     * @return the destination tracker map
     */
    public IMap<String, Instant> getDistributedLastTypingTracker() {
        if (this.lastTypingTracker == null) {
            this.lastTypingTracker = this.hazelcastInstance.getMap("lastTypingTracker");
        }
        return lastTypingTracker;
    }

    /**
     * Lazy Init: Returns the destination tracker map which keeps track of the destination that each session is subscribed to.
     * This is used to send out the list of online team members when a user subscribes or unsubscribes.
     *
     * @return the destination tracker map
     */
    public IMap<String, String> getDistributedDestinationTracker() {
        if (this.destinationTracker == null) {
            this.destinationTracker = this.hazelcastInstance.getMap("destinationTracker");
        }
        return destinationTracker;
    }

    /**
     * Lazy Init: Returns the last action tracker map which keeps track of the last action date for each user in a participation.
     * This is used to send out the list of online team members when a user subscribes or unsubscribes.
     *
     * @return the last action tracker map
     */
    public IMap<String, Instant> getDistributedLastActionTracker() {
        if (this.lastActionTracker == null) {
            this.lastActionTracker = this.hazelcastInstance.getMap("lastActionTracker");
        }
        return lastActionTracker;
    }

    /**
     * Checks if the Hazelcast instance is active and operational.
     *
     * @return {@code true} if the Hazelcast instance has been initialized and is actively running,
     *         {@code false} if the instance has not been initialized or is no longer running
     */
    public boolean isInstanceRunning() {
        return hazelcastInstance != null && hazelcastInstance.getLifecycleService().isRunning();
    }
}
