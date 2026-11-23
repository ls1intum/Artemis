package de.tum.cit.aet.artemis.course.dto.home;

import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.dto.CourseHomeQuestionsDTO;
import de.tum.cit.aet.artemis.course.domain.CourseHomeSectionStatus;
import de.tum.cit.aet.artemis.course.dto.CourseAvailableTabsDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisCourseHomeSessionsDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseHomeDTO(Instant asOf, VisitDTO visit, CourseInfoDTO course, CourseAvailableTabsDTO capabilities, ProgressDTO progress, CourseHomeWelcomeDTO welcome,
        @Nullable ItemDTO nextStep, List<ItemDTO> updates, @Nullable ItemDTO announcement, List<ItemDTO> faqs, CourseHomeTeachingTeamDTO teachingTeam,
        CourseHomeQuestionsDTO questions, IrisCourseHomeSessionsDTO irisSessions, java.util.Map<String, CourseHomeSectionStatus> sections, @Nullable Instant nextRefreshAt) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record VisitDTO(boolean firstVisit, @Nullable Instant previousVisitAt, Instant snapshotAt, long variantCursor, Instant newSince) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CourseInfoDTO(long id, String title, @Nullable String description, @Nullable String language, @Nullable Instant startDate, @Nullable Instant endDate,
            @Nullable String studentWelcomeContext) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ProgressDTO(long releasedExercises, long submittedExercises, long currentlyDueExercises, long currentlyDueSubmitted) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ItemDTO(String title, CourseHomeTargetDTO target, @Nullable Instant updatedAt, @Nullable Instant dueAt, String reason) {
    }
}
