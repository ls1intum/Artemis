package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the ExamRepository entity.
 */
@Repository
public interface ExamRepository extends JpaRepository<Exam, Long> {

    List<Exam> findByCourseId(long courseId);

    @Query("""
            SELECT ex
            FROM Exam ex LEFT JOIN FETCH ex.exerciseGroups eg LEFT JOIN FETCH eg.exercises
            WHERE ex.course.id = :courseId
            """)
    List<Exam> findByCourseIdWithExerciseGroupsAndExercises(@Param("courseId") long courseId);

    @Query("select exam from Exam exam where exam.course.testCourse = false and exam.endDate >= :#{#date} order by exam.startDate asc")
    List<Exam> findAllByEndDateGreaterThanEqual(@Param("date") ZonedDateTime date);

    @EntityGraph(type = LOAD, attributePaths = { "exerciseGroups" })
    Optional<Exam> findWithExerciseGroupsById(long examId);

    @EntityGraph(type = LOAD, attributePaths = { "exerciseGroups", "exerciseGroups.exercises" })
    Optional<Exam> findWithExerciseGroupsAndExercisesById(long examId);

    @EntityGraph(type = LOAD, attributePaths = { "registeredUsers" })
    Optional<Exam> findWithRegisteredUsersById(long examId);

    @EntityGraph(type = LOAD, attributePaths = { "registeredUsers", "exerciseGroups", "exerciseGroups.exercises" })
    Optional<Exam> findWithRegisteredUsersAndExerciseGroupsAndExercisesById(long examId);

    @EntityGraph(type = LOAD, attributePaths = { "studentExams", "studentExams.exercises" })
    Optional<Exam> findWithStudentExamsExercisesById(long id);

    @Query("""
            select distinct e
            from Exam e left join fetch e.exerciseGroups eg left join fetch eg.exercises ex
            where e.course.instructorGroupName in :#{#userGroups} and TYPE(ex) = QuizExercise
            """)
    List<Exam> getExamsWithQuizExercisesForWhichUserHasInstructorAccess(@Param("userGroups") List<String> userGroups);

    @Query("""
            select distinct e
            from Exam e left join fetch e.exerciseGroups eg left join fetch eg.exercises ex
            where TYPE(ex) = QuizExercise
            """)
    List<Exam> findAllWithQuizExercisesWithEagerExerciseGroupsAndExercises();

    // IMPORTANT: NEVER use the following EntityGraph because it will lead to crashes for exams with many users
    // The problem is that 2000 student Exams with 10 exercises and 2000 existing participations would load 2000*10*2000 = 40 mio objects
    // @EntityGraph(type = LOAD, attributePaths = { "studentExams", "studentExams.exercises", "studentExams.exercises.participations" })

    @Query("select distinct exam from Exam exam left join fetch exam.studentExams studentExams left join fetch exam.exerciseGroups exerciseGroups left join fetch exerciseGroups.exercises where (exam.id = :#{#examId})")
    Exam findOneWithEagerExercisesGroupsAndStudentExams(@Param("examId") long examId);

    /**
     * Checks if the user is registered for the exam.
     *
     * @param examId the id of the exam
     * @param userId the id of the user
     * @return true if the user is registered for the exam
     */
    @Query("SELECT CASE WHEN COUNT(exam) > 0 THEN true ELSE false END FROM Exam exam LEFT JOIN exam.registeredUsers registeredUsers WHERE exam.id = :#{#examId} AND registeredUsers.id = :#{#userId}")
    boolean isUserRegisteredForExam(@Param("examId") long examId, @Param("userId") long userId);

    @Query("select exam.id, count(registeredUsers) from Exam exam left join exam.registeredUsers registeredUsers where exam.id in :#{#examIds} group by exam.id")
    List<long[]> countRegisteredUsersByExamIds(@Param("examIds") List<Long> examIds);

    @Query("select count(studentExam) from StudentExam studentExam where studentExam.testRun = FALSE AND studentExam.exam.id = :#{#examId}")
    long countGeneratedStudentExamsByExamWithoutTestRuns(@Param("examId") long examId);

    /**
     * Returns the title of the exam with the given id
     *
     * @param examId the id of the exam
     * @return the name/title of the exam or null if the exam does not exist
     */
    @Query("""
            SELECT e.title
            FROM Exam e
            WHERE e.id = :examId
            """)
    String getExamTitle(@Param("examId") Long examId);

