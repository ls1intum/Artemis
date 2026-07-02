package de.tum.cit.aet.artemis.lecture.dto;

import java.time.ZonedDateTime;
import java.util.Map;

public record LectureContentUpdateSnapshot(Long lectureUnitId, String lectureUnitName, String lectureName, String courseName, String courseDescription, Integer attachmentVersion,
        String attachmentLink, String videoSource, ZonedDateTime releaseDate, Map<Integer, ZonedDateTime> slideHiddenUntilBySlideNumber) {

    public LectureContentUpdateSnapshot {
        slideHiddenUntilBySlideNumber = slideHiddenUntilBySlideNumber != null ? Map.copyOf(slideHiddenUntilBySlideNumber) : Map.of();
    }
}
