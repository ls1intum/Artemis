package de.tum.cit.aet.artemis.exam.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.NonNull;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.dto.calendar.ExamCalendarEventDTO;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.dto.ExamSidebarDataDTO;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * Spring Data JPA repository for the ExamRepository entity.
 */
@Conditional(ExamEnabled.class)
@Lazy
@Repository
public interface ExamRepository extends ArtemisJpaRepository<Exam, Long> {

    List<Exam> findByCourseId(long courseId);

    @Query("""
            SELECT DISTINCT exam
            FROM Exam exam
            WHERE exam.course.id IN :courses
            """)
    List<Exam> findExamsInCourses(@Param("courses") Iterable<Long> courseId);

    @Query("""
            SELECT DISTINCT ex
            FROM Exam ex
                LEFT JOIN FETCH ex.exerciseGroups eg
                LEFT JOIN FETCH eg.exercises
            WHERE ex.course.id = :courseId
            """)
    List<Exam> findByCourseIdWithExerciseGroupsAndExercises(@Param("courseId") long courseId);

    /**
     * Find all exams for a given course that are already visible to the user (either registered, at least tutor or the exam is a test exam)
     *
     * @param courseId   the course for which the exams should be retrieved
     * @param userId     the id of the user requesting the exams
     * @param groupNames the groups of the user requesting the exams
     * @param now        the current date, typically ZonedDateTime.now()
     * @return a set of all visible exams for the user in the provided courses
     */
    @Query("""
            SELECT DISTINCT e
            FROM Exam e
                LEFT JOIN e.examUsers eu
                LEFT JOIN e.course c
            WHERE c.id = :courseId
                AND e.visibleDate <= :now
                AND (
                    eu.user.id = :userId
                    OR c.teachingAssistantGroupName IN :groupNames
                    OR c.editorGroupName IN :groupNames
                    OR c.instructorGroupName IN :groupNames
                    OR e.testExam = TRUE
                )
            """)
    Set<Exam> findByCourseIdForUser(@Param("courseId") Long courseId, @Param("userId") long userId, @Param("groupNames") Set<String> groupNames, @Param("now") ZonedDateTime now);

    @Query("""
            SELECT exam
            FROM Exam exam
            WHERE exam.course.testCourse = FALSE
                AND exam.endDate >= :date
            ORDER BY exam.startDate ASC
            """)
    List<Exam> findAllByEndDateGreaterThanEqual(@Param("date") ZonedDateTime date);

    /**
     * Returns all exams visible to a user who is at least a teaching assistant in the corresponding course.
     *
     * <p>
     * The visibility window depends on the user's role <b>per course</b>:
     * <ul>
     * <li><b>Instructors</b> may see exams whose {@code visibleDate} is between
     * {@code fromDate} (typically now - 7 days) and {@code toDate} (typically now + 7 days).</li>
     * <li><b>Editors</b> and <b>teaching assistants</b> may only see exams whose {@code visibleDate}
     * lies between {@code now} (0 days offset) and {@code toDate} (typically now + 7 days).</li>
     * </ul>
     *
     * <p>
     * The lower bound is computed dynamically in the database via a JPQL {@code CASE} expression,
     * ensuring that paging is performed directly in the database and remains efficient.
     * </p>
     *
     * <p>
     * This method is database-agnostic and works on both MySQL and PostgreSQL, because all
     * temporal arithmetic is performed in Java, and the query uses only portable JPQL.
     * </p>
     *
     * @param groups   all authorization groups the user belongs to
     * @param pageable paging specification
     * @param fromDate lower bound for instructors (usually {@code now.minusDays(7)})
     * @param nowDate  lower bound for editors and tutors (usually the current timestamp)
     * @param toDate   upper bound for all roles (usually {@code now.plusDays(7)})
     * @return a paginated list of exams visible to the user according to their role
     */
    @Query("""
            SELECT e
            FROM Exam e
            WHERE
                e.visibleDate <= :toDate
                AND (
                        (e.course.instructorGroupName IN :groups
                         AND e.visibleDate >= :fromDate)
                    OR
                        ((e.course.editorGroupName IN :groups OR e.course.teachingAssistantGroupName IN :groups)
                         AND e.visibleDate >= :nowDate)
                )
            """)
    Page<Exam> findAllActiveExamsInCoursesWhereAtLeastTutor(@Param("groups") Set<String> groups, Pageable pageable, @Param("fromDate") ZonedDateTime fromDate,
            @Param("nowDate") ZonedDateTime nowDate, @Param("toDate") ZonedDateTime toDate);

