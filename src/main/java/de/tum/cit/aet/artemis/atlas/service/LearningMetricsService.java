package de.tum.cit.aet.artemis.atlas.service;

import static de.tum.cit.aet.artemis.core.config.Constants.MIN_SCORE_GREEN;
import static de.tum.cit.aet.artemis.core.util.TimeUtil.toRelativeTime;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.averagingDouble;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.time.ZonedDateTime;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyJolDTO;
import de.tum.cit.aet.artemis.atlas.dto.metrics.CompetencyInformationDTO;
import de.tum.cit.aet.artemis.atlas.dto.metrics.CompetencyProgressDTO;
import de.tum.cit.aet.artemis.atlas.dto.metrics.CompetencyStudentMetricsDTO;
import de.tum.cit.aet.artemis.atlas.dto.metrics.ExerciseStudentMetricsDTO;
import de.tum.cit.aet.artemis.atlas.dto.metrics.LectureUnitInformationDTO;
import de.tum.cit.aet.artemis.atlas.dto.metrics.LectureUnitStudentMetricsDTO;
import de.tum.cit.aet.artemis.atlas.dto.metrics.MapEntryLongLong;
import de.tum.cit.aet.artemis.atlas.dto.metrics.ResourceTimestampDTO;
import de.tum.cit.aet.artemis.atlas.dto.metrics.ScoreDTO;
import de.tum.cit.aet.artemis.atlas.dto.metrics.StudentMetricsDTO;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyMetricsRepository;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseInformationDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseMetricsRepository;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;
import de.tum.cit.aet.artemis.lecture.config.LectureApiNotPresentException;
import edu.stanford.nlp.util.Sets;

/**
 * Service class to access metrics regarding students' learning progress.
 */
@Conditional(AtlasEnabled.class)
@Service
public class LearningMetricsService {

    private final ExerciseMetricsRepository exerciseMetricsRepository;

    private final Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi;

    private final CompetencyMetricsRepository competencyMetricsRepository;

    public LearningMetricsService(ExerciseMetricsRepository exerciseMetricsRepository, Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi,
            CompetencyMetricsRepository competencyMetricsRepository) {
        this.exerciseMetricsRepository = exerciseMetricsRepository;
        this.lectureUnitRepositoryApi = lectureUnitRepositoryApi;
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
        final Predicate<ExerciseInformationDTO> started = e -> e.start() != null && e.start().isBefore(ZonedDateTime.now());
        // the database query should only return exercises that are started, i.e. have a start date in the past, however, we also filter in Java to ensure that to avoid exceptions
        final var exerciseInfo = exerciseMetricsRepository.findAllStartedExerciseInformationByCourseId(courseId).stream().filter(started).collect(Collectors.toSet());

        final var exerciseInfoMap = exerciseInfo.stream().collect(toMap(ExerciseInformationDTO::id, identity()));

        final var exerciseIds = exerciseInfo.stream().map(ExerciseInformationDTO::id).collect(toSet());

        final Set<Entry<Long, String>> categories = !exerciseIds.isEmpty() ? exerciseMetricsRepository.findCategoriesByExerciseIds(exerciseIds) : Set.of();
        final var categoryMap = categories.stream().collect(groupingBy(Entry::getKey, mapping(Entry::getValue, toSet())));

        final Set<ScoreDTO> averageScore = !exerciseIds.isEmpty() ? exerciseMetricsRepository.findAverageScore(exerciseIds) : Set.of();
        final var averageScoreMap = averageScore.stream().collect(toMap(ScoreDTO::exerciseId, ScoreDTO::score));

        final Set<ScoreDTO> score = !exerciseIds.isEmpty() ? exerciseMetricsRepository.findScore(exerciseIds, userId) : Set.of();
        final var scoreMap = score.stream().collect(toMap(ScoreDTO::exerciseId, ScoreDTO::score));

        final Predicate<ExerciseInformationDTO> hasDueDate = exercise -> exercise.due() != null;

        final var exerciseIdsWithDueDate = exerciseInfo.stream().filter(hasDueDate).map(ExerciseInformationDTO::id).collect(toSet());
        final Set<ResourceTimestampDTO> latestSubmissions = !exerciseIdsWithDueDate.isEmpty() ? exerciseMetricsRepository.findLatestSubmissionDates(exerciseIdsWithDueDate)
                : Set.of();
        final ToDoubleFunction<ResourceTimestampDTO> relativeTime = dto -> toRelativeTime(exerciseInfoMap.get(dto.id()).start(), exerciseInfoMap.get(dto.id()).due(),
                dto.timestamp());
        final var averageLatestSubmissionMap = latestSubmissions.stream().collect(groupingBy(ResourceTimestampDTO::id, averagingDouble(relativeTime)));

        final var individualExerciseIdsWithDueDate = exerciseInfo.stream().filter(ExerciseInformationDTO::isIndividual).filter(hasDueDate).map(ExerciseInformationDTO::id)
                .collect(toSet());
        final var teamExerciseIdsWithDueDate = exerciseInfo.stream().filter(ExerciseInformationDTO::isTeam).filter(hasDueDate).map(ExerciseInformationDTO::id).collect(toSet());

        // we split the latest submissions into individual and team exercises, because otherwise the database queries would be significantly slower (using OR in the WHERE clause
        // leads to a full table scan)
        final Set<ResourceTimestampDTO> latestIndividualSubmissionOfUser = !individualExerciseIdsWithDueDate.isEmpty()
                ? exerciseMetricsRepository.findLatestIndividualSubmissionDatesForUser(individualExerciseIdsWithDueDate, userId)
                : Set.of();
        final Set<ResourceTimestampDTO> latestTeamSubmissionOfUser = !teamExerciseIdsWithDueDate.isEmpty()
                ? exerciseMetricsRepository.findLatestTeamSubmissionDatesForUser(teamExerciseIdsWithDueDate, userId)
                : Set.of();
        // combine both sets of latest submissions
        final var latestSubmissionOfUser = Sets.union(latestIndividualSubmissionOfUser, latestTeamSubmissionOfUser);
        final var latestSubmissionMap = latestSubmissionOfUser.stream().collect(toMap(ResourceTimestampDTO::id, relativeTime::applyAsDouble));

        final Set<Long> completedExerciseIds = !exerciseIds.isEmpty()
                ? exerciseMetricsRepository.findAllCompletedExerciseIdsForUserByExerciseIds(userId, exerciseIds, MIN_SCORE_GREEN)
                : Set.of();

        final Set<MapEntryLongLong> teamIds = !exerciseIds.isEmpty() ? exerciseMetricsRepository.findTeamIdsForUserByExerciseIds(userId, exerciseIds) : Set.of();
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
        LectureUnitRepositoryApi api = lectureUnitRepositoryApi.orElseThrow(() -> new LectureApiNotPresentException(LectureUnitRepositoryApi.class));

        final var lectureUnitInfo = api.findAllLectureUnitInformationByCourseId(courseId);
        final var lectureUnitInfoMap = lectureUnitInfo.stream().collect(toMap(LectureUnitInformationDTO::id, identity()));

        final var lectureUnitIds = lectureUnitInfoMap.keySet();

        final var completedLectureUnitIds = api.findAllCompletedLectureUnitIdsForUserByLectureUnitIds(userId, lectureUnitIds);

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
