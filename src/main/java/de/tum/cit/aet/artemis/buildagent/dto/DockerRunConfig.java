package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serializable;
import java.util.List;

public record DockerRunConfig(List<String> env, String network, int cpuCount, int memory, int memorySwap) implements Serializable {
}
