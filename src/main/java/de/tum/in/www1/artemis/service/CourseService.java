package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.domain.enumeration.AssessmentType.AUTOMATIC;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.notification.GroupNotification;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.ArtemisAuthenticationProvider;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Service Implementation for managing Course.
 */
@Service
public class CourseService {

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

    public CourseService(CourseRepository courseRepository, ExerciseService exerciseService, AuthorizationCheckService authCheckService,
            ArtemisAuthenticationProvider artemisAuthenticationProvider, UserRepository userRepository, LectureService lectureService, NotificationService notificationService,
            ExerciseGroupService exerciseGroupService) {
        this.courseRepository = courseRepository;
        this.exerciseService = exerciseService;
        this.authCheckService = authCheckService;
        this.artemisAuthenticationProvider = artemisAuthenticationProvider;
        this.userRepository = userRepository;
        this.lectureService = lectureService;
        this.notificationService = notificationService;
        this.exerciseGroupService = exerciseGroupService;
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
     * @param courseId  the course to fetch
     * @param user      the user entity
     * @return          the course including exercises and lectures for the user
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
     * Get all courses with exercises and lectures (filtered for given user)
     *
     * @param user      the user entity
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
        for (Exercise exercise : course.getExercises()) {
            exerciseService.delete(exercise.getId(), true, true);
        }

        for (Lecture lecture : course.getLectures()) {
            lectureService.delete(lecture);
        }

        List<GroupNotification> notifications = notificationService.findAllGroupNotificationsForCourse(course);
        for (GroupNotification notification : notifications) {
            notificationService.deleteGroupNotification(notification);
        }

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

        // delete the Exams
        List<Exam> exams = examService.findAllByCourseId(course.getId());
        for (Exam exam : exams) {
            examService.deleteById(exam.getId());
        }
        courseRepository.deleteById(course.getId());
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

        if (exercise.hasExerciseGroup()) {
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
     * @param exercises all exercises (e.g. of a course or exercise group) that should be filtered
     * @return the filtered and relevant exercises for manual assessment
     */
    public Set<Exercise> getInterestingExercisesForAssessmentDashboards(Set<Exercise> exercises) {
        return exercises.stream().filter(exercise -> exercise instanceof TextExercise || exercise instanceof ModelingExercise || exercise instanceof FileUploadExercise
                || (exercise instanceof ProgrammingExercise && exercise.getAssessmentType() != AUTOMATIC)).collect(Collectors.toSet());
    }
}
