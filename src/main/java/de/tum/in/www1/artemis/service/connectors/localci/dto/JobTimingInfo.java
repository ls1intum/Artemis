package de.tum.in.www1.artemis.service.connectors.localci.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;

public record JobTimingInfo(ZonedDateTime submissionDate, ZonedDateTime buildStartDate, ZonedDateTime buildCompletionDate) implements Serializable {
}