    @NotNull
    default Exam findByIdElseThrow(long examId) throws EntityNotFoundException {
        return findById(examId).orElseThrow(() -> new EntityNotFoundException("Exam", examId));
    }

    /**
     * Get one exam by id with exercise groups.
     *
     * @param examId the id of the entity
     * @return the exam with exercise groups
     */
    @NotNull
    default Exam findByIdWithExerciseGroupsElseThrow(long examId) {
        return findWithExerciseGroupsById(examId).orElseThrow(() -> new EntityNotFoundException("Exam", examId));
    }

    /**
     * Returns a set containing all exercises that are defined in the
     * specified exam.
     *
     * @param examId The id of the exam
     * @return A set containing the exercises
     */
    default Set<Exercise> findAllExercisesByExamId(long examId) {
        var exam = findWithExerciseGroupsAndExercisesById(examId);
        if (exam.isEmpty()) {
            return Set.of();
        }
        return exam.get().getExerciseGroups().stream().map(ExerciseGroup::getExercises).flatMap(Collection::stream).collect(Collectors.toSet());
    }

    /**
     * Get one exam by id with registered users.
     *
     * @param examId the id of the entity
     * @return the exam with registered users
     */
    @NotNull
    default Exam findByIdWithRegisteredUsersElseThrow(long examId) {
        return findWithRegisteredUsersById(examId).orElseThrow(() -> new EntityNotFoundException("Exam", examId));
    }

    /**
     * Get one exam by id with registered users and exercise groups.
     *
     * @param examId the id of the entity
     * @return the exam with registered users and exercise groups
     */
    @NotNull
    default Exam findByIdWithRegisteredUsersExerciseGroupsAndExercisesElseThrow(long examId) {
        return findWithRegisteredUsersAndExerciseGroupsAndExercisesById(examId).orElseThrow(() -> new EntityNotFoundException("Exam", examId));
    }

    /**
     * Get all exams that are held today and/or in the future
     * (does not return exams belonging to test courses).
     *
     * @return the list of all exams
     */
    default List<Exam> findAllCurrentAndUpcomingExams() {
        return findAllByEndDateGreaterThanEqual(ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS));
    }

    default Exam findWithExerciseGroupsAndExercisesByIdOrElseThrow(long examId) throws EntityNotFoundException {
        return findWithExerciseGroupsAndExercisesById(examId).orElseThrow(() -> new EntityNotFoundException("Exam", examId));
    }

    /**
     * Filters the visible exams (excluding the ones that are not visible yet)
     *
     * @param exams a set of exams (e.g. the ones of a course)
     * @return only the visible exams
     */
    default Set<Exam> filterVisibleExams(Set<Exam> exams) {
        return exams.stream().filter(exam -> Boolean.TRUE.equals(exam.isVisibleToStudents())).collect(Collectors.toSet());
    }

    /**
     * Sets the transient attribute numberOfRegisteredUsers for all given exams
     *
     * @param exams Exams for which to compute and set the number of registered users
     */
    default void setNumberOfRegisteredUsersForExams(List<Exam> exams) {
        List<Long> examIds = exams.stream().map(Exam::getId).collect(Collectors.toList());
        List<long[]> examIdAndRegisteredUsersCountPairs = countRegisteredUsersByExamIds(examIds);
        Map<Long, Integer> registeredUsersCountMap = convertListOfCountsIntoMap(examIdAndRegisteredUsersCountPairs);
        exams.forEach(exam -> exam.setNumberOfRegisteredUsers(registeredUsersCountMap.get(exam.getId()).longValue()));
    }

    /**
     * Converts List<[examId, registeredUsersCount]> into Map<examId -> registeredUsersCount>
     *
     * @param examIdAndRegisteredUsersCountPairs list of pairs (examId, registeredUsersCount)
     * @return map of exam id to registered users count
     */
    private static Map<Long, Integer> convertListOfCountsIntoMap(List<long[]> examIdAndRegisteredUsersCountPairs) {
        return examIdAndRegisteredUsersCountPairs.stream().collect(Collectors.toMap(examIdAndRegisteredUsersCountPair -> examIdAndRegisteredUsersCountPair[0], // examId
                examIdAndRegisteredUsersCountPair -> Math.toIntExact(examIdAndRegisteredUsersCountPair[1]) // registeredUsersCount
        ));
    }
}
