package de.tum.in.www1.artemis.service;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Arrays;
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
    public Integer[] getTotalSubmissions(SpanType span) {
        Integer[] result;
        ZonedDateTime border;
        List<Map<String, Object>> outcome;
        switch (span) {
            case DAY:
                result = new Integer[24];
                Arrays.fill(result, 0);
                border = ZonedDateTime.now().minusDays(1).withHour(0).withMinute(0).withSecond(0);
                ZonedDateTime intervalEnd = ZonedDateTime.now().withHour(0).withMinute(0).withSecond(0);
                outcome = this.statisticsRepository.getTotalSubmissionsYesterday(border, intervalEnd);
                for (Map<String, Object> map : outcome) {
                    ZonedDateTime date = (ZonedDateTime) map.get("day");
                    Integer amount = map.get("amount") != null ? ((Long) map.get("amount")).intValue() : null;
                    for (int i = 0; i < 24; i++) {
                        if (date.getHour() == intervalEnd.minusHours(1 + i).getHour()) {
                            result[23 - i] += amount;
                        }
                    }
                }
                return result;
            case WEEK:
                result = new Integer[7];
                Arrays.fill(result, 0);
                border = ZonedDateTime.now().minusDays(6).withHour(0).withMinute(0).withSecond(0);
                outcome = this.statisticsRepository.getTotalSubmissions(border);
                for (Map<String, Object> map : outcome) {
                    ZonedDateTime date = (ZonedDateTime) map.get("day");
                    Integer amount = map.get("amount") != null ? ((Long) map.get("amount")).intValue() : null;
                    for (int i = 0; i < 7; i++) {
                        if (date.getDayOfMonth() == ZonedDateTime.now().minusDays(i).getDayOfMonth()) {
                            result[6 - i] += amount;
                        }
                    }
                }
                return result;
            case MONTH:
                result = new Integer[LocalDate.now().minusMonths(1).lengthOfMonth()];
                Arrays.fill(result, 0);
                border = ZonedDateTime.now().minusMonths(1).plusDays(1).withHour(0).withMinute(0).withSecond(0);
                outcome = this.statisticsRepository.getTotalSubmissions(border);
                for (Map<String, Object> map : outcome) {
                    ZonedDateTime date = (ZonedDateTime) map.get("day");
                    Integer amount = map.get("amount") != null ? ((Long) map.get("amount")).intValue() : null;
                    for (int i = 0; i < result.length; i++) {
                        if (date.getDayOfMonth() == ZonedDateTime.now().minusDays(i).getDayOfMonth()) {
                            result[result.length - 1 - i] += amount;
                        }
                    }
                }
                return result;
            case YEAR:
                result = new Integer[12];
                Arrays.fill(result, 0);
                border = ZonedDateTime.now().minusYears(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
                outcome = this.statisticsRepository.getTotalSubmissions(border);
                for (Map<String, Object> map : outcome) {
                    ZonedDateTime date = (ZonedDateTime) map.get("day");
                    Integer amount = map.get("amount") != null ? ((Long) map.get("amount")).intValue() : null;
                    for (int i = 0; i < 12; i++) {
                        if (date.getMonth() == ZonedDateTime.now().minusMonths(i).getMonth()) {
                            result[11 - i] += amount;
                        }
                    }
                }
                return result;
            default:
                return null;
        }
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
