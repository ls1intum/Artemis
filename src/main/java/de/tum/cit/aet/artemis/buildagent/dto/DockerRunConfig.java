package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;

public record DockerRunConfig(List<String> env, String network, int cpuCount, int memory, int memorySwap) implements Serializable {

    /**
     * Whether the given network value attaches a container to the network namespace of the host or of another container instead of to a network of its own.
     * Such a value is never a valid choice for a build, whichever networks are configured as allowed.
     *
     * @param network the requested network, may be null or blank
     * @return true for {@code host} and for {@code container:<name or id>}
     */
    public static boolean sharesNetworkNamespace(String network) {
        if (network == null) {
            return false;
        }
        String normalized = network.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("host") || normalized.startsWith("container:");
    }
}
