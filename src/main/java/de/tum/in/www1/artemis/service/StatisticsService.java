package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

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

    public Integer[] getTotalSubmissions(String span) {
        switch (span) {
            case "DAY": // result = this.statisticsRepository.getTotalSubmissionsDay(ZonedDateTime.now().minusDays(7));
                return null;
            case "WEEK":
                Integer[] result = new Integer[] { 0, 0, 0, 0, 0, 0, 0 };
                ZonedDateTime border = ZonedDateTime.now().minusDays(6).withHour(0).withMinute(0).withSecond(0);
                List<Map<String, Object>> outcome = this.statisticsRepository.getTotalSubmissionsWeek(border);
                for (Map<String, Object> map : outcome) {
                    ZonedDateTime date = (ZonedDateTime) map.get("day");
                    Integer amount = map.get("amount") != null ? ((Long) map.get("amount")).intValue() : null;
                    if (date.getDayOfMonth() == ZonedDateTime.now().minusDays(6).getDayOfMonth()) {
                        result[0] += amount;
                    }
                    else {
                        if (date.getDayOfMonth() == ZonedDateTime.now().minusDays(5).getDayOfMonth()) {
                            result[1] += amount;
                        }
                        else {
                            if (date.getDayOfMonth() == ZonedDateTime.now().minusDays(4).getDayOfMonth()) {
                                result[2] += amount;
                            }
                            else {
                                if (date.getDayOfMonth() == ZonedDateTime.now().minusDays(3).getDayOfMonth()) {
                                    result[3] += amount;
                                }
                                else {
                                    if (date.getDayOfMonth() == ZonedDateTime.now().minusDays(2).getDayOfMonth()) {
                                        result[4] += amount;
                                    }
                                    else {
                                        if (date.getDayOfMonth() == ZonedDateTime.now().minusDays(1).getDayOfMonth()) {
                                            result[5] += amount;
                                        }
                                        else {
                                            if (date.getDayOfMonth() == ZonedDateTime.now().getDayOfMonth()) {
                                                result[6] += amount;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    /*
                     * switch (date.getDayOfMonth()) { case ZonedDateTime.now().minusDays(6).getDayOfMonth(): result[0] = amount; break; case
                     * ZonedDateTime.now().minusDays(5).getDayOfMonth(): result[1] = amount; break; case ZonedDateTime.now().minusDays(4).getDayOfMonth(): result[2] = amount;
                     * break; case ZonedDateTime.now().minusDays(3).getDayOfMonth(): result[3] = amount; break; case ZonedDateTime.now().minusDays(2).getDayOfMonth(): result[4] =
                     * amount; break; case ZonedDateTime.now().minusDays(1).getDayOfMonth(): result[5] = amount; break; case ZonedDateTime.now().getDayOfMonth(): result[6] =
                     * amount; break; default: break; }
                     */
                }
                return result;
            case "MONTH":
                break;
            case "YEAR":
                // return this.statisticsRepository.getTotalSubmissionsWeek();
                break;
        }
        return null;
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
        Integer result = this.statisticsRepository.getExamRegistrations(ZonedDateTime.now().minusDays(span), ZonedDateTime.now());
        return (result != null ? result : 0);
    }

    public Integer getActiveTutors(Long span) {
        return this.statisticsRepository.getActiveTutors(ZonedDateTime.now().minusDays(span));
    }

    public Integer getCreatedResults(Long span) {
        return this.statisticsRepository.getCreatedResults(ZonedDateTime.now().minusDays(span));
    }

    public Integer getResultFeedbacks(Long span) {
        Integer result = this.statisticsRepository.getResultFeedbacks(ZonedDateTime.now().minusDays(span));
        return (result != null ? result : 0);
    }
}
