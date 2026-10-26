package de.tum.cit.aet.artemis.course.dto.home;

import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.CourseHomeWelcomeCase;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseHomeLearningDTO(CourseHomeDTO.ProgressDTO progress, List<CandidateDTO> candidates, List<CourseHomeDTO.ItemDTO> updates, @Nullable Instant nextRefreshAt) {

    public CourseHomeLearningDTO(CourseHomeDTO.ProgressDTO progress, List<CandidateDTO> candidates, List<CourseHomeDTO.ItemDTO> updates) {
        this(progress, candidates, updates, null);
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CandidateDTO(CourseHomeWelcomeCase welcomeCase, CourseHomeDTO.ItemDTO item, boolean actionable) {
    }
}
