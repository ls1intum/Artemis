package de.tum.cit.aet.artemis.buildagent.dto;

import java.util.Map;

public record DockerFlagsDTO(Map<String, String> env, String network, int cpuCount, int memory, int memorySwap) {
}
