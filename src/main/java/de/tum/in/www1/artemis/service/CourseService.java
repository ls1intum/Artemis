package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.domain.enumeration.AssessmentType.AUTOMATIC;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.notification.GroupNotification;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.LearningGoalRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.ArtemisAuthenticationProvider;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Service Implementation for managing Course.
 */
@Service
public class CourseService {

    @Value("${artemis.course-archives-path}")
    private String courseArchivesDirPath;

    private final Logger log = LoggerFactory.getLogger(CourseService.class);

    private final CourseRepository courseRepository;

    private final ExerciseService exerciseService;

    private final AuthorizationCheckService authCheckService;

    private final ArtemisAuthenticationProvider artemisAuthenticationProvider;

    private final UserRepository userRepository;

    private final LectureService lectureService;

    private final NotificationService notificationService;

    private ExamService examService;

    private final ExerciseGroupService exerciseGroupService;

    private final AuditEventRepository auditEventRepository;

    private final UserService userService;

    private final LearningGoalRepository learningGoalRepository;

    private final ProgrammingExerciseExportService programmingExerciseExportService;

    private final ZipFileService zipFileService;

    private final WebsocketMessagingService websocketMessagingService;

    public CourseService(CourseRepository courseRepository, ExerciseService exerciseService, AuthorizationCheckService authCheckService,
            ArtemisAuthenticationProvider artemisAuthenticationProvider, UserRepository userRepository, LectureService lectureService, NotificationService notificationService,
            ExerciseGroupService exerciseGroupService, AuditEventRepository auditEventRepository, UserService userService, LearningGoalRepository learningGoalRepository,
            ProgrammingExerciseExportService programmingExerciseExportService, ZipFileService zipFileService, FileService fileService,
            WebsocketMessagingService websocketMessagingService) {
        this.courseRepository = courseRepository;
        this.exerciseService = exerciseService;
        this.authCheckService = authCheckService;
        this.artemisAuthenticationProvider = artemisAuthenticationProvider;
        this.userRepository = userRepository;
        this.lectureService = lectureService;
        this.notificationService = notificationService;
        this.exerciseGroupService = exerciseGroupService;
        this.auditEventRepository = auditEventRepository;
        this.userService = userService;
        this.learningGoalRepository = learningGoalRepository;
        this.programmingExerciseExportService = programmingExerciseExportService;
        this.zipFileService = zipFileService;
        this.websocketMessagingService = websocketMessagingService;
    }

    @Autowired
    // break the dependency cycle
    public void setExamService(ExamService examService) {
        this.examService = examService;
    }

    /**
     * Save a course.
     *
     * @param course the entity to save
     * @return the persisted entity
     */
    public Course save(Course course) {
        log.debug("Request to save Course : {}", course);
        return courseRepository.save(course);
    }

    /**
     * Get all the courses.
     *
     * @return the list of entities
     */
    public List<Course> findAll() {
        log.debug("Request to get all courses");
        return courseRepository.findAll();
    }

    /**
     * Get all the courses.
     *
     * @return the list of entities
     */
    public List<Course> findAllActiveWithLecturesAndExams() {
        log.debug("Request to get all active courses");
        return courseRepository.findAllActiveWithLecturesAndExams(ZonedDateTime.now());
    }

    /**
     * Get all the courses.
     *
     * @return the list of entities
     */
    public List<Course> findAllCurrentlyActiveNotOnlineAndRegistrationEnabled() {
        log.debug("Request to get all active courses which are not online and enabled");
        return courseRepository.findAllCurrentlyActiveNotOnlineAndRegistrationEnabled(ZonedDateTime.now());
    }

    /**
     * Get one course with exercises and lectures (filtered for given user)
     *
     * @param courseId the course to fetch
     * @param user     the user entity
     * @return the course including exercises and lectures for the user
     */
    public Course findOneWithExercisesAndLecturesForUser(Long courseId, User user) {
        Course course = findOneWithLecturesAndExams(courseId);
        if (!authCheckService.isAtLeastStudentInCourse(course, user)) {
            throw new AccessForbiddenException("You are not allowed to access this resource");
        }
        course.setExercises(exerciseService.findAllForCourse(course, user));
        course.setLectures(lectureService.filterActiveAttachments(course.getLectures(), user));
        if (authCheckService.isOnlyStudentInCourse(course, user)) {
            course.setExams(examService.filterVisibleExams(course.getExams()));
        }
        return course;
    }

