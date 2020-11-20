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
}
