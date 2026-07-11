package de.tum.cit.aet.artemis.exam.dto;

import java.time.ZonedDateTime;
import java.util.List;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.TutorParticipation;
import de.tum.cit.aet.artemis.core.dto.DueDateStat;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorParticipationStatus;

/**
 * Response DTO for the exam assessment dashboard, returned by both
 * {@link de.tum.cit.aet.artemis.exam.web.ExamResource#getExamForAssessmentDashboard} (the tutor dashboard) and
 * {@link de.tum.cit.aet.artemis.exam.web.ExamResource#getExamForTestRunAssessmentDashboard} (the instructor test-run
 * dashboard). It is deliberately <em>not</em> the detailed-editor {@link ExamWithExerciseGroupsDTO}: the assessment
 * dashboard carries the per-exercise <em>transient assessment statistics</em> that
 * {@code AssessmentDashboardService.generateStatisticsForExercisesForAssessmentDashboard} attaches to each exercise
 * (submission / assessment counts, complaint & feedback counts, ratings, the tutor's participation status), which the
 * editor DTO neither computes nor serializes.
 * <p>
 * The client ({@code assessment-dashboard.component} + {@code tutor-participation-graph} + {@code tutor-leaderboard} +
 * {@code exam-start}/not-released tags) reads, off this response:
 * <ul>
 * <li>exam: {@code title}, {@code endDate}, {@code publishResultsDate}, {@code numberOfCorrectionRoundsInExam},
 * {@code id} (tutor-leaderboard complaint links) and {@code course} — the latter fed through {@code Course.from} then
 * {@code accountService.setAccessRightsForCourse}, which needs the three group names, plus {@code complaintsEnabled} /
 * {@code requestMoreFeedbackEnabled} (bound into the dashboard-information sub-component);</li>
 * <li>each exercise: {@code type} (icon), {@code title}, {@code releaseDate} (not-released tag),
 * {@code includedInOverallScore} (hide-optional filter), {@code teamMode} (Teams button),
 * {@code allowComplaintsForAutomaticAssessments} (unfinished filter), {@code secondCorrectionEnabled} (toggle button),
 * {@code averageRating} / {@code numberOfRatings} (rating cell), the submission / assessment {@link DueDateStat}s and the
 * four complaint / feedback counts (tutor-participation graph) and {@code tutorParticipations[0].status} (tutor status
 * column).</li>
 * </ul>
 * <p>
 * The two endpoints share one shape and one factory. The test-run endpoint does not attach the statistics (its client
 * screen renders in {@code isTestRun} mode, which hides the tutor-status column, the second-correction toggle and the
 * complaint graph), so those transient getters return {@code null}/absent there and {@code NON_EMPTY} drops them — the
 * same wire the entity produced. The tutorial-training example submissions the graph would otherwise read are omitted:
 * they are a course-tutorial concept, unset for exam exercises, and lazily uninitialised on this fetch (so the entity
 * never serialized them here either).
 *
 * @param id                             the id of the exam (tutor-leaderboard complaint links read it)
 * @param title                          the exam title
 * @param endDate                        the exam end date (shown in the dashboard header)
 * @param publishResultsDate             the publish-results date (shown in the dashboard header)
 * @param numberOfCorrectionRoundsInExam the number of correction rounds (gates the second-correction hint / toggle)
 * @param course                         the slim course projection used for access rights + complaint/feedback flags
 * @param exerciseGroups                 the exercise groups with their (interesting) exercises and assessment stats
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamForAssessmentDashboardDTO(long id, @Nullable String title, @Nullable ZonedDateTime endDate, @Nullable ZonedDateTime publishResultsDate,
        @Nullable Integer numberOfCorrectionRoundsInExam, @Nullable CourseForAssessmentDashboardDTO course, @Nullable List<ExerciseGroupForAssessmentDashboardDTO> exerciseGroups) {

    /**
     * Builds the assessment-dashboard response from an exam whose exercise groups and (interesting) exercises are loaded,
     * with the per-exercise assessment statistics attached (tutor dashboard) or absent (test-run dashboard).
     *
     * @param exam the exam (groups + exercises, optionally with transient assessment stats)
     * @return the assessment-dashboard DTO
     */
    public static ExamForAssessmentDashboardDTO of(Exam exam) {
        var groups = exam.getExerciseGroups();
        List<ExerciseGroupForAssessmentDashboardDTO> groupDTOs = Hibernate.isInitialized(groups) && groups != null
                ? groups.stream().map(ExerciseGroupForAssessmentDashboardDTO::of).toList()
                : null;
        return new ExamForAssessmentDashboardDTO(exam.getId(), exam.getTitle(), exam.getEndDate(), exam.getPublishResultsDate(), exam.getNumberOfCorrectionRoundsInExam(),
                CourseForAssessmentDashboardDTO.of(exam.getCourse()), groupDTOs);
    }

    /**
     * Slim course projection for the assessment dashboard: the three group names the client turns into
     * {@code isAtLeast{Tutor,Editor,Instructor}} via {@code accountService.setAccessRightsForCourse} (gating the scores /
     * grading / plagiarism / test-run action buttons and the exam-assessment buttons), plus the id and the two feature
     * flags the dashboard-information sub-component binds.
     *
     * @param id                         the id of the course
     * @param instructorGroupName        the instructor group name (client-side instructor access)
     * @param editorGroupName            the editor group name (client-side editor access)
     * @param teachingAssistantGroupName the teaching-assistant group name (client-side tutor access)
     * @param complaintsEnabled          whether complaints are enabled (dashboard-information binding)
     * @param requestMoreFeedbackEnabled whether more-feedback requests are enabled (dashboard-information binding)
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CourseForAssessmentDashboardDTO(long id, @Nullable String instructorGroupName, @Nullable String editorGroupName, @Nullable String teachingAssistantGroupName,
            boolean complaintsEnabled, boolean requestMoreFeedbackEnabled) {

        @Nullable
        static CourseForAssessmentDashboardDTO of(@Nullable Course course) {
            if (course == null) {
                return null;
            }
            return new CourseForAssessmentDashboardDTO(course.getId(), course.getInstructorGroupName(), course.getEditorGroupName(), course.getTeachingAssistantGroupName(),
                    course.getComplaintsEnabled(), course.getRequestMoreFeedbackEnabled());
        }
    }

    /**
     * An exercise group with its (interesting) exercises. The client iterates the groups to collect their exercises and
     * re-attaches each group to its exercises client-side, so only {@code id} and {@code title} are read off the group.
     *
     * @param id        the id of the exercise group
     * @param title     the title of the exercise group
     * @param exercises the interesting exercises of the group, each with its assessment statistics
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExerciseGroupForAssessmentDashboardDTO(long id, @Nullable String title, @Nullable List<ExerciseForAssessmentDashboardDTO> exercises) {

        static ExerciseGroupForAssessmentDashboardDTO of(ExerciseGroup group) {
            var exercises = group.getExercises();
            List<ExerciseForAssessmentDashboardDTO> exerciseDTOs = Hibernate.isInitialized(exercises) && exercises != null
                    ? exercises.stream().map(ExerciseForAssessmentDashboardDTO::of).toList()
                    : null;
            return new ExerciseGroupForAssessmentDashboardDTO(group.getId(), group.getTitle(), exerciseDTOs);
        }
    }

    /**
     * A single exercise row on the assessment dashboard, carrying the base scalars the table renders plus the transient
     * assessment statistics attached by the dashboard service (all {@code null}/absent on the test-run dashboard, which
     * does not compute them). {@code type} is the {@link ExerciseType} discriminator string the icon column reads; no
     * type-specific (programming / quiz / …) fields are needed here — the dashboard renders none.
     *
     * @param id                                     the id of the exercise
     * @param type                                   the exercise type discriminator (icon column)
     * @param title                                  the exercise title
     * @param releaseDate                            the release date (not-released tag)
     * @param includedInOverallScore                 whether the exercise counts towards the overall score (hide-optional)
     * @param teamMode                               whether the exercise is a team exercise (Teams button)
     * @param allowComplaintsForAutomaticAssessments whether complaints are allowed for automatic assessments (unfinished filter)
     * @param secondCorrectionEnabled                whether the second correction round is enabled (toggle button)
     * @param averageRating                          the average tutor-facing rating (transient; rating cell)
     * @param numberOfRatings                        the number of ratings (transient; rating cell)
     * @param numberOfSubmissions                    the submission count split (transient; graph)
     * @param totalNumberOfAssessments               the total assessment count split (transient; graph)
     * @param numberOfAssessmentsOfCorrectionRounds  the per-correction-round assessment counts (transient; graph)
     * @param numberOfComplaints                     the number of complaints (transient; graph)
     * @param numberOfOpenComplaints                 the number of open complaints (transient; graph)
     * @param numberOfMoreFeedbackRequests           the number of more-feedback requests (transient; graph)
     * @param numberOfOpenMoreFeedbackRequests       the number of open more-feedback requests (transient; graph)
     * @param tutorParticipations                    the current tutor's participation (id + status; tutor-status column)
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExerciseForAssessmentDashboardDTO(long id, ExerciseType type, @Nullable String title, @Nullable ZonedDateTime releaseDate,
            @Nullable IncludedInOverallScore includedInOverallScore, boolean teamMode, boolean allowComplaintsForAutomaticAssessments, boolean secondCorrectionEnabled,
            @Nullable Double averageRating, @Nullable Long numberOfRatings, @Nullable DueDateStat numberOfSubmissions, @Nullable DueDateStat totalNumberOfAssessments,
            @Nullable List<DueDateStat> numberOfAssessmentsOfCorrectionRounds, @Nullable Long numberOfComplaints, @Nullable Long numberOfOpenComplaints,
            @Nullable Long numberOfMoreFeedbackRequests, @Nullable Long numberOfOpenMoreFeedbackRequests,
            @Nullable List<TutorParticipationForAssessmentDashboardDTO> tutorParticipations) {

        static ExerciseForAssessmentDashboardDTO of(Exercise exercise) {
            DueDateStat[] correctionRounds = exercise.getNumberOfAssessmentsOfCorrectionRounds();
            List<DueDateStat> correctionRoundsList = correctionRounds != null ? List.of(correctionRounds) : null;
            var tutorParticipations = exercise.getTutorParticipations();
            List<TutorParticipationForAssessmentDashboardDTO> tutorParticipationDTOs = Hibernate.isInitialized(tutorParticipations) && tutorParticipations != null
                    && !tutorParticipations.isEmpty() ? tutorParticipations.stream().map(TutorParticipationForAssessmentDashboardDTO::of).toList() : null;
            return new ExerciseForAssessmentDashboardDTO(exercise.getId(), exercise.getExerciseType(), exercise.getTitle(), exercise.getReleaseDate(),
                    exercise.getIncludedInOverallScore(), exercise.isTeamMode(), exercise.getAllowComplaintsForAutomaticAssessments(), exercise.getSecondCorrectionEnabled(),
                    exercise.getAverageRating(), exercise.getNumberOfRatings(), exercise.getNumberOfSubmissions(), exercise.getTotalNumberOfAssessments(), correctionRoundsList,
                    exercise.getNumberOfComplaints(), exercise.getNumberOfOpenComplaints(), exercise.getNumberOfMoreFeedbackRequests(),
                    exercise.getNumberOfOpenMoreFeedbackRequests(), tutorParticipationDTOs);
        }
    }

    /**
     * The current tutor's participation for an exercise on the assessment dashboard. The tutor-participation graph reads
     * only {@code status} (the tutorial-training example submissions the graph would otherwise read are omitted — they
     * are unset for exam exercises and lazily uninitialised on this fetch).
     *
     * @param id     the id of the tutor participation ({@code null} for the synthetic not-participated placeholder)
     * @param status the tutor participation status (defaults to {@code NOT_PARTICIPATED} when the tutor has not started)
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TutorParticipationForAssessmentDashboardDTO(@Nullable Long id, @Nullable TutorParticipationStatus status) {

        static TutorParticipationForAssessmentDashboardDTO of(TutorParticipation tutorParticipation) {
            return new TutorParticipationForAssessmentDashboardDTO(tutorParticipation.getId(), tutorParticipation.getStatus());
        }
    }
}
