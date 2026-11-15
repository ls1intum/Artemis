package de.tum.cit.aet.artemis.lecture.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LectureTranscriptionSegment(Double startTime, Double endTime, String text, int slideNumber) {
}
