package de.tum.cit.aet.artemis.videosource.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GocastApprovalResultDTO(boolean completed, Long artemisCourseId) {
}
