package de.tum.cit.aet.artemis.core.service.course;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
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

import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.atlas.api.PrerequisitesApi;
import de.tum.cit.aet.artemis.communication.domain.FaqState;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.PageUtil;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;
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
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
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

    private final Optional<LectureApi> lectureApi;

    private final Optional<ExerciseGroupApi> exerciseGroupApi;

    private final Optional<ExamRepositoryApi> examRepositoryApi;

    private final CourseRepository courseRepository;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final Optional<PrerequisitesApi> prerequisitesApi;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ExerciseRepository exerciseRepository;

    private final Optional<TutorialGroupApi> tutorialGroupApi;

    private final Optional<PlagiarismCaseApi> plagiarismCaseApi;

    private final FaqRepository faqRepository;

    private final CourseVisibleService courseVisibleService;

    public CourseService(Optional<LectureApi> lectureApi, CourseRepository courseRepository, ExerciseService exerciseService, AuthorizationCheckService authCheckService,
            Optional<CompetencyProgressApi> competencyProgressApi, Optional<ExamRepositoryApi> examRepositoryApi, Optional<ExerciseGroupApi> exerciseGroupApi,
            StudentParticipationRepository studentParticipationRepository, ExerciseRepository exerciseRepository, Optional<TutorialGroupApi> tutorialGroupApi,
            Optional<PlagiarismCaseApi> plagiarismCaseApi, Optional<PrerequisitesApi> prerequisitesApi, FaqRepository faqRepository, CourseVisibleService courseVisibleService) {
        this.lectureApi = lectureApi;
        this.courseRepository = courseRepository;
        this.exerciseService = exerciseService;
        this.authCheckService = authCheckService;
        this.exerciseGroupApi = exerciseGroupApi;
        this.competencyProgressApi = competencyProgressApi;
        this.examRepositoryApi = examRepositoryApi;
        this.studentParticipationRepository = studentParticipationRepository;
        this.exerciseRepository = exerciseRepository;
        this.tutorialGroupApi = tutorialGroupApi;
        this.plagiarismCaseApi = plagiarismCaseApi;
        this.prerequisitesApi = prerequisitesApi;
        this.faqRepository = faqRepository;
        this.courseVisibleService = courseVisibleService;
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
        if (authCheckService.isAdmin(user)) {
            coursePage = courseRepository.findByTitleIgnoreCaseContaining(searchTerm, pageable);
        }
        else {
            coursePage = courseRepository.findByTitleInCoursesWhereInstructorOrEditor(searchTerm, user.getGroups(), pageable);
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
     * Add plagiarism cases to each exercise.
     *
     * @param exercises the course exercises for which the plagiarism cases should be fetched.
     * @param userId    the user for which the plagiarism cases should be fetched.
     */
    public void fetchPlagiarismCasesForCourseExercises(Set<Exercise> exercises, Long userId) {
        if (plagiarismCaseApi.isEmpty()) {
            return;
        }

        PlagiarismCaseApi api = plagiarismCaseApi.get();
        Set<Long> exerciseIds = exercises.stream().map(Exercise::getId).collect(Collectors.toSet());
        List<PlagiarismCase> plagiarismCasesOfUserInCourseExercises = api.findByStudentIdAndExerciseIds(userId, exerciseIds);
        for (Exercise exercise : exercises) {
            // Add plagiarism cases to each exercise.
            Set<PlagiarismCase> plagiarismCasesForExercise = plagiarismCasesOfUserInCourseExercises.stream()
                    .filter(plagiarismCase -> plagiarismCase.getExercise().getId().equals(exercise.getId())).collect(Collectors.toSet());
            exercise.setPlagiarismCases(plagiarismCasesForExercise);
        }
    }

    /**
     * Get one course with exercises, lectures, exams, competencies and tutorial groups (filtered for given user)
     *
     * @param courseId the course to fetch
     * @param user     the user entity
     * @return the course including exercises, lectures, exams, competencies and tutorial groups (filtered for given user)
     */
    public Course findOneWithExercisesAndLecturesAndExamsAndCompetenciesAndTutorialGroupsAndFaqForUser(Long courseId, User user) {
        Course course = courseRepository.findByIdWithLecturesElseThrow(courseId);
        // Load exercises with categories separately because this is faster than loading them with lectures and exam above (the query would become too complex)
        course.setExercises(exerciseRepository.findByCourseIdWithCategories(courseId));
        course.setExercises(exerciseService.filterExercisesForCourse(course, user, true));
        exerciseService.loadExerciseDetailsIfNecessary(course, user, true);
        examRepositoryApi.ifPresent(api -> course.setExams(api.findByCourseIdForUser(courseId, user.getId(), user.getGroups(), ZonedDateTime.now())));
        // TODO: in the future, we only want to know if lectures exist, the actual lectures will be loaded when the user navigates into the lecture
        lectureApi.ifPresent(api -> course.setLectures(api.filterVisibleLecturesWithActiveAttachments(course, course.getLectures(), user)));
        // NOTE: in this call we only want to know if competencies exist in the course, we will load them when the user navigates into them
        competencyProgressApi.ifPresent(api -> course.setNumberOfCompetencies(api.countByCourseId(courseId)));
        // NOTE: in this call we only want to know if prerequisites exist in the course, we will load them when the user navigates into them
        prerequisitesApi.ifPresent(api -> course.setNumberOfPrerequisites(api.countByCourseId(courseId)));
        // NOTE: in this call we only want to know if tutorial groups exist in the course, we will load them when the user navigates into them
        if (tutorialGroupApi.isPresent()) {
            course.setNumberOfTutorialGroups(tutorialGroupApi.get().countByCourseId(courseId));
        }
        else {
            course.setNumberOfTutorialGroups(0L);
        }
        if (course.isFaqEnabled()) {
            course.setFaqs(faqRepository.findAllByCourseIdAndFaqState(courseId, FaqState.ACCEPTED));
        }
        if (authCheckService.isOnlyStudentInCourse(course, user) && examRepositoryApi.isPresent()) {
            var examRepoApi = examRepositoryApi.get();
            course.setExams(examRepoApi.filterVisibleExams(course.getExams()));
        }
        return course;
    }

    /**
     * Get all courses for the given user
     *
     * @param user the user entity
     * @return an unmodifiable set of all courses for the user
     */
    public Set<Course> findAllActiveForUser(User user) {
        return courseRepository.findAllActive(ZonedDateTime.now()).stream().filter(course -> courseVisibleService.isCourseVisibleForUser(user, course)).collect(Collectors.toSet());
    }

    /**
     * Get all courses with exercises (filtered for given user)
     *
     * @param user the user entity
     * @return an unmodifiable list of all courses including exercises for the user
     */
    public Set<Course> findAllActiveWithExercisesForUser(User user) {
        long start = System.nanoTime();

        var userVisibleCourses = courseRepository.findAllActive().stream().filter(course -> courseVisibleService.isCourseVisibleForUser(user, course)).filter(Objects::nonNull)
                .collect(Collectors.toSet());

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
        var courses = userVisibleCourses.stream().peek(course -> {
            // connect the exercises with the course
            course.setExercises(allExercises.stream().filter(ex -> ex.getCourseViaExerciseGroupOrCourseMember().getId().equals(course.getId())).collect(Collectors.toSet()));
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
