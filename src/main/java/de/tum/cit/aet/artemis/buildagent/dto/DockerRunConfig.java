package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serializable;
import java.util.List;

public record DockerRunConfig(boolean isNetworkDisabled, List<String> env) implements Serializable {
}
