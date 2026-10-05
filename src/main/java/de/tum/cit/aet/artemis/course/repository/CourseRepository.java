package de.tum.cit.aet.artemis.course.repository;

import static de.tum.cit.aet.artemis.assessment.domain.AssessmentType.AUTOMATIC;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.account.domain.Organization;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.admin.dto.StatisticsEntry;
import de.tum.cit.aet.artemis.communication.domain.FaqState;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseInformationSharingConfiguration;
import de.tum.cit.aet.artemis.course.dto.ActiveCourseDTO;
import de.tum.cit.aet.artemis.course.dto.CourseContentAvailabilityDTO;
import de.tum.cit.aet.artemis.course.dto.CourseForArchiveDTO;
import de.tum.cit.aet.artemis.course.dto.CourseForOverviewDTO;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Spring Data JPA repository for the Course entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface CourseRepository extends ArtemisJpaRepository<Course, Long>, JpaSpecificationExecutor<Course> {

    /**
     * Stores a course and initializes its permanent configuration rows on first creation, including rows of disabled
     * optional modules. Later edits only update the course. Every configuration owns the key to its course and is inserted
     * after the course exists.
     * <p>
     * There is deliberately no transaction around the statements: each one commits on its own, so no lock is held across
     * them. If one of the inserts fails, the exception reaches the caller and the course stays without the settings that
     * were not inserted yet, and without the settings the request asked for. The next save through this method repairs the
     * missing rows with their defaults (see {@link #ensureDefaultConfigurations(long)}); creating the course again is
     * refused until then, because its short name is taken.
     *
     * @param course the course to store
     * @return the stored course
     */
    default Course saveWithDefaultConfigurations(Course course) {
        boolean newCourse = course.getId() == null;
        Course saved = save(course);
        ensureDefaultConfigurations(saved.getId());
        if (newCourse) {
            applyRequestedConfigurations(course, saved.getId());
        }
        return saved;
    }

    /**
     * Applies the settings the creating request carried on the course's slots to the permanent rows that were just created
     * for it. A slot that is empty leaves the defaults in place.
     *
     * @param course   the course that was just stored, carrying the requested settings on its slots
     * @param courseId the id of the stored course
     */
    default void applyRequestedConfigurations(Course course, long courseId) {
        var athenaConfig = course.getAthenaConfig();
        if (athenaConfig != null) {
            updateAthenaConfig(courseId, athenaConfig.isGradingFeedbackEnabled(), athenaConfig.isFormativeFeedbackEnabled());
        }
        var courseConfiguration = course.getCourseConfiguration();
        if (courseConfiguration != null) {
            updateCourseConfiguration(courseId, courseConfiguration.isGradeRelevant(), courseConfiguration.isDataRetentionHold(), courseConfiguration.isAutoOrchestratorEnabled(),
                    courseConfiguration.getDebounceWindowSecondsOverride(), courseConfiguration.getMaxDailyOrchestrationOverride());
        }
    }

    /**
     * Changes the Athena settings of a course in place.
     *
     * @param courseId                 the id of the course
     * @param gradingFeedbackEnabled   whether Athena grading feedback is on
     * @param formativeFeedbackEnabled whether Athena formative feedback is on
     */
    @Modifying
    @Transactional // ok because of the update
    @Query("""
            UPDATE CourseAthenaConfig config
            SET config.gradingFeedbackEnabled = :gradingFeedbackEnabled, config.formativeFeedbackEnabled = :formativeFeedbackEnabled
            WHERE config.course.id = :courseId
            """)
    void updateAthenaConfig(@Param("courseId") long courseId, @Param("gradingFeedbackEnabled") boolean gradingFeedbackEnabled,
            @Param("formativeFeedbackEnabled") boolean formativeFeedbackEnabled);

    /**
     * Changes the general settings of a course in place.
     *
     * @param courseId                      the id of the course
     * @param gradeRelevant                 whether the course is grade-relevant
     * @param dataRetentionHold             whether the course is under a data-retention hold
     * @param autoOrchestratorEnabled       whether the auto-orchestration pipeline is on
     * @param debounceWindowSecondsOverride the debounce override, or null for the global default
     * @param maxDailyOrchestrationOverride the daily run cap override, or null for the global default
     */
    @Modifying
    @Transactional // ok because of the update
    @Query("""
            UPDATE CourseConfiguration config
            SET config.gradeRelevant = :gradeRelevant, config.dataRetentionHold = :dataRetentionHold, config.autoOrchestratorEnabled = :autoOrchestratorEnabled,
                config.debounceWindowSecondsOverride = :debounceWindowSecondsOverride, config.maxDailyOrchestrationOverride = :maxDailyOrchestrationOverride
            WHERE config.course.id = :courseId
            """)
    void updateCourseConfiguration(@Param("courseId") long courseId, @Param("gradeRelevant") boolean gradeRelevant, @Param("dataRetentionHold") boolean dataRetentionHold,
            @Param("autoOrchestratorEnabled") boolean autoOrchestratorEnabled, @Param("debounceWindowSecondsOverride") @Nullable Integer debounceWindowSecondsOverride,
            @Param("maxDailyOrchestrationOverride") @Nullable Integer maxDailyOrchestrationOverride);

    /**
     * Creates the Athena settings of a newly stored course with every feature switched off.
     *
     * @param courseId the new course id
     */
    @Modifying
    @Transactional // ok because of the insert
    @Query(value = """
            INSERT INTO course_athena_config (course_id, grading_feedback_enabled, formative_feedback_enabled)
            VALUES (:courseId, FALSE, FALSE)
            """, nativeQuery = true)
    void initializeAthenaConfig(@Param("courseId") long courseId);

    /**
     * Creates the general course settings of a newly stored course: grade-relevant, no retention hold, no auto-orchestration.
     *
     * @param courseId the new course id
     */
    @Modifying
    @Transactional // ok because of the insert
    @Query(value = """
            INSERT INTO course_configuration (course_id, grade_relevant, data_retention_hold, auto_orchestrator_enabled)
            VALUES (:courseId, TRUE, FALSE, FALSE)
            """, nativeQuery = true)
    void initializeCourseConfiguration(@Param("courseId") long courseId);

    /**
     * Inserts the default settings a stored course is missing, which is what a creation that failed half-way leaves behind.
     * For a course that has all its settings this is five unlocked reads and nothing else.
     * <p>
     * It takes no lock and needs no transaction: a missing row is found by a plain read and added by a plain insert. If two
     * saves repair the same course at the same moment, the unique key lets one insert win and the other finds the row
     * already there. Settings of a course are never removed or replaced here, only added where they are missing.
     *
     * @param courseId the id of the stored course
     * @return the number of settings rows that had to be added; greater than zero means an earlier creation was incomplete
     */
    default int ensureDefaultConfigurations(long courseId) {
        int added = 0;
        if (!hasAthenaConfig(courseId)) {
            added += insertUnlessAlreadyThere(() -> initializeAthenaConfig(courseId), () -> hasAthenaConfig(courseId));
        }
        if (!hasCourseConfiguration(courseId)) {
            added += insertUnlessAlreadyThere(() -> initializeCourseConfiguration(courseId), () -> hasCourseConfiguration(courseId));
        }
        if (!hasOnlineCourseConfiguration(courseId)) {
            added += insertUnlessAlreadyThere(() -> initializeOnlineCourseConfiguration(courseId), () -> hasOnlineCourseConfiguration(courseId));
        }
        if (!hasTutorialGroupsConfiguration(courseId)) {
            added += insertUnlessAlreadyThere(() -> initializeTutorialGroupsConfiguration(courseId), () -> hasTutorialGroupsConfiguration(courseId));
        }
        if (!hasIrisCourseSettings(courseId)) {
            added += insertUnlessAlreadyThere(() -> initializeIrisCourseSettings(courseId), () -> hasIrisCourseSettings(courseId));
        }
        return added;
    }

    private static int insertUnlessAlreadyThere(Runnable insert, BooleanSupplier exists) {
        try {
            insert.run();
            return 1;
        }
        catch (DataIntegrityViolationException e) {
            // a concurrent save adding the same row first is what was wanted; any other violation is a real failure
            if (exists.getAsBoolean()) {
                return 0;
            }
            throw e;
        }
    }

    @Query("""
            SELECT COUNT(config) > 0
            FROM CourseAthenaConfig config
            WHERE config.course.id = :courseId
            """)
    boolean hasAthenaConfig(@Param("courseId") long courseId);

    @Query("""
            SELECT COUNT(config) > 0
            FROM CourseConfiguration config
            WHERE config.course.id = :courseId
            """)
    boolean hasCourseConfiguration(@Param("courseId") long courseId);

    @Query("""
            SELECT COUNT(configuration) > 0
            FROM OnlineCourseConfiguration configuration
            WHERE configuration.course.id = :courseId
            """)
    boolean hasOnlineCourseConfiguration(@Param("courseId") long courseId);

    @Query("""
            SELECT COUNT(configuration) > 0
            FROM TutorialGroupsConfiguration configuration
            WHERE configuration.course.id = :courseId
            """)
    boolean hasTutorialGroupsConfiguration(@Param("courseId") long courseId);

    @Query("""
            SELECT COUNT(settings) > 0
            FROM IrisCourseSettingsEntity settings
            WHERE settings.courseId = :courseId
            """)
    boolean hasIrisCourseSettings(@Param("courseId") long courseId);

    /**
     * Creates the default LTI settings for a newly stored course, independently of whether online mode is enabled.
     *
     * @param courseId the new course id
     */
    @Modifying
    @Transactional // ok because of the insert
    @Query(value = """
            INSERT INTO online_course_configuration (course_id, user_prefix, require_existing_user)
            SELECT id, COALESCE(short_name, CONCAT('course', id)), FALSE FROM course WHERE id = :courseId
            """, nativeQuery = true)
    void initializeOnlineCourseConfiguration(@Param("courseId") long courseId);

    /**
     * Creates inactive tutorial-group settings; a tutorial period must be configured before the feature becomes available.
     *
     * @param courseId the new course id
     */
    @Modifying
    @Transactional // ok because of the insert
    @Query(value = """
            INSERT INTO tutorial_groups_configuration (course_id, use_tutorial_group_channels, use_public_tutorial_group_channels)
            VALUES (:courseId, FALSE, FALSE)
            """, nativeQuery = true)
    void initializeTutorialGroupsConfiguration(@Param("courseId") long courseId);

    /**
     * Creates Iris settings matching the defaults previously used when a course had no settings row.
     *
     * @param courseId the new course id
     */
    @Modifying
    @Transactional // ok because of the insert
    @Query(value = """
            INSERT INTO course_iris_settings (course_id, settings)
            VALUES (:courseId, '{"enabled":true,"variant":"default","supportLevel":"moderate"}')
            """, nativeQuery = true)
    void initializeIrisCourseSettings(@Param("courseId") long courseId);

    /**
     * Answers in one query whether a course has lectures, competencies, tutorial groups, accepted FAQs, quiz questions
     * available for practice, and an exam already visible to the user.
     * <p>
     * These six used to be six separate round trips, one per feature module, issued on every course entry to build the
     * sidebar. The schema is shared regardless of which modules are enabled, so a single course-side query can answer
     * all of them; a disabled module simply has no rows. Module enablement is still applied on top in
     * {@code CourseAvailableTabsService}, so a disabled module hides its tab even when rows exist.
     * <p>
     * Each branch below is an independent existence check against an indexed {@code course_id} (plus one join for quiz
     * questions and the user-scoped predicate for exams), so the planner satisfies them with index seeks and folding
     * them together measured faster than issuing them separately rather than slower.
     * <p>
     * Iris enablement is deliberately not folded in: its flag lives inside a JSON column and the answer for a course
     * without a settings row comes from {@code IrisCourseSettings.defaultSettings()} in Java, so expressing it here
     * would need database-specific JSON extraction and would duplicate that default. It is a primary-key lookup anyway.
     *
     * @param courseId      the course to inspect
     * @param userId        the user asking, for the user-scoped exam visibility check
     * @param acceptedState the FAQ state that counts as visible to students
     * @param now           the current time, used for quiz due dates and exam visibility
     * @return which kinds of content the course has
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.course.dto.CourseContentAvailabilityDTO(
                CASE WHEN EXISTS (SELECT 1 FROM Lecture l WHERE l.course.id = :courseId) THEN TRUE ELSE FALSE END,
                CASE WHEN EXISTS (SELECT 1 FROM CourseCompetency cc WHERE cc.course.id = :courseId) THEN TRUE ELSE FALSE END,
                CASE WHEN EXISTS (SELECT 1 FROM TutorialGroup tg WHERE tg.course.id = :courseId) THEN TRUE ELSE FALSE END,
                CASE WHEN EXISTS (SELECT 1 FROM Faq f WHERE f.course.id = :courseId AND f.faqState = :acceptedState) THEN TRUE ELSE FALSE END,
                CASE WHEN EXISTS (SELECT 1 FROM QuizQuestion q WHERE q.exercise.course.id = :courseId AND q.exercise.dueDate IS NOT NULL AND q.exercise.dueDate < :now)
                    THEN TRUE ELSE FALSE END,
                CASE WHEN EXISTS (
                    SELECT 1 FROM Exam e
                    WHERE e.course.id = :courseId
                        AND e.visibleDate <= :now
                        AND (
                            e.testExam = TRUE
                            OR EXISTS (SELECT 1 FROM ExamUser eu WHERE eu.exam = e AND eu.user.id = :userId)
                            OR EXISTS (SELECT 1 FROM UserCourseRole ucr WHERE ucr.user.id = :userId AND ucr.course.id = :courseId AND ucr.role IN (de.tum.cit.aet.artemis.core.domain.CourseRole.TEACHING_ASSISTANT, de.tum.cit.aet.artemis.core.domain.CourseRole.EDITOR, de.tum.cit.aet.artemis.core.domain.CourseRole.INSTRUCTOR))
                        )
                ) THEN TRUE ELSE FALSE END
            )
            FROM Course c
            WHERE c.id = :courseId
            """)
    CourseContentAvailabilityDTO findContentAvailability(@Param("courseId") long courseId, @Param("userId") long userId, @Param("acceptedState") FaqState acceptedState,
            @Param("now") ZonedDateTime now);

    @Query("""
            SELECT COUNT(c) > 0
            FROM Course c
            WHERE c.id = :courseId
                AND c.courseInformationSharingConfiguration IN :values
            """)
    boolean informationSharingConfigurationIsOneOf(@Param("courseId") long courseId, @Param("values") Set<CourseInformationSharingConfiguration> values);

    @Query("""
            SELECT c
            FROM Course c
            WHERE c.startDate <= :now
                AND c.endDate >= :now
            """)
    List<Course> findAllActive(@Param("now") ZonedDateTime now);

    /**
     * Finds all courses that ended before the given date. Used by the data-privacy retention cleanup to determine which
     * old courses are due for a student-data reset. The course configuration is not part of the result: the cleanup
     * attaches it with {@code CourseConfigurationRepository.attachTo}, in one query for all of them.
     *
     * @param endDateBefore only courses whose end date is non-null and strictly before this are returned
     * @return the matching courses
     */
    @Query("""
            SELECT c
            FROM Course c
            WHERE c.endDate IS NOT NULL
                AND c.endDate < :endDateBefore
            """)
    List<Course> findAllByEndDateBefore(@Param("endDateBefore") ZonedDateTime endDateBefore);

    /**
     * Finds all courses whose data-privacy reset warning has already been sent (i.e. their configuration has a non-null
     * reset warning date) and that have not been reset yet. Used by the retention cleanup to determine which warned
     * courses are past the grace period and due for a student-data reset. The configuration is not part of the result:
     * the cleanup attaches it with {@code CourseConfigurationRepository.attachTo}.
     *
     * @return the matching courses
     */
    @Query("""
            SELECT c
            FROM Course c
                JOIN CourseConfiguration cc ON cc.course = c
            WHERE cc.resetWarningSentDate IS NOT NULL
                AND cc.studentDataResetDate IS NULL
            """)
    List<Course> findAllWithResetWarningSent();

    /**
     * Returns the active courses in which the given user holds any role. For an active course (already started, not yet
     * finished) holding any role is exactly the course visibility condition for a non-admin, so this lets the
     * dashboard/dropdown load only the user's own courses via an indexed join instead of loading all active courses and
     * filtering them in memory.
     *
     * @param userId the id of the user
     * @param now    the current time used to determine whether a course is active
     * @return the list of active courses the (non-admin) user can see
     */
    @Query("""
            SELECT DISTINCT c
            FROM Course c
                JOIN UserCourseRole ucr ON ucr.course = c AND ucr.user.id = :userId
            WHERE c.startDate <= :now
                AND c.endDate >= :now
            """)
    List<Course> findAllActiveWhereUserHasAnyRole(@Param("userId") long userId, @Param("now") ZonedDateTime now);

    /**
     * Returns all courses for the consolidated course dashboard. Active courses are included for every enrolled role;
     * courses that have not started yet are included only when the user has a management role.
     *
     * @param userId the id of the user
     * @param now    the current time used to determine whether a course is active or has ended
     * @return the courses visible to the user on the consolidated dashboard
     */
    @Query("""
            SELECT DISTINCT c
            FROM Course c
                JOIN UserCourseRole ucr ON ucr.course = c AND ucr.user.id = :userId
            WHERE c.endDate >= :now
                AND (
                    c.startDate <= :now
                    OR ucr.role IN (de.tum.cit.aet.artemis.core.domain.CourseRole.TEACHING_ASSISTANT,
                                    de.tum.cit.aet.artemis.core.domain.CourseRole.EDITOR,
                                    de.tum.cit.aet.artemis.core.domain.CourseRole.INSTRUCTOR)
                )
            """)
    List<Course> findAllForDashboardWhereUserHasAnyRole(@Param("userId") long userId, @Param("now") ZonedDateTime now);

    /**
     * Returns the active courses with learning paths enabled in which the given user holds any role. Equivalent to
     * {@link #findAllActiveWhereUserHasAnyRole} with the additional {@code learningPathsEnabled} filter, so callers
     * that need learning-path courses for a specific user avoid loading all such courses and filtering in memory.
     *
     * @param userId the id of the user
     * @param now    the current time used to determine whether a course is active
     * @return the list of active learning-path courses the (non-admin) user can see
     */
    @Query("""
            SELECT DISTINCT c
            FROM Course c
                JOIN UserCourseRole ucr ON ucr.course = c AND ucr.user.id = :userId
            WHERE c.startDate <= :now
                AND c.endDate >= :now
                AND c.learningPathsEnabled = TRUE
            """)
    List<Course> findAllActiveWhereUserHasAnyRoleAndLearningPathsEnabled(@Param("userId") long userId, @Param("now") ZonedDateTime now);

    @Query("""
            SELECT DISTINCT c
            FROM Course c
            WHERE c.startDate <= :now
                AND c.endDate >= :now
                AND c.learningPathsEnabled=true
            """)
    List<Course> findAllActiveForUserAndLearningPathsEnabled(@Param("now") ZonedDateTime now);

    /**
     * Returns all active non-test courses with the count of enrolled students in each.
     *
     * @param now the current time used to determine whether a course is active
     * @return a set of active course DTOs including the student count per course
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.course.dto.ActiveCourseDTO(c.id, c.title, c.shortName, c.semester, COUNT(DISTINCT ucr.user.id))
            FROM Course c
                LEFT JOIN c.courseRoles ucr ON ucr.role = de.tum.cit.aet.artemis.core.domain.CourseRole.STUDENT
                    AND ucr.user.deleted = FALSE
            WHERE c.startDate <= :now
                AND c.endDate >= :now
                AND c.testCourse = FALSE
            GROUP BY c.id, c.title, c.shortName, c.semester
            """)
    Set<ActiveCourseDTO> findAllActiveWithoutTestCourses(@Param("now") ZonedDateTime now);

    @Query("""
            SELECT DISTINCT c
            FROM Course c
                LEFT JOIN FETCH c.organizations organizations
                LEFT JOIN FETCH c.prerequisites prerequisites
            WHERE c.enrollmentEnabled = TRUE
                AND c.enrollmentStartDate <= :now
                AND c.enrollmentEndDate >= :now
            """)
    List<Course> findAllEnrollmentActiveWithOrganizationsAndPrerequisites(@Param("now") ZonedDateTime now);

    // The variant group is LAZY on Exercise, so it must be loaded explicitly here: the exercise management view and the
    // instructor scores page both read it off this response, and an unloaded proxy would serialize as null.
    @EntityGraph(type = LOAD, attributePaths = { "exercises", "exercises.categories", "exercises.exerciseVariantGroup" })
    Course findWithEagerExercisesById(long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "competencies", "prerequisites" })
    Optional<Course> findWithEagerCompetenciesAndPrerequisitesById(long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "lectures" })
    Optional<Course> findWithEagerLecturesById(long courseId);

    /**
     * Returns an optional course by id with eagerly loaded exercises, lectures and attachments. The plagiarism detection and team assignment configurations are not part of the
     * course or its exercises; a caller that reports them reads them through their repositories.
     *
     * @param courseId The id of the course to find
     * @return the populated course or an empty optional if no course was found
     */
    @EntityGraph(type = LOAD, attributePaths = { "exercises.exerciseVariantGroup", "lectures" })
    Optional<Course> findWithEagerExercisesAndExerciseDetailsAndLecturesById(long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "organizations", "competencies", "prerequisites" })
    Optional<Course> findForUpdateById(long courseId);

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
                JOIN course.organizations organization
            WHERE organization.id = :organizationId
            """)
    Set<Course> findAllByOrganizationId(@Param("organizationId") Long organizationId);

    @Query("""
            SELECT course
            FROM Course course
                LEFT JOIN FETCH course.organizations
                LEFT JOIN FETCH course.competencies
                LEFT JOIN FETCH course.prerequisites
                LEFT JOIN FETCH course.learningPaths
            WHERE course.id = :courseId
            """)
    Optional<Course> findWithEagerOrganizationsAndCompetenciesAndPrerequisitesAndLearningPaths(@Param("courseId") long courseId);

    /**
     * Fetches the online courses of an LTI platform. The configuration holds the key to its course, so the query starts
     * from it; every course returned belongs to the platform with the given registration id.
     *
     * @param registrationId The LTI platform's registration ID.
     * @return Set of courses.
     */
    @Query("""
            SELECT course
            FROM OnlineCourseConfiguration onlineCourseConfiguration
                JOIN onlineCourseConfiguration.course course
            WHERE course.onlineCourse = TRUE
                AND onlineCourseConfiguration.ltiPlatformConfiguration.registrationId = :registrationId
            """)
    Set<Course> findOnlineCoursesWithRegistrationId(@Param("registrationId") String registrationId);

    List<Course> findAllByShortName(String shortName);

    boolean existsByShortNameIgnoreCase(String shortName);

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
    String getCourseTitle(@Param("courseId") long courseId);

    /**
     * Returns the course icon path of the course with the given id.
     *
     * @param courseId the id of the course
     * @return the course icon path or null if the course does not exist or has no icon
     */
    @Query("""
            SELECT c.courseIcon
            FROM Course c
            WHERE c.id = :courseId
            """)
    String getCourseIconById(@Param("courseId") long courseId);

    /**
     * Returns all courses with quiz exercises for which the user has at least editor access.
     *
     * @param userId the id of the user
     * @return a list of courses with quiz exercises where the user is an editor or instructor
     */
    @Query("""
            SELECT DISTINCT c
            FROM Course c
                LEFT JOIN FETCH c.exercises e
            WHERE TYPE(e) = QuizExercise
                AND EXISTS (
                    SELECT ucr FROM UserCourseRole ucr
                    WHERE ucr.course.id = c.id AND ucr.user.id = :userId
                    AND ucr.role IN (de.tum.cit.aet.artemis.core.domain.CourseRole.EDITOR,
                                     de.tum.cit.aet.artemis.core.domain.CourseRole.INSTRUCTOR)
                )
            """)
    List<Course> getCoursesWithQuizExercisesForWhichUserHasAtLeastEditorAccess(@Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT c
            FROM Course c
                LEFT JOIN FETCH c.exercises e
            WHERE TYPE(e) = QuizExercise
            """)
    List<Course> findAllWithQuizExercisesWithEagerExercises();

    /**
     * Get active students in the timeframe from startDate to endDate for the exerciseIds
     * <p>
     * Keyed on the participating student's id rather than their login: the consumer only needs a stable key to count
     * each student once per week, and the login would require joining {@code jhi_user} for every submission in the
     * window, which measurably costs more than counting on the id alone.
     * That join was also what excluded team participations, which have no student, so they are excluded explicitly
     * now.
     *
     * @param exerciseIds exerciseIds from all exercises to get the statistics for
     * @param startDate   the starting date of the query
     * @param endDate     the end date for the query
     * @return one entry per day and active student, holding the day and the student's id as the deduplication key
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.admin.dto.StatisticsEntry(
                SUBSTRING(CAST(s.submissionDate AS string), 1, 10),
                CAST(p.student.id AS string)
            )
            FROM StudentParticipation p
                JOIN p.submissions s
            WHERE p.exercise.id IN :exerciseIds
                AND s.submissionDate >= :startDate
                AND s.submissionDate <= :endDate
                AND p.student.id IS NOT NULL
            GROUP BY SUBSTRING(CAST(s.submissionDate AS string), 1, 10), p.student.id
            """)
    List<StatisticsEntry> getActiveStudents(@Param("exerciseIds") Set<Long> exerciseIds, @Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate);

    /**
     * Get all courses that are not ended yet.
     *
     * @param now the current time
     * @return a list of courses that are not ended yet
     */
    @Query("""
            SELECT c
            FROM Course c
            WHERE c.endDate >= :now
            """)
    List<Course> findAllNotEnded(@Param("now") ZonedDateTime now);

    /**
     * Get all courses where the user has a management role (TA, editor, or instructor).
     *
     * @param userId the id of the user
     * @return a list of courses where the user has a management role
     */
    @Query("""
            SELECT c
            FROM Course c
            WHERE EXISTS (
                SELECT ucr FROM UserCourseRole ucr
                WHERE ucr.course.id = c.id AND ucr.user.id = :userId
                AND ucr.role IN (de.tum.cit.aet.artemis.core.domain.CourseRole.TEACHING_ASSISTANT,
                                 de.tum.cit.aet.artemis.core.domain.CourseRole.EDITOR,
                                 de.tum.cit.aet.artemis.core.domain.CourseRole.INSTRUCTOR)
            )
            """)
    List<Course> findAllCoursesByManagementRole(@Param("userId") Long userId);

    /**
     * Get all courses that are not ended yet where the user has a management role (TA, editor, or instructor).
     *
     * @param now    the current time
     * @param userId the id of the user
     * @return a list of active courses where the user has a management role
     */
    @Query("""
            SELECT c
            FROM Course c
            WHERE c.endDate >= :now
            AND EXISTS (
                SELECT ucr FROM UserCourseRole ucr
                WHERE ucr.course.id = c.id AND ucr.user.id = :userId
                AND ucr.role IN (de.tum.cit.aet.artemis.core.domain.CourseRole.TEACHING_ASSISTANT,
                                 de.tum.cit.aet.artemis.core.domain.CourseRole.EDITOR,
                                 de.tum.cit.aet.artemis.core.domain.CourseRole.INSTRUCTOR)
            )
            """)
    List<Course> findAllNotEndedCoursesByManagementRole(@Param("now") ZonedDateTime now, @Param("userId") Long userId);

    /**
     * Counts the number of members of a course across all roles.
     * Users with multiple roles in the same course are counted once.
     *
     * @param courseId id of the course to count the members for
     * @return number of users in the course
     */
    @Query("""
            SELECT COUNT(DISTINCT ucr.user.id)
            FROM UserCourseRole ucr
            WHERE ucr.course.id = :courseId
            """)
    Integer countCourseMembers(@Param("courseId") long courseId);

    /**
     * Query which fetches all courses for which the user is editor or instructor and matching the search criteria.
     *
     * @param partialTitle title search term
     * @param userId       the id of the user
     * @param pageable     Pageable
     * @return Page with course results
     */
    @Query("""
            SELECT c
            FROM Course c
            WHERE (LOWER(c.title) LIKE CONCAT('%', LOWER(CAST(:partialTitle AS string)), '%'))
                AND EXISTS (
                    SELECT ucr FROM UserCourseRole ucr
                    WHERE ucr.course.id = c.id AND ucr.user.id = :userId
                    AND ucr.role IN (de.tum.cit.aet.artemis.core.domain.CourseRole.EDITOR,
                                     de.tum.cit.aet.artemis.core.domain.CourseRole.INSTRUCTOR)
                )
            """)
    Page<Course> findByTitleInCoursesWhereInstructorOrEditor(@Param("partialTitle") String partialTitle, @Param("userId") Long userId, Pageable pageable);

    default Course findByIdWithEagerExercisesElseThrow(long courseId) throws EntityNotFoundException {
        return getValueElseThrow(Optional.ofNullable(findWithEagerExercisesById(courseId)), courseId);
    }

    @NonNull
    default Course findWithEagerOrganizationsElseThrow(long courseId) throws EntityNotFoundException {
        return getValueElseThrow(findWithEagerOrganizations(courseId), courseId);
    }

    @NonNull
    default Course findWithEagerOrganizationsAndCompetenciesAndPrerequisitesAndLearningPathsElseThrow(long courseId) throws EntityNotFoundException {
        return getValueElseThrow(findWithEagerOrganizationsAndCompetenciesAndPrerequisitesAndLearningPaths(courseId), courseId);
    }

    /**
     * filters the passed exercises for the relevant ones that need to be manually assessed. This excludes quizzes and automatic programming exercises
     *
     * @param exercises all exercises (e.g. of a course or exercise group) that should be filtered
     * @return the filtered and relevant exercises for manual assessment
     */
    default Set<Exercise> filterInterestingExercisesForAssessmentDashboards(Set<Exercise> exercises) {
        return exercises.stream()
                .filter(exercise -> exercise instanceof TextExercise || exercise instanceof ModelingExercise || exercise instanceof FileUploadExercise
                        || (exercise instanceof ProgrammingExercise && (exercise.getAssessmentType() != AUTOMATIC || exercise.getAllowComplaintsForAutomaticAssessments())))
                .collect(Collectors.toSet());
    }

    /**
     * Get a single course to enroll with eagerly loaded organizations and prerequisites.
     *
     * @param courseId the id of the course
     * @return the course entity
     */
    default Course findSingleWithOrganizationsAndPrerequisitesElseThrow(long courseId) {
        return getValueElseThrow(findSingleWithOrganizationsAndPrerequisites(courseId), courseId);
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

    /**
     * Returns a course by id with eagerly loaded exercises, plagiarism detection configuration, team assignment configuration, lectures and attachments.
     *
     * @param courseId The id of the course to find
     * @return the populated course
     * @throws EntityNotFoundException if no course was found
     */
    @NonNull
    default Course findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(long courseId) {
        return getValueElseThrow(findWithEagerExercisesAndExerciseDetailsAndLecturesById(courseId), courseId);
    }

    @NonNull
    default Course findByIdWithLecturesElseThrow(long courseId) {
        return getValueElseThrow(findWithEagerLecturesById(courseId), courseId);
    }

    @NonNull
    default Course findByIdForUpdateElseThrow(long courseId) {
        return getValueElseThrow(findForUpdateById(courseId), courseId);
    }

    @NonNull
    default Course findWithEagerCompetenciesAndPrerequisitesByIdElseThrow(long courseId) {
        return getValueElseThrow(findWithEagerCompetenciesAndPrerequisitesById(courseId), courseId);
    }

    Page<Course> findByTitleIgnoreCaseContaining(String partialTitle, Pageable pageable);

    /**
     * Checks if the messaging feature is enabled for a course.
     *
     * @param courseId the id of the course
     * @return true if the messaging feature is enabled for the course, false otherwise
     */
    default boolean isMessagingEnabled(long courseId) {
        return informationSharingConfigurationIsOneOf(courseId, Set.of(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING));
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

    @Query("""
            SELECT COUNT(c) > 0
            FROM Course c
            WHERE c.id = :courseId
            AND c.learningPathsEnabled IS TRUE
            """)
    boolean hasLearningPathsEnabled(@Param("courseId") long courseId);

    /**
     * Retrieves all inactive courses that the user has access to.
     * Returns all such courses for admins, otherwise only courses where the user has any role.
     *
     * @param isAdmin whether the user is an admin
     * @param userId  the id of the user
     * @param now     the current time used to determine whether a course is inactive
     * @return a set of inactive courses that the user can access
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.course.dto.CourseForArchiveDTO(
                c.id, c.title, c.semester, c.color, c.courseIcon, c.testCourse,
                CASE WHEN :isAdmin = TRUE OR EXISTS (
                    SELECT managementRole FROM UserCourseRole managementRole
                    WHERE managementRole.course.id = c.id
                        AND managementRole.user.id = :userId
                        AND managementRole.role IN (
                            de.tum.cit.aet.artemis.core.domain.CourseRole.TEACHING_ASSISTANT,
                            de.tum.cit.aet.artemis.core.domain.CourseRole.EDITOR,
                            de.tum.cit.aet.artemis.core.domain.CourseRole.INSTRUCTOR
                        )
                ) THEN TRUE ELSE FALSE END
            )
            FROM Course c
            WHERE (:isAdmin = TRUE
                   OR EXISTS (
                       SELECT ucr FROM UserCourseRole ucr
                       WHERE ucr.course.id = c.id AND ucr.user.id = :userId
                   ))
                AND c.endDate IS NOT NULL
                AND c.endDate < :now
            """)
    Set<CourseForArchiveDTO> findInactiveCoursesForUserRolesForArchive(@Param("isAdmin") boolean isAdmin, @Param("userId") Long userId, @Param("now") ZonedDateTime now);

    /**
     * Finds all courses where the user has at least a teaching assistant role (TA, editor, or instructor),
     * or all courses if the user is an admin.
     *
     * @param userId  the id of the user
     * @param isAdmin whether the user is an admin
     * @return a list of courses where the user has at least TA access
     */
    @Query("""
            SELECT c
            FROM Course c
            WHERE :isAdmin = TRUE
               OR EXISTS (
                   SELECT ucr FROM UserCourseRole ucr
                   WHERE ucr.course.id = c.id AND ucr.user.id = :userId
                   AND ucr.role IN (de.tum.cit.aet.artemis.core.domain.CourseRole.TEACHING_ASSISTANT,
                                    de.tum.cit.aet.artemis.core.domain.CourseRole.EDITOR,
                                    de.tum.cit.aet.artemis.core.domain.CourseRole.INSTRUCTOR)
               )
            """)
    List<Course> findCoursesForAtLeastTutor(@Param("userId") Long userId, @Param("isAdmin") boolean isAdmin);

    /**
     * Finds all courses where the user has any role (student, TA, editor, or instructor).
     *
     * @param userId  the id of the user
     * @param isAdmin whether the user is an admin
     * @return a list of courses accessible to the user
     */
    @Query("""
            SELECT c
            FROM Course c
            WHERE :isAdmin = TRUE
               OR EXISTS (
                   SELECT ucr FROM UserCourseRole ucr
                   WHERE ucr.course.id = c.id AND ucr.user.id = :userId
               )
            """)
    List<Course> findAllAccessibleCoursesForUser(@Param("userId") Long userId, @Param("isAdmin") boolean isAdmin);

    /**
     * Finds the courses among the requested ids where the user has any role (student, TA, editor, or instructor).
     * <p>
     * Same access rule as {@link #findAllAccessibleCoursesForUser}, narrowed in the query so a scoped request does not
     * load every accessible course only to drop most of them. Ids the user cannot access are simply not returned.
     *
     * @param userId    the id of the user
     * @param isAdmin   whether the user is an admin
     * @param courseIds the course ids the caller asked for
     * @return the requested courses the user can access
     */
    @Query("""
            SELECT c
            FROM Course c
            WHERE c.id IN :courseIds
               AND (:isAdmin = TRUE
                   OR EXISTS (
                       SELECT ucr FROM UserCourseRole ucr
                       WHERE ucr.course.id = c.id AND ucr.user.id = :userId
                   ))
            """)
    List<Course> findAllAccessibleCoursesForUserAndIdIn(@Param("userId") Long userId, @Param("isAdmin") boolean isAdmin, @Param("courseIds") Collection<Long> courseIds);

    @Query("""
                SELECT course.timeZone
                FROM Course course
                WHERE course.id = :courseId
            """)
    Optional<String> getTimeZoneOfCourseById(@Param("courseId") long courseId);

    /**
     * Counts the number of courses where the user has the instructor role.
     *
     * @param userId the id of the user
     * @return the count of courses where the user is an instructor
     */
    @Query("""
            SELECT COUNT(DISTINCT ucr.course.id)
            FROM UserCourseRole ucr
            WHERE ucr.user.id = :userId
            AND ucr.role = de.tum.cit.aet.artemis.core.domain.CourseRole.INSTRUCTOR
            """)
    long countCoursesForInstructor(@Param("userId") Long userId);

    /**
     * Projects the fields the course overview container renders.
     * <p>
     * The endpoint used to load the whole {@code Course} to read a handful of scalars off it. Selecting them directly
     * means the successful path materialises no entity at all, so nothing can lazily initialise on the way out and the
     * response cannot drift as the entity gains fields.
     * <p>
     * The unread notification count lives outside this table, so the caller fills it in with
     * {@link CourseForOverviewDTO#withNotificationCount(long)}.
     *
     * @param courseId the course to project
     * @return the projected course, or empty when it does not exist
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.course.dto.CourseForOverviewDTO(
                course.id,
                course.title,
                course.startDate,
                course.endDate,
                course.color,
                course.courseIcon,
                course.testCourse,
                course.onlineCourse,
                course.enrollmentEnabled,
                course.enrollmentEndDate,
                course.unenrollmentEnabled,
                course.unenrollmentEndDate,
                course.courseInformationSharingConfiguration,
                course.courseInformationSharingMessagingCodeOfConduct,
                course.accuracyOfScores,
                course.presentationScore,
                course.maxComplaints,
                course.maxTeamComplaints,
                course.maxComplaintTimeDays,
                course.maxComplaintTextLimit,
                course.maxComplaintResponseTextLimit,
                course.maxRequestMoreFeedbackTimeDays,
                COALESCE(athenaConfig.gradingFeedbackEnabled, false),
                COALESCE(athenaConfig.formativeFeedbackEnabled, false))
            FROM Course course
                LEFT JOIN CourseAthenaConfig athenaConfig ON athenaConfig.course = course
            WHERE course.id = :courseId
            """)
    Optional<CourseForOverviewDTO> findForOverview(@Param("courseId") long courseId);
}
