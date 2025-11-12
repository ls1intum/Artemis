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
import org.springframework.context.annotation.Lazy;
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
@Lazy
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
     * Aggregates all available course metrics for a specific student.
     * <p>
     * This method consolidates exercise, lecture unit, and competency metrics for the given student in the specified course.
     * The result provides a comprehensive overview of the student’s engagement and performance across all relevant course components.
     *
     * @param userId   the unique identifier of the student
     * @param courseId the unique identifier of the course
     * @return a {@link StudentMetricsDTO} containing exercise metrics, lecture unit metrics, and competency metrics for the student in the course
     */
    public StudentMetricsDTO getStudentCourseMetrics(long userId, long courseId) {
        final var exerciseMetricsDTO = getStudentExerciseMetrics(userId, courseId);
        final var lectureUnitMetricsDTO = getStudentLectureUnitMetrics(userId, courseId);
        final var competencyMetricsDTO = getStudentCompetencyMetrics(userId, courseId);
        return new StudentMetricsDTO(exerciseMetricsDTO, lectureUnitMetricsDTO, competencyMetricsDTO);
    }

    /**
     * Computes and retrieves exercise metrics for a specific student in a given course.
     * <p>
     * This method aggregates multiple metrics, such as exercise information, categories,
     * scores, latest submissions, and completion status, by querying the database
     * and processing the results in memory.
     *
     * @param userId   the unique identifier of the student
     * @param courseId the unique identifier of the course
     * @return an {@link ExerciseStudentMetricsDTO} containing all relevant exercise metrics for the student in the course
     */
    public ExerciseStudentMetricsDTO getStudentExerciseMetrics(long userId, long courseId) {
        // Predicate to check if an exercise has started (start date in the past).
        final Predicate<ExerciseInformationDTO> started = e -> e.start() != null && e.start().isBefore(ZonedDateTime.now());
        // Defensive: Also filter in Java to guarantee only started exercises, even if the DB query should already do this.
        final var exerciseInfo = exerciseMetricsRepository.findAllStartedExerciseInformationByCourseId(courseId).stream().filter(started).collect(Collectors.toSet());

        // Map exercise IDs to their ExerciseInformationDTOs for fast access.
        final var exerciseInfoMap = exerciseInfo.stream().collect(toMap(ExerciseInformationDTO::id, identity()));

        // Collect all relevant exercise IDs.
        final var exerciseIds = exerciseInfo.stream().map(ExerciseInformationDTO::id).collect(toSet());

        // Fetch and group all categories for these exercises.
        final Set<Entry<Long, String>> categories = !exerciseIds.isEmpty() ? exerciseMetricsRepository.findCategoriesByExerciseIds(exerciseIds) : Set.of();
        final var categoryMap = categories.stream().collect(groupingBy(Entry::getKey, mapping(Entry::getValue, toSet())));

        // Fetch and map average score for each exercise.
        final Set<ScoreDTO> averageScore = !exerciseIds.isEmpty() ? exerciseMetricsRepository.findAverageScore(exerciseIds) : Set.of();
        final var averageScoreMap = averageScore.stream().collect(toMap(ScoreDTO::exerciseId, ScoreDTO::score));

        // Fetch and map the user's actual score for each exercise.
        final Set<ScoreDTO> score = !exerciseIds.isEmpty() ? exerciseMetricsRepository.findScore(exerciseIds, userId) : Set.of();
        final var scoreMap = score.stream().collect(toMap(ScoreDTO::exerciseId, ScoreDTO::score));

        final Predicate<ExerciseInformationDTO> hasDueDate = exercise -> exercise.due() != null;
        final var exerciseIdsWithDueDate = exerciseInfo.stream().filter(hasDueDate).map(ExerciseInformationDTO::id).collect(toSet());

        // Compute average relative submission times for all exercises with a due date.
        final Set<ResourceTimestampDTO> latestSubmissions = !exerciseIdsWithDueDate.isEmpty() ? exerciseMetricsRepository.findLatestSubmissionDates(exerciseIdsWithDueDate)
                : Set.of();
        final ToDoubleFunction<ResourceTimestampDTO> relativeTime = dto -> toRelativeTime(exerciseInfoMap.get(dto.exerciseId()).start(),
                exerciseInfoMap.get(dto.exerciseId()).due(), dto.timestamp());
        final var averageLatestSubmissionMap = latestSubmissions.stream().collect(groupingBy(ResourceTimestampDTO::exerciseId, averagingDouble(relativeTime)));

        // Partition exercises into individual and team, with due date.
        final var individualExerciseIdsWithDueDate = exerciseInfo.stream().filter(ExerciseInformationDTO::isIndividual).filter(hasDueDate).map(ExerciseInformationDTO::id)
                .collect(toSet());
        final var teamExerciseIdsWithDueDate = exerciseInfo.stream().filter(ExerciseInformationDTO::isTeam).filter(hasDueDate).map(ExerciseInformationDTO::id).collect(toSet());

        // For performance: Fetch latest submissions for individual and team exercises separately, avoid OR in DB query (which would require a full table scan).
        final Set<ResourceTimestampDTO> latestIndividualSubmissionOfUser = !individualExerciseIdsWithDueDate.isEmpty()
                ? exerciseMetricsRepository.findLatestIndividualSubmissionDatesForUser(individualExerciseIdsWithDueDate, userId)
                : Set.of();
        final Set<ResourceTimestampDTO> latestTeamSubmissionOfUser = !teamExerciseIdsWithDueDate.isEmpty()
                ? exerciseMetricsRepository.findLatestTeamSubmissionDatesForUser(teamExerciseIdsWithDueDate, userId)
                : Set.of();
        // Combine individual and team latest submissions for the user.
        final var latestSubmissionOfUser = Sets.union(latestIndividualSubmissionOfUser, latestTeamSubmissionOfUser);
        final var latestSubmissionMap = latestSubmissionOfUser.stream().collect(toMap(ResourceTimestampDTO::exerciseId, relativeTime::applyAsDouble));

        // Find all completed exercises for the user, based on a minimum score threshold.
        final Set<Long> completedExerciseIds = !exerciseIds.isEmpty()
                ? exerciseMetricsRepository.findAllCompletedExerciseIdsForUserByExerciseIds(userId, exerciseIds, MIN_SCORE_GREEN)
                : Set.of();

        // Map team exercise IDs to the user's team ID (if any).
        final Set<MapEntryLongLong> teamIds = !exerciseIds.isEmpty() ? exerciseMetricsRepository.findTeamIdsForUserByExerciseIds(userId, exerciseIds) : Set.of();
        final var teamIdMap = teamIds.stream().collect(toMap(MapEntryLongLong::key, MapEntryLongLong::value));

        // Assemble and return all computed metrics.
        return new ExerciseStudentMetricsDTO(exerciseInfoMap, categoryMap, averageScoreMap, scoreMap, averageLatestSubmissionMap, latestSubmissionMap, completedExerciseIds,
                teamIdMap);
    }

    /**
     * Retrieves aggregated lecture unit metrics for a specific student in a given course.
     * <p>
     * This method queries the underlying lecture unit repository for all lecture units of normal lectures (not tutorial lectures) associated
     * with the specified course, and identifies which units have been completed by the student.
     * The resulting metrics provide a structured overview of the student's progress with respect to lecture units.
     *
     * @param userId   the unique identifier of the student
     * @param courseId the unique identifier of the course
     * @return a {@link LectureUnitStudentMetricsDTO} containing metadata for all lecture units in the course,
     *         and the set of lecture unit IDs the student has completed
     * @throws LectureApiNotPresentException if the lecture unit repository API is not available
     */
    public LectureUnitStudentMetricsDTO getStudentLectureUnitMetrics(long userId, long courseId) {
        LectureUnitRepositoryApi api = lectureUnitRepositoryApi.orElseThrow(() -> new LectureApiNotPresentException(LectureUnitRepositoryApi.class));

        final var lectureUnitInfo = api.findAllNormalLectureUnitInformationByCourseId(courseId);
        final var lectureUnitInfoMap = lectureUnitInfo.stream().collect(toMap(LectureUnitInformationDTO::id, identity()));

        final var lectureUnitIds = lectureUnitInfoMap.keySet();

        final var completedLectureUnitIds = api.findAllCompletedLectureUnitIdsForUserByLectureUnitIds(userId, lectureUnitIds);

        return new LectureUnitStudentMetricsDTO(lectureUnitInfoMap, completedLectureUnitIds);
    }

    /**
     * Retrieves comprehensive competency metrics for a student within a specific course.
     * <p>
     * This method aggregates competency information, links each competency to related exercises and lecture units,
     * and collects the student's progress, confidence, and most recent self-assessment (JOL) values.
     * Both current and prior JOL values are included to enable tracking of changes in self-assessment over time.
     *
     * @param userId   the unique identifier of the student
     * @param courseId the unique identifier of the course
     * @return a {@link CompetencyStudentMetricsDTO} containing detailed competency metadata,
     *         mappings to exercises and lecture units, student progress, confidence, and current/prior JOL values
     */
    public CompetencyStudentMetricsDTO getStudentCompetencyMetrics(long userId, long courseId) {
        final var competencyInfo = competencyMetricsRepository.findAllCompetencyInformationByCourseId(courseId);
        final var competencyInfoMap = competencyInfo.stream().collect(toMap(CompetencyInformationDTO::id, identity()));

        final var competencyIds = competencyInfoMap.keySet();

        // Map each competency to the set of related exercise IDs
        final var competencyExerciseMapEntries = competencyMetricsRepository.findAllExerciseIdsByCompetencyIds(competencyIds);
        final var exerciseMap = competencyExerciseMapEntries.stream().collect(groupingBy(MapEntryLongLong::key, mapping(MapEntryLongLong::value, toSet())));

        // Map each competency to the set of related lecture unit IDs
        final var competencyLectureUnitMapEntries = competencyMetricsRepository.findAllLectureUnitIdsByCompetencyIds(competencyIds);
        final var lectureUnitMap = competencyLectureUnitMapEntries.stream().collect(groupingBy(MapEntryLongLong::key, mapping(MapEntryLongLong::value, toSet())));

        // Gather student progress and confidence for each competency
        final var competencyProgress = competencyMetricsRepository.findAllCompetencyProgressForUserByCompetencyIds(userId, competencyIds);
        final var competencyProgressMap = competencyProgress.stream().collect(toMap(CompetencyProgressDTO::competencyId, CompetencyProgressDTO::progress));
        final var competencyConfidenceMap = competencyProgress.stream().collect(toMap(CompetencyProgressDTO::competencyId, CompetencyProgressDTO::confidence));

        // Get latest self-assessment (JOL) values and separate from prior JOLs
        final var currentJolValues = competencyMetricsRepository.findAllLatestCompetencyJolValuesForUserByCompetencyIds(userId, competencyIds);
        final var currentJolValuesMap = currentJolValues.stream().collect(toMap(CompetencyJolDTO::competencyId, identity()));

        final var currentJolIds = currentJolValues.stream().map(CompetencyJolDTO::id).collect(toSet());
        final var priorJolValues = competencyMetricsRepository.findAllLatestCompetencyJolValuesForUserByCompetencyIdsExcludeJolIds(userId, competencyIds, currentJolIds);
        final var priorJolValuesMap = priorJolValues.stream().collect(toMap(CompetencyJolDTO::competencyId, identity()));

        return new CompetencyStudentMetricsDTO(competencyInfoMap, exerciseMap, lectureUnitMap, competencyProgressMap, competencyConfidenceMap, currentJolValuesMap,
                priorJolValuesMap);
    }
}
