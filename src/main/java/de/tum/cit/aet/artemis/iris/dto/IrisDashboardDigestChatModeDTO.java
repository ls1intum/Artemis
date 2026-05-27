package de.tum.cit.aet.artemis.iris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisDashboardDigestChatModeDTO(String mode, long sessions, long messages, double noResponseRate, long thumbsUpCount, long thumbsDownCount) {
}
