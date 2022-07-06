package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.service.util.RoundingUtil.roundScoreSpecifiedByCourseSettings;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.enumeration.GraphType;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.domain.enumeration.SpanType;
import de.tum.in.www1.artemis.domain.enumeration.StatisticsView;
import de.tum.in.www1.artemis.domain.statistics.CourseStatisticsAverageScore;
import de.tum.in.www1.artemis.domain.statistics.ScoreDistribution;
import de.tum.in.www1.artemis.domain.statistics.StatisticsEntry;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.web.rest.dto.CourseManagementStatisticsDTO;
import de.tum.in.www1.artemis.web.rest.dto.ExerciseManagementStatisticsDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class StatisticsService {

    private final StatisticsRepository statisticsRepository;

    private final ParticipantScoreRepository participantScoreRepository;

    private final CourseRepository courseRepository;

    private final ExerciseRepository exerciseRepository;

    private final UserRepository userRepository;

    private final TeamRepository teamRepository;

    public StatisticsService(StatisticsRepository statisticsRepository, ParticipantScoreRepository participantScoreRepository, CourseRepository courseRepository,
            ExerciseRepository exerciseRepository, UserRepository userRepository, TeamRepository teamRepository) {
        this.statisticsRepository = statisticsRepository;
        this.participantScoreRepository = participantScoreRepository;
        this.courseRepository = courseRepository;
        this.exerciseRepository = exerciseRepository;
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
    }

    /**
     * Forwards the request to the repository, which returns a List<Map<String, Object>>. For week, month or year the map from the Repository contains a String with the column name,
     * "day" and "amount" and an Object being the value, either the date in the format "YYYY-MM-DD" or the amount of the findings. For day, the column names are "day" and "amount",
     * which then contains the date in the ZonedDateFormat as Integer and the amount as Long
     * It then collects the amounts in an array, depending on the span value, and returns it
     *
     * @param span        DAY,WEEK,MONTH or YEAR depending on the active tab in the view
     * @param periodIndex an index indicating which time period, 0 is current week, -1 is one week in the past, -2 is two weeks in the past ...
     * @param graphType   the type of graph the data should be fetched
     * @param view        the view in which the data will be displayed (Artemis, Course, Exercise)
     * @param entityId    the entityId. Only set if we fetch value for the course statistics
     * @return an array, containing the values for each bar in the graph
     */
    public List<Integer> getChartData(SpanType span, Integer periodIndex, GraphType graphType, StatisticsView view, @Nullable Long entityId) {
        ZonedDateTime startDate;
        ZonedDateTime endDate;
        List<StatisticsEntry> outcome;
        List<Integer> result = null;
        ZonedDateTime now = ZonedDateTime.now();
        int lengthOfMonth;
        if (span != SpanType.MONTH) {
            result = new ArrayList<>(Collections.nCopies(spanMap.get(span), 0));
        }
        switch (span) {
            case DAY -> {
                startDate = now.minusDays(-periodIndex).withHour(0).withMinute(0).withSecond(0).withNano(0);
                endDate = now.minusDays(-periodIndex).withHour(23).withMinute(59).withSecond(59);
                outcome = this.statisticsRepository.getNumberOfEntriesPerTimeSlot(graphType, span, startDate, endDate, view, entityId);
                this.statisticsRepository.sortDataIntoHours(outcome, result);
            }
            case WEEK -> {
                startDate = now.minusWeeks(-periodIndex).minusDays(6).withHour(0).withMinute(0).withSecond(0).withNano(0);
                endDate = now.minusWeeks(-periodIndex).withHour(23).withMinute(59).withSecond(59);
                outcome = this.statisticsRepository.getNumberOfEntriesPerTimeSlot(graphType, span, startDate, endDate, view, entityId);
                this.statisticsRepository.sortDataIntoDays(outcome, result, startDate);
            }
            case MONTH -> {
                startDate = now.minusMonths(1L - periodIndex).withHour(0).withMinute(0).withSecond(0).withNano(0);
                endDate = now.minusMonths(-periodIndex).withHour(23).withMinute(59).withSecond(59);
                result = new ArrayList<>(Collections.nCopies((int) ChronoUnit.DAYS.between(startDate, endDate), 0));
                outcome = this.statisticsRepository.getNumberOfEntriesPerTimeSlot(graphType, span, startDate.plusDays(1), endDate, view, entityId);
                this.statisticsRepository.sortDataIntoDays(outcome, result, startDate.plusDays(1));
            }
            case QUARTER -> {
                LocalDateTime localStartDate = now.toLocalDateTime().with(DayOfWeek.MONDAY);
                LocalDateTime localEndDate = now.toLocalDateTime().with(DayOfWeek.SUNDAY);
                ZoneId zone = now.getZone();
                startDate = localStartDate.atZone(zone).minusWeeks(11 + (12L * (-periodIndex))).withHour(0).withMinute(0).withSecond(0).withNano(0);
                endDate = periodIndex != 0 ? localEndDate.atZone(zone).minusWeeks(12L * (-periodIndex)).withHour(23).withMinute(59).withSecond(59)
                        : localEndDate.atZone(zone).withHour(23).withMinute(59).withSecond(59);
                outcome = this.statisticsRepository.getNumberOfEntriesPerTimeSlot(graphType, span, startDate, endDate, view, entityId);
                this.statisticsRepository.sortDataIntoWeeks(outcome, result, startDate);
            }
            case YEAR -> {
                startDate = now.minusYears(1L - periodIndex).plusMonths(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
                lengthOfMonth = YearMonth.of(now.minusYears(-periodIndex).getYear(), now.minusYears(-periodIndex).getMonth()).lengthOfMonth();
                endDate = now.minusYears(-periodIndex).withDayOfMonth(lengthOfMonth).withHour(23).withMinute(59).withSecond(59);
                outcome = this.statisticsRepository.getNumberOfEntriesPerTimeSlot(graphType, span, startDate, endDate, view, entityId);
                this.statisticsRepository.sortDataIntoMonths(outcome, result, startDate);
            }
        }
        return result;
    }

    /**
     * A map to manage the spanTypes and the corresponding array length of the result
     */
    private static final Map<SpanType, Integer> spanMap = Map.of(SpanType.DAY, 24, SpanType.WEEK, 7, SpanType.QUARTER, 12, SpanType.YEAR, 12);

    /**
     * Get the average score of the course and all exercises for the course statistics
     *
     * @param courseId the id of the course for which the data should be fetched
     * @return a custom CourseManagementStatisticsDTO, which contains the relevant data
     */
    public CourseManagementStatisticsDTO getCourseStatistics(Long courseId) {

        var courseManagementStatisticsDTO = new CourseManagementStatisticsDTO();
        Set<Exercise> exercises = exerciseRepository.findByCourseIdWithCategories(courseId);

        if (exercises.isEmpty()) {
            // Handle newly created courses that have no exercises
            courseManagementStatisticsDTO.setAverageScoreOfCourse(0.0);
            courseManagementStatisticsDTO.setAverageScoresOfExercises(Collections.emptyList());
            return courseManagementStatisticsDTO;
        }

        Course course = exercises.stream().findFirst().orElseThrow().getCourseViaExerciseGroupOrCourseMember();
        var includedExercises = exercises.stream().filter(Exercise::isCourseExercise)
                .filter(exercise -> !exercise.getIncludedInOverallScore().equals(IncludedInOverallScore.NOT_INCLUDED)).collect(Collectors.toSet());
        Double averageScoreForCourse = participantScoreRepository.findAvgScore(includedExercises);
        List<CourseStatisticsAverageScore> averageScoreForExercises = statisticsRepository.findAvgPointsForExercises(includedExercises);
        sortAfterReleaseDate(averageScoreForExercises);
        averageScoreForExercises.forEach(exercise -> {
            var roundedAverageScore = roundScoreSpecifiedByCourseSettings(exercise.getAverageScore(), course);
            exercise.setAverageScore(roundedAverageScore);
            var fittingExercise = includedExercises.stream().filter(includedExercise -> includedExercise.getId() == exercise.getExerciseId()).findAny().orElseThrow();
            exercise.setExerciseType(fittingExercise.getExerciseType());
            exercise.setCategories(fittingExercise.getCategories());
        });

        if (averageScoreForCourse != null && averageScoreForCourse > 0) {
            courseManagementStatisticsDTO.setAverageScoreOfCourse(roundScoreSpecifiedByCourseSettings(averageScoreForCourse, course));
        }
        else {
            courseManagementStatisticsDTO.setAverageScoreOfCourse(0.0);
        }

        if (!averageScoreForExercises.isEmpty()) {
            courseManagementStatisticsDTO.setAverageScoresOfExercises(averageScoreForExercises);
        }
        else {
            courseManagementStatisticsDTO.setAverageScoresOfExercises(Collections.emptyList());
        }

        return courseManagementStatisticsDTO;
    }

    /**
     * Get statistics regarding a specific exercise
     *
     * @param exercise the exercise for which the data should be fetched
     * @return a custom ExerciseManagementStatisticsDTO, which contains the relevant data
     */
    public ExerciseManagementStatisticsDTO getExerciseStatistics(Exercise exercise) throws EntityNotFoundException {
        var course = courseRepository.findByIdElseThrow(exercise.getCourseViaExerciseGroupOrCourseMember().getId());
        var exerciseManagementStatisticsDTO = new ExerciseManagementStatisticsDTO();

        // number of students or teams and number of participations of students or teams
        long numberOfParticipationsOfStudentsOrTeams;
        long numberOfStudentsOrTeams;
        if (exercise.isTeamMode()) {
            Long teamParticipations = exerciseRepository.getTeamParticipationCountById(exercise.getId());
            numberOfParticipationsOfStudentsOrTeams = teamParticipations == null ? 0L : teamParticipations;

            numberOfStudentsOrTeams = teamRepository.getNumberOfTeamsForExercise(exercise.getId());
        }
        else {
            Long studentParticipations = exerciseRepository.getStudentParticipationCountById(exercise.getId());
            numberOfParticipationsOfStudentsOrTeams = studentParticipations == null ? 0L : studentParticipations;

            numberOfStudentsOrTeams = userRepository.countUserInGroup(course.getStudentGroupName());
        }
        exerciseManagementStatisticsDTO.setNumberOfParticipations(numberOfParticipationsOfStudentsOrTeams);
        exerciseManagementStatisticsDTO.setNumberOfStudentsOrTeamsInCourse(Objects.requireNonNullElse(numberOfStudentsOrTeams, 0L));

        // post stats
        long numberOfExercisePosts = statisticsRepository.getNumberOfExercisePosts(exercise.getId());
        exerciseManagementStatisticsDTO.setNumberOfPosts(numberOfExercisePosts);
        long resolvedExercisePosts = statisticsRepository.getNumberOfResolvedExercisePosts(exercise.getId());
        exerciseManagementStatisticsDTO.setNumberOfResolvedPosts(resolvedExercisePosts);

        // average score & max points
        Double maxPoints = exercise.getMaxPoints();
        exerciseManagementStatisticsDTO.setMaxPointsOfExercise(Objects.requireNonNullElse(maxPoints, 0D));
        Double averageScore = participantScoreRepository.findAvgScore(Set.of(exercise));
        double averageScoreForExercise = averageScore != null ? roundScoreSpecifiedByCourseSettings(averageScore, course) : 0.0;
        exerciseManagementStatisticsDTO.setAverageScoreOfExercise(averageScoreForExercise);
        List<ScoreDistribution> scores = participantScoreRepository.getScoreDistributionForExercise(exercise.getId());
        var scoreDistribution = new int[10];
        Arrays.fill(scoreDistribution, 0);

        scores.forEach(score -> {
            var index = (int) (score.getScore() / 10.0);
            if (index >= 10) {
                scoreDistribution[9] += 1;
            }
            else {
                scoreDistribution[index] += 1;
            }
        });

        exerciseManagementStatisticsDTO.setScoreDistribution(scoreDistribution);
        exerciseManagementStatisticsDTO.setNumberOfExerciseScores(scores.size());

        return exerciseManagementStatisticsDTO;
    }

    /**
     * Sorting averageScores for release dates
     *
     * @param exercises the exercises which we want to sort
     */
    private void sortAfterReleaseDate(List<CourseStatisticsAverageScore> exercises) {
        exercises.sort((exerciseA, exerciseB) -> {
            var releaseDateA = exerciseA.getReleaseDate();
            var releaseDateB = exerciseB.getReleaseDate();
            if (releaseDateA == null || releaseDateB == null || releaseDateA.isEqual(releaseDateB)) {
                return 0;
            }
            else {
                // Sort the one with the earlier release date first
                return releaseDateA.isBefore(releaseDateB) ? -1 : 1;
            }
        });
    }
}
