package de.tum.in.www1.artemis.service.connectors;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import com.hazelcast.core.HazelcastInstance;

@Component
public class HazelcastHealthIndicator implements HealthIndicator {

    private final HazelcastInstance hazelcastInstance;

    public HazelcastHealthIndicator(HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    @Override
    public Health health() {

        var members = hazelcastInstance.getCluster().getMembers();
        final var health = members.isEmpty() ? Health.down() : Health.up();
        Map<String, String> details = new HashMap<>();
        for (var member : members) {
            details.put("Member uuid " + member.getUuid().toString(), "Member address " + member.getAddress().toString());
        }
        details.put("This hazelcast instance name", hazelcastInstance.getName());
        details.put("This hazelcast instance uuid", hazelcastInstance.getLocalEndpoint().getUuid().toString());
        details.put("Cluster name", hazelcastInstance.getConfig().getClusterName());
        details.put("Cluster size", String.valueOf(members.size()));
        return health.withDetails(details).build();
    }
}
