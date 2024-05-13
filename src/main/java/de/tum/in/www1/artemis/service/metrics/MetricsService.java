package de.tum.in.www1.artemis.service.metrics;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;
import static de.tum.in.www1.artemis.service.util.ZonedDateTimeUtil.toRelativeTime;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.averagingDouble;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

import java.time.ZonedDateTime;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.repository.metrics.ExerciseMetricsRepository;
import de.tum.in.www1.artemis.web.rest.dto.metrics.ExerciseInformationDTO;
import de.tum.in.www1.artemis.web.rest.dto.metrics.ExerciseStudentMetricsDTO;
import de.tum.in.www1.artemis.web.rest.dto.metrics.ResourceTimestampDTO;
import de.tum.in.www1.artemis.web.rest.dto.metrics.ScoreDTO;
import de.tum.in.www1.artemis.web.rest.dto.metrics.StudentMetricsDTO;

/**
 * Service class to access metrics regarding students' learning progress.
 */
@Profile(PROFILE_CORE)
@Service
public class MetricsService {

    private final ExerciseMetricsRepository exerciseMetricsRepository;

    public MetricsService(ExerciseMetricsRepository exerciseMetricsRepository) {
        this.exerciseMetricsRepository = exerciseMetricsRepository;
    }

    /**
     * Get the metrics for a student in a course.
     *
     * @param userId   the id of the student
     * @param courseId the id of the course
     * @return the metrics for the student in the course
     */
    public StudentMetricsDTO getStudentCourseMetrics(long userId, long courseId) {
        final var exerciseMetricsDTO = getStudentExerciseMetrics(userId, courseId);
        return new StudentMetricsDTO(exerciseMetricsDTO);
    }

    /**
     * Get the exercise metrics for a student in a course.
     *
     * @param userId   the id of the student
     * @param courseId the id of the course
     * @return the metrics for the student in the course
     */
    public ExerciseStudentMetricsDTO getStudentExerciseMetrics(long userId, long courseId) {
        final var exerciseInfo = exerciseMetricsRepository.findAllExerciseInformationByCourseId(courseId);
        // generate map and remove exercises that are not yet started
        final Predicate<ExerciseInformationDTO> started = e -> e.start().isBefore(ZonedDateTime.now());
        final var exerciseInfoMap = exerciseInfo.stream().filter(started).collect(toMap(ExerciseInformationDTO::id, identity()));

        final var exerciseIds = exerciseInfoMap.keySet();

        final var averageScore = exerciseMetricsRepository.findAverageScore(exerciseIds);
        final var averageScoreMap = averageScore.stream().collect(toMap(ScoreDTO::exerciseId, ScoreDTO::score));

        final var score = exerciseMetricsRepository.findScore(exerciseIds, userId);
        final var scoreMap = score.stream().collect(toMap(ScoreDTO::exerciseId, ScoreDTO::score));

        final var latestSubmissions = exerciseMetricsRepository.findLatestSubmissionDates(exerciseIds);
        final ToDoubleFunction<ResourceTimestampDTO> relativeTime = dto -> toRelativeTime(exerciseInfoMap.get(dto.id()).start(), exerciseInfoMap.get(dto.id()).due(),
                dto.timestamp());
        final var averageLatestSubmissionMap = latestSubmissions.stream().collect(groupingBy(ResourceTimestampDTO::id, averagingDouble(relativeTime)));

        final var latestSubmissionOfUser = exerciseMetricsRepository.findLatestSubmissionDatesForUser(exerciseIds, userId);
        final var latestSubmissionMap = latestSubmissionOfUser.stream().collect(toMap(ResourceTimestampDTO::id, relativeTime::applyAsDouble));

        return new ExerciseStudentMetricsDTO(exerciseInfoMap, averageScoreMap, scoreMap, averageLatestSubmissionMap, latestSubmissionMap);
    }
}
