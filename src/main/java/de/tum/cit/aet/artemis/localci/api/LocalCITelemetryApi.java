package de.tum.cit.aet.artemis.localci.api;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;

/** Exposes a connectivity-aware count without the administration view's fallback to stale agent entries. */
@Controller
@Lazy
@Profile("core & localci")
public class LocalCITelemetryApi implements AbstractApi {

    private final DistributedDataAccessService data;

    private final DistributedDataProvider provider;

    public LocalCITelemetryApi(DistributedDataAccessService data, DistributedDataProvider provider) {
        this.data = data;
        this.provider = provider;
    }

    /**
     * Counts connected agents, including paused agents and core nodes that also build.
     *
     * @return connected agent count, or null when connectivity cannot be determined
     */
    @Nullable
    public Integer getConnectedBuildAgentCount() {
        var membership = provider.getClusterMembership();
        var clients = membership.connectedClientNames();
        var members = membership.clusterMemberAddresses();
        if (clients.isEmpty() && members.isEmpty()) {
            return null;
        }
        int count = 0;
        for (var information : data.getBuildAgentInformationMap().values()) {
            if (information == null || information.buildAgent() == null) {
                continue;
            }
            var agent = information.buildAgent();
            if (clients.contains(agent.name()) || clients.contains(agent.memberAddress()) || members.contains(agent.memberAddress())) {
                count++;
            }
            else if (clients.isEmpty() && !provider.buildAgentsAppearInClusterMemberList()) {
                // An empty client view cannot distinguish a disconnected client from an unavailable lookup.
                return null;
            }
        }
        return count;
    }
}
