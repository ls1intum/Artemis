package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.service.util.RoundingUtil.round;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.enumeration.GraphType;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.domain.enumeration.SpanType;
import de.tum.in.www1.artemis.repository.ParticipantScoreRepository;
import de.tum.in.www1.artemis.repository.StatisticsRepository;
import de.tum.in.www1.artemis.web.rest.dto.CourseManagementStatisticsDTO;

@Service
public class StatisticsService {

    private final StatisticsRepository statisticsRepository;

    private final ParticipantScoreRepository participantScoreRepository;

    public StatisticsService(StatisticsRepository statisticsRepository, ParticipantScoreRepository participantScoreRepository) {
        this.statisticsRepository = statisticsRepository;
        this.participantScoreRepository = participantScoreRepository;
    }

    /**
     * Forwards the request to the repository, which returns a List<Map<String, Object>>. For week, month or year the map from the Repository contains a String with the column name,
     * "day" and "amount" and an Object being the value, either the date in the format "YYYY-MM-DD" or the amount of the findings. For day, the column names are "day" and "amount",
     * which then contains the date in the ZonedDateFormat as Integer and the amount as Long
     * It then collects the amounts in an array, depending on the span value, and returns it
     *
     * @param span DAY,WEEK,MONTH or YEAR depending on the active tab in the view
     * @param periodIndex an index indicating which time period, 0 is current week, -1 is one week in the past, -2 is two weeks in the past ...
     * @param graphType the type of graph the data should be fetched
     * @param courseId the courseId. Only set if we fetch value for the course statistics
     * @return an array, containing the values for each bar in the graph
     */
    public Integer[] getChartData(SpanType span, Integer periodIndex, GraphType graphType, Long courseId) {
        ZonedDateTime startDate;
        ZonedDateTime endDate;
        List<Map<String, Object>> outcome;
        Integer[] result = new Integer[0];
        ZonedDateTime now = ZonedDateTime.now();
        int lengthOfMonth;
        if (span != SpanType.MONTH) {
            result = new Integer[createSpanMap().get(span)];
            Arrays.fill(result, 0);
        }
        switch (span) {
            case DAY:
                startDate = now.minusDays(-periodIndex).withHour(0).withMinute(0).withSecond(0).withNano(0);
                endDate = now.minusDays(-periodIndex).withHour(23).withMinute(59).withSecond(59);
                outcome = this.statisticsRepository.getDataFromDatabase(span, startDate, endDate, graphType, courseId);
                return this.statisticsRepository.createResultArrayForDay(outcome, result, endDate);
            case WEEK:
                startDate = now.minusWeeks(-periodIndex).minusDays(6).withHour(0).withMinute(0).withSecond(0).withNano(0);
                endDate = now.minusWeeks(-periodIndex).withHour(23).withMinute(59).withSecond(59);
                outcome = this.statisticsRepository.getDataFromDatabase(span, startDate, endDate, graphType, courseId);
                return this.statisticsRepository.createResultArrayForWeek(outcome, result, endDate);
            case MONTH:
                startDate = now.minusMonths(1 - periodIndex).withHour(0).withMinute(0).withSecond(0).withNano(0);
                endDate = now.minusMonths(-periodIndex).withHour(23).withMinute(59).withSecond(59);
                result = new Integer[(int) ChronoUnit.DAYS.between(startDate, endDate)];
                Arrays.fill(result, 0);
                outcome = this.statisticsRepository.getDataFromDatabase(span, startDate.plusDays(1), endDate, graphType, courseId);
                return this.statisticsRepository.createResultArrayForMonth(outcome, result, endDate);
            case QUARTER:
                LocalDateTime localStartDate = now.toLocalDateTime().with(DayOfWeek.MONDAY);
                LocalDateTime localEndDate = now.toLocalDateTime().with(DayOfWeek.SUNDAY);
                ZoneId zone = now.getZone();
                startDate = localStartDate.atZone(zone).minusWeeks(11 + (12 * (-periodIndex))).withHour(0).withMinute(0).withSecond(0).withNano(0);
                endDate = periodIndex != 0 ? localEndDate.atZone(zone).minusWeeks(12 * (-periodIndex)).withHour(23).withMinute(59).withSecond(59)
                        : localEndDate.atZone(zone).withHour(23).withMinute(59).withSecond(59);
                outcome = this.statisticsRepository.getDataFromDatabase(span, startDate, endDate, graphType, courseId);
                return this.statisticsRepository.createResultArrayForQuarter(outcome, result, endDate);
            case YEAR:
                startDate = now.minusYears(1 - periodIndex).plusMonths(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
                lengthOfMonth = YearMonth.of(now.minusYears(-periodIndex).getYear(), now.minusYears(-periodIndex).getMonth()).lengthOfMonth();
                endDate = now.minusYears(-periodIndex).withDayOfMonth(lengthOfMonth).withHour(23).withMinute(59).withSecond(59);
                outcome = this.statisticsRepository.getDataFromDatabase(span, startDate, endDate, graphType, courseId);
                return this.statisticsRepository.createResultArrayForYear(outcome, result, endDate);
            default:
                return null;
        }
    }

    /**
     * A map to manage the spanTypes and the corresponding array length of the result
     */
    private Map<SpanType, Integer> createSpanMap() {
        Map<SpanType, Integer> spanMap = new HashMap<>();
        spanMap.put(SpanType.DAY, 24);
        spanMap.put(SpanType.WEEK, 7);
        spanMap.put(SpanType.QUARTER, 12);
        spanMap.put(SpanType.YEAR, 12);
        return spanMap;
    }

    /**
     * Get the average score of the course and all exercises for the course statistics
     *
     * @param courseId    the id of the course for which the data should be fetched
     * @return a custom CourseManagementStatisticsDTO, which contains the relevant data
     */
    public CourseManagementStatisticsDTO getCourseStatistics(Long courseId) {
        var courseManagementStatisticsDTO = new CourseManagementStatisticsDTO();
        var exercises = statisticsRepository.findExercisesByCourseId(courseId);
        var includedExercises = exercises.stream().filter(Exercise::isCourseExercise)
                .filter(exercise -> !exercise.getIncludedInOverallScore().equals(IncludedInOverallScore.NOT_INCLUDED)).collect(Collectors.toSet());
        var averageScoreForCourse = participantScoreRepository.findAvgScore(includedExercises);
        var averageScoreForExercises = statisticsRepository.findAvgPointsForExercises(includedExercises);
        averageScoreForExercises.forEach(exercise -> {
            var roundedAverageScore = round(exercise.getAverageScore());
            exercise.setAverageScore(roundedAverageScore);
        });

        if (averageScoreForCourse != null && averageScoreForCourse > 0) {
            courseManagementStatisticsDTO.setAverageScoreOfCourse(round(averageScoreForCourse));
        }
        else {
            courseManagementStatisticsDTO.setAverageScoreOfCourse(0.0);
        }

        if (averageScoreForExercises.size() > 0) {
            courseManagementStatisticsDTO.setAverageScoresOfExercises(averageScoreForExercises);
        }
        else {
            courseManagementStatisticsDTO.setAverageScoresOfExercises(new ArrayList<>());
        }

        return courseManagementStatisticsDTO;
    }
}
