package de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook;

import java.time.ZonedDateTime;

public record PyrisSlideVisibilityDTO(Integer slideNumber, ZonedDateTime hiddenUntil) {
}
