package de.tum.in.www1.artemis.service.metrics;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;
import static de.tum.in.www1.artemis.service.util.ZonedDateTimeUtil.toRelativeTime;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.averagingDouble;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.time.ZonedDateTime;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.repository.metrics.CompetencyMetricsRepository;
import de.tum.in.www1.artemis.repository.metrics.ExerciseMetricsRepository;
import de.tum.in.www1.artemis.web.rest.dto.metrics.CompetencyInformationDTO;
import de.tum.in.www1.artemis.web.rest.dto.metrics.CompetencyStudentMetricsDTO;
import de.tum.in.www1.artemis.web.rest.dto.metrics.ExerciseInformationDTO;
import de.tum.in.www1.artemis.web.rest.dto.metrics.ExerciseStudentMetricsDTO;
import de.tum.in.www1.artemis.web.rest.dto.metrics.MapEntryDTO;
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

    private final CompetencyMetricsRepository competencyMetricsRepository;

    public MetricsService(ExerciseMetricsRepository exerciseMetricsRepository, CompetencyMetricsRepository competencyMetricsRepository) {
        this.exerciseMetricsRepository = exerciseMetricsRepository;
        this.competencyMetricsRepository = competencyMetricsRepository;
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
        final var competencyMetricsDTO = getStudentCompetencyMetrics(userId, courseId);
        return new StudentMetricsDTO(exerciseMetricsDTO, competencyMetricsDTO);
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
        final Predicate<ExerciseInformationDTO> started = e -> e.start() != null && e.start().isBefore(ZonedDateTime.now());
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

    /**
     * Get the competency metrics for a student in a course.
     *
     * @param userId   the id of the student
     * @param courseId the id of the course
     * @return the metrics for the student in the course
     */
    public CompetencyStudentMetricsDTO getStudentCompetencyMetrics(long userId, long courseId) {
        final var competencyInfo = competencyMetricsRepository.findAllCompetencyInformationByCourseId(courseId);
        final var competencyInfoMap = competencyInfo.stream().collect(toMap(CompetencyInformationDTO::id, identity()));

        final var competencyIds = competencyInfoMap.keySet();

        final var competencyExerciseMapEntries = competencyMetricsRepository.findAllExerciseIdsByCompetencyIds(competencyIds);
        final var exerciseMap = competencyExerciseMapEntries.stream().collect(groupingBy(MapEntryDTO::key, mapping(MapEntryDTO::value, toSet())));

        final var competencyLectureUnitMapEntries = competencyMetricsRepository.findAllLectureUnitIdsByCompetencyIds(competencyIds);
        final var lectureUnitMap = competencyLectureUnitMapEntries.stream().collect(groupingBy(MapEntryDTO::key, mapping(MapEntryDTO::value, toSet())));

        return new CompetencyStudentMetricsDTO(competencyInfoMap, exerciseMap, lectureUnitMap);
    }
}
