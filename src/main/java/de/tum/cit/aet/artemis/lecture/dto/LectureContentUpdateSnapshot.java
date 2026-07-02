package de.tum.cit.aet.artemis.lecture.dto;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record LectureContentUpdateSnapshot(Long lectureUnitId, String lectureUnitName, String lectureName, String courseName, String courseDescription, Integer attachmentVersion,
        String attachmentLink, String videoSource, ZonedDateTime releaseDate, Map<Integer, ZonedDateTime> slideHiddenUntilBySlideNumber) {

    public LectureContentUpdateSnapshot {
        if (slideHiddenUntilBySlideNumber == null) {
            slideHiddenUntilBySlideNumber = Map.of();
        }
        else {
            var copiedSlideHiddenUntilBySlideNumber = new LinkedHashMap<Integer, ZonedDateTime>();
            slideHiddenUntilBySlideNumber.forEach(
                    (slideNumber, hiddenUntil) -> copiedSlideHiddenUntilBySlideNumber.put(Objects.requireNonNull(slideNumber, "slideNumber"), hiddenUntil));
            slideHiddenUntilBySlideNumber = Collections.unmodifiableMap(copiedSlideHiddenUntilBySlideNumber);
        }
    }
}
