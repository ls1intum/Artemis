package de.tum.cit.aet.artemis.buildagent.dto;

import java.util.Map;

/**
 * The Docker settings of an exercise as the instructor stores them.
 *
 * @param allowRawSockets whether the build keeps the capability to open raw sockets, which a ping needs. It is off unless the exercise asks for it, and a build without a network
 *                            never gets it.
 */
public record DockerFlagsDTO(String network, Map<String, String> env, int cpuCount, int memory, int memorySwap, boolean allowRawSockets) {

    public DockerFlagsDTO(String network, Map<String, String> env, int cpuCount, int memory, int memorySwap) {
        this(network, env, cpuCount, memory, memorySwap, false);
    }
}
