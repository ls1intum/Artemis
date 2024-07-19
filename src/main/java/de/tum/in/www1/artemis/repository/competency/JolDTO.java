package de.tum.in.www1.artemis.repository.competency;

import java.time.ZonedDateTime;

import de.tum.in.www1.artemis.domain.competency.CourseCompetency;

public record JolDTO(Long userId, CourseCompetency competency, ZonedDateTime time) {
}
