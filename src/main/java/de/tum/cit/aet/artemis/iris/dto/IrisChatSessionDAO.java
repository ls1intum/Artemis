package de.tum.cit.aet.artemis.iris.dto;

import java.time.ZonedDateTime;

public record IrisChatSessionDAO(Long id, Long exerciseId, Long lectureId, Long courseId, ZonedDateTime creationDate) {
}
