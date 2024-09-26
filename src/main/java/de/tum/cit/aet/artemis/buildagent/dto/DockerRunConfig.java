package de.tum.cit.aet.artemis.buildagent.dto;

import java.util.Map;

public record DockerRunConfig(Map<String, String> flags) {

    public static String NETWORK_FLAG = "network";
}
