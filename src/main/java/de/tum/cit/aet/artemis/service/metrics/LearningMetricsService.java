package de.tum.cit.aet.artemis.service.metrics;

import static de.tum.cit.aet.artemis.core.config.Constants.MIN_SCORE_GREEN;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.service.util.TimeUtil.toRelativeTime;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.averagingDouble;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.time.ZonedDateTime;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.repository.metrics.CompetencyMetricsRepository;
import de.tum.cit.aet.artemis.repository.metrics.ExerciseMetricsRepository;
import de.tum.cit.aet.artemis.repository.metrics.LectureUnitMetricsRepository;
import de.tum.cit.aet.artemis.web.rest.dto.competency.CompetencyJolDTO;
import de.tum.cit.aet.artemis.web.rest.dto.metrics.CompetencyInformationDTO;
import de.tum.cit.aet.artemis.web.rest.dto.metrics.CompetencyProgressDTO;
import de.tum.cit.aet.artemis.web.rest.dto.metrics.CompetencyStudentMetricsDTO;
import de.tum.cit.aet.artemis.web.rest.dto.metrics.ExerciseInformationDTO;
import de.tum.cit.aet.artemis.web.rest.dto.metrics.ExerciseStudentMetricsDTO;
import de.tum.cit.aet.artemis.web.rest.dto.metrics.LectureUnitInformationDTO;
import de.tum.cit.aet.artemis.web.rest.dto.metrics.LectureUnitStudentMetricsDTO;
import de.tum.cit.aet.artemis.web.rest.dto.metrics.MapEntryLongLong;
import de.tum.cit.aet.artemis.web.rest.dto.metrics.ResourceTimestampDTO;
import de.tum.cit.aet.artemis.web.rest.dto.metrics.ScoreDTO;
import de.tum.cit.aet.artemis.web.rest.dto.metrics.StudentMetricsDTO;

/**
 * Service class to access metrics regarding students' learning progress.
 */
@Profile(PROFILE_CORE)
@Service
public class LearningMetricsService {

    private final ExerciseMetricsRepository exerciseMetricsRepository;

    private final LectureUnitMetricsRepository lectureUnitMetricsRepository;

    private final CompetencyMetricsRepository competencyMetricsRepository;

    public LearningMetricsService(ExerciseMetricsRepository exerciseMetricsRepository, LectureUnitMetricsRepository lectureUnitMetricsRepository,
            CompetencyMetricsRepository competencyMetricsRepository) {
        this.exerciseMetricsRepository = exerciseMetricsRepository;
        this.lectureUnitMetricsRepository = lectureUnitMetricsRepository;
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
        final var lectureUnitMetricsDTO = getStudentLectureUnitMetrics(userId, courseId);
        final var competencyMetricsDTO = getStudentCompetencyMetrics(userId, courseId);
        return new StudentMetricsDTO(exerciseMetricsDTO, lectureUnitMetricsDTO, competencyMetricsDTO);
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

        final var categories = exerciseMetricsRepository.findCategoriesByExerciseIds(exerciseIds);
        final var categoryMap = categories.stream().collect(groupingBy(Entry::getKey, mapping(Entry::getValue, toSet())));

        final var averageScore = exerciseMetricsRepository.findAverageScore(exerciseIds);
        final var averageScoreMap = averageScore.stream().collect(toMap(ScoreDTO::exerciseId, ScoreDTO::score));

        final var score = exerciseMetricsRepository.findScore(exerciseIds, userId);
        final var scoreMap = score.stream().collect(toMap(ScoreDTO::exerciseId, ScoreDTO::score));

        final var exerciseIdsWithDueDate = exerciseIds.stream().filter(exerciseInfoMap::containsKey).filter(id -> exerciseInfoMap.get(id).due() != null).collect(toSet());
        final var latestSubmissions = exerciseMetricsRepository.findLatestSubmissionDates(exerciseIdsWithDueDate);
        final ToDoubleFunction<ResourceTimestampDTO> relativeTime = dto -> toRelativeTime(exerciseInfoMap.get(dto.id()).start(), exerciseInfoMap.get(dto.id()).due(),
                dto.timestamp());
        final var averageLatestSubmissionMap = latestSubmissions.stream().collect(groupingBy(ResourceTimestampDTO::id, averagingDouble(relativeTime)));

        final var latestSubmissionOfUser = exerciseMetricsRepository.findLatestSubmissionDatesForUser(exerciseIdsWithDueDate, userId);
        final var latestSubmissionMap = latestSubmissionOfUser.stream().collect(toMap(ResourceTimestampDTO::id, relativeTime::applyAsDouble));

        final var completedExerciseIds = exerciseMetricsRepository.findAllCompletedExerciseIdsForUserByExerciseIds(userId, exerciseIds, MIN_SCORE_GREEN);

        final var teamIds = exerciseMetricsRepository.findTeamIdsForUserByExerciseIds(userId, exerciseIds);
        final var teamIdMap = teamIds.stream().collect(toMap(MapEntryLongLong::key, MapEntryLongLong::value));

        return new ExerciseStudentMetricsDTO(exerciseInfoMap, categoryMap, averageScoreMap, scoreMap, averageLatestSubmissionMap, latestSubmissionMap, completedExerciseIds,
                teamIdMap);
    }

