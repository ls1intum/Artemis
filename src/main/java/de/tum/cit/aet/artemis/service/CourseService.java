package de.tum.cit.aet.artemis.service;

import static de.tum.cit.aet.artemis.assessment.domain.ComplaintType.COMPLAINT;
import static de.tum.cit.aet.artemis.assessment.domain.ComplaintType.MORE_FEEDBACK;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.service.util.RoundingUtil.roundScoreSpecifiedByCourseSettings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintResponseRepository;
import de.tum.cit.aet.artemis.assessment.repository.GradingScaleRepository;
import de.tum.cit.aet.artemis.assessment.repository.ParticipantScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.RatingRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRelationRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.atlas.repository.PrerequisiteRepository;
import de.tum.cit.aet.artemis.communication.domain.NotificationType;
import de.tum.cit.aet.artemis.communication.domain.notification.GroupNotification;
import de.tum.cit.aet.artemis.communication.repository.GroupNotificationRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ConversationRepository;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.StatisticsEntry;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.StatisticsRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.repository.ExerciseGroupRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismCaseRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.service.dto.StudentDTO;
import de.tum.cit.aet.artemis.service.exam.ExamDeletionService;
import de.tum.cit.aet.artemis.service.export.CourseExamExportService;
import de.tum.cit.aet.artemis.service.iris.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.service.learningpath.LearningPathService;
import de.tum.cit.aet.artemis.service.notifications.GroupNotificationService;
import de.tum.cit.aet.artemis.service.tutorialgroups.TutorialGroupChannelManagementService;
import de.tum.cit.aet.artemis.service.user.UserService;
import de.tum.cit.aet.artemis.service.util.TimeLogUtil;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupNotificationRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupRepository;
import de.tum.cit.aet.artemis.web.rest.dto.CourseContentCount;
import de.tum.cit.aet.artemis.web.rest.dto.CourseManagementDetailViewDTO;
import de.tum.cit.aet.artemis.web.rest.dto.DueDateStat;
import de.tum.cit.aet.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.web.rest.dto.StatsForDashboardDTO;
import de.tum.cit.aet.artemis.web.rest.dto.TutorLeaderboardDTO;
import de.tum.cit.aet.artemis.web.rest.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.web.rest.util.PageUtil;

/**
 * Service Implementation for managing Course.
 */
@Profile(PROFILE_CORE)
@Service
public class CourseService {

    private static final Logger log = LoggerFactory.getLogger(CourseService.class);

    @Value("${artemis.course-archives-path}")
    private Path courseArchivesDirPath;

    private final TutorialGroupChannelManagementService tutorialGroupChannelManagementService;

    private final CompetencyRelationRepository competencyRelationRepository;

    private final ExerciseService exerciseService;

    private final ExerciseDeletionService exerciseDeletionService;

    private final AuthorizationCheckService authCheckService;

    private final LectureService lectureService;

    private final GroupNotificationRepository groupNotificationRepository;

    private final UserService userService;

    private final ExerciseGroupRepository exerciseGroupRepository;

    private final CourseExamExportService courseExamExportService;

    private final ExamDeletionService examDeletionService;

    private final ExamRepository examRepository;

    private final GroupNotificationService groupNotificationService;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final AuditEventRepository auditEventRepository;

    private final CompetencyRepository competencyRepository;

    private final PrerequisiteRepository prerequisiteRepository;

    private final GradingScaleRepository gradingScaleRepository;

    private final StatisticsRepository statisticsRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final TutorLeaderboardService tutorLeaderboardService;

    private final RatingRepository ratingRepository;

    private final ComplaintService complaintService;

    private final ComplaintRepository complaintRepository;

    private final ComplaintResponseRepository complaintResponseRepository;

    private final SubmissionRepository submissionRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ResultRepository resultRepository;

    private final ExerciseRepository exerciseRepository;

    private final ParticipantScoreRepository participantScoreRepository;

    private final PresentationPointsCalculationService presentationPointsCalculationService;

    private final TutorialGroupRepository tutorialGroupRepository;

    private final PlagiarismCaseRepository plagiarismCaseRepository;

    private final ConversationRepository conversationRepository;

    private final LearningPathService learningPathService;

    private final Optional<IrisSettingsService> irisSettingsService;

    private final LectureRepository lectureRepository;

    private final TutorialGroupNotificationRepository tutorialGroupNotificationRepository;