    /**
     * Count all active exams
     *
     * @param now Current time
     * @return Number of active exams
     */
    @Query("""
            SELECT COUNT(exam)
            FROM Exam exam
            WHERE exam.course.testCourse = FALSE
                AND exam.visibleDate >= :now
                AND exam.endDate <= :now
            """)
    Integer countAllActiveExams(@Param("now") ZonedDateTime now);

    /**
     * Count all exams that end within the given dates.
     *
     * @param minDate the minimum due date
     * @param maxDate the maximum due date
     * @return the number of exercises ending between minDate and maxDate
     */
    @Query("""
            SELECT COUNT(exam)
            FROM Exam exam
            WHERE exam.course.testCourse = FALSE
                AND exam.endDate >= :minDate
                AND exam.endDate <= :maxDate
            """)
    Integer countExamsWithEndDateBetween(@Param("minDate") ZonedDateTime minDate, @Param("maxDate") ZonedDateTime maxDate);

    /**
     * Count all exam users for exams that end within the given dates.
     *
     * @param minDate the minimum due date
     * @param maxDate the maximum due date
     * @return the number of students registered in exams ending between minDate and maxDate
     */
    @Query("""
            SELECT COUNT(DISTINCT examUsers.user.id)
            FROM Exam exam
            JOIN exam.examUsers examUsers
            WHERE exam.course.testCourse = FALSE
                AND exam.endDate >= :minDate
                AND exam.endDate <= :maxDate
            """)
    Integer countExamUsersInExamsWithEndDateBetween(@Param("minDate") ZonedDateTime minDate, @Param("maxDate") ZonedDateTime maxDate);

    /**
     * Count all exams that start within the given dates.
     *
     * @param minDate the minimum start date
     * @param maxDate the maximum start date
     * @return the number of exercises starting between minDate and maxDate
     */
    @Query("""
            SELECT COUNT(exam)
            FROM Exam exam
            WHERE exam.course.testCourse = FALSE
                AND exam.startDate >= :minDate
                AND exam.startDate <= :maxDate
            """)
    Integer countExamsWithStartDateBetween(@Param("minDate") ZonedDateTime minDate, @Param("maxDate") ZonedDateTime maxDate);

    /**
     * Count all exam users for exams that start within the given dates.
     *
     * @param minDate the minimum start date
     * @param maxDate the maximum start date
     * @return the number of students registered in exams starting between minDate and maxDate
     */
    @Query("""
            SELECT COUNT(DISTINCT examUsers.user.id)
            FROM Exam exam
            JOIN exam.examUsers examUsers
            WHERE exam.course.testCourse = FALSE
                AND exam.startDate >= :minDate
                AND exam.startDate <= :maxDate
            """)
    Integer countExamUsersInExamsWithStartDateBetween(@Param("minDate") ZonedDateTime minDate, @Param("maxDate") ZonedDateTime maxDate);

    @EntityGraph(type = LOAD, attributePaths = { "exerciseGroups" })
    Optional<Exam> findWithExerciseGroupsById(long examId);

    @EntityGraph(type = LOAD, attributePaths = { "exerciseGroups", "exerciseGroups.exercises" })
    Optional<Exam> findWithExerciseGroupsAndExercisesById(long examId);

    @EntityGraph(type = LOAD, attributePaths = { "exerciseGroups", "exerciseGroups.exercises", "exerciseGroups.exercises.plagiarismDetectionConfig",
            "exerciseGroups.exercises.teamAssignmentConfig" })
    Optional<Exam> findWithExerciseGroupsAndExercisesAndExerciseDetailsById(long examId);

