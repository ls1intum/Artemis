package de.tum.cit.aet.artemis.buildagent.dto;

import java.util.Map;

public record DockerFlagsDTO(String network, Map<String, String> env) {
}