    /**
     * Get all courses for the given user
     *
     * @param user the user entity
     * @return the list of all courses for the user
     */
    public List<Course> findAllActiveForUser(User user) {
        return courseRepository.findAllActive(ZonedDateTime.now()).stream().filter(course -> course.getEndDate() == null || course.getEndDate().isAfter(ZonedDateTime.now()))
                .filter(course -> isActiveCourseVisibleForUser(user, course)).collect(Collectors.toList());
    }

    /**
     * Get all courses with exercises and lectures (filtered for given user)
     *
     * @param user the user entity
     * @return the list of all courses including exercises and lectures for the user
     */
    public List<Course> findAllActiveWithExercisesAndLecturesForUser(User user) {
        return findAllActiveWithLecturesAndExams().stream()
                // filter old courses and courses the user should not be able to see
                // skip old courses that have already finished
                .filter(course -> course.getEndDate() == null || course.getEndDate().isAfter(ZonedDateTime.now())).filter(course -> isActiveCourseVisibleForUser(user, course))
                .peek(course -> {
                    course.setExercises(exerciseService.findAllForCourse(course, user));
                    course.setLectures(lectureService.filterActiveAttachments(course.getLectures(), user));
                    if (authCheckService.isOnlyStudentInCourse(course, user)) {
                        course.setExams(examService.filterVisibleExams(course.getExams()));
                    }
                }).collect(Collectors.toList());
    }