    @EntityGraph(type = LOAD, attributePaths = { "examUsers" })
    Optional<Exam> findWithExamUsersById(long examId);

    @EntityGraph(type = LOAD, attributePaths = { "examUsers", "exerciseGroups", "exerciseGroups.exercises" })
    Optional<Exam> findWithExamUsersAndExerciseGroupsAndExercisesById(long examId);

    @EntityGraph(type = LOAD, attributePaths = { "studentExams", "studentExams.exercises" })
    Optional<Exam> findWithStudentExamsExercisesById(long id);

    @Query("""
            SELECT DISTINCT e
            FROM Exam e
                LEFT JOIN FETCH e.exerciseGroups eg
                LEFT JOIN FETCH eg.exercises ex
            WHERE e.course.instructorGroupName IN :userGroups
                AND TYPE(ex) = QuizExercise
            """)
    List<Exam> getExamsWithQuizExercisesForWhichUserHasInstructorAccess(@Param("userGroups") List<String> userGroups);

    @Query("""
            SELECT DISTINCT e
            FROM Exam e
                LEFT JOIN FETCH e.exerciseGroups eg
                LEFT JOIN FETCH eg.exercises ex
            WHERE TYPE(ex) = QuizExercise
            """)
    List<Exam> findAllWithQuizExercisesWithEagerExerciseGroupsAndExercises();

    /**
     * Query which fetches all the exams for which the user is instructor in the course and matching the search criteria.
     *
     * @param searchTerm search term
     * @param groups     user groups
     * @param pageable   Pageable
     * @return Page with search results
     */
    @Query("""
            SELECT e
            FROM Exam e
            WHERE e.course.instructorGroupName IN :groups
                AND (
                    CONCAT(e.id, '') = :searchTerm
                    OR e.title LIKE %:searchTerm%
                    OR e.course.title LIKE %:searchTerm%
                )
            """)
    Page<Exam> queryBySearchTermInCoursesWhereInstructor(@Param("searchTerm") String searchTerm, @Param("groups") Set<String> groups, Pageable pageable);

    /**
     * Query which fetches all the exams with at least one exercise group for which the user is instructor in the course and matching the search criteria.
     *
     * @param searchTerm search term
     * @param groups     user groups
     * @param pageable   Pageable
     * @return Page with search results
     */
    @Query("""
            SELECT e
            FROM Exam e
            WHERE e.course.instructorGroupName IN :groups
                AND e.exerciseGroups IS NOT EMPTY
                AND (
                    CONCAT(e.id, '') = :searchTerm
                    OR e.title LIKE %:searchTerm%
                    OR e.course.title LIKE %:searchTerm%
                )
            """)
    Page<Exam> queryNonEmptyBySearchTermInCoursesWhereInstructor(@Param("searchTerm") String searchTerm, @Param("groups") Set<String> groups, Pageable pageable);

    /**
     * Query which fetches all the exams for an admin and matching the search criteria.
     *
     * @param searchTerm search term
     * @param pageable   Pageable
     * @return Page with search results
     */
    @Query("""
            SELECT e
            FROM Exam e
            WHERE (
                CONCAT(e.id, '') = :searchTerm
                OR e.title LIKE %:searchTerm%
                OR e.course.title LIKE %:searchTerm%
            )
            """)
    Page<Exam> queryBySearchTermInAllCourses(@Param("searchTerm") String searchTerm, Pageable pageable);

    /**
     * Query which fetches all the exams with at least one exercise Group for an admin and matching the search criteria.
     *
     * @param searchTerm search term
     * @param pageable   Pageable
     * @return Page with search results
     */
    @Query("""
            SELECT e
            FROM Exam e
            WHERE e.exerciseGroups IS NOT EMPTY
                AND (
                    CONCAT(e.id, '') = :searchTerm
                    OR e.title LIKE %:searchTerm%
                    OR e.course.title LIKE %:searchTerm%
                )
            """)
    Page<Exam> queryNonEmptyBySearchTermInAllCourses(@Param("searchTerm") String searchTerm, Pageable pageable);

