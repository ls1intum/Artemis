package de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisSlideVisibilityDTO(int slideNumber, ZonedDateTime hiddenUntil) {
}
