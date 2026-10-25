package de.tum.cit.aet.artemis.course.dto.home;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.CourseHomeWelcomeCase;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseHomeLearningDTO(CourseHomeDTO.Progress progress, List<Candidate> candidates, List<CourseHomeDTO.Item> updates) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Candidate(CourseHomeWelcomeCase welcomeCase, CourseHomeDTO.Item item, boolean actionable) {
    }
}
