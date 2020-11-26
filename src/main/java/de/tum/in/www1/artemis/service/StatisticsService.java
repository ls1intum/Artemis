package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.StatisticsObject;
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

    public StatisticsObject getTotalSubmissions(SpanType span) {
        /*
         * ZonedDateTime[] outcome; Integer[] result; switch (span) { case DAY: // result = this.statisticsRepository.getTotalSubmissionsDay(ZonedDateTime.now().minusDays(7));
         * return result; case WEEK: result = new Integer[]{0, 0, 0, 0, 0, 0, 0}; outcome = this.statisticsRepository.getTotalSubmissionsWeek(ZonedDateTime.now().minusDays(7));
         * break; case MONTH: break; case YEAR: //result = this.statisticsRepository.getTotalSubmissionsDay(); return result; } return result;
         */
        return this.statisticsRepository.getTotalSubmissionsDay();
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
