package de.tum.cit.aet.artemis.course.dto;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.assessment.domain.TutorParticipation;
import de.tum.cit.aet.artemis.assessment.domain.TutorParticipationStatus;
import de.tum.cit.aet.artemis.core.dto.DueDateStat;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;

/** Course and derived exercise statistics displayed on the tutor assessment dashboard. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseAssessmentDashboardDTO(@JsonUnwrapped CourseManagementDTO course, Set<AssessmentExerciseDTO> exercises) {

    /** Maps the course after assessment statistics have been generated. */
    public static CourseAssessmentDashboardDTO of(Course course) {
        return new CourseAssessmentDashboardDTO(CourseManagementDTO.of(course), course.getExercises().stream().map(AssessmentExerciseDTO::of).collect(Collectors.toSet()));
    }

    /** Exercise statistics needed by the assessment dashboard. */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record AssessmentExerciseDTO(long id, String type, String title, @Nullable ZonedDateTime dueDate, @Nullable ZonedDateTime assessmentDueDate,
            IncludedInOverallScore includedInOverallScore, boolean allowComplaintsForAutomaticAssessments, boolean secondCorrectionEnabled,
            @Nullable DueDateStat numberOfSubmissions, @Nullable DueDateStat totalNumberOfAssessments, DueDateStat[] numberOfAssessmentsOfCorrectionRounds,
            @Nullable Long numberOfComplaints, @Nullable Long numberOfOpenComplaints, @Nullable Long numberOfMoreFeedbackRequests, @Nullable Long numberOfOpenMoreFeedbackRequests,
            @Nullable Double averageRating, @Nullable Long numberOfRatings, Set<TutorParticipationDTO> tutorParticipations) {

        private static AssessmentExerciseDTO of(Exercise exercise) {
            DueDateStat[] correctionRounds = exercise.getNumberOfAssessmentsOfCorrectionRounds();
            return new AssessmentExerciseDTO(exercise.getId(), exercise.getType(), exercise.getTitle(), exercise.getDueDate(), exercise.getAssessmentDueDate(),
                    exercise.getIncludedInOverallScore(), exercise.getAllowComplaintsForAutomaticAssessments(), exercise.getSecondCorrectionEnabled(),
                    exercise.getNumberOfSubmissions(), exercise.getTotalNumberOfAssessments(),
                    correctionRounds == null ? new DueDateStat[0] : Arrays.copyOf(correctionRounds, correctionRounds.length), exercise.getNumberOfComplaints(),
                    exercise.getNumberOfOpenComplaints(), exercise.getNumberOfMoreFeedbackRequests(), exercise.getNumberOfOpenMoreFeedbackRequests(), exercise.getAverageRating(),
                    exercise.getNumberOfRatings(), exercise.getTutorParticipations().stream().map(TutorParticipationDTO::of).collect(Collectors.toSet()));
        }
    }

    /** Tutor participation state attached to one assessed exercise. */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TutorParticipationDTO(long id, @Nullable Long tutorId, TutorParticipationStatus status) {

        private static TutorParticipationDTO of(TutorParticipation participation) {
            return new TutorParticipationDTO(participation.getId(), participation.getTutor() == null ? null : participation.getTutor().getId(), participation.getStatus());
        }
    }
}
