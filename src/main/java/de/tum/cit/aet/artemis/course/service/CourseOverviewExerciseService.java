package de.tum.cit.aet.artemis.course.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.dto.CourseScoreContextDTO;
import de.tum.cit.aet.artemis.assessment.dto.CourseScoreSettingsDTO;
import de.tum.cit.aet.artemis.assessment.dto.ExerciseCourseScoreDTO;
import de.tum.cit.aet.artemis.assessment.dto.GradedPresentationConfigDTO;
import de.tum.cit.aet.artemis.assessment.dto.StudentCourseScoreInputDTO;
import de.tum.cit.aet.artemis.assessment.repository.GradingScaleRepository;
import de.tum.cit.aet.artemis.assessment.service.CourseScoreCalculator;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.dto.CourseExercisesForOverviewDTO;
import de.tum.cit.aet.artemis.course.dto.CourseScoresDTO;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.dto.CourseGradeScoreDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseCategoryDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseForCourseOverviewDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseOverviewDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseTeamAssignmentDTO;
import de.tum.cit.aet.artemis.exercise.dto.ParticipationOverviewDTO;
import de.tum.cit.aet.artemis.exercise.dto.ParticipationOverviewRowDTO;
import de.tum.cit.aet.artemis.exercise.dto.ParticipationResultDTO;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionOverviewDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.plagiarism.api.PlagiarismCaseApi;
import de.tum.cit.aet.artemis.plagiarism.api.dtos.PlagiarismCaseScoreDTO;

