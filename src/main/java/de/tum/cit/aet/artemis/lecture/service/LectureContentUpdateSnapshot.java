package de.tum.cit.aet.artemis.lecture.service;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;

public record LectureContentUpdateSnapshot(Long lectureUnitId, String lectureUnitName, String lectureName, String courseName, String courseDescription, Integer attachmentVersion,
        String attachmentLink, String videoSource, ZonedDateTime releaseDate, Map<Integer, ZonedDateTime> slideHiddenUntilBySlideNumber) {

    public LectureContentUpdateSnapshot {
        Objects.requireNonNull(lectureUnitId, "lectureUnitId");
        if (slideHiddenUntilBySlideNumber == null) {
            slideHiddenUntilBySlideNumber = Map.of();
        }
        else {
            var copiedSlideHiddenUntilBySlideNumber = new LinkedHashMap<Integer, ZonedDateTime>();
            slideHiddenUntilBySlideNumber
                    .forEach((slideNumber, hiddenUntil) -> copiedSlideHiddenUntilBySlideNumber.put(Objects.requireNonNull(slideNumber, "slideNumber"), hiddenUntil));
            slideHiddenUntilBySlideNumber = Collections.unmodifiableMap(copiedSlideHiddenUntilBySlideNumber);
        }
    }

    /**
     * Resolves the effective release date used for visibility synchronization.
     *
     * @param unit the attachment video unit
     * @return the unit release date, or the attachment release date when the unit date is absent
     */
    public static ZonedDateTime resolveReleaseDate(AttachmentVideoUnit unit) {
        if (unit.getReleaseDate() != null) {
            return unit.getReleaseDate();
        }
        return unit.getAttachment() != null ? unit.getAttachment().getReleaseDate() : null;
    }
}
