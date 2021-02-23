package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the ExamRepository entity.
 */
@Repository
public interface ExamRepository extends JpaRepository<Exam, Long> {

    List<Exam> findByCourseId(Long courseId);

    @Query("select exam from Exam exam where exam.course.testCourse = false and exam.startDate >= :#{#date} order by exam.startDate asc")
    List<Exam> findAllByStartDateGreaterThanEqual(@Param("date") ZonedDateTime date);

    @EntityGraph(type = LOAD, attributePaths = { "exerciseGroups" })
    Optional<Exam> findWithExerciseGroupsById(Long examId);

    @EntityGraph(type = LOAD, attributePaths = { "exerciseGroups", "exerciseGroups.exercises" })
    Optional<Exam> findWithExerciseGroupsAndExercisesById(Long examId);

    @EntityGraph(type = LOAD, attributePaths = { "registeredUsers" })
    Optional<Exam> findWithRegisteredUsersById(Long examId);

    @EntityGraph(type = LOAD, attributePaths = { "registeredUsers", "exerciseGroups", "exerciseGroups.exercises" })
    Optional<Exam> findWithRegisteredUsersAndExerciseGroupsAndExercisesById(Long examId);

    @EntityGraph(type = LOAD, attributePaths = { "studentExams" })
    Optional<Exam> findWithStudentExamsById(Long examId);

    @EntityGraph(type = LOAD, attributePaths = { "studentExams", "studentExams.exercises" })
    Optional<Exam> findWithStudentExamsExercisesById(Long id);

    @Query("""
            select distinct e
            from Exam e left join fetch e.exerciseGroups eg left join fetch eg.exercises ex, User u
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
    long countGeneratedStudentExamsByExamWithoutTestRuns(@Param("examId") Long examId);

    @NotNull
    default Exam findByIdElseThrow(Long examId) throws EntityNotFoundException {
        return findById(examId).orElseThrow(() -> new EntityNotFoundException("Exam", examId));
    }

    /**
     * Get one exam by id with exercise groups.
     *
     * @param examId the id of the entity
     * @return the exam with exercise groups
     */
    @NotNull
    default Exam findByIdWithExerciseGroupsElseThrow(Long examId) {
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
    default Exam findByIdWithRegisteredUsersElseThrow(Long examId) {
        return findWithRegisteredUsersById(examId).orElseThrow(() -> new EntityNotFoundException("Exam", examId));
    }

    /**
     * Get one exam by id with registered users and exercise groups.
     *
     * @param examId the id of the entity
     * @return the exam with registered users and exercise groups
     */
    @NotNull
    default Exam findByIdWithRegisteredUsersExerciseGroupsAndExercisesElseThrow(Long examId) {
        return findWithRegisteredUsersAndExerciseGroupsAndExercisesById(examId).orElseThrow(() -> new EntityNotFoundException("Exam", examId));
    }

    /**
     * Get all exams that are held today and/or in the future
     * (does not return exams belonging to test courses).
     *
     * @return the list of all exams
     */
    default List<Exam> findAllCurrentAndUpcomingExams() {
        return findAllByStartDateGreaterThanEqual(ZonedDateTime.now());
    }

    /**
     * Finds an exam based on the id with all student exams which are not marked as test runs.
     *
     * @param examId the id of the exam
     * @return the exam with student exams loaded
     */
    @NotNull
    default Exam findByIdWithStudentExamsElseThrow(long examId) {
        Exam exam = findWithStudentExamsById(examId).orElseThrow(() -> new EntityNotFoundException("Exam with id " + examId + " does not exist"));
        // drop all test runs and set the remaining student exams to the exam
        exam.setStudentExams(exam.getStudentExams().stream().dropWhile(StudentExam::isTestRun).collect(Collectors.toSet()));
        return exam;
    }
}
