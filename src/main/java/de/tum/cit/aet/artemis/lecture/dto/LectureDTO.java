package de.tum.cit.aet.artemis.lecture.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public interface LectureDTO {

    Long id();

    String title();

    String description();

    ZonedDateTime startDate();

    ZonedDateTime endDate();

    @JsonProperty("isTutorialLecture")
    boolean isTutorialLecture();
}
