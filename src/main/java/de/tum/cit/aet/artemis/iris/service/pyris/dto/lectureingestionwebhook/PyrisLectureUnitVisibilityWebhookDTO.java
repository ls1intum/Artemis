package de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook;

import java.time.ZonedDateTime;
import java.util.List;

public record PyrisLectureUnitVisibilityWebhookDTO(Long lectureUnitId, Long lectureId, Long courseId, String baseUrl, ZonedDateTime releaseDate,
        List<PyrisSlideVisibilityDTO> slides) {
}
