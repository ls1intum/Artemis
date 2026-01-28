package de.tum.cit.aet.artemis.programming.service.localci.distributed.hazelcast;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.hazelcast.client.impl.clientside.HazelcastClientProxy;
import com.hazelcast.cluster.Member;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceNotActiveException;

import de.tum.cit.aet.artemis.core.config.LocalCIBuildAgentHazelcastDataCondition;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.queue.DistributedQueue;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.topic.DistributedTopic;

@Lazy
@Service
@Conditional(LocalCIBuildAgentHazelcastDataCondition.class)
public class HazelcastDistributedDataProviderService implements DistributedDataProvider {

    private final HazelcastInstance hazelcastInstance;

    public HazelcastDistributedDataProviderService(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    @Override
    public <T> DistributedQueue<T> getQueue(String name) {
        return new HazelcastDistributedQueue<>(hazelcastInstance.getQueue(name));
    }

    @Override
    public <T extends Comparable<T>> DistributedQueue<T> getPriorityQueue(String name) {
        return getQueue(name);
    }

    @Override
    public <K, V> DistributedMap<K, V> getMap(String name) {
        return new HazelcastDistributedMap<>(hazelcastInstance.getMap(name));
    }

    @Override
    public <T> DistributedTopic<T> getTopic(String name) {
        return new HazelcastDistributedTopic<>(hazelcastInstance.getTopic(name));
    }

    /**
     * Checks if the Hazelcast instance is active and operational.
     *
     * @return {@code true} if the Hazelcast instance has been initialized and is actively running,
     *         {@code false} if the instance has not been initialized or is no longer running
     */
    @Override
    public boolean isInstanceRunning() {
        try {
            return hazelcastInstance != null && hazelcastInstance.getLifecycleService().isRunning();
        }
        catch (HazelcastInstanceNotActiveException e) {
            return false;
        }

    }

    /**
     * Returns a unique identifier for the local Hazelcast instance.
     * For cluster members, returns the member's cluster address in normalized format.
     * For clients (e.g., build agents in client mode), returns the client's local endpoint address,
     * or a unique fallback identifier if not yet connected.
     *
     * <p>
     * The address format is always {@code [host]:port} where the host is the IP address or hostname.
     * IPv6 addresses are wrapped in brackets per RFC 3986, and IPv4 addresses are also
     * wrapped in brackets for consistency across the codebase. Note that for cluster members,
     * {@code memberAddress.getHost()} may return either an IP address or a hostname depending
     * on the Hazelcast configuration and network environment.
     *
     * <p>
     * <strong>Examples:</strong>
     * <ul>
     * <li>IPv4: {@code [192.168.1.1]:5701}</li>
     * <li>IPv6: {@code [2001:db8::1]:5701}</li>
     * <li>Hostname: {@code [artemis-node-1]:5701}</li>
     * <li>Client not connected (fallback): {@code artemis-build-agent-1/Artemis-client}</li>
     * </ul>
     *
     * <p>
     * <strong>Important:</strong> When using asyncStart=true for Hazelcast clients, the endpoint
     * address may not be available immediately. In this case, a unique fallback identifier
     * combining hostname and instance name (format: {@code hostname/instanceName}) is returned.
     * This ensures that each build agent has a unique key in the distributed map even before
     * connecting to the cluster.
     *
     * @return a unique identifier for this instance: the address in format {@code [host]:port},
     *         or {@code hostname/instanceName} if the client endpoint is not yet connected
     * @throws HazelcastInstanceNotActiveException if the Hazelcast instance is not running
     */
    @Override
    public String getLocalMemberAddress() {
        if (!isInstanceRunning()) {
            throw new HazelcastInstanceNotActiveException();
        }

        // Check if this is a Hazelcast client (build agents in client mode)
        if (hazelcastInstance instanceof HazelcastClientProxy) {
            // For clients with asyncStart=true, getLocalEndpoint() may return null
            // if the client hasn't connected to the cluster yet
            var localEndpoint = hazelcastInstance.getLocalEndpoint();
            if (localEndpoint == null || localEndpoint.getSocketAddress() == null) {
                // Use hostname + instance name as a unique fallback identifier.
                // This ensures each build agent has a unique key in the distributed map
                // even before connecting to the cluster.
                return getUniqueInstanceIdentifier();
            }
            // Verify the socket address is an InetSocketAddress before casting
            // In practice, Hazelcast always returns InetSocketAddress, but we guard against
            // potential future changes or unexpected implementations
            if (!(localEndpoint.getSocketAddress() instanceof InetSocketAddress socketAddress)) {
                return getUniqueInstanceIdentifier();
            }
            // Format as [host]:port for consistency
            return "[" + socketAddress.getAddress().getHostAddress() + "]:" + socketAddress.getPort();
        }

        // For cluster members, format consistently with client addresses
        var memberAddress = hazelcastInstance.getCluster().getLocalMember().getAddress();
        return "[" + memberAddress.getHost() + "]:" + memberAddress.getPort();
    }

    /**
     * Generates a unique identifier for this instance when the endpoint address is not available.
     * This is used as a fallback for Hazelcast clients with asyncStart=true before they connect.
     *
     * @return a unique identifier combining hostname and Hazelcast instance name
     */
    private String getUniqueInstanceIdentifier() {
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        }
        catch (UnknownHostException e) {
            hostname = "unknown-host";
        }
        // Combine hostname with instance name to ensure uniqueness across hosts
        // even if they share the same Hazelcast instance name configuration
        return hostname + "/" + hazelcastInstance.getName();
    }

    /**
     * Retrieves the addresses of all members in the Hazelcast cluster.
     *
     * @return a set of addresses of all cluster members
     */
    @Override
    public Set<String> getClusterMemberAddresses() {
        // stream is on the copy so fine here
        return getClusterMembers().stream().map(Member::getAddress).map(Object::toString).collect(Collectors.toSet());
    }

    /**
     * Checks if there are no data members available in the cluster.
     *
     * @return {@code true} if all members in the cluster are lite members (i.e., no data members are available),
     */
    @Override
    public boolean noDataMemberInClusterAvailable() {
        // stream is on the copy so fine here
        return getClusterMembers().stream().allMatch(Member::isLiteMember);
    }

    /**
     * Retrieves the members of the Hazelcast cluster.
     *
     * @return a stream of Hazelcast cluster members
     */
    private ArrayList<Member> getClusterMembers() {
        if (!isInstanceRunning()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(hazelcastInstance.getCluster().getMembers());
    }
}