    private boolean isActiveCourseVisibleForUser(User user, Course course) {
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
     * Get one course by id.
     *
     * @param courseId the id of the entity
     * @return the entity
     */
    @NotNull
    public Course findOne(Long courseId) {
        log.debug("Request to get Course : {}", courseId);
        return courseRepository.findById(courseId).orElseThrow(() -> new EntityNotFoundException("Course with id: \"" + courseId + "\" does not exist"));
    }

    /**
     * Get one course by id.
     *
     * @param courseId the id of the entity
     * @return the entity
     */
    @NotNull
    public Course findOneWithLecturesAndExams(Long courseId) {
        log.debug("Request to get Course : {}", courseId);
        return courseRepository.findWithEagerLecturesAndExamsById(courseId).orElseThrow(() -> new EntityNotFoundException("Course with id: \"" + courseId + "\" does not exist"));
    }

    /**
     * Get one course by id with all its exercises.
     *
     * @param courseId the id of the entity
     * @return the entity
     */
    public Course findOneWithExercises(long courseId) {
        log.debug("Request to get Course : {}", courseId);
        return courseRepository.findWithEagerExercisesById(courseId);
    }

    public Course findOneWithExercisesAndLectures(long courseId) {
        log.debug("Request to get Course : {}", courseId);
        return courseRepository.findWithEagerExercisesAndLecturesById(courseId);
    }

    public Course findOneWithExercisesAndLecturesAndLectureUnitsAndLearningGoals(long courseId) {
        log.debug("Request to get Course : {}", courseId);
        return courseRepository.findWithEagerExercisesAndLecturesAndLectureUnitsAndLearningGoalsById(courseId);
    }

    /**
     * Deletes all elements associated with the course including:
     * <ul>
     *     <li>The Course</li>
     *     <li>All Exercises including:
     *      Submissions, Participations, Results, Repositories and Buildplans, see {@link ExerciseService#delete}</li>
     *     <li>All Lectures and their Attachments, see {@link LectureService#delete}</li>
     *     <li>All GroupNotifications of the course, see {@link NotificationService#deleteGroupNotification}</li>
     *     <li>All default groups created by Artemis, see {@link ArtemisAuthenticationProvider#deleteGroup}</li>
     *     <li>All Exams, see {@link ExamService#deleteById}</li>
     * </ul>
     *
     * @param course the course to be deleted
     */
    public void delete(Course course) {
        log.debug("Request to delete Course : {}", course.getTitle());

        deleteLearningGoalsOfCourse(course);
        deleteExercisesOfCourse(course);
        deleteLecturesOfCourse(course);
        deleteNotificationsOfCourse(course);
        deleteDefaultGroups(course);
        deleteExamsOfCourse(course);
        courseRepository.deleteById(course.getId());
    }

    private void deleteExamsOfCourse(Course course) {
        // delete the Exams
        List<Exam> exams = examService.findAllByCourseId(course.getId());
        for (Exam exam : exams) {
            examService.delete(exam.getId());
        }
    }

    private void deleteDefaultGroups(Course course) {
        // only delete (default) groups which have been created by Artemis before
        if (course.getStudentGroupName().equals(course.getDefaultStudentGroupName())) {
            artemisAuthenticationProvider.deleteGroup(course.getStudentGroupName());
        }
        if (course.getTeachingAssistantGroupName().equals(course.getDefaultTeachingAssistantGroupName())) {
            artemisAuthenticationProvider.deleteGroup(course.getTeachingAssistantGroupName());
        }
        if (course.getInstructorGroupName().equals(course.getDefaultInstructorGroupName())) {
            artemisAuthenticationProvider.deleteGroup(course.getInstructorGroupName());
        }
    }

    private void deleteNotificationsOfCourse(Course course) {
        List<GroupNotification> notifications = notificationService.findAllGroupNotificationsForCourse(course);
        for (GroupNotification notification : notifications) {
            notificationService.deleteGroupNotification(notification);
        }
    }

    private void deleteLecturesOfCourse(Course course) {
        for (Lecture lecture : course.getLectures()) {
            lectureService.delete(lecture);
        }
    }

    private void deleteExercisesOfCourse(Course course) {
        for (Exercise exercise : course.getExercises()) {
            exerciseService.delete(exercise.getId(), true, true);
        }
    }

    private void deleteLearningGoalsOfCourse(Course course) {
        for (LearningGoal learningGoal : course.getLearningGoals()) {
            learningGoalRepository.deleteById(learningGoal.getId());
        }
    }

    /**
     * Given a Course object, it returns the number of users enrolled in the course
     *
     * @param course - the course object we are interested in
     * @return the number of students for that course
     */
    public long countNumberOfStudentsForCourse(Course course) {
        String groupName = course.getStudentGroupName();
        return userRepository.countByGroupsIsContaining(groupName);
    }

    /**
     * If the exercise is part of an exam, retrieve the course through ExerciseGroup -> Exam -> Course.
     * Otherwise the course is already set and the id can be used to retrieve the course from the database.
     *
     * @param exercise the Exercise for which the course is retrieved
     * @return the Course of the Exercise
     */
    public Course retrieveCourseOverExerciseGroupOrCourseId(Exercise exercise) {

        if (exercise.isExamExercise()) {
            ExerciseGroup exerciseGroup = exerciseGroupService.findOneWithExam(exercise.getExerciseGroup().getId());
            exercise.setExerciseGroup(exerciseGroup);
            return exerciseGroup.getExam().getCourse();
        }
        else {
            Course course = findOne(exercise.getCourseViaExerciseGroupOrCourseMember().getId());
            exercise.setCourse(course);
            return course;
        }
    }

    /**
     * filters the passed exercises for the relevant ones that need to be manually assessed. This excludes quizzes and automatic programming exercises
     *
     * @param exercises all exercises (e.g. of a course or exercise group) that should be filtered
     * @return the filtered and relevant exercises for manual assessment
     */
    public Set<Exercise> getInterestingExercisesForAssessmentDashboards(Set<Exercise> exercises) {
        return exercises.stream().filter(exercise -> exercise instanceof TextExercise || exercise instanceof ModelingExercise || exercise instanceof FileUploadExercise
                || (exercise instanceof ProgrammingExercise && exercise.getAssessmentType() != AUTOMATIC)).collect(Collectors.toSet());
    }

    /**
     * Registers a user in a course by adding him to the student group of the course
     *
     * @param user   The user that should get added to the course
     * @param course The course to which the user should get added to
     */
    public void registerUserForCourse(User user, Course course) {
        userService.addUserToGroup(user, course.getStudentGroupName());

        final var auditEvent = new AuditEvent(user.getLogin(), Constants.REGISTER_FOR_COURSE, "course=" + course.getTitle());
        auditEventRepository.add(auditEvent);
        log.info("User " + user.getLogin() + " has successfully registered for course " + course.getTitle());
    }

    /**
     * Archives the course. The course must be over before it can be archived.
     *
     * @param course The course to archive
     */
    @Async
    public void archiveCourse(Course course) {
        SecurityUtils.setAuthorizationObject();

        // Archiving a course is only possible after the course is over
        if (ZonedDateTime.now().isBefore(course.getEndDate())) {
            return;
        }

        notifyUserAboutCourseArchiveState(course.getId(), CourseArchiveState.RUNNING, "Archiving course...");

        var courseDirName = course.getShortName() + "-" + course.getTitle() + "-" + ZonedDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        var courseDirPath = Path.of("./exports", courseDirName);
        try {
            Files.createDirectories(courseDirPath);
        }
        catch (IOException e) {
            log.info("Cannot archive course {} because the temporary directories cannot be created: {}", course.getId(), e.getMessage());
            return;
        }

        // Archive course exercises and exams
        archiveCourseExercises(course, courseDirPath.toString());
        archiveCourseExams(course, courseDirPath.toString());

        var courseArchivePath = createCourseZipFile(courseDirPath);

        // Attach the path to the archive to the course and save it in the database
        course.setCourseArchivePath(courseArchivePath.toString());
        courseRepository.save(course);

        notifyUserAboutCourseArchiveState(course.getId(), CourseArchiveState.COMPLETED, "Archiving course finished!");
        log.info("Successfully archived course {}. The archive is located at: {}", course.getId(), courseArchivePath);
    }

    /**
     * Archives all exercises of the course and adds them into the directory
     * outputDir/exercises/
     *
     * @param course    The course where the exercises are located
     * @param outputDir The directory that will be used to store the exercises subdirectory
     */
    private void archiveCourseExercises(Course course, String outputDir) {
        Path exercisesDir = Path.of(outputDir, "exercises");
        try {
            Files.createDirectory(exercisesDir);
            archiveExercises(course.getExercises(), exercisesDir.toString());
        }
        catch (IOException e) {
            log.info("Failed to create course exercise directory {}: {}", exercisesDir, e.getMessage());
        }
    }

    /**
     * Archives all exams of the course and adds them into the directory
     * outputDir/exams/
     *
     * @param course    The course where the exercises are located
     * @param outputDir The directory that will be used to store the exams
     */
    private void archiveCourseExams(Course course, String outputDir) {
        Path examsDir = Path.of(outputDir, "exams");
        try {
            Files.createDirectory(examsDir);
            var exams = findOneWithLecturesAndExams(course.getId()).getExams();
            exams.forEach(exam -> archiveExam(exam.getId(), examsDir.toString()));
        }
        catch (IOException e) {
            log.info("Failed to create course exams directory {}: {}", examsDir, e.getMessage());
        }
    }

    /**
     * Archives an exams adds it into the directory outputDir/examId-examTitle
     *
     * @param examId    The id of the exam to archive
     * @param outputDir The directory that will be used to store the exam
     */
    private void archiveExam(long examId, String outputDir) {
        var exam = examService.findOneWithExerciseGroupsAndExercises(examId);
        Path examDir = Path.of(outputDir, exam.getId() + "-" + exam.getTitle());
        try {
            Files.createDirectory(examDir);
            // We retrieve every exercise from each exercise group and flatten the list.
            var exercises = exam.getExerciseGroups().stream().map(ExerciseGroup::getExercises).flatMap(Collection::stream).collect(Collectors.toSet());
            archiveExercises(exercises, examDir.toString());
        }
        catch (IOException e) {
            log.info("Failed to create exam directory {}: {}", examDir, e.getMessage());
        }
    }

    /**
     * Archives the exercises by creating a zip file for each exercise.
     *
     * @param exercises The exercises to archive
     * @param outputDir The path to a directory that will be used to store the zipped files.
     */
    private void archiveExercises(Set<Exercise> exercises, String outputDir) {
        ArrayList<Long> exercisesThatFailedToExport = new ArrayList<>();

        // Used to send websocket message with the progress
        AtomicInteger currentExerciseIndex = new AtomicInteger(1);

        exercises.forEach(exercise -> {
            // We need this call because we need to lazy load the student participations.
            var participations = exerciseService.findOneWithStudentParticipations(exercise.getId()).getStudentParticipations();

            if (exercise instanceof ProgrammingExercise) {
                var programmingParticipations = participations.stream().map(participation -> (ProgrammingExerciseStudentParticipation) participation).collect(Collectors.toList());
                var exportedExercise = programmingExerciseExportService.archiveProgrammingExercise((ProgrammingExercise) exercise, programmingParticipations, outputDir);

                if (exportedExercise == null) {
                    exercisesThatFailedToExport.add(exercise.getId());
                }
            }

            // TODO: Handle archiving for other exercise types
            log.info("Skipping export of exercise: {} because it's not supported yet.", exercise.getTitle());

            // Notify the client about the progress.
            notifyUserAboutCourseArchiveState(exercise.getCourseViaExerciseGroupOrCourseMember().getId(), CourseArchiveState.RUNNING,
                    currentExerciseIndex + "/" + exercises.size() + " done");
            currentExerciseIndex.addAndGet(1);
        });

        // Notify that we couldn't export every exercise
        if (exercisesThatFailedToExport.size() > 0) {
            var failedExerciseIds = exercisesThatFailedToExport.stream().map(String::valueOf).collect(Collectors.joining(","));
            log.info("The following exercises couldn't be exported {}", failedExerciseIds);
        }

    }

    /**
     * Creates a zip file out of all the files and directories inside courseDirPath and saves it to the
     * course archives directory.
     *
     * @param courseDirPath Directory of the contents to zip
     * @return The path to the course zip file
     */
    private Path createCourseZipFile(Path courseDirPath) {
        var courseArchivePath = Path.of(courseArchivesDirPath, courseDirPath.getFileName() + ".zip");
        try {
            // Create course archives directory if it doesn't exist
            Files.createDirectories(Path.of(courseArchivesDirPath));
            log.info("Created the course archives directory at {} because it didn't exist.", courseArchivesDirPath);

            zipFileService.createZipFileWithFolderContent(courseArchivePath, courseDirPath);
            log.info("Successfully created a zip file at: {}", courseArchivePath);
            return courseArchivePath;
        }
        catch (IOException e) {
            log.info("Failed to created a zip file at {}: {}", courseArchivePath, e.getMessage());
            return null;
        }
    }

    /***
     * Sends a message to the archive-course topic notifying the user about the current archiving state
     *
     * @param courseId The id of the course that is being archived
     * @param archiveState The archive state
     * @param progress Some optional message e.g "50%"
     */
    private void notifyUserAboutCourseArchiveState(long courseId, CourseArchiveState archiveState, String progress) {
        var topic = "/topic/courses/" + courseId + "/archive-course";

        Map<String, String> message = new HashMap<>();
        message.put("archiveState", archiveState.toString());
        message.put("progress", progress);

        var mapper = new ObjectMapper();
        try {
            websocketMessagingService.sendMessage(topic, mapper.writeValueAsString(message));
        }
        catch (IOException e) {
            // I guess we can ignore this ?
        }
    }

    /**
     * Cleans up a course by cleaning up all exercises from that course. This deletes all student
     * submissions. Note that a course has to be archived first before being cleaned up.
     *
     * @param courseId The id of the course to clean up
     */
    public void cleanupCourse(Long courseId) {
        // Get the course with all exercises
        var course = findOneWithExercisesAndLectures(courseId);
        course.getExercises().forEach(exercise -> {
            if (exercise instanceof ProgrammingExercise) {
                exerciseService.cleanup(exercise.getId(), true);
            }

            // TODO: extend exerciseService.cleanup to clean up all exercise types
        });

        log.info("The course {} has been cleaned up!", courseId);
    }
}
