package de.tum.in.www1.artemis.web.rest.dto;

import de.tum.in.www1.artemis.domain.Lecture;

/**
 * Represents a lecture to be created with a channel
 */
public record LectureDTO(Lecture lecture, String channelName) {
}
