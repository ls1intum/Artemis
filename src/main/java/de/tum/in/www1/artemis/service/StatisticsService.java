package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;

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

    public Integer getTotalSubmissions(Long span) {
        return this.statisticsRepository.getTotalsubmissions(ZonedDateTime.now().minusDays(span));
    }

    public Integer getReleasedExercises(Long span) {
        return this.statisticsRepository.getReleasedExercises(ZonedDateTime.now().minusDays(span));
    }

    public Integer getExerciseDeadlines(Long span) {
        return this.statisticsRepository.getExerciseDeadlines(ZonedDateTime.now().minusDays(span));
    }

    public Integer getConductedExams(Long span) {
        return this.statisticsRepository.getConductedExams(ZonedDateTime.now().minusDays(span));
    }

    public Integer getExamParticipations(Long span) {
        return this.statisticsRepository.getExamParticipations(ZonedDateTime.now().minusDays(span));
    }

    public Integer getExamRegistrations(Long span) {
        Integer result = this.statisticsRepository.getExamRegistrations(ZonedDateTime.now().minusDays(span));
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