    /**
     * Get the lecture unit metrics for a student in a course.
     *
     * @param userId   the id of the student
     * @param courseId the id of the course
     * @return the metrics for the student in the course
     */
    public LectureUnitStudentMetricsDTO getStudentLectureUnitMetrics(long userId, long courseId) {
        final var lectureUnitInfo = lectureUnitMetricsRepository.findAllLectureUnitInformationByCourseId(courseId);
        final var lectureUnitInfoMap = lectureUnitInfo.stream().collect(toMap(LectureUnitInformationDTO::id, identity()));

        final var lectureUnitIds = lectureUnitInfoMap.keySet();

        final var completedLectureUnitIds = lectureUnitMetricsRepository.findAllCompletedLectureUnitIdsForUserByLectureUnitIds(userId, lectureUnitIds);

        return new LectureUnitStudentMetricsDTO(lectureUnitInfoMap, completedLectureUnitIds);
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
        final var exerciseMap = competencyExerciseMapEntries.stream().collect(groupingBy(MapEntryLongLong::key, mapping(MapEntryLongLong::value, toSet())));

        final var competencyLectureUnitMapEntries = competencyMetricsRepository.findAllLectureUnitIdsByCompetencyIds(competencyIds);
        final var lectureUnitMap = competencyLectureUnitMapEntries.stream().collect(groupingBy(MapEntryLongLong::key, mapping(MapEntryLongLong::value, toSet())));

        final var competencyProgress = competencyMetricsRepository.findAllCompetencyProgressForUserByCompetencyIds(userId, competencyIds);
        final var competencyProgressMap = competencyProgress.stream().collect(toMap(CompetencyProgressDTO::competencyId, CompetencyProgressDTO::progress));
        final var competencyConfidenceMap = competencyProgress.stream().collect(toMap(CompetencyProgressDTO::competencyId, CompetencyProgressDTO::confidence));

        final var currentJolValues = competencyMetricsRepository.findAllLatestCompetencyJolValuesForUserByCompetencyIds(userId, competencyIds);
        final var currentJolValuesMap = currentJolValues.stream().collect(toMap(CompetencyJolDTO::competencyId, identity()));

        final var currentJolIds = currentJolValues.stream().map(CompetencyJolDTO::id).collect(toSet());
        final var priorJolValues = competencyMetricsRepository.findAllLatestCompetencyJolValuesForUserByCompetencyIdsExcludeJolIds(userId, competencyIds, currentJolIds);
        final var priorJolValuesMap = priorJolValues.stream().collect(toMap(CompetencyJolDTO::competencyId, identity()));

        return new CompetencyStudentMetricsDTO(competencyInfoMap, exerciseMap, lectureUnitMap, competencyProgressMap, competencyConfidenceMap, currentJolValuesMap,
                priorJolValuesMap);
    }
}
