package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.domain.enumeration.AssessmentType.AUTOMATIC;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.statistics.StatisticsEntry;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the Course entity.
 */
@SuppressWarnings("unused")
@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    @Query("select distinct course.instructorGroupName from Course course")
    Set<String> findAllInstructorGroupNames();

    @Query("select distinct course.editorGroupName from Course course")
    Set<String> findAllEditorGroupNames();

    @Query("select distinct course.teachingAssistantGroupName from Course course")
    Set<String> findAllTeachingAssistantGroupNames();

    @Query("select distinct course from Course course where course.instructorGroupName like :#{#name}")
    Course findCourseByInstructorGroupName(@Param("name") String name);

    @Query("select distinct course from Course course where course.editorGroupName like :#{#name}")
    Course findCourseByEditorGroupName(@Param("name") String name);

    @Query("select distinct course from Course course where course.teachingAssistantGroupName like :#{#name}")
    Course findCourseByTeachingAssistantGroupName(@Param("name") String name);

    @Query("select distinct course from Course course where course.studentGroupName like :#{#name}")
    Course findCourseByStudentGroupName(@Param("name") String name);

    @Query("""
            SELECT DISTINCT c FROM Course c
            WHERE (c.startDate <= :now
            	OR c.startDate IS NULL)
            AND (c.endDate >= :now
            	OR c.endDate IS NULL)
            """)
    List<Course> findAllActive(@Param("now") ZonedDateTime now);

    @EntityGraph(type = LOAD, attributePaths = { "lectures", "lectures.attachments", "exams" })
    @Query("""
            SELECT DISTINCT c FROM Course c
            WHERE (c.startDate <= :now
            	OR c.startDate IS NULL)
            AND (c.endDate >= :now
            	OR c.endDate IS NULL)
            """)
    List<Course> findAllActiveWithLecturesAndExams(@Param("now") ZonedDateTime now);

    @Query("""
            SELECT DISTINCT c FROM Course c
            LEFT JOIN FETCH c.tutorialGroups tutorialGroups
            LEFT JOIN FETCH tutorialGroups.teachingAssistant tutor
            LEFT JOIN FETCH tutorialGroups.registrations registrations
            LEFT JOIN FETCH registrations.student student
            WHERE (c.startDate <= :#{#now}
            	OR c.startDate IS NULL)
            AND (c.endDate >= :#{#now}
            	OR c.endDate IS NULL)
            AND (student.id = :#{#userId} OR tutor.id = :#{#userId})
            """)
    List<Course> findAllActiveWithTutorialGroupsWhereUserIsRegisteredOrTutor(@Param("now") ZonedDateTime now, @Param("userId") Long userId);

    @EntityGraph(type = LOAD, attributePaths = { "lectures", "lectures.attachments", "exams" })
    Optional<Course> findWithEagerLecturesAndExamsById(long courseId);

    // Note: this is currently only used for testing purposes
    @Query("""
            SELECT DISTINCT c FROM Course c
            LEFT JOIN FETCH c.exercises exercises
            LEFT JOIN FETCH c.lectures lectures
            LEFT JOIN FETCH lectures.attachments
                LEFT JOIN FETCH exercises.categories
                WHERE (c.startDate <= :now
                	OR c.startDate IS NULL)
                AND (c.endDate >= :now
                	OR c.endDate IS NULL)
                """)
    List<Course> findAllActiveWithEagerExercisesAndLectures(@Param("now") ZonedDateTime now);

    @EntityGraph(type = LOAD, attributePaths = { "exercises", "exercises.categories", "exercises.teamAssignmentConfig" })
    Course findWithEagerExercisesById(long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "learningGoals", "prerequisites" })
    Optional<Course> findWithEagerLearningGoalsById(long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "lectures" })
    Optional<Course> findWithEagerLecturesById(long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "exercises", "lectures" })
    Optional<Course> findWithEagerExercisesAndLecturesById(long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "lectures", "lectures.lectureUnits" })
    Optional<Course> findWithEagerLecturesAndLectureUnitsById(long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "organizations", "learningGoals", "prerequisites" })
    Optional<Course> findWithEagerOrganizationsAndLearningGoalsById(long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "exercises", "lectures", "lectures.lectureUnits", "learningGoals", "prerequisites" })
    Optional<Course> findWithEagerExercisesAndLecturesAndLectureUnitsAndLearningGoalsById(long courseId);

    @Query("select distinct course from Course course left join fetch course.organizations organizations left join fetch course.prerequisites prerequisites where (course.startDate is null or course.startDate <= :now) and (course.endDate is null or course.endDate >= :now) and course.onlineCourse = false and course.registrationEnabled = true")
    List<Course> findAllCurrentlyActiveNotOnlineAndRegistrationEnabledWithOrganizationsAndPrerequisites(@Param("now") ZonedDateTime now);

    @Query("select course from Course course left join fetch course.organizations co where course.id = :#{#courseId}")
    Optional<Course> findWithEagerOrganizations(@Param("courseId") long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "onlineCourseConfiguration" })
    Course findWithEagerOnlineCourseConfigurationById(long courseId);

    List<Course> findAllByShortName(String shortName);

    Optional<Course> findById(long courseId);

    /**
     * Returns the title of the course with the given id.
     *
     * @param courseId the id of the course
     * @return the name/title of the course or null if the course does not exist
     */
    @Query("""
            SELECT c.title
            FROM Course c
            WHERE c.id = :courseId
            """)
    @Cacheable(cacheNames = "courseTitle", key = "#courseId", unless = "#result == null")
    String getCourseTitle(@Param("courseId") Long courseId);

    @Query("""
            SELECT DISTINCT c
            FROM Course c LEFT JOIN FETCH c.exercises e
            WHERE (c.instructorGroupName IN :#{#userGroups} OR c.editorGroupName IN :#{#userGroups})
                AND TYPE(e) = QuizExercise
            """)
    List<Course> getCoursesWithQuizExercisesForWhichUserHasAtLeastEditorAccess(@Param("userGroups") List<String> userGroups);

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
            SELECT new de.tum.in.www1.artemis.domain.statistics.StatisticsEntry(
                substring(cast(s.submissionDate as string), 1, 10), p.student.login
                )
            FROM StudentParticipation p JOIN p.submissions s
            WHERE p.exercise.id IN :exerciseIds
                AND s.submissionDate >= cast(:startDate as timestamp)
                AND s.submissionDate <= cast(:endDate as timestamp)
                group by substring(cast(s.submissionDate as string), 1, 10), p.student.login
            """)
    List<StatisticsEntry> getActiveStudents(@Param("exerciseIds") Set<Long> exerciseIds, @Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

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
            WHERE (c.endDate IS NULL OR cast(:now as timestamp) IS NULL OR c.endDate >= cast(:now as timestamp))
                AND (:isAdmin = TRUE OR c.teachingAssistantGroupName IN :userGroups OR c.editorGroupName IN :userGroups OR c.instructorGroupName IN :userGroups)
            """)
    List<Course> getAllCoursesForManagementOverview(@Param("now") ZonedDateTime now, @Param("isAdmin") boolean isAdmin, @Param("userGroups") List<String> userGroups);

    @NotNull
    default Course findByIdElseThrow(long courseId) throws EntityNotFoundException {
        return findById(courseId).orElseThrow(() -> new EntityNotFoundException("Course", courseId));
    }

    default Course findByIdWithEagerExercisesElseThrow(long courseId) throws EntityNotFoundException {
        return Optional.ofNullable(findWithEagerExercisesById(courseId)).orElseThrow(() -> new EntityNotFoundException("Course", courseId));
    }

    default Course findByIdWithEagerOnlineCourseConfigurationElseThrow(long courseId) throws EntityNotFoundException {
        return Optional.ofNullable(findWithEagerOnlineCourseConfigurationById(courseId)).orElseThrow(() -> new EntityNotFoundException("Course", courseId));
    }

    @NotNull
    default Course findWithEagerOrganizationsElseThrow(long courseId) throws EntityNotFoundException {
        return findWithEagerOrganizations(courseId).orElseThrow(() -> new EntityNotFoundException("Course", courseId));
    }

    /**
     * filters the passed exercises for the relevant ones that need to be manually assessed. This excludes quizzes and automatic programming exercises
     *
     * @param exercises all exercises (e.g. of a course or exercise group) that should be filtered
     * @return the filtered and relevant exercises for manual assessment
     */
    default Set<Exercise> getInterestingExercisesForAssessmentDashboards(Set<Exercise> exercises) {
        return exercises.stream()
                .filter(exercise -> exercise instanceof TextExercise || exercise instanceof ModelingExercise || exercise instanceof FileUploadExercise
                        || (exercise instanceof ProgrammingExercise && (exercise.getAssessmentType() != AUTOMATIC || exercise.getAllowComplaintsForAutomaticAssessments())))
                .collect(Collectors.toSet());
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
     * Get all the courses to register with eagerly loaded organizations and prerequisites.
     *
     * @return the list of course entities
     */
    default List<Course> findAllCurrentlyActiveNotOnlineAndRegistrationEnabledWithOrganizationsAndPrerequisites() {
        return findAllCurrentlyActiveNotOnlineAndRegistrationEnabledWithOrganizationsAndPrerequisites(ZonedDateTime.now());
    }

    /**
     * Get one course by id with lectures and exams. If the course cannot be found throw an exception
     *
     * @param courseId the id of the entity
     * @return the entity
     */
    @NotNull
    default Course findByIdWithLecturesAndExamsElseThrow(long courseId) {
        return findWithEagerLecturesAndExamsById(courseId).orElseThrow(() -> new EntityNotFoundException("Course", courseId));
    }

    /**
     * Add organization to course, if not contained already
     * @param courseId the id of the course to add to the organization
     * @param organization the organization to add to the course
     */
    @NotNull
    default void addOrganizationToCourse(long courseId, Organization organization) {
        Course course = findWithEagerOrganizationsElseThrow(courseId);
        if (!course.getOrganizations().contains(organization)) {
            course.getOrganizations().add(organization);
            save(course);
        }
    }

    /**
     * Remove organization from course, if currently contained
     * @param courseId the id of the course to remove from the organization
     * @param organization the organization to remove from the course
     */
    @NotNull
    default void removeOrganizationFromCourse(long courseId, Organization organization) {
        Course course = findWithEagerOrganizationsElseThrow(courseId);
        if (course.getOrganizations().contains(organization)) {
            course.getOrganizations().remove(organization);
            save(course);
        }
    }

    @NotNull
    default Course findByIdWithExercisesAndLecturesElseThrow(long courseId) {
        return findWithEagerExercisesAndLecturesById(courseId).orElseThrow(() -> new EntityNotFoundException("Course", courseId));
    }

    @NotNull
    default Course findByIdWithLecturesElseThrow(long courseId) {
        return findWithEagerLecturesById(courseId).orElseThrow(() -> new EntityNotFoundException("Course", courseId));
    }

    @NotNull
    default Course findByIdWithLecturesAndLectureUnitsElseThrow(long courseId) {
        return findWithEagerLecturesAndLectureUnitsById(courseId).orElseThrow(() -> new EntityNotFoundException("Course", courseId));
    }

    @NotNull
    default Course findByIdWithOrganizationsAndLearningGoalsElseThrow(long courseId) {
        return findWithEagerOrganizationsAndLearningGoalsById(courseId).orElseThrow(() -> new EntityNotFoundException("Course", courseId));
    }

    @NotNull
    default Course findByIdWithExercisesAndLecturesAndLectureUnitsAndLearningGoalsElseThrow(long courseId) {
        return findWithEagerExercisesAndLecturesAndLectureUnitsAndLearningGoalsById(courseId).orElseThrow(() -> new EntityNotFoundException("Course", courseId));
    }

    @NotNull
    default Course findWithEagerLearningGoalsByIdElseThrow(long courseId) {
        return findWithEagerLearningGoalsById(courseId).orElseThrow(() -> new EntityNotFoundException("Course", courseId));
    }

    /**
     * Utility method used to check whether a user is member of at least one organization of a given course
     * @param user the user to check
     * @param course the course to check
     * @return true if the user is member of at least one organization of the course. false otherwise
     */
    default boolean checkIfUserIsMemberOfCourseOrganizations(User user, Course course) {
        boolean isMember = false;
        for (Organization organization : findWithEagerOrganizationsElseThrow(course.getId()).getOrganizations()) {
            if (user.getOrganizations().contains(organization)) {
                isMember = true;
                break;
            }
        }
        return isMember;
    }
}
