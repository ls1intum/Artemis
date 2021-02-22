package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.web.rest.dto.CourseExerciseStatisticsDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the Exercise entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    @Query("select e from Exercise e left join fetch e.categories where e.course.id = :#{#courseId}")
    Set<Exercise> findByCourseIdWithCategories(@Param("courseId") Long courseId);

    @Query("select e from Exercise e where e.course.id = :#{#courseId} and e.mode = 'TEAM'")
    Set<Exercise> findAllTeamExercisesByCourseId(@Param("courseId") Long courseId);

    @Query("select e from Exercise e where e.course.testCourse = false and e.dueDate >= :#{#now} order by e.dueDate asc")
    Set<Exercise> findAllExercisesWithUpcomingDueDate(@Param("now") ZonedDateTime now);

    /**
     * Select Exercise for Course ID WHERE there does exist an LtiOutcomeUrl for the current user (-> user has started exercise once using LTI)
     * @param courseId the id of the course
     * @param login the login of the corresponding user
     * @return list of exercises
     */
    @Query("select e from Exercise e where e.course.id = :#{#courseId} and exists (select l from LtiOutcomeUrl l where e = l.exercise and l.user.login = :#{#login})")
    Set<Exercise> findByCourseIdWhereLtiOutcomeUrlExists(@Param("courseId") Long courseId, @Param("login") String login);

    @Query("select distinct c from Exercise e join e.categories c where e.course.id = :#{#courseId}")
    Set<String> findAllCategoryNames(@Param("courseId") Long courseId);

    @Query("select distinct exercise from Exercise exercise left join fetch exercise.studentParticipations where exercise.id = :#{#exerciseId}")
    Optional<Exercise> findByIdWithEagerParticipations(@Param("exerciseId") Long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "categories", "teamAssignmentConfig" })
    Optional<Exercise> findWithEagerCategoriesAndTeamAssignmentConfigById(Long exerciseId);

    @Query("select distinct exercise from Exercise exercise left join fetch exercise.exampleSubmissions examplesub left join fetch examplesub.submission exsub left join fetch exsub.results where exercise.id = :#{#exerciseId}")
    Optional<Exercise> findByIdWithEagerExampleSubmissions(@Param("exerciseId") Long exerciseId);

    @Query("select distinct exercise from Exercise exercise left join fetch exercise.exerciseHints left join fetch exercise.studentQuestions left join fetch exercise.categories where exercise.id = :#{#exerciseId}")
    Optional<Exercise> findByIdWithDetailsForStudent(@Param("exerciseId") Long exerciseId);

    /**
     * calculates the average score and the participation rate of students for each given individual course exercise
     * by using the last result (rated or not)
     * @param exerciseIds - exercise ids to count the statistics for
     * @return <code>Object[]</code> where each index corresponds to the column from the db (0 refers to exerciseId and so on)
     */
    @Query("""
            SELECT
            e.id,
            AVG(r.score),
            Count(Distinct p.student.id),
            (SELECT count(distinct u.id)
            FROM User u
            WHERE
            e.course.studentGroupName member of u.groups
            AND e.course.teachingAssistantGroupName not member of u.groups
            AND e.course.instructorGroupName not member of u.groups
            )
            FROM Exercise e JOIN e.studentParticipations p JOIN p.submissions s JOIN s.results r
            WHERE e.id IN :exerciseIds
            AND e.course.studentGroupName member of p.student.groups
            AND e.course.teachingAssistantGroupName not member of p.student.groups
            AND e.course.instructorGroupName not member of p.student.groups
            AND r.score IS NOT NULL
            AND
            s.id = (
                SELECT max(s2.id)
                FROM Submission s2 JOIN s2.results r2
                WHERE s2.participation.id = s.participation.id
                AND r2.score IS NOT NULL
                )
            GROUP BY e.id
            """)
    List<Object[]> calculateStatisticsForIndividualCourseExercises(@Param("exerciseIds") List<Long> exerciseIds);

    /**
     * calculates the average score and the participation rate of students for each given team course exercise
     * by using the last result (rated or not)
     * @param exerciseIds - exercise ids to count the statistics for
     * @return <code>Object[]</code> where each index corresponds to the column from the db (0 refers to exerciseId and so on)
     */
    @Query("""
            SELECT
            e.id,
            AVG(r.score),
            Count(Distinct p.team.id),
            (SELECT count(distinct t.id)
             FROM Team t JOIN t.students st2
             WHERE st2.id IN (
                 SELECT DISTINCT u.id
                FROM User u
                WHERE
                e.course.studentGroupName member of u.groups
                AND e.course.teachingAssistantGroupName not member of u.groups
                AND e.course.instructorGroupName not member of u.groups
             )
            )
            FROM Exercise e JOIN e.studentParticipations p JOIN p.submissions s JOIN s.results r JOIN p.team.students st
            WHERE e.id IN :exerciseIds
            AND r.score IS NOT NULL
            AND
            st.id IN (
                 SELECT DISTINCT u.id
                FROM User u
                WHERE
                e.course.studentGroupName member of u.groups
                AND e.course.teachingAssistantGroupName not member of u.groups
                AND e.course.instructorGroupName not member of u.groups
             )
             AND
            s.id = (
                SELECT max(s2.id)
                FROM Submission s2 JOIN s2.results r2
                WHERE s2.participation.id = s.participation.id
                AND r2.score IS NOT NULL
                )
            GROUP BY e.id
            """)
    List<Object[]> calculateStatisticsForTeamCourseExercises(@Param("exerciseIds") List<Long> exerciseIds);

    @EntityGraph(type = LOAD, attributePaths = { "studentParticipations", "studentParticipations.student", "studentParticipations.submissions" })
    Optional<Exercise> findWithEagerStudentParticipationsStudentAndSubmissionsById(Long exerciseId);

    @NotNull
    default Exercise findByIdElseThrow(Long exerciseId) throws EntityNotFoundException {
        return findById(exerciseId).orElseThrow(() -> new EntityNotFoundException("Exercise", exerciseId));
    }

    /**
     * Get one exercise by exerciseId with its categories and its team assignment config
     *
     * @param exerciseId the exerciseId of the entity
     * @return the entity
     */
    @NotNull
    default Exercise findByIdWithCategoriesAndTeamAssignmentConfigElseThrow(Long exerciseId) {
        return findWithEagerCategoriesAndTeamAssignmentConfigById(exerciseId).orElseThrow(() -> new EntityNotFoundException("Exercise", exerciseId));
    }

    /**
     * Finds all exercises where the due date is in the future
     * (does not return exercises belonging to test courses).
     *
     * @return set of exercises
     */
    default Set<Exercise> findAllExercisesWithUpcomingDueDate() {
        return findAllExercisesWithUpcomingDueDate(ZonedDateTime.now());
    }

    /**
     * Find exercise by exerciseId and load participations in this exercise.
     *
     * @param exerciseId the exerciseId of the exercise entity
     * @return the exercise entity
     */
    @NotNull
    default Exercise findByIdWithStudentParticipationsElseThrow(Long exerciseId) {
        return findByIdWithEagerParticipations(exerciseId).orElseThrow(() -> new EntityNotFoundException("Exercise", exerciseId));
    }

    /**
     * Gets the {@link CourseExerciseStatisticsDTO} for each exercise proved in <code>exerciseIds</code>.
     *
     * calculates the average score and the participation rate of students for each given course exercise (team or individual)
     * by using the last result (rated or not)
     *
     * @param exerciseIds - list of exercise ids (must be belong to the same course)
     * @return the list of {@link CourseExerciseStatisticsDTO}
     * @throws IllegalArgumentException if exercise is not found in database, exercise is not a course exercise or not all exercises are from the same course
     */
    default List<CourseExerciseStatisticsDTO> calculateExerciseStatistics(List<Long> exerciseIds) throws IllegalArgumentException {
        List<Exercise> exercisesFromDb = new ArrayList<>();
        for (Long exerciseId : exerciseIds) {
            Exercise exerciseFromDb = findByIdElseThrow(exerciseId);

            if (!exerciseFromDb.isCourseExercise()) {
                throw new IllegalArgumentException("Exercise is not a course exercise");
            }

            exercisesFromDb.add(exerciseFromDb);
        }

        List<Long> uniqueCourseIds = exercisesFromDb.stream().map(exercise -> exercise.getCourseViaExerciseGroupOrCourseMember().getId()).distinct().collect(Collectors.toList());
        if (uniqueCourseIds.size() > 1) {
            throw new IllegalArgumentException("Not all exercises are from the same course");
        }

        List<CourseExerciseStatisticsDTO> courseExerciseStatisticsDTOs = new ArrayList<>();

        Map<Long, Object[]> exerciseIdToRawStatisticQueryData = getRawStatisticQueryData(exercisesFromDb);

        exercisesFromDb.forEach((exercise) -> {
            CourseExerciseStatisticsDTO courseExerciseStatisticsDTO = convertRawStatisticQueryDataToDTO(exerciseIdToRawStatisticQueryData, exercise);
            courseExerciseStatisticsDTOs.add(courseExerciseStatisticsDTO);

        });

        return courseExerciseStatisticsDTOs;
    }

    /**
     * Converts the row data from the exercise statistic query into the corresponding DTO
     * @param exerciseIdToRawStatisticQueryData map from exerciseId to query data
     * @param exercise exercise
     * @return converted DTO
     */
    private CourseExerciseStatisticsDTO convertRawStatisticQueryDataToDTO(Map<Long, Object[]> exerciseIdToRawStatisticQueryData, Exercise exercise) {
        CourseExerciseStatisticsDTO courseExerciseStatisticsDTO = new CourseExerciseStatisticsDTO();
        courseExerciseStatisticsDTO.setExerciseId(exercise.getId());
        courseExerciseStatisticsDTO.setExerciseTitle(exercise.getTitle());
        courseExerciseStatisticsDTO.setExerciseMaxPoints(exercise.getMaxPoints());
        courseExerciseStatisticsDTO.setExerciseMode(exercise.getMode().toString());

        if (exerciseIdToRawStatisticQueryData.containsKey(exercise.getId())) {
            Object[] exerciseStatistics = exerciseIdToRawStatisticQueryData.get(exercise.getId());
            courseExerciseStatisticsDTO.setAverageScoreInPercent(exerciseStatistics[1] != null ? ((Number) exerciseStatistics[1]).doubleValue() : 0.0);
            courseExerciseStatisticsDTO.setNoOfParticipatingStudentsOrTeams(exerciseStatistics[2] != null ? ((Number) exerciseStatistics[2]).intValue() : 0);
            int numberOfPossibleParticipants = exerciseStatistics[3] != null ? ((Number) exerciseStatistics[3]).intValue() : 0;

            if (numberOfPossibleParticipants != 0) {
                double participationRate = ((courseExerciseStatisticsDTO.getNoOfParticipatingStudentsOrTeams() * 1.0) / (numberOfPossibleParticipants * 1.0)) * 100.0;
                courseExerciseStatisticsDTO.setParticipationRateInPercent(Math.round(participationRate * 100.0) / 100.0);
            }
            else {
                courseExerciseStatisticsDTO.setParticipationRateInPercent(0.0);
            }

        }
        else {
            courseExerciseStatisticsDTO.setAverageScoreInPercent(0.0);
            courseExerciseStatisticsDTO.setParticipationRateInPercent(0.0);
            courseExerciseStatisticsDTO.setNoOfParticipatingStudentsOrTeams(0);
        }
        return courseExerciseStatisticsDTO;
    }

    /**
     * calculates the average score and the participation rate of students for each given course exercise (team or individual)
     * by using the last result (rated or not)
     * @param exercisesFromDb exercises to calculate the statistics for
     * @return Map which maps from exercise id to statistic query row data
     */
    private Map<Long, Object[]> getRawStatisticQueryData(List<Exercise> exercisesFromDb) {
        var individualExercises = exercisesFromDb.stream().filter(exercise -> exercise.getMode().equals(ExerciseMode.INDIVIDUAL)).collect(Collectors.toList());
        var teamExercises = exercisesFromDb.stream().filter(exercise -> exercise.getMode().equals(ExerciseMode.TEAM)).collect(Collectors.toList());
        var statisticIndividualExercises = calculateStatisticsForIndividualCourseExercises(individualExercises.stream().map(Exercise::getId).collect(Collectors.toList()));
        var statisticTeamExercises = calculateStatisticsForTeamCourseExercises(teamExercises.stream().map(Exercise::getId).collect(Collectors.toList()));

        List<Object[]> combinedStatistics = new ArrayList<>();
        combinedStatistics.addAll(statisticIndividualExercises);
        combinedStatistics.addAll(statisticTeamExercises);

        Map<Long, Object[]> exerciseIdToStatistic = new HashMap<>();
        for (Object[] exerciseStatistic : combinedStatistics) {
            exerciseIdToStatistic.put(((Number) exerciseStatistic[0]).longValue(), exerciseStatistic);
        }
        return exerciseIdToStatistic;
    }

    /**
     * Activates or deactivates the possibility for tutors to assess within the correction round
     *
     * @param exercise - the exercise for which we want to toggle if the second correction round is enabled
     * @return the new state of the second correction
     */
    default boolean toggleSecondCorrection(Exercise exercise) {
        exercise.setSecondCorrectionEnabled(!exercise.getSecondCorrectionEnabled());
        return save(exercise).getSecondCorrectionEnabled();
    }
}
