package de.tum.cit.aet.artemis.course.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.ws.rs.BadRequestException;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.atlas.api.CompetencyApi;
import de.tum.cit.aet.artemis.atlas.api.PrerequisitesApi;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.PageUtil;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exam.api.ExamRepositoryApi;
import de.tum.cit.aet.artemis.exam.api.ExerciseGroupApi;
import de.tum.cit.aet.artemis.exam.config.ExamApiNotPresentException;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.lecture.api.LectureApi;
import de.tum.cit.aet.artemis.plagiarism.api.PlagiarismCaseApi;
import de.tum.cit.aet.artemis.tutorialgroup.api.TutorialGroupApi;

/**
 * Service Implementation for managing Course.
 */
@Profile(PROFILE_CORE)
@Service
@Lazy
public class CourseService {

    private static final Logger log = LoggerFactory.getLogger(CourseService.class);

    private final ExerciseService exerciseService;

    private final AuthorizationCheckService authCheckService;

    private final Optional<ExerciseGroupApi> exerciseGroupApi;

    private final CourseRepository courseRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ExerciseRepository exerciseRepository;

    public CourseService(Optional<LectureApi> ignoredLectureApi, CourseRepository courseRepository, ExerciseService exerciseService, AuthorizationCheckService authCheckService,
            Optional<CompetencyApi> ignoredCompetencyApi, Optional<ExamRepositoryApi> ignoredExamRepositoryApi, Optional<ExerciseGroupApi> exerciseGroupApi,
            StudentParticipationRepository studentParticipationRepository, ExerciseRepository exerciseRepository, Optional<TutorialGroupApi> ignoredTutorialGroupApi,
            Optional<PlagiarismCaseApi> ignoredPlagiarismCaseApi, Optional<PrerequisitesApi> ignoredPrerequisitesApi) {
        this.courseRepository = courseRepository;
        this.exerciseService = exerciseService;
        this.authCheckService = authCheckService;
        this.exerciseGroupApi = exerciseGroupApi;
        this.studentParticipationRepository = studentParticipationRepository;
        this.exerciseRepository = exerciseRepository;
    }

    /**
     * Search for all courses fitting a {@link SearchTermPageableSearchDTO search query}. The result is paged.
     *
     * @param search The search query defining the search term and the size of the returned page
     * @param user   The user for whom to fetch all available lectures
     * @return A wrapper object containing a list of all found courses and the total number of pages
     */
    public SearchResultPageDTO<Course> getAllOnPageWithSize(final SearchTermPageableSearchDTO<String> search, final User user) {
        final var pageable = PageUtil.createDefaultPageRequest(search, PageUtil.ColumnMapping.COURSE);

        final var searchTerm = search.getSearchTerm();
        final Page<Course> coursePage;
        if (authCheckService.isCurrentUserAdminAccessEnabled()) {
            coursePage = courseRepository.findByTitleIgnoreCaseContaining(searchTerm, pageable);
        }
        else {
            coursePage = courseRepository.findByTitleInCoursesWhereInstructorOrEditor(searchTerm, user.getId(), pageable);
        }
        return new SearchResultPageDTO<>(coursePage.getContent(), coursePage.getTotalPages());
    }

    /**
     * Note: The number of courses should not change
     *
     * @param courses         the courses for which the participations should be fetched
     * @param user            the user for which the participations should be fetched
     * @param includeTestRuns flag that indicates whether test run participations should be included
     */
    public void fetchParticipationsWithSubmissionsAndResultsForCourses(Collection<Course> courses, User user, boolean includeTestRuns) {
        var exercises = courses.stream().flatMap(course -> course.getExercises().stream()).collect(Collectors.toSet());
        var participationsOfUserInExercises = studentParticipationRepository.getAllParticipationsOfUserInExercises(user, exercises, includeTestRuns);
        if (participationsOfUserInExercises.isEmpty()) {
            return;
        }
        for (Course course : courses) {
            boolean isStudent = !authCheckService.isAtLeastTeachingAssistantInCourse(course, user);
            for (Exercise exercise : course.getExercises()) {
                // add participation with submission and result to each exercise
                exerciseService.filterExerciseForCourseDashboard(exercise, participationsOfUserInExercises, isStudent);
                // remove sensitive information from the exercise for students
                if (isStudent) {
                    exercise.filterSensitiveInformation();
                }
            }
        }
    }

    /**
     * Get all courses for the given user
     *
     * @param user the user entity
     * @return an unmodifiable set of all courses for the user
     */
    public Set<Course> findAllActiveForUser(User user) {
        ZonedDateTime now = ZonedDateTime.now();
        // Admins see every active course — no per-course visibility check needed since isAdmin always returns true.
        if (authCheckService.isCurrentUserAdminAccessEnabled()) {
            return new HashSet<>(courseRepository.findAllActive(now));
        }
        // Non-admins only see courses they are a member of: push that filter into the query (indexed join) so we load
        // only the user's own courses instead of all active courses + an in-memory visibility check per course.
        return new HashSet<>(courseRepository.findAllActiveWhereUserHasAnyRole(user.getId(), now));
    }

