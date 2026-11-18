package de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook;

import java.time.ZonedDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisLectureUnitVisibilityWebhookDTO(Long lectureUnitId, Long lectureId, Long courseId, String baseUrl, ZonedDateTime releaseDate,
        List<PyrisSlideVisibilityDTO> slides) {
}
