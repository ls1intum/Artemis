package de.tum.cit.aet.artemis.core.service.connectors;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.hazelcast.client.impl.clientside.HazelcastClientProxy;
import com.hazelcast.core.HazelcastInstance;

import de.tum.cit.aet.artemis.core.config.CoreOrHazelcastBuildAgent;

@Conditional(CoreOrHazelcastBuildAgent.class)
@Component
@Lazy(false)
public class HazelcastHealthIndicator implements HealthIndicator {

    private final HazelcastInstance hazelcastInstance;

    public HazelcastHealthIndicator(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    @Override
    public Health health() {
        var members = hazelcastInstance.getCluster().getMembers();
        final var health = members.isEmpty() ? Health.down() : Health.up();
        Map<String, String> details = new HashMap<>();

        // Add cluster member information
        for (var member : members) {
            String memberType = member.isLiteMember() ? "Lite member" : "Data member";
            details.put("Member uuid " + member.getUuid().toString(), memberType + " address " + member.getAddress().toString());
        }

        details.put("This hazelcast instance name", hazelcastInstance.getName());
        details.put("This hazelcast instance uuid", hazelcastInstance.getLocalEndpoint().getUuid().toString());
        details.put("Cluster name", hazelcastInstance.getConfig().getClusterName());
        details.put("Cluster size", String.valueOf(members.size()));

        // Add connected client information (only available on cluster members, not on clients)
        if (!(hazelcastInstance instanceof HazelcastClientProxy)) {
            try {
                var clientService = hazelcastInstance.getClientService();
                var connectedClients = clientService.getConnectedClients();
                details.put("Connected clients", String.valueOf(connectedClients.size()));

                int clientIndex = 1;
                for (var client : connectedClients) {
                    String clientInfo = "Client " + clientIndex + " (uuid " + client.getUuid() + ")";
                    String clientDetails = "Type: " + client.getClientType() + ", Address: " + client.getSocketAddress() + ", Name: " + client.getName();
                    details.put(clientInfo, clientDetails);
                    clientIndex++;
                }
            }
            catch (UnsupportedOperationException e) {
                // Client service not available (e.g., on Hazelcast clients)
                details.put("Connected clients", "N/A (running as client)");
            }
        }
        else {
            details.put("Instance type", "Hazelcast Client");
        }

        return health.withDetails(details).build();
    }
}
