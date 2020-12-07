package de.tum.in.www1.artemis.service;

import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.enumeration.SpanType;
import de.tum.in.www1.artemis.repository.StatisticsRepository;

@Service
public class StatisticsService {

    private final StatisticsRepository statisticsRepository;

    public StatisticsService(StatisticsRepository statisticsRepository) {
        this.statisticsRepository = statisticsRepository;
    }

    public Integer getLoggedInUsers(Long span) {
        return this.statisticsRepository.getLoggedInUsers(ZonedDateTime.now().minusDays(span).toInstant());
    }

    public Integer getActiveUsers(Long span) {
        return this.statisticsRepository.getActiveUsers(ZonedDateTime.now().minusDays(span));
    }

    /**
     * Forwards the request to the repository, which returns a List<Map<String, Object>>, with String being the column name, "day" and "amount" and Object being the value,
     * either the date or the amount of submissions. It then collects the amounts in an array, depending on the span value, and returns it
     *
     * @param span DAY,WEEK,MONTH or YEAR depending on the active tab in the view
     * @return a array, containing the values for each bar in the graph
     */
    public Integer[] getTotalSubmissions(SpanType span, Integer periodIndex) {
        ZonedDateTime startDate;
        ZonedDateTime endDate;
        List<Map<String, Object>> outcome;
        ZonedDateTime now = ZonedDateTime.now();
        Integer lengthOfMonth = YearMonth.of(now.getYear(), now.minusMonths(1).getMonth()).lengthOfMonth();

        // A map to manage the spanTypes and the corresponding array length of the result
        Map<SpanType, Integer> spanMap = new HashMap<>();
        spanMap.put(SpanType.DAY, 24);
        spanMap.put(SpanType.WEEK, 7);
        spanMap.put(SpanType.MONTH, lengthOfMonth);
        spanMap.put(SpanType.YEAR, 12);
        Integer[] result = new Integer[spanMap.get(span)];
        Arrays.fill(result, 0);
        switch (span) {
            case DAY:
                startDate = now.minusDays(-periodIndex).withHour(0).withMinute(0).withSecond(0);
                endDate = now.minusDays(-periodIndex).withHour(23).withMinute(59).withSecond(59);
                outcome = this.statisticsRepository.getTotalSubmissions(startDate, endDate);
                return createSubmissionCountArrayForDay(outcome, result, now);
            case WEEK:
                startDate = now.minusWeeks(-periodIndex).minusDays(6).withHour(0).withMinute(0).withSecond(0);
                endDate = now.minusWeeks(-periodIndex).withHour(23).withMinute(59).withSecond(59);
                outcome = this.statisticsRepository.getTotalSubmissions(startDate, endDate);
                return createSubmissionCountArrayForWeek(outcome, result, now);
            case MONTH:
                startDate = now.minusMonths(1 - periodIndex).plusDays(1).withHour(0).withMinute(0).withSecond(0);
                endDate = now.minusMonths(-periodIndex).withHour(23).withMinute(59).withSecond(59);
                outcome = this.statisticsRepository.getTotalSubmissions(startDate, endDate);
                return createSubmissionCountArrayForMonth(outcome, result, now);
            case YEAR:
                startDate = now.minusYears(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
                lengthOfMonth = YearMonth.of(now.minusYears(-periodIndex).getYear(), now.minusYears(-periodIndex).getMonth()).lengthOfMonth();
                endDate = now.minusYears(-periodIndex).withDayOfMonth(lengthOfMonth).withHour(23).withMinute(59).withSecond(59);
                outcome = this.statisticsRepository.getTotalSubmissions(startDate, endDate);
                return createSubmissionCountArrayForYear(outcome, result, now);
            default:
                return null;
        }
    }

    /**
     * Gets a List of Maps, each Map describing an entry in the database. The Map has the two keys "day" and "amount",
     * which map to the date and the amount of submissions. This Method handles the spanType DAY
     *
     * @param outcome A List<Map<String, Object>>, containing the content which should be refactored into an array
     * @param result the array in which the converted outcome should be inserted
     * @param currentDate the current time
     * @return a array, containing the values for each bar in the graph
     */
    private Integer[] createSubmissionCountArrayForDay(List<Map<String, Object>> outcome, Integer[] result, ZonedDateTime currentDate) {
        for (Map<String, Object> map : outcome) {
            ZonedDateTime date = (ZonedDateTime) map.get("day");
            Integer amount = map.get("amount") != null ? ((Long) map.get("amount")).intValue() : null;
            for (int i = 0; i < 24; i++) {
                if (date.getHour() == currentDate.minusHours(i).getHour()) {
                    result[currentDate.getHour() - i] += amount;
                }
            }
        }
        return result;
    }

    /**
     * Gets a List of Maps, each Map describing an entry in the database. The Map has the two keys "day" and "amount",
     * which map to the date and the amount of submissions. This Method handles the spanType WEEK
     *
     * @param outcome A List<Map<String, Object>>, containing the content which should be refactored into an array
     * @param result the array in which the converted outcome should be inserted
     * @param currentDate the current time
     * @return a array, containing the values for each bar in the graph
     */
    private Integer[] createSubmissionCountArrayForWeek(List<Map<String, Object>> outcome, Integer[] result, ZonedDateTime currentDate) {
        for (Map<String, Object> map : outcome) {
            ZonedDateTime date = (ZonedDateTime) map.get("day");
            Integer amount = map.get("amount") != null ? ((Long) map.get("amount")).intValue() : null;
            for (int i = 0; i < 7; i++) {
                if (date.getDayOfMonth() == currentDate.minusDays(i).getDayOfMonth()) {
                    result[6 - i] += amount;
                }
            }
        }
        return result;
    }

    /**
     * Gets a List of Maps, each Map describing an entry in the database. The Map has the two keys "day" and "amount",
     * which map to the date and the amount of submissions. This Method handles the spanType MONTH
     *
     * @param outcome A List<Map<String, Object>>, containing the content which should be refactored into an array
     * @param result the array in which the converted outcome should be inserted
     * @param currentDate the current time
     * @return a array, containing the values for each bar in the graph
     */
    private Integer[] createSubmissionCountArrayForMonth(List<Map<String, Object>> outcome, Integer[] result, ZonedDateTime currentDate) {
        for (Map<String, Object> map : outcome) {
            ZonedDateTime date = (ZonedDateTime) map.get("day");
            Integer amount = map.get("amount") != null ? ((Long) map.get("amount")).intValue() : null;
            for (int i = 0; i < result.length; i++) {
                if (date.getDayOfMonth() == currentDate.minusDays(i).getDayOfMonth()) {
                    result[result.length - 1 - i] += amount;
                }
            }
        }
        return result;
    }

    /**
     * Gets a List of Maps, each Map describing an entry in the database. The Map has the two keys "day" and "amount",
     * which map to the date and the amount of submissions. This Method handles the spanType YEAR
     *
     * @param outcome A List<Map<String, Object>>, containing the content which should be refactored into an array
     * @param result the array in which the converted outcome should be inserted
     * @param currentDate the current time
     * @return a array, containing the values for each bar in the graph
     */
    private Integer[] createSubmissionCountArrayForYear(List<Map<String, Object>> outcome, Integer[] result, ZonedDateTime currentDate) {
        for (Map<String, Object> map : outcome) {
            ZonedDateTime date = (ZonedDateTime) map.get("day");
            Integer amount = map.get("amount") != null ? ((Long) map.get("amount")).intValue() : null;
            for (int i = 0; i < 12; i++) {
                if (date.getMonth() == currentDate.minusMonths(i).getMonth() && date.getYear() == currentDate.minusMonths(i).getYear()) {
                    result[11 - i] += amount;
                }
            }
        }
        return result;
    }

    public Integer getReleasedExercises(Long span) {
        return this.statisticsRepository.getReleasedExercises(ZonedDateTime.now().minusDays(span), ZonedDateTime.now());
    }

    public Integer getExerciseDeadlines(Long span) {
        return this.statisticsRepository.getExerciseDeadlines(ZonedDateTime.now().minusDays(span), ZonedDateTime.now());
    }

    public Integer getConductedExams(Long span) {
        return this.statisticsRepository.getConductedExams(ZonedDateTime.now().minusDays(span), ZonedDateTime.now());
    }

    public Integer getExamParticipations(Long span) {
        return this.statisticsRepository.getExamParticipations(ZonedDateTime.now().minusDays(span));
    }

    public Integer getExamRegistrations(Long span) {
        return this.statisticsRepository.getExamRegistrations(ZonedDateTime.now().minusDays(span), ZonedDateTime.now());
    }

    public Integer getActiveTutors(Long span) {
        return this.statisticsRepository.getActiveTutors(ZonedDateTime.now().minusDays(span));
    }

    public Integer getCreatedResults(Long span) {
        return this.statisticsRepository.getCreatedResults(ZonedDateTime.now().minusDays(span));
    }

    public Integer getResultFeedbacks(Long span) {
        return this.statisticsRepository.getResultFeedbacks(ZonedDateTime.now().minusDays(span));
    }
}
