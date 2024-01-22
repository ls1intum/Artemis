package de.tum.in.www1.artemis.service.connectors.localci.dto;

import java.time.ZonedDateTime;

public record DockerImageBuild(String dockerImage, ZonedDateTime lastBuildCompletionDate) {
}
