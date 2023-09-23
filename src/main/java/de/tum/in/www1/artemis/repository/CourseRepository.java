package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.domain.enumeration.AssessmentType.AUTOMATIC;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.CourseInformationSharingConfiguration;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.statistics.StatisticsEntry;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the Course entity.
 */
@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    @Query("select distinct course.instructorGroupName from Course course")
    Set<String> findAllInstructorGroupNames();

    @Query("select distinct course.editorGroupName from Course course")
    Set<String> findAllEditorGroupNames();

    @Query("select distinct course.teachingAssistantGroupName from Course course")
    Set<String> findAllTeachingAssistantGroupNames();

    @Query("select distinct course from Course course where course.instructorGroupName like :name")
    Course findCourseByInstructorGroupName(@Param("name") String name);

    @Query("select distinct course from Course course where course.editorGroupName like :name")
    Course findCourseByEditorGroupName(@Param("name") String name);

    @Query("select distinct course from Course course where course.teachingAssistantGroupName like :name")
    Course findCourseByTeachingAssistantGroupName(@Param("name") String name);

    @Query("select distinct course from Course course where course.studentGroupName like :name")
    Course findCourseByStudentGroupName(@Param("name") String name);

    @Query("""
            SELECT DISTINCT course
            FROM Course course
            WHERE course.instructorGroupName = :name
            """)
    List<Course> findCoursesByInstructorGroupName(@Param("name") String name);

    @Query("""
            SELECT DISTINCT course
            FROM Course course
            WHERE course.teachingAssistantGroupName = :name
            """)
    List<Course> findCoursesByTeachingAssistantGroupName(@Param("name") String name);

    @Query("""
            SELECT DISTINCT course
            FROM Course course
            WHERE course.studentGroupName = :name
            """)
    List<Course> findCoursesByStudentGroupName(@Param("name") String name);

    @Query("""
            SELECT CASE WHEN (count(c) > 0) THEN true ELSE false END
            FROM Course c
            WHERE c.id = :#{#courseId}
                AND c.courseInformationSharingConfiguration IN :#{#values}
            """)
    boolean informationSharingConfigurationIsOneOf(@Param("courseId") long courseId, @Param("values") Set<CourseInformationSharingConfiguration> values);

    @Query("""
            SELECT DISTINCT c
            FROM Course c
            WHERE (c.startDate <= :now OR c.startDate IS NULL)
                AND (c.endDate >= :now OR c.endDate IS NULL)
            """)
    List<Course> findAllActive(@Param("now") ZonedDateTime now);

    @Query("""
            SELECT DISTINCT c
            FROM Course c
            WHERE (c.startDate <= :now OR c.startDate IS NULL)
                AND (c.endDate >= :now OR c.endDate IS NULL)
                AND c.testCourse = false
            """)
    List<Course> findAllActiveWithoutTestCourses(@Param("now") ZonedDateTime now);

    /**
     * Note: you should not add exercises or exercises+categories here, because this would make the query too complex and would take significantly longer
     *
     * @param now the current date, typically ZonedDateTime.now()
     * @return List of found courses with lectures and their attachments
     */
    @EntityGraph(type = LOAD, attributePaths = { "lectures", "lectures.attachments" })
    @Query("""
            SELECT DISTINCT c
            FROM Course c
            WHERE (c.startDate <= :now OR c.startDate IS NULL)
                AND (c.endDate >= :now OR c.endDate IS NULL)
            """)
    List<Course> findAllActiveWithLectures(@Param("now") ZonedDateTime now);

    @Query("""
            SELECT DISTINCT c FROM Course c
                LEFT JOIN FETCH c.organizations organizations
                LEFT JOIN FETCH c.prerequisites prerequisites
            WHERE (c.enrollmentEnabled = true)
                AND (c.enrollmentStartDate <= :now)
                AND (c.enrollmentEndDate >= :now)
            """)
    List<Course> findAllEnrollmentActiveWithOrganizationsAndPrerequisites(@Param("now") ZonedDateTime now);

    @EntityGraph(type = LOAD, attributePaths = { "exercises", "exercises.categories", "exercises.teamAssignmentConfig" })
    Course findWithEagerExercisesById(long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "competencies", "prerequisites" })
    Optional<Course> findWithEagerCompetenciesById(long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "learningPaths" })
    Optional<Course> findWithEagerLearningPathsById(long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "competencies", "learningPaths", "learningPaths.competencies" })
    Optional<Course> findWithEagerLearningPathsAndCompetenciesById(long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "lectures" })
    Optional<Course> findWithEagerLecturesById(long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "exercises", "lectures" })
    Optional<Course> findWithEagerExercisesAndLecturesById(long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "lectures", "lectures.lectureUnits" })
    Optional<Course> findWithEagerLecturesAndLectureUnitsById(long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "organizations", "competencies", "prerequisites", "tutorialGroupsConfiguration", "onlineCourseConfiguration" })
    Optional<Course> findWithEagerOrganizationsAndCompetenciesAndOnlineConfigurationById(long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "exercises", "lectures", "lectures.lectureUnits", "competencies", "prerequisites" })
    Optional<Course> findWithEagerExercisesAndLecturesAndLectureUnitsAndCompetenciesById(long courseId);

    @Query("""
                SELECT course
                FROM Course course
                    LEFT JOIN FETCH course.organizations organizations
                    LEFT JOIN FETCH course.prerequisites prerequisites
                WHERE course.id = :courseId
            """)
    Optional<Course> findSingleWithOrganizationsAndPrerequisites(@Param("courseId") long courseId);

    @Query("""
                SELECT course
                FROM Course course
                    LEFT JOIN FETCH course.organizations
                WHERE course.id = :courseId
            """)
    Optional<Course> findWithEagerOrganizations(@Param("courseId") long courseId);

    @Query("""
                SELECT course
                FROM Course course
                    LEFT JOIN FETCH course.organizations
                    LEFT JOIN FETCH course.competencies
                    LEFT JOIN FETCH course.learningPaths
                WHERE course.id = :courseId
            """)
    Optional<Course> findWithEagerOrganizationsAndCompetenciesAndLearningPaths(@Param("courseId") long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "onlineCourseConfiguration", "tutorialGroupsConfiguration" })
    Course findWithEagerOnlineCourseConfigurationAndTutorialGroupConfigurationById(long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "onlineCourseConfiguration" })
    Course findWithEagerOnlineCourseConfigurationById(long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "tutorialGroupsConfiguration" })
    Course findWithEagerTutorialGroupConfigurationsById(long courseId);

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
            FROM Course c
                LEFT JOIN FETCH c.exercises e
            WHERE (c.instructorGroupName IN :userGroups OR c.editorGroupName IN :userGroups)
                AND TYPE(e) = QuizExercise
            """)
    List<Course> getCoursesWithQuizExercisesForWhichUserHasAtLeastEditorAccess(@Param("userGroups") List<String> userGroups);

    @Query("""
            SELECT DISTINCT c
            FROM Course c
            LEFT JOIN FETCH c.exercises e
            WHERE TYPE(e) = QuizExercise
            """)
    List<Course> findAllWithQuizExercisesWithEagerExercises();

    /**
     * Get active students in the timeframe from startDate to endDate for the exerciseIds
     *
     * @param exerciseIds exerciseIds from all exercises to get the statistics for
     * @param startDate   the starting date of the query
     * @param endDate     the end date for the query
     * @return A list with a map for every submission containing date and the username
     */
    @Query("""
            SELECT new de.tum.in.www1.artemis.domain.statistics.StatisticsEntry(
                substring(cast(s.submissionDate as string), 1, 10), p.student.login
                )
            FROM StudentParticipation p
                JOIN p.submissions s
            WHERE p.exercise.id IN :exerciseIds
                AND s.submissionDate >= cast(:startDate as timestamp)
                AND s.submissionDate <= cast(:endDate as timestamp)
            GROUP BY substring(cast(s.submissionDate as string), 1, 10), p.student.login
            """)
    List<StatisticsEntry> getActiveStudents(@Param("exerciseIds") Set<Long> exerciseIds, @Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    /**
     * Fetches the courses to display for the management overview
     *
     * @param now        ZonedDateTime of the current time. If an end date is set only courses before this time are returned. May be null to return all
     * @param isAdmin    whether the user to fetch the courses for is an admin (which gets all courses)
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

    /**
     * Counts the number of members of a course, i.e. users that are a member of the course's student, tutor, editor or instructor group.
     * Users that are part of multiple groups are NOT counted multiple times.
     *
     * @param courseId id of the course to count the members for
     * @return number of users in the course
     */
    @Query("""
            SELECT COUNT(DISTINCT ug.userId)
            FROM Course c
            JOIN UserGroup ug
            ON c.studentGroupName = ug.group OR c.teachingAssistantGroupName = ug.group OR c.editorGroupName = ug.group OR c.instructorGroupName = ug.group
            WHERE c.id = :courseId
            """)
    Integer countCourseMembers(@Param("courseId") Long courseId);

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

    default Course findByIdWithEagerOnlineCourseConfigurationAndTutorialGroupConfigurationElseThrow(long courseId) throws EntityNotFoundException {
        return Optional.ofNullable(findWithEagerOnlineCourseConfigurationAndTutorialGroupConfigurationById(courseId))
                .orElseThrow(() -> new EntityNotFoundException("Course", courseId));
    }

    default Course findByIdWithEagerTutorialGroupConfigurationElseThrow(long courseId) throws EntityNotFoundException {
        return Optional.ofNullable(findWithEagerTutorialGroupConfigurationsById(courseId)).orElseThrow(() -> new EntityNotFoundException("Course", courseId));
    }

    @NotNull
    default Course findWithEagerOrganizationsElseThrow(long courseId) throws EntityNotFoundException {
        return findWithEagerOrganizations(courseId).orElseThrow(() -> new EntityNotFoundException("Course", courseId));
    }

    @NotNull
    default Course findWithEagerOrganizationsAndCompetenciesAndLearningPathsElseThrow(long courseId) throws EntityNotFoundException {
        return findWithEagerOrganizationsAndCompetenciesAndLearningPaths(courseId).orElseThrow(() -> new EntityNotFoundException("Course", courseId));
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
    default List<Course> findAllActiveWithLectures() {
        return findAllActiveWithLectures(ZonedDateTime.now());
    }

    /**
     * Get a single course to enroll with eagerly loaded organizations and prerequisites.
     *
     * @param courseId the id of the course
     * @return the course entity
     */
    default Course findSingleWithOrganizationsAndPrerequisitesElseThrow(long courseId) {
        return findSingleWithOrganizationsAndPrerequisites(courseId).orElseThrow(() -> new EntityNotFoundException("Course", courseId));
    }

    /**
     * Add organization to course, if not contained already
     *
     * @param courseId     the id of the course to add to the organization
     * @param organization the organization to add to the course
     */
    default void addOrganizationToCourse(long courseId, Organization organization) {
        Course course = findWithEagerOrganizationsElseThrow(courseId);
        if (!course.getOrganizations().contains(organization)) {
            course.getOrganizations().add(organization);
            save(course);
        }
    }

    /**
     * Remove organization from course, if currently contained
     *
     * @param courseId     the id of the course to remove from the organization
     * @param organization the organization to remove from the course
     */
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
    default Course findByIdWithOrganizationsAndCompetenciesAndOnlineConfigurationElseThrow(long courseId) {
        return findWithEagerOrganizationsAndCompetenciesAndOnlineConfigurationById(courseId).orElseThrow(() -> new EntityNotFoundException("Course", courseId));
    }

    @NotNull
    default Course findByIdWithExercisesAndLecturesAndLectureUnitsAndCompetenciesElseThrow(long courseId) {
        return findWithEagerExercisesAndLecturesAndLectureUnitsAndCompetenciesById(courseId).orElseThrow(() -> new EntityNotFoundException("Course", courseId));
    }

    @NotNull
    default Course findWithEagerCompetenciesByIdElseThrow(long courseId) {
        return findWithEagerCompetenciesById(courseId).orElseThrow(() -> new EntityNotFoundException("Course", courseId));
    }

    @NotNull
    default Course findWithEagerLearningPathsByIdElseThrow(long courseId) {
        return findWithEagerLearningPathsById(courseId).orElseThrow(() -> new EntityNotFoundException("Course", courseId));
    }

    @NotNull
    default Course findWithEagerLearningPathsAndCompetenciesByIdElseThrow(long courseId) {
        return findWithEagerLearningPathsAndCompetenciesById(courseId).orElseThrow(() -> new EntityNotFoundException("Course", courseId));
    }

    /**
     * Checks if the messaging feature is enabled for a course.
     *
     * @param courseId the id of the course
     * @return true if the messaging feature is enabled for the course, false otherwise
     */
    default boolean isMessagingEnabled(long courseId) {
        return informationSharingConfigurationIsOneOf(courseId,
                Set.of(CourseInformationSharingConfiguration.MESSAGING_ONLY, CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING));
    }

    /**
     * Checks if the communication feature is enabled for a course.
     *
     * @param courseId the id of the course
     * @return true if the communication feature is enabled for the course, false otherwise
     */
    default boolean isCommunicationEnabled(long courseId) {
        return informationSharingConfigurationIsOneOf(courseId,
                Set.of(CourseInformationSharingConfiguration.COMMUNICATION_ONLY, CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING));
    }

    /**
     * Utility method used to check whether a user is member of at least one organization of a given course
     *
     * @param user   the user to check, organizations must NOT be lazily loaded
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