/**
 * Loads and assembles the exercise/statistics part of the course overview exclusively from database projections.
 *
 * The endpoint used to hydrate exercises, their concrete subtype columns, the course, participations, submissions,
 * results, and eager result associations before discarding most of that graph. This service keeps every query narrow,
 * makes the conditional presentation-score query explicit, and hands completed DTO inputs to the stateless
 * {@link CourseScoreCalculator}.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class CourseOverviewExerciseService {

    private final ExerciseRepository exerciseRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final TeamRepository teamRepository;

    private final GradingScaleRepository gradingScaleRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final Optional<PlagiarismCaseApi> plagiarismCaseApi;

    public CourseOverviewExerciseService(ExerciseRepository exerciseRepository, StudentParticipationRepository studentParticipationRepository, TeamRepository teamRepository,
            GradingScaleRepository gradingScaleRepository, AuthorizationCheckService authorizationCheckService, Optional<PlagiarismCaseApi> plagiarismCaseApi) {
        this.exerciseRepository = exerciseRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.teamRepository = teamRepository;
        this.gradingScaleRepository = gradingScaleRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.plagiarismCaseApi = plagiarismCaseApi;
    }

    /**
     * Returns the visible exercises, the requesting user's lean participation tree, and all derived course scores. All
     * database access stays in this orchestration layer; the calculator receives only already-loaded DTO input and never
     * fetches while evaluating it.
     *
     * @param course the already-authorized course
     * @param user   the requesting user
     * @return the projection-backed overview response
     */
    public CourseExercisesForOverviewDTO getCourseExercisesForOverview(Course course, User user) {
        long courseId = course.getId();
        long studentId = user.getId();
        ZonedDateTime calculationTime = ZonedDateTime.now();
        boolean includeUnreleased = authorizationCheckService.isAtLeastTeachingAssistantInCourse(course, user);
        boolean requireLtiLaunch = !includeUnreleased && course.isOnlineCourse();

        List<ExerciseForCourseOverviewDTO> exerciseDetails = exerciseRepository.findForCourseOverview(courseId, calculationTime, includeUnreleased, requireLtiLaunch,
                user.getLogin());
        Set<Long> exerciseIds = exerciseDetails.stream().map(ExerciseForCourseOverviewDTO::id).collect(Collectors.toSet());
        Set<Long> startedQuizExerciseIds = loadStartedQuizExerciseIds(exerciseDetails, studentId, calculationTime);

        Map<Long, Set<String>> categoriesByExercise = loadCategories(exerciseIds);
        Map<Long, Long> teamAssignmentByExercise = loadTeamAssignments(exerciseDetails, studentId);
        OverviewParticipations overviewParticipations = loadParticipations(exerciseDetails, studentId, calculationTime);
        List<CourseGradeScoreDTO> gradeScores = loadGradeScores(exerciseDetails, courseId, studentId);
        List<PlagiarismCaseScoreDTO> plagiarismCases = exerciseIds.isEmpty() ? List.of()
                : plagiarismCaseApi.map(api -> api.findScoreInformationByStudentIdAndExerciseIds(studentId, exerciseIds)).orElse(List.of());

        Set<ExerciseCourseScoreDTO> scoreExercises = exerciseDetails.stream().map(exercise -> exercise.toCourseScoreDTO(courseId)).collect(Collectors.toSet());
        CourseScoreSettingsDTO settings = CourseScoreSettingsDTO.from(course);
        GradedPresentationConfigDTO presentationConfig = exerciseIds.isEmpty() ? null : gradingScaleRepository.findPresentationConfigByCourseId(courseId).orElse(null);
        CourseScoreContextDTO totalContext = CourseScoreCalculator.createContext(settings, presentationConfig, scoreExercises, calculationTime);
        double gradedPresentationScoreSum = totalContext.usesGradedPresentations() ? studentParticipationRepository.sumPresentationScoreByStudentIdAndCourseId(courseId, studentId)
                : 0.0;
        StudentCourseScoreInputDTO studentInput = new StudentCourseScoreInputDTO(studentId, gradeScores, plagiarismCases, gradedPresentationScoreSum,
                overviewParticipations.basicPresentationScoreCount());

        CourseScoresDTO totalScores = calculateScores(totalContext, studentInput);
        Map<ExerciseType, CourseScoresDTO> scoresByType = calculateScoresByType(settings, scoreExercises, calculationTime, studentInput,
                overviewParticipations.basicPresentationScoreCountByType());
        Set<ParticipationResultDTO> participationResults = buildParticipationResults(overviewParticipations.rowsByParticipationId(), gradeScores);
        Map<Long, Double> achievedPointsPerVariantGroup = CourseScoreCalculator.calculateAchievedPointsPerVariantGroup(totalContext, studentInput);

        Set<ExerciseOverviewDTO> exercises = exerciseDetails.stream()
                .map(exercise -> exercise.toOverviewDTO(categoriesByExercise.getOrDefault(exercise.id(), Set.of()), teamAssignmentByExercise.get(exercise.id()),
                        overviewParticipations.participationsByExerciseId().getOrDefault(exercise.id(), Set.of()), calculationTime, startedQuizExerciseIds.contains(exercise.id())))
                .collect(Collectors.toSet());

        return new CourseExercisesForOverviewDTO(exercises, totalScores, scoresByType.get(ExerciseType.TEXT), scoresByType.get(ExerciseType.PROGRAMMING),
                scoresByType.get(ExerciseType.MODELING), scoresByType.get(ExerciseType.FILE_UPLOAD), scoresByType.get(ExerciseType.QUIZ), participationResults,
                achievedPointsPerVariantGroup);
    }

    private Map<Long, Set<String>> loadCategories(Set<Long> exerciseIds) {
        if (exerciseIds.isEmpty()) {
            return Map.of();
        }
        return exerciseRepository.findCategoriesForCourseOverview(exerciseIds).stream()
                .collect(Collectors.groupingBy(ExerciseCategoryDTO::exerciseId, Collectors.mapping(ExerciseCategoryDTO::category, Collectors.toSet())));
    }

    private Set<Long> loadStartedQuizExerciseIds(List<ExerciseForCourseOverviewDTO> exerciseDetails, long studentId, ZonedDateTime calculationTime) {
        Set<Long> quizExerciseIds = exerciseDetails.stream().filter(exercise -> exercise.type() == ExerciseType.QUIZ).map(ExerciseForCourseOverviewDTO::id)
                .collect(Collectors.toSet());
        return quizExerciseIds.isEmpty() ? Set.of() : exerciseRepository.findStartedQuizExerciseIdsForCourseOverview(quizExerciseIds, studentId, calculationTime);
    }

    private Map<Long, Long> loadTeamAssignments(List<ExerciseForCourseOverviewDTO> exerciseDetails, long studentId) {
        Set<Long> teamExerciseIds = exerciseDetails.stream().filter(exercise -> exercise.mode() == ExerciseMode.TEAM).map(ExerciseForCourseOverviewDTO::id)
                .collect(Collectors.toSet());
        if (teamExerciseIds.isEmpty()) {
            return Map.of();
        }
        return teamRepository.findAssignmentsForCourseOverview(teamExerciseIds, studentId).stream()
                .collect(Collectors.toMap(ExerciseTeamAssignmentDTO::exerciseId, ExerciseTeamAssignmentDTO::teamId));
    }

    private OverviewParticipations loadParticipations(List<ExerciseForCourseOverviewDTO> exerciseDetails, long studentId, ZonedDateTime calculationTime) {
        Set<Long> individualExerciseIds = exerciseDetails.stream().filter(exercise -> exercise.mode() == ExerciseMode.INDIVIDUAL).map(ExerciseForCourseOverviewDTO::id)
                .collect(Collectors.toSet());
        Set<Long> teamExerciseIds = exerciseDetails.stream().filter(exercise -> exercise.mode() == ExerciseMode.TEAM).map(ExerciseForCourseOverviewDTO::id)
                .collect(Collectors.toSet());

        List<ParticipationOverviewRowDTO> rows = new ArrayList<>();
        if (!individualExerciseIds.isEmpty()) {
            rows.addAll(studentParticipationRepository.findIndividualRowsForCourseOverview(studentId, individualExerciseIds, true));
        }
        if (!teamExerciseIds.isEmpty()) {
            rows.addAll(studentParticipationRepository.findTeamRowsForCourseOverview(studentId, teamExerciseIds));
        }

        Map<Long, ExerciseForCourseOverviewDTO> detailsById = exerciseDetails.stream().collect(Collectors.toMap(ExerciseForCourseOverviewDTO::id, Function.identity()));
        Map<Long, List<ParticipationOverviewRowDTO>> rowsByParticipationId = rows.stream().collect(Collectors.groupingBy(ParticipationOverviewRowDTO::participationId));
        Map<Long, Set<ParticipationOverviewDTO>> participationsByExerciseId = new HashMap<>();
        Map<ExerciseType, Long> basicPresentationScoreCountByType = new HashMap<>();
        long basicPresentationScoreCount = 0;
        for (List<ParticipationOverviewRowDTO> participationRows : rowsByParticipationId.values()) {
            ParticipationOverviewRowDTO firstRow = participationRows.getFirst();
            ExerciseForCourseOverviewDTO exercise = detailsById.get(firstRow.exerciseId());
            ParticipationOverviewDTO participation = assembleParticipation(participationRows, exercise, calculationTime);
            participationsByExerciseId.computeIfAbsent(firstRow.exerciseId(), ignored -> new HashSet<>()).add(participation);
            if (!firstRow.isTestRun() && firstRow.presentationScore() != null && firstRow.presentationScore() > 0.0) {
                basicPresentationScoreCount++;
                basicPresentationScoreCountByType.merge(exercise.type(), 1L, Long::sum);
            }
        }
        return new OverviewParticipations(participationsByExerciseId, rowsByParticipationId, basicPresentationScoreCount, basicPresentationScoreCountByType);
    }

    private List<CourseGradeScoreDTO> loadGradeScores(List<ExerciseForCourseOverviewDTO> exerciseDetails, long courseId, long studentId) {
        Set<Long> visibleExerciseIds = exerciseDetails.stream().map(ExerciseForCourseOverviewDTO::id).collect(Collectors.toSet());
        if (visibleExerciseIds.isEmpty()) {
            return List.of();
        }

        boolean hasIndividualQuiz = exerciseDetails.stream().anyMatch(exercise -> exercise.mode() == ExerciseMode.INDIVIDUAL && exercise.type() == ExerciseType.QUIZ);
        Set<Long> individualNonQuizExerciseIds = exerciseDetails.stream().filter(exercise -> exercise.mode() == ExerciseMode.INDIVIDUAL && exercise.type() != ExerciseType.QUIZ)
                .map(ExerciseForCourseOverviewDTO::id).collect(Collectors.toSet());
        Set<Long> teamExerciseIds = exerciseDetails.stream().filter(exercise -> exercise.mode() == ExerciseMode.TEAM).map(ExerciseForCourseOverviewDTO::id)
                .collect(Collectors.toSet());

        List<CourseGradeScoreDTO> gradeScores = new ArrayList<>();
        if (hasIndividualQuiz) {
            gradeScores.addAll(studentParticipationRepository.findIndividualQuizGradesByCourseIdAndStudentId(Set.of(courseId), studentId));
        }
        if (!individualNonQuizExerciseIds.isEmpty()) {
            gradeScores.addAll(studentParticipationRepository.findIndividualGradesForCourseOverview(individualNonQuizExerciseIds, studentId));
        }
        if (!teamExerciseIds.isEmpty()) {
            gradeScores.addAll(studentParticipationRepository.findTeamGradesForCourseOverview(teamExerciseIds, studentId));
        }
        return gradeScores.stream().filter(gradeScore -> visibleExerciseIds.contains(gradeScore.exerciseId())).toList();
    }

    private Map<ExerciseType, CourseScoresDTO> calculateScoresByType(CourseScoreSettingsDTO settings, Set<ExerciseCourseScoreDTO> exercises, ZonedDateTime calculationTime,
            StudentCourseScoreInputDTO studentInput, Map<ExerciseType, Long> basicPresentationScoreCountByType) {
        Map<ExerciseType, CourseScoresDTO> scoresByType = new HashMap<>();
        for (ExerciseType exerciseType : ExerciseType.values()) {
            Set<ExerciseCourseScoreDTO> exercisesOfType = exercises.stream().filter(exercise -> exercise.type() == exerciseType).collect(Collectors.toSet());
            CourseScoreContextDTO context = CourseScoreCalculator.createContext(settings, null, exercisesOfType, calculationTime);
            StudentCourseScoreInputDTO inputForType = studentInput.forExerciseType(basicPresentationScoreCountByType.getOrDefault(exerciseType, 0L));
            scoresByType.put(exerciseType, calculateScores(context, inputForType));
        }
        return scoresByType;
    }

    private CourseScoresDTO calculateScores(CourseScoreContextDTO context, StudentCourseScoreInputDTO studentInput) {
        return new CourseScoresDTO(context.maxAndReachablePoints().maxPoints(), context.maxAndReachablePoints().reachablePoints(),
                context.maxAndReachablePoints().reachablePresentationPoints(), CourseScoreCalculator.calculateCourseScoreForStudent(context, studentInput));
    }

    private ParticipationOverviewDTO assembleParticipation(List<ParticipationOverviewRowDTO> participationRows, ExerciseForCourseOverviewDTO exercise,
            ZonedDateTime calculationTime) {
        ParticipationOverviewRowDTO firstRow = participationRows.getFirst();
        Optional<ParticipationOverviewRowDTO> latestSubmissionRow = participationRows.stream().filter(row -> row.submissionId() != null)
                .max(CourseOverviewExerciseService::compareSubmissions);
        Set<SubmissionOverviewDTO> submissions = latestSubmissionRow.map(row -> {
            List<ParticipationOverviewRowDTO> submissionRows = participationRows.stream().filter(candidate -> row.submissionId().equals(candidate.submissionId())).toList();
            return visibleSubmission(row, submissionRows, exercise, calculationTime).map(Set::of).orElse(Set.of());
        }).orElse(Set.of());

        return new ParticipationOverviewDTO(firstRow.participationId(), firstRow.participationType(), firstRow.initializationState(), firstRow.initializationDate(),
                firstRow.testRun(), firstRow.individualDueDate(), firstRow.repositoryUri(), submissions);
    }

    private Optional<SubmissionOverviewDTO> visibleSubmission(ParticipationOverviewRowDTO submissionRow, List<ParticipationOverviewRowDTO> submissionRows,
            ExerciseForCourseOverviewDTO exercise, ZonedDateTime calculationTime) {
        ParticipationOverviewRowDTO latestResultRow = submissionRows.stream().filter(row -> row.resultId() != null)
                .max(Comparator.comparingLong(ParticipationOverviewRowDTO::resultId)).orElse(null);

        if (exercise.type() == ExerciseType.QUIZ) {
            boolean quizEnded = exercise.dueDate() != null && calculationTime.isAfter(exercise.dueDate());
            if (!quizEnded) {
                return Boolean.TRUE.equals(submissionRow.submitted()) ? Optional.ofNullable(submissionRow.toSubmissionOverviewDTO(List.of(), exercise.type())) : Optional.empty();
            }
            return latestResultRow != null && Boolean.TRUE.equals(latestResultRow.resultRated()) && latestResultRow.resultCompletionDate() != null
                    ? Optional.ofNullable(submissionRow.toSubmissionOverviewDTO(List.of(latestResultRow.toResultOverviewDTO()), exercise.type()))
                    : Optional.empty();
        }

        if (exercise.type() == ExerciseType.PROGRAMMING) {
            if (latestResultRow == null) {
                ZonedDateTime dueDate = submissionRow.individualDueDate() != null ? submissionRow.individualDueDate() : exercise.dueDate();
                // The same grace period the grade projections allow, so a submission that counts towards the score is
                // never hidden from the card that is supposed to show it
                ZonedDateTime latestAccepted = dueDate == null ? null : dueDate.plusSeconds(Constants.PROGRAMMING_GRACE_PERIOD_SECONDS);
                boolean submittedInTime = latestAccepted == null || submissionRow.submissionDate() != null && !submissionRow.submissionDate().isAfter(latestAccepted);
                return submittedInTime ? Optional.ofNullable(submissionRow.toSubmissionOverviewDTO(List.of(), exercise.type())) : Optional.empty();
            }
            if (!Boolean.TRUE.equals(latestResultRow.resultRated())) {
                return Optional.empty();
            }
            if (!isManual(latestResultRow.resultAssessmentType()) || isAssessmentDone(exercise, calculationTime) && latestResultRow.resultCompletionDate() != null) {
                return Optional.ofNullable(submissionRow.toSubmissionOverviewDTO(List.of(latestResultRow.toResultOverviewDTO()), exercise.type()));
            }
            ParticipationOverviewRowDTO latestAutomaticResult = submissionRows.stream().filter(CourseOverviewExerciseService::isAutomatic)
                    .max(Comparator.comparingLong(ParticipationOverviewRowDTO::resultId)).orElse(null);
            return latestAutomaticResult == null ? Optional.empty()
                    : Optional.ofNullable(submissionRow.toSubmissionOverviewDTO(List.of(latestAutomaticResult.toResultOverviewDTO()), exercise.type()));
        }

        if (latestResultRow == null || !Boolean.TRUE.equals(latestResultRow.resultRated()) || !isAssessmentDone(exercise, calculationTime)) {
            return Optional.empty();
        }
        return Optional.ofNullable(submissionRow.toSubmissionOverviewDTO(List.of(latestResultRow.toResultOverviewDTO()), exercise.type()));
    }

    private Set<ParticipationResultDTO> buildParticipationResults(Map<Long, List<ParticipationOverviewRowDTO>> rowsByParticipationId, Collection<CourseGradeScoreDTO> gradeScores) {
        Map<Long, CourseGradeScoreDTO> gradeScoreByParticipation = gradeScores.stream()
                .collect(Collectors.toMap(CourseGradeScoreDTO::participationId, Function.identity(), (first, ignored) -> first));
        Set<ParticipationResultDTO> participationResults = new HashSet<>();
        for (Map.Entry<Long, List<ParticipationOverviewRowDTO>> entry : rowsByParticipationId.entrySet()) {
            if (entry.getValue().getFirst().isTestRun()) {
                continue;
            }
            CourseGradeScoreDTO gradeScore = gradeScoreByParticipation.get(entry.getKey());
            participationResults.add(gradeScore == null ? new ParticipationResultDTO(0.0, false, entry.getKey())
                    : new ParticipationResultDTO(gradeScore.score(), gradeScore.rated(), entry.getKey()));
        }
        return participationResults;
    }

    private static int compareSubmissions(ParticipationOverviewRowDTO first, ParticipationOverviewRowDTO second) {
        if (first.submissionDate() == null || second.submissionDate() == null || first.submissionDate().equals(second.submissionDate())) {
            return first.submissionId().compareTo(second.submissionId());
        }
        return first.submissionDate().compareTo(second.submissionDate());
    }

    private static boolean isManual(@Nullable AssessmentType assessmentType) {
        return assessmentType == AssessmentType.MANUAL || assessmentType == AssessmentType.SEMI_AUTOMATIC;
    }

    private static boolean isAutomatic(ParticipationOverviewRowDTO row) {
        return row.resultId() != null && (row.resultAssessmentType() == AssessmentType.AUTOMATIC || row.resultAssessmentType() == AssessmentType.AUTOMATIC_ATHENA);
    }

    private static boolean isAssessmentDone(ExerciseForCourseOverviewDTO exercise, ZonedDateTime calculationTime) {
        return exercise.assessmentDueDate() == null || calculationTime.isAfter(exercise.assessmentDueDate());
    }

    private record OverviewParticipations(Map<Long, Set<ParticipationOverviewDTO>> participationsByExerciseId, Map<Long, List<ParticipationOverviewRowDTO>> rowsByParticipationId,
            long basicPresentationScoreCount, Map<ExerciseType, Long> basicPresentationScoreCountByType) {
    }
}