    // IMPORTANT: NEVER use the following EntityGraph because it will lead to crashes for exams with many users
    // The problem is that 2000 student Exams with 10 exercises and 2000 existing participations would load 2000*10*2000 = 40 mio objects
    // @EntityGraph(type = LOAD, attributePaths = { "studentExams", "studentExams.exercises", "studentExams.exercises.participations" })

    @Query("""
            SELECT exam
            FROM Exam exam
                LEFT JOIN FETCH exam.studentExams studentExams
                LEFT JOIN FETCH exam.exerciseGroups exerciseGroups
                LEFT JOIN FETCH exerciseGroups.exercises
            WHERE exam.id = :examId
            """)
    Exam findOneWithEagerExercisesGroupsAndStudentExams(@Param("examId") long examId);

    /**
     * Checks if the user is registered for the exam.
     *
     * @param examId the id of the exam
     * @param userId the id of the user
     * @return true if the user is registered for the exam
     */
    @Query("""
            SELECT COUNT(exam) > 0
            FROM Exam exam
                LEFT JOIN exam.examUsers examUsers
            WHERE exam.id = :examId
                AND examUsers.user.id = :userId
            """)
    boolean isUserRegisteredForExam(@Param("examId") long examId, @Param("userId") long userId);

    @Query("""
            SELECT exam.id, COUNT(registeredUsers)
            FROM Exam exam
                LEFT JOIN exam.examUsers registeredUsers
            WHERE exam.id IN :examIds
            GROUP BY exam.id
            """)
    List<long[]> countExamUsersByExamIds(@Param("examIds") List<Long> examIds);

    @Query("""
            SELECT COUNT(studentExam)
            FROM StudentExam studentExam
            WHERE studentExam.testRun = FALSE
                AND studentExam.exam.id = :examId
            """)
    long countGeneratedStudentExamsByExamWithoutTestRuns(@Param("examId") long examId);

    long countByCourse_Id(long courseId);

    /**
     * Returns the title of the exam with the given id.
     *
     * @param examId the id of the exam
     * @return the name/title of the exam or null if the exam does not exist
     */
    @Query("""
            SELECT e.title
            FROM Exam e
            WHERE e.id = :examId
            """)
    @Cacheable(cacheNames = "examTitle", key = "#examId", unless = "#result == null")
    String getExamTitle(@Param("examId") long examId);

    @Query("""
            SELECT e
            FROM Exam e
            WHERE e.course.id = :courseId
                AND e.visibleDate <= :now
            """)
    Set<Exam> findAllVisibleByCourseId(@Param("courseId") long courseId, @Param("now") ZonedDateTime now);

    /**
     * Get one exam by id with exercise groups.
     *
     * @param examId the id of the entity
     * @return the exam with exercise groups
     */
    @NonNull
    default Exam findByIdWithExerciseGroupsElseThrow(long examId) {
        return getValueElseThrow(findWithExerciseGroupsById(examId), examId);
    }

    /**
     * Returns a set containing all exercises that are defined in the
     * specified exam including their details.
     *
     * @param examId The id of the exam
     * @return A set containing the exercises
     */
    default Set<Exercise> findAllExercisesWithDetailsByExamId(long examId) {
        var exam = findWithExerciseGroupsAndExercisesAndExerciseDetailsById(examId);
        return exam.map(value -> value.getExerciseGroups().stream().map(ExerciseGroup::getExercises).flatMap(Collection::stream).collect(Collectors.toSet())).orElseGet(Set::of);
    }

    /**
     * Get one exam by id with registered users.
     *
     * @param examId the id of the entity
     * @return the exam with registered users
     */
    @NonNull
    default Exam findByIdWithExamUsersElseThrow(long examId) {
        return getValueElseThrow(findWithExamUsersById(examId), examId);
    }

