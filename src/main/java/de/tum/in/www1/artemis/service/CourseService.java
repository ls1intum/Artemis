package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
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

    private final UserRepository userRepository;

    private final LectureService lectureService;

    public CourseService(CourseRepository courseRepository, ExerciseService exerciseService, AuthorizationCheckService authCheckService, UserRepository userRepository,
            LectureService lectureService) {
        this.courseRepository = courseRepository;
        this.exerciseService = exerciseService;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
        this.lectureService = lectureService;
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
    public List<Course> findAllActive() {
        log.debug("Request to get all active courses");
        return courseRepository.findAllActive(ZonedDateTime.now());
    }

    /**
     * Get all the courses.
     *
     * @return the list of entities
     */
    public List<Course> findAllCurrentlyActiveAndNotOnlineAndEnabled() {
        log.debug("Request to get all active courses which are not online and enabled");
        return courseRepository.findAllCurrentlyActiveAndNotOnlineAndEnabled(ZonedDateTime.now());
    }

    /**
     * Get one course with exercises and lectures (filtered for given user)
     *
     * @param courseId  the course to fetch
     * @param user      the user entity
     * @return          the course including exercises and lectures for the user
     */
    public Course findOneWithExercisesAndLecturesForUser(Long courseId, User user) {
        Course course = findOne(courseId);
        fetchExercisesAndLecturesForCourse(user, course);
        return course;
    }

    /**
     * Get all courses with exercises and lectures (filtered for given user)
     *
     * @param user      the user entity
     * @return the list of all courses including exercises and lectures for the user
     */
    public List<Course> findAllActiveWithExercisesAndLecturesForUser(User user) {
        return findAllActive().stream()
                // filter old courses and courses the user should not be able to see
                // skip old courses that have already finished
                .filter(course -> course.getEndDate() == null || course.getEndDate().isAfter(ZonedDateTime.now())).filter(course -> isActiveCourseVisibleForUser(user, course))
                .peek(course -> {
                    fetchExercisesAndLecturesForCourse(user, course);
                }).collect(Collectors.toList());
    }

    /**
     * fetch exercises and lectures for one course
     *
     * @param user to determine which exercises and lectures the user can see
     * @param course the course for which exercises and lectures should be fetched
     */
    private void fetchExercisesAndLecturesForCourse(User user, Course course) {
        // fetch visible lectures exercises for each course after filtering
        Set<Lecture> lectures = lectureService.findAllForCourse(course, user);
        List<Exercise> exercises = exerciseService.findAllForCourse(course, user);
        course.setExercises(new HashSet<>(exercises));
        course.setLectures(lectures);
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
     * Delete the course by id.
     *
     * @param id the id of the entity
     */
    public void delete(Long id) {
        log.debug("Request to delete Course : {}", id);
        courseRepository.deleteById(id);
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
}
