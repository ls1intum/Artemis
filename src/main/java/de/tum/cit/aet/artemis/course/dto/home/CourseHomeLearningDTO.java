package de.tum.cit.aet.artemis.course.dto.home;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.CourseHomeWelcomeCase;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseHomeLearningDTO(CourseHomeDTO.ProgressDTO progress, List<CandidateDTO> candidates, List<CourseHomeDTO.ItemDTO> updates) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CandidateDTO(CourseHomeWelcomeCase welcomeCase, CourseHomeDTO.ItemDTO item, boolean actionable) {
    }
}
