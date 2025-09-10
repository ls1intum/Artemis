package de.tum.cit.aet.artemis.lecture.domain;

public record LectureTranscriptionSegment(Double startTime, Double endTime, String text, int slideNumber) {
}
