package de.tum.cit.aet.artemis.lecture.dto;

import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.CourseHomeSectionStatus;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LectureCourseHomeDTO(CourseHomeSectionStatus status, List<CourseHomeDTO.ItemDTO> items, @Nullable Instant nextRefreshAt) {

    public LectureCourseHomeDTO(CourseHomeSectionStatus status, List<CourseHomeDTO.ItemDTO> items) {
        this(status, items, null);
    }
}
