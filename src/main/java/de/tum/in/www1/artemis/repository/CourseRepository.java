package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.domain.enumeration.AssessmentType.AUTOMATIC;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the Course entity.
 */
@SuppressWarnings("unused")
@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    @Query("select distinct course.teachingAssistantGroupName from Course course")
    Set<String> findAllTeachingAssistantGroupNames();

    @Query("select distinct course.instructorGroupName from Course course")
    Set<String> findAllInstructorGroupNames();

    @Query("select distinct course from Course course where course.instructorGroupName like :#{#name}")
    Course findCourseByInstructorGroupName(@Param("name") String name);

    @Query("select distinct course from Course course where course.studentGroupName like :#{#name}")
    Course findCourseByStudentGroupName(@Param("name") String name);

    @Query("select distinct course from Course course where course.teachingAssistantGroupName like :#{#name}")
    Course findCourseByTeachingAssistantGroupName(@Param("name") String name);

    @Query("select distinct course from Course course where (course.startDate <= :#{#now} or course.startDate is null) and (course.endDate >= :#{#now} or course.endDate is null)")
    List<Course> findAllActive(@Param("now") ZonedDateTime now);

    @EntityGraph(type = LOAD, attributePaths = { "lectures", "lectures.attachments", "exams" })
    @Query("select distinct course from Course course where (course.startDate <= :#{#now} or course.startDate is null) and (course.endDate >= :#{#now} or course.endDate is null)")
    List<Course> findAllActiveWithLecturesAndExams(@Param("now") ZonedDateTime now);

    @EntityGraph(type = LOAD, attributePaths = { "lectures", "lectures.attachments", "exams" })
    Optional<Course> findWithEagerLecturesAndExamsById(long courseId);

    // Note: this is currently only used for testing purposes
    @Query("select distinct course from Course course left join fetch course.exercises exercises left join fetch course.lectures lectures left join fetch lectures.attachments left join fetch exercises.categories where (course.startDate <= :#{#now} or course.startDate is null) and (course.endDate >= :#{#now} or course.endDate is null)")
    List<Course> findAllActiveWithEagerExercisesAndLectures(@Param("now") ZonedDateTime now);

    @EntityGraph(type = LOAD, attributePaths = { "exercises", "exercises.categories", "exercises.teamAssignmentConfig" })
    Course findWithEagerExercisesById(long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "learningGoals" })
    Optional<Course> findWithEagerLearningGoalsById(long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "exercises", "lectures" })
    Course findWithEagerExercisesAndLecturesById(long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "exercises", "lectures", "lectures.lectureUnits", "learningGoals" })
    Course findWithEagerExercisesAndLecturesAndLectureUnitsAndLearningGoalsById(long courseId);

    @Query("select distinct course from Course course where (course.startDate is null or course.startDate <= :#{#now}) and (course.endDate is null or course.endDate >= :#{#now}) and course.onlineCourse = false and course.registrationEnabled = true")
    List<Course> findAllCurrentlyActiveNotOnlineAndRegistrationEnabled(@Param("now") ZonedDateTime now);

    List<Course> findAllByShortName(String shortName);

    Optional<Course> findById(long courseId);

    @Query("""
            select distinct c
            from Course c left join fetch c.exercises e
            where c.instructorGroupName in :#{#userGroups} and TYPE(e) = QuizExercise
            """)
    List<Course> getCoursesWithQuizExercisesForWhichUserHasInstructorAccess(@Param("userGroups") List<String> userGroups);

    @Query("""
            select distinct c
            from Course c left join fetch c.exercises e
            where TYPE(e) = QuizExercise
            """)
    List<Course> findAllWithQuizExercisesWithEagerExercises();

    /**
     * Returns the student group name of a single course
     *
     * @param courseId the course id of the course to get the name for
     * @return the student group name
     */
    @Query("""
            SELECT c.studentGroupName
            FROM Course c
            WHERE c.id = :courseId
            """)
    String findStudentGroupName(@Param("courseId") long courseId);

    /**
     * Get active students in the timeframe from startDate to endDate for the exerciseIds
     *
     * @param exerciseIds exerciseIds from all exercises to get the statistics for
     * @param startDate the starting date of the query
     * @param endDate the end date for the query
     * @return A list with a map for every submission containing date and the username
     */
    @Query("""
            SELECT s.submissionDate AS day, p.student.login AS username
            FROM StudentParticipation p JOIN p.submissions s
            WHERE p.exercise.id IN :exerciseIds
                AND s.submissionDate >= :#{#startDate}
                AND s.submissionDate <= :#{#endDate}
            """)
    List<Map<String, Object>> getActiveStudents(@Param("exerciseIds") List<Long> exerciseIds, @Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    /**
     * Fetches the courses to display for the management overview
     *
     * @param now ZonedDateTime of the current time. If an end date is set only courses before this time are returned. May be null to return all
     * @param isAdmin whether the user to fetch the courses for is an admin (which gets all courses)
     * @param userGroups the user groups of the user to fetch the courses for (ignored if the user is an admin)
     * @return a list of courses for the overview
     */
    @Query("""
            SELECT c
            FROM Course c
            WHERE (c.endDate IS NULL OR :#{#now} IS NULL OR c.endDate >= :#{#now})
                AND (:isAdmin = TRUE OR c.teachingAssistantGroupName IN :userGroups OR c.instructorGroupName IN :userGroups)
            """)
    List<Course> getAllCoursesForManagementOverview(@Param("now") ZonedDateTime now, @Param("isAdmin") boolean isAdmin, @Param("userGroups") List<String> userGroups);

    @NotNull
    default Course findByIdElseThrow(Long courseId) throws EntityNotFoundException {
        return findById(courseId).orElseThrow(() -> new EntityNotFoundException("Course", courseId));
    }

    default Course findByIdWithEagerExercisesElseThrow(Long courseId) throws EntityNotFoundException {
        return Optional.ofNullable(findWithEagerExercisesById(courseId)).orElseThrow(() -> new EntityNotFoundException("Course", courseId));
    }

    /**
     * filters the passed exercises for the relevant ones that need to be manually assessed. This excludes quizzes and automatic programming exercises
     *
     * @param exercises all exercises (e.g. of a course or exercise group) that should be filtered
     * @return the filtered and relevant exercises for manual assessment
     */
    default Set<Exercise> getInterestingExercisesForAssessmentDashboards(Set<Exercise> exercises) {
        return exercises.stream().filter(exercise -> exercise instanceof TextExercise || exercise instanceof ModelingExercise || exercise instanceof FileUploadExercise
                || (exercise instanceof ProgrammingExercise && exercise.getAssessmentType() != AUTOMATIC)).collect(Collectors.toSet());
    }

    /**
     * Get all the courses.
     *
     * @return the list of entities
     */
    default List<Course> findAllActiveWithLecturesAndExams() {
        return findAllActiveWithLecturesAndExams(ZonedDateTime.now());
    }

    /**
     * Get all the courses.
     *
     * @return the list of entities
     */
    default List<Course> findAllCurrentlyActiveNotOnlineAndRegistrationEnabled() {
        return findAllCurrentlyActiveNotOnlineAndRegistrationEnabled(ZonedDateTime.now());
    }

    /**
     * Get one course by id.
     *
     * @param courseId the id of the entity
     * @return the entity
     */
    @NotNull
    default Course findByIdWithLecturesAndExamsElseThrow(Long courseId) {
        return findWithEagerLecturesAndExamsById(courseId).orElseThrow(() -> new EntityNotFoundException("Course", courseId));
    }
}
