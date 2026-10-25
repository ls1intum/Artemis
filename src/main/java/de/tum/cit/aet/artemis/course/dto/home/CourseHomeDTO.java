package de.tum.cit.aet.artemis.course.dto.home;

import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.dto.CourseHomeQuestionsDTO;
import de.tum.cit.aet.artemis.course.dto.CourseAvailableTabsDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisCourseHomeSessionsDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseHomeDTO(Instant asOf, Visit visit, CourseInfo course, CourseAvailableTabsDTO capabilities, Progress progress, CourseHomeWelcomeDTO welcome,
        @Nullable Item nextStep, List<Item> updates, @Nullable Item announcement, List<Item> faqs, CourseHomeTeachingTeamDTO teachingTeam, CourseHomeQuestionsDTO questions,
        IrisCourseHomeSessionsDTO irisSessions) {

    public enum SectionStatus {
        AVAILABLE, DISABLED, UNAVAILABLE
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Visit(boolean firstVisit, @Nullable Instant previousVisitAt, Instant snapshotAt, long variantCursor, Instant newSince) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CourseInfo(long id, String title, @Nullable String description, @Nullable String language, @Nullable Instant startDate, @Nullable Instant endDate,
            @Nullable String studentWelcomeContext) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Progress(long releasedExercises, long submittedExercises, long currentlyDueExercises, long currentlyDueSubmitted) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Item(String title, CourseHomeTargetDTO target, @Nullable Instant updatedAt, @Nullable Instant dueAt, String reason) {
    }
}