    public CourseService(CourseRepository courseRepository, ExerciseService exerciseService, ExerciseDeletionService exerciseDeletionService,
            AuthorizationCheckService authCheckService, UserRepository userRepository, LectureService lectureService, GroupNotificationRepository groupNotificationRepository,
            ExerciseGroupRepository exerciseGroupRepository, AuditEventRepository auditEventRepository, UserService userService, ExamDeletionService examDeletionService,
            CompetencyRepository competencyRepository, GroupNotificationService groupNotificationService, ExamRepository examRepository,
            CourseExamExportService courseExamExportService, GradingScaleRepository gradingScaleRepository, StatisticsRepository statisticsRepository,
            StudentParticipationRepository studentParticipationRepository, TutorLeaderboardService tutorLeaderboardService, RatingRepository ratingRepository,
            ComplaintService complaintService, ComplaintRepository complaintRepository, ResultRepository resultRepository, ComplaintResponseRepository complaintResponseRepository,
            SubmissionRepository submissionRepository, ProgrammingExerciseRepository programmingExerciseRepository, ExerciseRepository exerciseRepository,
            ParticipantScoreRepository participantScoreRepository, PresentationPointsCalculationService presentationPointsCalculationService,
            TutorialGroupRepository tutorialGroupRepository, PlagiarismCaseRepository plagiarismCaseRepository, ConversationRepository conversationRepository,
            LearningPathService learningPathService, Optional<IrisSettingsService> irisSettingsService, LectureRepository lectureRepository,
            TutorialGroupNotificationRepository tutorialGroupNotificationRepository, TutorialGroupChannelManagementService tutorialGroupChannelManagementService,
            PrerequisiteRepository prerequisiteRepository, CompetencyRelationRepository competencyRelationRepository) {
        this.courseRepository = courseRepository;
        this.exerciseService = exerciseService;
        this.exerciseDeletionService = exerciseDeletionService;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
        this.lectureService = lectureService;
        this.groupNotificationRepository = groupNotificationRepository;
        this.exerciseGroupRepository = exerciseGroupRepository;
        this.auditEventRepository = auditEventRepository;
        this.userService = userService;
        this.examDeletionService = examDeletionService;
        this.competencyRepository = competencyRepository;
        this.groupNotificationService = groupNotificationService;
        this.examRepository = examRepository;
        this.courseExamExportService = courseExamExportService;
        this.gradingScaleRepository = gradingScaleRepository;
        this.statisticsRepository = statisticsRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.tutorLeaderboardService = tutorLeaderboardService;
        this.ratingRepository = ratingRepository;
        this.complaintService = complaintService;
        this.complaintRepository = complaintRepository;
        this.complaintResponseRepository = complaintResponseRepository;
        this.submissionRepository = submissionRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.resultRepository = resultRepository;
        this.exerciseRepository = exerciseRepository;
        this.participantScoreRepository = participantScoreRepository;
        this.presentationPointsCalculationService = presentationPointsCalculationService;
        this.tutorialGroupRepository = tutorialGroupRepository;
        this.plagiarismCaseRepository = plagiarismCaseRepository;
        this.conversationRepository = conversationRepository;
        this.learningPathService = learningPathService;
        this.irisSettingsService = irisSettingsService;
        this.lectureRepository = lectureRepository;
        this.tutorialGroupNotificationRepository = tutorialGroupNotificationRepository;
        this.tutorialGroupChannelManagementService = tutorialGroupChannelManagementService;
        this.prerequisiteRepository = prerequisiteRepository;
        this.competencyRelationRepository = competencyRelationRepository;
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
                exerciseService.filterForCourseDashboard(exercise, participationsOfUserInExercises, isStudent);
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
        Set<Long> exerciseIds = exercises.stream().map(Exercise::getId).collect(Collectors.toSet());
        List<PlagiarismCase> plagiarismCasesOfUserInCourseExercises = plagiarismCaseRepository.findByStudentIdAndExerciseIds(userId, exerciseIds);
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
    public Course findOneWithExercisesAndLecturesAndExamsAndCompetenciesAndTutorialGroupsForUser(Long courseId, User user) {
        Course course = courseRepository.findByIdWithLecturesElseThrow(courseId);
        // Load exercises with categories separately because this is faster than loading them with lectures and exam above (the query would become too complex)
        course.setExercises(exerciseRepository.findByCourseIdWithCategories(course.getId()));
        course.setExercises(exerciseService.filterExercisesForCourse(course, user));
        exerciseService.loadExerciseDetailsIfNecessary(course, user);
        course.setExams(examRepository.findByCourseIdsForUser(Set.of(course.getId()), user.getId(), user.getGroups(), ZonedDateTime.now()));
        // TODO: in the future, we only want to know if lectures exist, the actual lectures will be loaded when the user navigates into the lecture
        course.setLectures(lectureService.filterVisibleLecturesWithActiveAttachments(course, course.getLectures(), user));
        // NOTE: in this call we only want to know if competencies exist in the course, we will load them when the user navigates into them
        course.setNumberOfCompetencies(competencyRepository.countByCourse(course));
        // NOTE: in this call we only want to know if prerequisites exist in the course, we will load them when the user navigates into them
        course.setNumberOfPrerequisites(prerequisiteRepository.countByCourse(course));
        // NOTE: in this call we only want to know if tutorial groups exist in the course, we will load them when the user navigates into them
        course.setNumberOfTutorialGroups(tutorialGroupRepository.countByCourse(course));
        if (authCheckService.isOnlyStudentInCourse(course, user)) {
            course.setExams(examRepository.filterVisibleExams(course.getExams()));
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
        return courseRepository.findAllActive(ZonedDateTime.now()).stream().filter(course -> isCourseVisibleForUser(user, course)).collect(Collectors.toSet());
    }

    /**
     * Get all courses with exercises (filtered for given user)
     *
     * @param user the user entity
     * @return an unmodifiable list of all courses including exercises for the user
     */
    public Set<Course> findAllActiveWithExercisesForUser(User user) {
        long start = System.nanoTime();

        var userVisibleCourses = courseRepository.findAllActive().stream().filter(course -> isCourseVisibleForUser(user, course)).filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (log.isDebugEnabled()) {
            log.debug("Find user visible courses finished after {}", TimeLogUtil.formatDurationFrom(start));
        }
        long startFindAllExercises = System.nanoTime();
        var courseIds = userVisibleCourses.stream().map(DomainObject::getId).collect(Collectors.toSet());
        // TODO Performance: we only need the total score, the number of exercises and exams and - in case there is one - the currently active exercise(s)/exam(s)
        // we do NOT need to retrieve this information and send it to the client
        Set<Exercise> allExercises = exerciseRepository.findByCourseIdsWithCategories(courseIds);

        if (log.isDebugEnabled()) {
            log.debug("findAllExercisesByCourseIdsWithCategories finished with {} exercises after {}", allExercises.size(), TimeLogUtil.formatDurationFrom(startFindAllExercises));
        }
        var examCounts = examRepository.countVisibleExams(courseIds, ZonedDateTime.now());

        var lectureCounts = lectureRepository.countVisibleLectures(courseIds, ZonedDateTime.now());

        long startFilterAll = System.nanoTime();
        var courses = userVisibleCourses.stream().peek(course -> {
            // connect the exercises with the course
            course.setExercises(allExercises.stream().filter(ex -> ex.getCourseViaExerciseGroupOrCourseMember().getId().equals(course.getId())).collect(Collectors.toSet()));
            course.setExercises(exerciseService.filterExercisesForCourse(course, user));
            exerciseService.loadExerciseDetailsIfNecessary(course, user);
            long numberOfLectures = lectureCounts.stream().filter(count -> count.courseId() == course.getId()).map(CourseContentCount::count).findFirst().orElse(0L);
            course.setNumberOfLectures(numberOfLectures);
            long numberOfExams = examCounts.stream().filter(count -> count.courseId() == course.getId()).map(CourseContentCount::count).findFirst().orElse(0L);
            course.setNumberOfExams(numberOfExams);
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

    private boolean isCourseVisibleForUser(User user, Course course) {
        // Instructors and TAs see all courses that have not yet finished
        if (authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
            return true;
        }
        // Students see all courses that have already started (and not yet finished)
        if (user.getGroups().contains(course.getStudentGroupName())) {
            return course.getStartDate() == null || course.getStartDate().isBefore(ZonedDateTime.now());
        }

        return false;
    }

    /**
     * Gets all courses that the specified user can enroll in.
     *
     * @param user the user entity
     * @return unmodifiable set of courses the student can enroll in
     */
    public Set<Course> findAllEnrollableForUser(User user) {
        return courseRepository.findAllEnrollmentActiveWithOrganizationsAndPrerequisites(ZonedDateTime.now()).stream()
                .filter(course -> !user.getGroups().contains(course.getStudentGroupName())).collect(Collectors.toSet());
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
     * Deletes all elements associated with the course including:
     * <ul>
     * <li>The Course</li>
     * <li>All Exercises including:
     * submissions, participations, results, repositories and build plans, see {@link ExerciseDeletionService#delete}</li>
     * <li>All Lectures and their Attachments, see {@link LectureService#delete}</li>
     * <li>All GroupNotifications of the course, see {@link GroupNotificationRepository#delete}</li>
     * <li>All default groups created by Artemis, see {@link UserService#deleteGroup}</li>
     * <li>All Exams, see {@link ExamDeletionService#delete}</li>
     * <li>The Grading Scale if such exists, see {@link GradingScaleRepository#delete}</li>
     * </ul>
     *
     * @param course the course to be deleted
     */
    public void delete(Course course) {
        log.debug("Request to delete Course : {}", course.getTitle());

        deleteExercisesOfCourse(course);
        deleteLecturesOfCourse(course);
        deleteCompetenciesOfCourse(course);
        deleteTutorialGroupsOfCourse(course);
        deleteConversationsOfCourse(course);
        deleteNotificationsOfCourse(course);
        deleteDefaultGroups(course);
        deleteExamsOfCourse(course);
        deleteGradingScaleOfCourse(course);
        irisSettingsService.ifPresent(iss -> iss.deleteSettingsFor(course));
        courseRepository.deleteById(course.getId());
        log.debug("Successfully deleted course {}.", course.getTitle());
    }

    private void deleteTutorialGroupsOfCourse(Course course) {
        var tutorialGroups = tutorialGroupRepository.findAllByCourseId(course.getId());
        // we first need to delete notifications and channels, only then we can delete the tutorial group
        tutorialGroups.forEach(tutorialGroup -> {
            tutorialGroupNotificationRepository.deleteAllByTutorialGroupId(tutorialGroup.getId());
            tutorialGroupChannelManagementService.deleteTutorialGroupChannel(tutorialGroup);
            tutorialGroupRepository.deleteById(tutorialGroup.getId());
        });
    }

    private void deleteConversationsOfCourse(Course course) {
        // We cannot delete tutorial group channels here because the tutorial group references the channel.
        // These are deleted on deleteTutorialGroupsOfCourse().
        // Posts and Conversation Participants should be automatically deleted due to cascade
        conversationRepository.deleteAllByCourseId(course.getId());
    }

    private void deleteGradingScaleOfCourse(Course course) {
        // delete course grading scale if it exists
        Optional<GradingScale> gradingScale = gradingScaleRepository.findByCourseId(course.getId());
        gradingScale.ifPresent(gradingScaleRepository::delete);
    }

    private void deleteExamsOfCourse(Course course) {
        // delete the Exams
        List<Exam> exams = examRepository.findByCourseId(course.getId());
        for (Exam exam : exams) {
            examDeletionService.delete(exam.getId());
        }
    }

    private void deleteDefaultGroups(Course course) {
        // only delete (default) groups which have been created by Artemis before
        if (Objects.equals(course.getStudentGroupName(), course.getDefaultStudentGroupName())) {
            userService.deleteGroup(course.getStudentGroupName());
        }
        if (Objects.equals(course.getTeachingAssistantGroupName(), course.getDefaultTeachingAssistantGroupName())) {
            userService.deleteGroup(course.getTeachingAssistantGroupName());
        }
        if (Objects.equals(course.getEditorGroupName(), course.getDefaultEditorGroupName())) {
            userService.deleteGroup(course.getEditorGroupName());
        }
        if (Objects.equals(course.getInstructorGroupName(), course.getDefaultInstructorGroupName())) {
            userService.deleteGroup(course.getInstructorGroupName());
        }
    }

    private void deleteNotificationsOfCourse(Course course) {
        List<GroupNotification> notifications = groupNotificationRepository.findAllByCourseId(course.getId());
        groupNotificationRepository.deleteAll(notifications);
    }

    private void deleteLecturesOfCourse(Course course) {
        for (Lecture lecture : course.getLectures()) {
            lectureService.delete(lecture, false);
        }
    }

    private void deleteExercisesOfCourse(Course course) {
        for (Exercise exercise : course.getExercises()) {
            exerciseDeletionService.delete(exercise.getId(), true, true);
        }
    }

    private void deleteCompetenciesOfCourse(Course course) {
        competencyRelationRepository.deleteAllByCourseId(course.getId());
        prerequisiteRepository.deleteAll(course.getPrerequisites());
        competencyRepository.deleteAll(course.getCompetencies());
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
            ExerciseGroup exerciseGroup = exerciseGroupRepository.findByIdElseThrow(exercise.getExerciseGroup().getId());
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
     * Enrolls a user in a course by adding them to the student group of the course
     *
     * @param user   The user that should get added to the course
     * @param course The course to which the user should get added to
     */
    public void enrollUserForCourseOrThrow(User user, Course course) {
        authCheckService.checkUserAllowedToEnrollInCourseElseThrow(user, course);
        userService.addUserToGroup(user, course.getStudentGroupName());
        if (course.getLearningPathsEnabled()) {
            learningPathService.generateLearningPathForUser(course, user);
        }
        final var auditEvent = new AuditEvent(user.getLogin(), Constants.ENROLL_IN_COURSE, "course=" + course.getTitle());
        auditEventRepository.add(auditEvent);
        log.info("User {} has successfully enrolled in course {}", user.getLogin(), course.getTitle());
    }

    /**
     * Add multiple users to the course so that they can access it
     * The passed list of UserDTOs must include at least one unique user identifier (i.e. registration number OR email OR login)
     * <p>
     * This method first tries to find the user in the internal Artemis user database (because the user is probably already using Artemis).
     * In case the user cannot be found, it additionally searches the connected LDAP in case it is configured.
     *
     * @param courseId    the id of the course
     * @param studentDTOs the list of students (with at least registration number)
     * @param courseGroup the group the students should be added to
     * @return the list of students who could not be enrolled in the course, because they could NOT be found in the Artemis database and could NOT be found in the TUM LDAP
     */
    public List<StudentDTO> registerUsersForCourseGroup(Long courseId, List<StudentDTO> studentDTOs, String courseGroup) {
        var course = courseRepository.findByIdElseThrow(courseId);
        if (course.getLearningPathsEnabled()) {
            course = courseRepository.findWithEagerCompetenciesAndPrerequisitesByIdElseThrow(course.getId());
        }
        String courseGroupName = course.defineCourseGroupName(courseGroup);
        Role courseGroupRole = Role.fromString(courseGroup);
        List<StudentDTO> notFoundStudentsDTOs = new ArrayList<>();
        for (var studentDto : studentDTOs) {
            var optionalStudent = userService.findUserAndAddToCourse(studentDto.registrationNumber(), studentDto.login(), studentDto.email(), courseGroupName);
            if (optionalStudent.isEmpty()) {
                notFoundStudentsDTOs.add(studentDto);
            }
            else if (courseGroupRole == Role.STUDENT && course.getLearningPathsEnabled()) {
                learningPathService.generateLearningPathForUser(course, optionalStudent.get());
            }
        }

        return notFoundStudentsDTOs;
    }

    /**
     * Unenroll a user from a course by removing them from the student group of the course
     *
     * @param user   The user that should get removed from the course
     * @param course The course from which the user should be removed from
     */
    public void unenrollUserForCourseOrThrow(User user, Course course) {
        authCheckService.checkUserAllowedToUnenrollFromCourseElseThrow(user, course);
        userService.removeUserFromGroup(user, course.getStudentGroupName());
        final var auditEvent = new AuditEvent(user.getLogin(), Constants.UNENROLL_FROM_COURSE, "course=" + course.getTitle());
        auditEventRepository.add(auditEvent);
        log.info("User {} has successfully unenrolled from course {}", user.getLogin(), course.getTitle());
    }

    /**
     * Fetches a list of Courses
     *
     * @param onlyActive Whether to include courses with a past endDate
     * @return A list of Courses for the course management overview
     */
    public List<Course> getAllCoursesForManagementOverview(boolean onlyActive) {
        var user = userRepository.getUserWithGroupsAndAuthorities();
        boolean isAdmin = authCheckService.isAdmin(user);
        if (isAdmin && !onlyActive) {
            return courseRepository.findAll();
        }

        if (isAdmin) {
            return courseRepository.findAllNotEnded(ZonedDateTime.now());
        }
        var userGroups = new ArrayList<>(user.getGroups());

        if (onlyActive) {
            return courseRepository.findAllNotEndedCoursesByManagementGroupNames(ZonedDateTime.now(), userGroups);
        }

        return courseRepository.findAllCoursesByManagementGroupNames(userGroups);
    }

    /**
     * Get the active students for these particular exercise ids
     *
     * @param exerciseIds the ids to get the active students for
     * @param periodIndex the deviation from the current time
     * @param length      the length of the chart which we want to fill. This can either be 4 for the course overview or 17 for the course detail view
     * @param date        the date for which the active students' calculation should end (e.g. now)
     * @return An Integer list containing active students for each index. An index corresponds to a week
     */
    public List<Integer> getActiveStudents(Set<Long> exerciseIds, long periodIndex, int length, ZonedDateTime date) {
        /*
         * If the course did not start yet, the length of the chart will be negative (as the time difference between the start date end the current date is passed). In this case,
         * we return an empty list.
         */
        if (length < 0) {
            return new ArrayList<>(0);
        }
        LocalDateTime localStartDate = date.toLocalDateTime().with(DayOfWeek.MONDAY);
        LocalDateTime localEndDate = date.toLocalDateTime().with(DayOfWeek.SUNDAY);
        ZoneId zone = date.getZone();
        // startDate is the starting point of the data collection which is the Monday 3 weeks ago +/- the deviation from the current timeframe
        ZonedDateTime startDate = localStartDate.atZone(zone).minusWeeks((length - 1) + (length * (-periodIndex))).withHour(0).withMinute(0).withSecond(0).withNano(0);
        // the endDate depends on whether the current week is shown. If it is, the endDate is the Sunday of the current week at 23:59.
        // If the timeframe was adapted (periodIndex != 0), the endDate needs to be adapted according to the deviation
        ZonedDateTime endDate = periodIndex != 0 ? localEndDate.atZone(zone).minusWeeks(length * (-periodIndex)).withHour(23).withMinute(59).withSecond(59)
                : localEndDate.atZone(zone).withHour(23).withMinute(59).withSecond(59);
        if (exerciseIds.isEmpty()) {
            // avoid database call if there are no exercises to reduce performance issues
            return List.of();
        }
        List<StatisticsEntry> outcome = courseRepository.getActiveStudents(exerciseIds, startDate, endDate);
        List<StatisticsEntry> distinctOutcome = removeDuplicateActiveUserRows(outcome, startDate);
        List<Integer> result = new ArrayList<>(Collections.nCopies(length, 0));
        statisticsRepository.sortDataIntoWeeks(distinctOutcome, result, startDate);
        return result;
    }

    /**
     * The List of StatisticsEntries can contain duplicated entries, which means that a user has two entries in the same week.
     * This method compares the values and returns a List<StatisticsEntry> without duplicated entries.
     *
     * @param activeUserRows a list of entries
     * @param startDate      the startDate of the period
     * @return a List<StatisticsEntry> containing date and amount of active users in this period
     */

    private List<StatisticsEntry> removeDuplicateActiveUserRows(List<StatisticsEntry> activeUserRows, ZonedDateTime startDate) {
        int startIndex = statisticsRepository.getWeekOfDate(startDate);
        Map<Integer, Set<String>> usersByDate = new HashMap<>();
        for (StatisticsEntry listElement : activeUserRows) {
            // listElement.date has the form "2021-05-04", to convert it to ZonedDateTime, it needs a time
            String dateOfElement = listElement.getDate() + " 10:00";
            var zone = startDate.getZone();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            ZonedDateTime date = LocalDateTime.parse(dateOfElement, formatter).atZone(zone);
            int index = statisticsRepository.getWeekOfDate(date);
            /*
             * The database stores entries in UTC, so it can happen that entries lay in the calendar week one week before the calendar week of the startDate If startDate lays in a
             * calendar week other than the first one, we simply check whether the calendar week of the entry equals to the calendar week of startDate - 1. If startDate lays in the
             * first calendar week, we check whether the calendar week of the entry equals the last calendar week of the prior year. In either case, if the condition resolves to
             * true, we shift the index the submission is sorted in to the calendar week of startDate, as this is the first bucket in the timeframe of interest.
             */
            var unifiedDateWeekBeforeStartIndex = startIndex == 1 ? Math.toIntExact(IsoFields.WEEK_OF_WEEK_BASED_YEAR.rangeRefinedBy(startDate.minusWeeks(1)).getMaximum())
                    : startIndex - 1;
            index = index == unifiedDateWeekBeforeStartIndex ? startIndex : index;
            statisticsRepository.addUserToTimeslot(usersByDate, listElement, index);
        }
        List<StatisticsEntry> returnList = new ArrayList<>();
        usersByDate.forEach((date, users) -> {
            int year = date < statisticsRepository.getWeekOfDate(startDate) ? startDate.getYear() + 1 : startDate.getYear();
            ZonedDateTime firstDateOfYear = ZonedDateTime.of(year, 1, 1, 0, 0, 0, 0, startDate.getZone());
            ZonedDateTime start = statisticsRepository.getWeekOfDate(firstDateOfYear) == 1 ? firstDateOfYear.plusWeeks(date - 1) : firstDateOfYear.plusWeeks(date);
            StatisticsEntry listElement = new StatisticsEntry(start, users.size());
            returnList.add(listElement);
        });
        return returnList;
    }

    /**
     * Fetches Course Management Detail View data from repository and returns a DTO
     *
     * @param course       the course for with the details should be calculated
     * @param gradingScale the grading scale for the course
     * @return The DTO for the course management detail view
     */
    public CourseManagementDetailViewDTO getStatsForDetailView(Course course, GradingScale gradingScale) {

        var numberOfStudentsInCourse = Math.toIntExact(userRepository.countUserInGroup(course.getStudentGroupName()));
        var numberOfTeachingAssistantsInCourse = Math.toIntExact(userRepository.countUserInGroup(course.getTeachingAssistantGroupName()));
        var numberOfEditorsInCourse = Math.toIntExact(userRepository.countUserInGroup(course.getEditorGroupName()));
        var numberOfInstructorsInCourse = Math.toIntExact(userRepository.countUserInGroup(course.getInstructorGroupName()));

        Set<Exercise> exercises = exerciseRepository.findAllExercisesByCourseId(course.getId());
        if (exercises == null || exercises.isEmpty()) {
            return new CourseManagementDetailViewDTO(numberOfStudentsInCourse, numberOfTeachingAssistantsInCourse, numberOfEditorsInCourse, numberOfInstructorsInCourse, 0.0, 0L,
                    0L, 0.0, 0L, 0L, 0.0, 0L, 0L, 0.0, 0.0, 0.0, List.of());
        }
        // For the average score we need to only consider scores which are included completely or as bonus
        Set<Exercise> includedExercises = exercises.stream().filter(Exercise::isCourseExercise)
                .filter(exercise -> !exercise.getIncludedInOverallScore().equals(IncludedInOverallScore.NOT_INCLUDED)).collect(Collectors.toSet());
        Double averageScoreForCourse = participantScoreRepository.findAvgScore(includedExercises);
        averageScoreForCourse = averageScoreForCourse != null ? averageScoreForCourse : 0.0;
        double currentMaxAverageScore = includedExercises.stream().map(Exercise::getMaxPoints).mapToDouble(Double::doubleValue).sum();

        // calculate scores taking presentation points into account, if a grading scale is present and set for graded presentations
        if (gradingScale != null && gradingScale.getCourse().equals(course) && gradingScale.getPresentationsNumber() != null && gradingScale.getPresentationsWeight() != null) {
            double maxBaseScore = includedExercises.stream().filter(e -> !e.getIncludedInOverallScore().equals(IncludedInOverallScore.INCLUDED_AS_BONUS))
                    .map(Exercise::getMaxPoints).mapToDouble(Double::doubleValue).sum();
            currentMaxAverageScore += presentationPointsCalculationService.calculateReachablePresentationPoints(gradingScale, maxBaseScore);

            double avgPresentationScore = studentParticipationRepository.getAvgPresentationScoreByCourseId(course.getId());
            averageScoreForCourse = gradingScale.getPresentationsWeight() / 100.0 * avgPresentationScore
                    + (100.0 - gradingScale.getPresentationsWeight()) / 100.0 * averageScoreForCourse;
        }

        Set<Long> exerciseIds = exercises.stream().map(Exercise::getId).collect(Collectors.toSet());

        var endDate = this.determineEndDateForActiveStudents(course);
        var spanSize = this.determineTimeSpanSizeForActiveStudents(course, endDate, 17);
        var activeStudents = getActiveStudents(exerciseIds, 0, spanSize, endDate);

        DueDateStat assessments = resultRepository.countNumberOfAssessments(exerciseIds);
        long numberOfAssessments = assessments.inTime() + assessments.late();

        long numberOfInTimeSubmissions = submissionRepository.countAllByExerciseIdsSubmittedBeforeDueDate(exerciseIds)
                + programmingExerciseRepository.countAllSubmissionsByExerciseIdsSubmitted(exerciseIds);
        long numberOfLateSubmissions = submissionRepository.countAllByExerciseIdsSubmittedAfterDueDate(exerciseIds);

        long numberOfSubmissions = numberOfInTimeSubmissions + numberOfLateSubmissions;
        var currentPercentageAssessments = calculatePercentage(numberOfAssessments, numberOfSubmissions);

        long currentAbsoluteComplaints = complaintResponseRepository
                .countByComplaint_Result_Participation_Exercise_Course_Id_AndComplaint_ComplaintType_AndSubmittedTimeIsNotNull(course.getId(), COMPLAINT);
        long currentMaxComplaints = complaintRepository.countByResult_Participation_Exercise_Course_IdAndComplaintType(course.getId(), COMPLAINT);
        var currentPercentageComplaints = calculatePercentage(currentAbsoluteComplaints, currentMaxComplaints);

        long currentAbsoluteMoreFeedbacks = complaintResponseRepository
                .countByComplaint_Result_Participation_Exercise_Course_Id_AndComplaint_ComplaintType_AndSubmittedTimeIsNotNull(course.getId(), MORE_FEEDBACK);
        long currentMaxMoreFeedbacks = complaintRepository.countByResult_Participation_Exercise_Course_IdAndComplaintType(course.getId(), MORE_FEEDBACK);
        var currentPercentageMoreFeedbacks = calculatePercentage(currentAbsoluteMoreFeedbacks, currentMaxMoreFeedbacks);

        var currentAbsoluteAverageScore = roundScoreSpecifiedByCourseSettings((averageScoreForCourse / 100.0) * currentMaxAverageScore, course);
        var currentPercentageAverageScore = currentMaxAverageScore > 0.0 ? roundScoreSpecifiedByCourseSettings(averageScoreForCourse, course) : 0.0;

        return new CourseManagementDetailViewDTO(numberOfStudentsInCourse, numberOfTeachingAssistantsInCourse, numberOfEditorsInCourse, numberOfInstructorsInCourse,
                currentPercentageAssessments, numberOfAssessments, numberOfSubmissions, currentPercentageComplaints, currentAbsoluteComplaints, currentMaxComplaints,
                currentPercentageMoreFeedbacks, currentAbsoluteMoreFeedbacks, currentMaxMoreFeedbacks, currentPercentageAverageScore, currentAbsoluteAverageScore,
                currentMaxAverageScore, activeStudents);
    }

    private double calculatePercentage(double positive, double total) {
        return total > 0.0 ? Math.round(positive * 1000.0 / total) / 10.0 : 0.0;
    }

    /**
     * calculate statistics for the course administration dashboard
     *
     * @param course the course for which the statistics should be calculated
     * @return a DTO containing the statistics
     */
    public StatsForDashboardDTO getStatsForDashboardDTO(Course course) {
        Set<Long> courseExerciseIds = exerciseRepository.findAllIdsByCourseId(course.getId());

        StatsForDashboardDTO stats = new StatsForDashboardDTO();

        long numberOfInTimeSubmissions = submissionRepository.countAllByExerciseIdsSubmittedBeforeDueDate(courseExerciseIds);
        numberOfInTimeSubmissions += programmingExerciseRepository.countAllSubmissionsByExerciseIdsSubmitted(courseExerciseIds);

        final long numberOfLateSubmissions = submissionRepository.countAllByExerciseIdsSubmittedAfterDueDate(courseExerciseIds);
        DueDateStat totalNumberOfAssessments = resultRepository.countNumberOfAssessments(courseExerciseIds);
        stats.setTotalNumberOfAssessments(totalNumberOfAssessments);

        // no examMode here, so it's the same as totalNumberOfAssessments
        DueDateStat[] numberOfAssessmentsOfCorrectionRounds = { totalNumberOfAssessments };
        stats.setNumberOfAssessmentsOfCorrectionRounds(numberOfAssessmentsOfCorrectionRounds);
        stats.setNumberOfSubmissions(new DueDateStat(numberOfInTimeSubmissions, numberOfLateSubmissions));

        final long numberOfMoreFeedbackRequests = complaintService.countMoreFeedbackRequestsByCourseId(course.getId());
        stats.setNumberOfMoreFeedbackRequests(numberOfMoreFeedbackRequests);
        final long numberOfMoreFeedbackComplaintResponses = complaintService.countMoreFeedbackRequestResponsesByCourseId(course.getId());
        stats.setNumberOfOpenMoreFeedbackRequests(numberOfMoreFeedbackRequests - numberOfMoreFeedbackComplaintResponses);
        final long numberOfComplaints = complaintService.countComplaintsByCourseId(course.getId());
        stats.setNumberOfComplaints(numberOfComplaints);
        final long numberOfComplaintResponses = complaintService.countComplaintResponsesByCourseId(course.getId());
        stats.setNumberOfOpenComplaints(numberOfComplaints - numberOfComplaintResponses);
        final long numberOfAssessmentLocks = submissionRepository.countLockedSubmissionsByUserIdAndCourseId(userRepository.getUserWithGroupsAndAuthorities().getId(),
                course.getId());
        stats.setNumberOfAssessmentLocks(numberOfAssessmentLocks);
        final long totalNumberOfAssessmentLocks = submissionRepository.countLockedSubmissionsByCourseId(course.getId());
        stats.setTotalNumberOfAssessmentLocks(totalNumberOfAssessmentLocks);

        List<TutorLeaderboardDTO> leaderboardEntries = tutorLeaderboardService.getCourseLeaderboard(course, courseExerciseIds);
        stats.setTutorLeaderboardEntries(leaderboardEntries);
        stats.setNumberOfRatings(ratingRepository.countByResult_Participation_Exercise_Course_Id(course.getId()));
        return stats;
    }

    /**
     * Archives the course by creating a zip file will student submissions for
     * both the course exercises and exams.
     *
     * @param course the course to archive
     */
    @Async
    public void archiveCourse(Course course) {
        long start = System.nanoTime();
        SecurityUtils.setAuthorizationObject();

        // Archiving a course is only possible after the course is over
        if (ZonedDateTime.now().isBefore(course.getEndDate())) {
            return;
        }

        // This contains possible errors encountered during the archive process
        List<String> exportErrors = Collections.synchronizedList(new ArrayList<>());

        groupNotificationService.notifyInstructorGroupAboutCourseArchiveState(course, NotificationType.COURSE_ARCHIVE_STARTED, exportErrors);

        try {
            // Create course archives directory if it doesn't exist
            Files.createDirectories(courseArchivesDirPath);
            log.info("Created the course archives directory at {} because it didn't exist.", courseArchivesDirPath);

            // Export the course to the archives' directory.
            var archivedCoursePath = courseExamExportService.exportCourse(course, courseArchivesDirPath, exportErrors);

            // Attach the path to the archive to the course and save it in the database
            if (archivedCoursePath.isPresent()) {
                course.setCourseArchivePath(archivedCoursePath.get().getFileName().toString());
                courseRepository.saveAndFlush(course);
            }
            else {
                groupNotificationService.notifyInstructorGroupAboutCourseArchiveState(course, NotificationType.COURSE_ARCHIVE_FAILED, exportErrors);
                return;
            }
        }
        catch (Exception e) {
            var error = "Failed to create course archives directory " + courseArchivesDirPath + ": " + e.getMessage();
            exportErrors.add(error);
            log.info(error);
        }

        groupNotificationService.notifyInstructorGroupAboutCourseArchiveState(course, NotificationType.COURSE_ARCHIVE_FINISHED, exportErrors);
        log.info("archive course took {}", TimeLogUtil.formatDurationFrom(start));
    }

    /**
     * Cleans up a course by cleaning up all exercises from that course. This deletes all student
     * repositories and build plans. Note that a course has to be archived first before being cleaned up.
     *
     * @param courseId  The id of the course to clean up
     * @param principal the user that wants to cleanup the course
     */
    public void cleanupCourse(Long courseId, Principal principal) {
        final var auditEvent = new AuditEvent(principal.getName(), Constants.CLEANUP_COURSE, "course=" + courseId);
        auditEventRepository.add(auditEvent);
        // Get the course with all exercises
        var course = courseRepository.findByIdWithEagerExercisesElseThrow(courseId);
        if (!course.hasCourseArchive()) {
            log.info("Cannot clean up course {} because it hasn't been archived.", courseId);
            return;
        }

        // The Objects::nonNull is needed here because the relationship exam -> exercise groups is ordered and
        // hibernate sometimes adds nulls into the list of exercise groups to keep the order
        Set<Exercise> examExercises = examRepository.findByCourseIdWithExerciseGroupsAndExercises(courseId).stream().flatMap(e -> e.getExerciseGroups().stream())
                .filter(Objects::nonNull).map(ExerciseGroup::getExercises).flatMap(Collection::stream).collect(Collectors.toSet());

        var exercisesToCleanup = Stream.concat(course.getExercises().stream(), examExercises.stream()).collect(Collectors.toSet());
        exercisesToCleanup.forEach(exercise -> {
            if (exercise instanceof ProgrammingExercise) {
                exerciseDeletionService.cleanup(exercise.getId(), true);
            }
        });

        log.info("The course {} has been cleaned up!", courseId);
    }

    /**
     * Returns all users in a course that belong to the given group
     *
     * @param course    the course
     * @param groupName the name of the group
     * @return list of users
     */
    @NotNull
    public ResponseEntity<Set<User>> getAllUsersInGroup(Course course, String groupName) {
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        var usersInGroup = userRepository.findAllByIsDeletedIsFalseAndGroupsContains(groupName);
        usersInGroup.forEach(user -> {
            // explicitly set the registration number
            user.setVisibleRegistrationNumber(user.getRegistrationNumber());
            // remove some values which are not needed in the client
            user.setLastNotificationRead(null);
            user.setActivationKey(null);
            user.setLangKey(null);
            user.setLastNotificationRead(null);
            user.setLastModifiedBy(null);
            user.setLastModifiedDate(null);
            user.setCreatedBy(null);
            user.setCreatedDate(null);
        });
        removeUserVariables(usersInGroup);
        return ResponseEntity.ok().body(usersInGroup);
    }

    /**
     * Search for users of all user groups by login or name in course
     *
     * @param course     Course in which to search students
     * @param nameOfUser Login or name by which to search students
     * @return users whose login matched
     */
    public List<User> searchOtherUsersNameInCourse(Course course, String nameOfUser) {
        Set<String> groupNames = new HashSet<>();
        groupNames.add(course.getStudentGroupName());
        groupNames.add(course.getTeachingAssistantGroupName());
        groupNames.add(course.getEditorGroupName());
        groupNames.add(course.getInstructorGroupName());

        List<User> searchResult = userRepository.searchByNameInGroups(groupNames, nameOfUser);
        removeUserVariables(searchResult);

        // users should not find themselves
        User searchingUser = userRepository.getUser();
        searchResult = searchResult.stream().distinct().filter(user -> !user.getId().equals(searchingUser.getId())).toList();

        return (searchResult);
    }

    /**
     * adds a given user to a user group
     *
     * @param user  user to be added to a group
     * @param group user-group where the user should be added
     */
    public void addUserToGroup(User user, String group) {
        userService.addUserToGroup(user, group);
    }

    /**
     * removes a given user to a user group
     *
     * @param user  user to be removed from a group
     * @param group user-group where the user should be removed
     */
    public void removeUserFromGroup(User user, String group) {
        userService.removeUserFromGroup(user, group);
    }

    /**
     * If the corresponding group (student, tutor, editor, instructor) is not defined, this method will set the default group.
     *
     * @param course the course (typically created on the client and not yet existing) for which the groups should be validated
     */
    public void setDefaultGroupsIfNotSet(Course course) {
        if (!StringUtils.hasText(course.getStudentGroupName())) {
            course.setStudentGroupName(course.getDefaultStudentGroupName());
        }

        if (!StringUtils.hasText(course.getTeachingAssistantGroupName())) {
            course.setTeachingAssistantGroupName(course.getDefaultTeachingAssistantGroupName());
        }

        if (!StringUtils.hasText(course.getEditorGroupName())) {
            course.setEditorGroupName(course.getDefaultEditorGroupName());
        }

        if (!StringUtils.hasText(course.getInstructorGroupName())) {
            course.setInstructorGroupName(course.getDefaultInstructorGroupName());
        }
    }

    /**
     * Special case for editors: checks if the default editor group needs to be created when old courses are edited
     *
     * @param course the course for which the default editor group will be created if it does not exist
     */
    public void checkIfEditorGroupsNeedsToBeCreated(Course course) {
        // Courses that have been created before Artemis version 4.11.9 do not have an editor group.
        // The editor group would be need to be set manually by instructors for the course and manually added to external user management.
        // To increase the usability the group is automatically generated when a user is added.
        if (!StringUtils.hasText(course.getEditorGroupName())) {
            course.setEditorGroupName(course.getDefaultEditorGroupName());
            courseRepository.save(course);
        }
    }

    /**
     * Determines end date for the displayed time span of active student charts
     * If the course end date is passed, only information until this date are collected and sent
     *
     * @param course the corresponding course the active students should be collected
     * @return end date of the time span
     */
    public ZonedDateTime determineEndDateForActiveStudents(Course course) {
        var endDate = ZonedDateTime.now();
        if (course.getEndDate() != null && ZonedDateTime.now().isAfter(course.getEndDate())) {
            endDate = course.getEndDate();
        }
        return endDate;
    }

    /**
     * Determines the allowed time span for active student charts
     * The span time can be restricted if the temporal distance between the course start date
     * and the priorly determined end date is smaller than the intended time frame
     *
     * @param course      the corresponding course the time frame should be computed
     * @param endDate     the priorly determined end date of the time span
     * @param maximalSize the normal time span size
     * @return the allowed time span size
     */
    public int determineTimeSpanSizeForActiveStudents(Course course, ZonedDateTime endDate, int maximalSize) {
        var spanTime = maximalSize;
        if (course.getStartDate() != null) {
            long amountOfWeeksBetween = calculateWeeksBetweenDates(course.getStartDate(), endDate);
            spanTime = Math.toIntExact(Math.min(maximalSize, amountOfWeeksBetween));
        }
        return spanTime;
    }

    /**
     * Auxiliary method that returns the number of weeks between two dates
     * Note: The calculation includes the week of the end date. This is needed for the active students line charts
     *
     * @param startDate the start date of the period to calculate
     * @param endDate   the end date of the period to calculate
     * @return the number of weeks the period contains + one week
     */
    public long calculateWeeksBetweenDates(ZonedDateTime startDate, ZonedDateTime endDate) {
        startDate = startDate.withZoneSameInstant(endDate.getZone());
        var mondayInWeekOfStart = startDate.with(DayOfWeek.MONDAY).withHour(0).withMinute(0).withSecond(0).withNano(0);
        var mondayInWeekOfEnd = endDate.plusWeeks(1).with(DayOfWeek.MONDAY).withHour(0).withMinute(0).withSecond(0).withNano(0);
        return mondayInWeekOfStart.until(mondayInWeekOfEnd, ChronoUnit.WEEKS);
    }

    /**
     * Helper method which removes some values from the user entity which are not needed in the client
     *
     * @param usersInGroup user whose variables are removed
     */
    private void removeUserVariables(Iterable<User> usersInGroup) {
        usersInGroup.forEach(user -> {
            user.setLastNotificationRead(null);
            user.setActivationKey(null);
            user.setLangKey(null);
            user.setLastNotificationRead(null);
            user.setLastModifiedBy(null);
            user.setLastModifiedDate(null);
            user.setCreatedBy(null);
            user.setCreatedDate(null);
        });
    }

    /**
     * Checks if learning paths are enabled for the given course. If not, a BadRequestException is thrown.
     * <p>
     * If fetching the course from the database is not necessary, prefer using the method {@link #checkLearningPathsEnabledElseThrow(long)} with the course id as parameter.
     *
     * @param course the course to check
     */
    public void checkLearningPathsEnabledElseThrow(@NotNull Course course) {
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
