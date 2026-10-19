package de.tum.cit.aet.artemis.course.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.lecture.domain.Lecture;

/** A lecture summary used by course content selection screens. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LectureForCourseManagementDTO(long id, String title, @Nullable String description, @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime endDate,
        @JsonProperty("isTutorialLecture") boolean tutorialLecture) {

    /** Maps a lecture without traversing its units or course. */
    public static LectureForCourseManagementDTO of(Lecture lecture) {
        return new LectureForCourseManagementDTO(lecture.getId(), lecture.getTitle(), lecture.getDescription(), lecture.getStartDate(), lecture.getEndDate(),
                lecture.isTutorialLecture());
    }
}
