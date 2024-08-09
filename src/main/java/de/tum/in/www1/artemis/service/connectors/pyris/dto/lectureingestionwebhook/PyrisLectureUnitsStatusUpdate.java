package de.tum.in.www1.artemis.service.connectors.pyris.dto.lectureingestionwebhook;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents a webhook data transfer object for lecture units in the Pyris system.
 * This DTO is used to encapsulate the information related to updates of lecture units,
 * providing necessary details such as lecture and course identifiers, names, and descriptions.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)

public record PyrisLectureUnitsStatusUpdate(Long lectureUnitId, Long lectureId, Long courseId) {
}