    /**
     * Gets the courses displayed on the consolidated dashboard, including their exercises. Active courses are visible
     * to every enrolled user; courses that have not started yet are additionally visible to their management users.
     *
     * @param user the user for whom dashboard visibility is evaluated
     * @return the dashboard courses including their exercises
     */
    public Set<Course> findAllForDashboardWithExercisesForUser(User user) {
        long start = System.nanoTime();
        var now = ZonedDateTime.now();

        // Management users must be able to prepare courses before their start date. Students continue to see only active courses.
        // Admins can manage every course, while non-admins only receive future courses in which they hold a management role.
        var userVisibleCourses = (authCheckService.isCurrentUserAdminAccessEnabled() ? courseRepository.findAllNotEnded(now).stream()
                : courseRepository.findAllForDashboardWhereUserHasAnyRole(user.getId(), now).stream()).filter(Objects::nonNull).collect(Collectors.toSet());

        if (log.isDebugEnabled()) {
            log.debug("Find user visible courses finished after {}", TimeLogUtil.formatDurationFrom(start));
        }
        long startFindAllExercises = System.nanoTime();
        var courseIds = userVisibleCourses.stream().map(DomainObject::getId).collect(Collectors.toSet());
        // TODO Performance: we only need the total score, the number of exercises and exams and - in case there is one - the currently active exercise(s)/exam(s)
        // we do NOT need to retrieve this information and send it to the client
        Set<Exercise> allExercises = exerciseRepository.findByCourseIds(courseIds);

        if (log.isDebugEnabled()) {
            log.debug("findAllExercisesByCourseIds finished with {} exercises after {}", allExercises.size(), TimeLogUtil.formatDurationFrom(startFindAllExercises));
        }

        long startFilterAll = System.nanoTime();
        // Pre-assign the course back-reference on all exercises to prevent Hibernate 7 lazy proxy initialization.
        // findByCourseIds only returns course exercises (not exam exercises), so we can safely set the course directly.
        Map<Long, Course> courseById = userVisibleCourses.stream().collect(Collectors.toMap(DomainObject::getId, c -> c));
        allExercises.forEach(ex -> {
            Course c = courseById.get(ex.getCourseViaExerciseGroupOrCourseMember().getId());
            if (c != null) {
                ex.setCourse(c);
            }
        });

        var courses = userVisibleCourses.stream().peek(course -> {
            // connect the exercises with the course
            course.setExercises(allExercises.stream().filter(ex -> course.equals(ex.getCourseViaExerciseGroupOrCourseMember())).collect(Collectors.toSet()));
            course.setExercises(exerciseService.filterExercisesForCourse(course, user, false));
            exerciseService.loadExerciseDetailsIfNecessary(course, user, false);
            // we do not send actual lectures or exams to the client, not needed
            course.setLectures(Set.of());
            course.setExams(Set.of());
        }).collect(Collectors.toSet());

        if (log.isDebugEnabled()) {
            log.debug("all {} filterExercisesForCourse individually finished together after {}", courses.size(), TimeLogUtil.formatDurationFrom(startFilterAll));
            log.debug("Filter exercises, lectures, and exams finished after {}", TimeLogUtil.formatDurationFrom(start));
        }
        return courses;
    }

    /**
     * Gets a set of all online courses for a specific LTI platform registration, filtered by the instructor user.
     *
     * @param registrationId the registration ID of the LTI platform to filter courses.
     * @param user           the User object representing the instructor whose courses are to be fetched.
     * @return a set of {@link Course} objects where the user is an instructor, related to the specified LTI platform.
     */
    public Set<Course> findAllOnlineCoursesForPlatformForUser(String registrationId, User user) {
        return courseRepository.findOnlineCoursesWithRegistrationIdEager(registrationId).stream().filter(course -> authCheckService.isInstructorInCourse(course, user))
                .collect(Collectors.toSet());
    }

    /**
     * If the exercise is part of an exam, retrieve the course through ExerciseGroup -> Exam -> Course.
     * Otherwise, the course is already set and the id can be used to retrieve the course from the database.
     *
     * @param exercise the Exercise for which the course is retrieved
     * @return the Course of the Exercise
     */
    public Course retrieveCourseOverExerciseGroupOrCourseId(Exercise exercise) {

        if (exercise.isExamExercise()) {
            ExerciseGroupApi api = exerciseGroupApi.orElseThrow(() -> new ExamApiNotPresentException(ExerciseGroupApi.class));
            ExerciseGroup exerciseGroup = api.findByIdElseThrow(exercise.getExerciseGroup().getId());
            exercise.setExerciseGroup(exerciseGroup);
            return exerciseGroup.getExam().getCourse();
        }
        else {
            Course course = courseRepository.findByIdElseThrow(exercise.getCourseViaExerciseGroupOrCourseMember().getId());
            exercise.setCourse(course);
            return course;
        }
    }

    /**
     * Checks if learning paths are enabled for the given course. If not, a BadRequestException is thrown.
     * <p>
     * If fetching the course from the database is not necessary, prefer using the method {@link #checkLearningPathsEnabledElseThrow(long)} with the course id as parameter.
     *
     * @param course the course to check
     */
    public void checkLearningPathsEnabledElseThrow(@NonNull Course course) {
        if (!course.getLearningPathsEnabled()) {
            throw new BadRequestException("Learning paths are not enabled for this course.");
        }
    }

    /**
     * Checks if learning paths are enabled for the given course. If not, a BadRequestException is thrown.
     *
     * @param courseId the id of the course to check
     */
    public void checkLearningPathsEnabledElseThrow(long courseId) {
        if (!courseRepository.hasLearningPathsEnabled(courseId)) {
            throw new BadRequestException("Learning paths are not enabled for this course.");
        }
    }
}