    /**
     * Get one exam by id with registered users and exercise groups.
     *
     * @param examId the id of the entity
     * @return the exam with registered users and exercise groups
     */
    @NonNull
    default Exam findByIdWithExamUsersExerciseGroupsAndExercisesElseThrow(long examId) {
        return getValueElseThrow(findWithExamUsersAndExerciseGroupsAndExercisesById(examId), examId);
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
        return getValueElseThrow(findWithExerciseGroupsAndExercisesById(examId), examId);
    }

    /**
     * Filters the visible exams (excluding the ones that are not visible yet)
     *
     * @param exams a set of exams (e.g. the ones of a course)
     * @return only the visible exams
     */
    default Set<Exam> filterVisibleExams(Set<Exam> exams) {
        return exams.stream().filter(Exam::isVisibleToStudents).collect(Collectors.toSet());
    }

    /**
     * Sets the transient attribute numberOfRegisteredUsers for all given exams
     *
     * @param exams Exams for which to compute and set the number of registered users
     */
    default void setNumberOfExamUsersForExams(List<Exam> exams) {
        List<Long> examIds = exams.stream().map(Exam::getId).toList();
        List<long[]> examIdAndRegisteredUsersCountPairs = countExamUsersByExamIds(examIds);
        Map<Long, Integer> registeredUsersCountMap = convertListOfCountsIntoMap(examIdAndRegisteredUsersCountPairs);
        exams.forEach(exam -> exam.setNumberOfExamUsers(registeredUsersCountMap.get(exam.getId()).longValue()));
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

    @Query("""
            SELECT e
            FROM Exam e
                LEFT JOIN e.examUsers registeredUsers
            WHERE e.course.id IN :courseIds
                AND e.visibleDate <= :visible
                AND e.endDate >= :end
                AND e.testExam = FALSE
                AND registeredUsers.user.id = :userId
            """)
    Set<Exam> findActiveExams(@Param("courseIds") Set<Long> courseIds, @Param("userId") long userId, @Param("visible") ZonedDateTime visible, @Param("end") ZonedDateTime end);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.calendar.ExamCalendarEventDTO(
                exam.id,
                exam.title,
                exam.visibleDate,
                exam.startDate,
                exam.endDate,
                exam.publishResultsDate,
                exam.examStudentReviewStart,
                exam.examStudentReviewEnd,
                exam.examiner
            )
            FROM Exam exam
            WHERE exam.course.id = :courseId
            """)
    Set<ExamCalendarEventDTO> getExamCalendarEventDTOsForCourseId(@Param("courseId") long courseId);

    @Query("""
            SELECT DISTINCT new de.tum.cit.aet.artemis.exam.dto.ExamSidebarDataDTO(
                exam.title,
                exam.id,
                exam.moduleNumber,
                exam.startDate,
                se.workingTime,
                exam.examMaxPoints
            )
            FROM Exam exam
            JOIN exam.studentExams se
            WHERE exam.course.id = :courseId
              AND se.user.id  = :studentId
              AND exam.testExam  = FALSE
              AND exam.visibleDate <= :now
            """)

    Set<ExamSidebarDataDTO> findSidebarDataForRealStudentExamsByCourseId(@Param("courseId") long courseId, @Param("now") ZonedDateTime now, @Param("studentId") long studentId);

    @Query("""
            SELECT DISTINCT exam.id
            FROM Exam exam
            WHERE exam.course.id = :courseId
            """)
    Set<Long> findExamIdsByCourseId(@Param("courseId") long courseId);

    @Query("""
            SELECT DISTINCT ex.id
            FROM Exam exam
                JOIN exam.exerciseGroups eg
                JOIN eg.exercises ex
            WHERE exam.id = :examId
            """)
    Set<Long> findExerciseIdsByExamId(@Param("examId") Long examId);

    @Query("""
            SELECT ex.numberOfCorrectionRoundsInExam
            FROM Exam ex
            WHERE ex.id = :examId
            """)
    int findNumberOfCorrectionRoundsByExamId(@Param("examId") Long examId);
}
