package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.CourseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Service Implementation for managing Course.
 */
@Service
@Transactional
public class CourseService {

    private final Logger log = LoggerFactory.getLogger(CourseService.class);

    private final CourseRepository courseRepository;
    private final UserService userService;
    private final ExerciseService exerciseService;
    private final AuthorizationCheckService authCheckService;


    public CourseService(CourseRepository courseRepository,
                         UserService userService,
                         ExerciseService exerciseService,
                         AuthorizationCheckService authCheckService) {
        this.courseRepository = courseRepository;
        this.userService = userService;
        this.exerciseService = exerciseService;
        this.authCheckService = authCheckService;
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
    @Transactional(readOnly = true)
    public List<Course> findAll() {
        log.debug("Request to get all Courses");
        return courseRepository.findAll();
    }

    /**
     * Check if the current user has at least Student-level permissions for the given course
     *
     * @param course the course to check permissions for
     * @return true, if the user has the required permissions, false otherwise
     */
    public boolean userHasAtLeastStudentPermissions(Course course) {
        User user = userService.getUserWithGroupsAndAuthorities();
        return authCheckService.isStudentInCourse(course, user) ||
            authCheckService.isTeachingAssistantInCourse(course, user) ||
            authCheckService.isInstructorInCourse(course, user) ||
            authCheckService.isAdmin();
    }

    /**
     * Check if the current user has at least TA-level permissions for the given course
     *
     * @param course the course to check permissions for
     * @return true, if the user has the required permissions, false otherwise
     */
    public boolean userHasAtLeastTAPermissions(Course course) {
        User user = userService.getUserWithGroupsAndAuthorities();
        return authCheckService.isTeachingAssistantInCourse(course, user) ||
            authCheckService.isInstructorInCourse(course, user) ||
            authCheckService.isAdmin();
    }

    /**
     * Check if the current user has at least Instructor-level permissions for the given course
     *
     * @param course the course to check permissions for
     * @return true, if the user has the required permissions, false otherwise
     */
    public boolean userHasAtLeastInstructorPermissions(Course course) {
        User user = userService.getUserWithGroupsAndAuthorities();
        return authCheckService.isInstructorInCourse(course, user) ||
            authCheckService.isAdmin();
    }

    /**
     * Get all the courses with exercises.
     *
     * @return the list of entities
     */
    @Transactional(readOnly = true)
    public List<Course> findAllWithExercises() {
        log.debug("Request to get all Courses with Exercises");
        return courseRepository.findAllWithEagerExercises();
    }

    /**
     * Get all courses with exercises (filtered for given user)
     *
     * @param principal the user principal
     * @param user      the user entity
     * @return the list of all courses including exercises for the user
     */
    @Transactional(readOnly = true)
    public List<Course> findAllWithExercisesForUser(Principal principal, User user) {
        if (authCheckService.isAdmin()) {
            // admin => fetch all courses with all exercises immediately
            List<Course> allCourses = findAllWithExercises();
            Set<Course> userCourses = new HashSet<>();
            // filter old courses and unnecessary information anyway
            for (Course course : allCourses) {
                if (course.getEndDate() != null && course.getEndDate().isBefore(ZonedDateTime.now())) {
                    //skip old courses that have already finished
                    continue;
                }
                userCourses.add(course);
            }
            return new ArrayList<>(userCourses);
        } else {
            // not admin => fetch visible courses first
            List<Course> allCourses = findAll();
            Set<Course> userCourses = new HashSet<>();
            // filter old courses and courses the user should not be able to see
            for (Course course : allCourses) {
                if (course.getEndDate() != null && course.getEndDate().isBefore(ZonedDateTime.now())) {
                    //skip old courses that have already finished
                    continue;
                }
                //Instructors and TAs see all courses that have not yet finished
                if (user.getGroups().contains(course.getTeachingAssistantGroupName()) ||
                    user.getGroups().contains(course.getInstructorGroupName())) {

                    userCourses.add(course);
                }
                //Students see all courses that have already started (and not yet finished)
                else if (user.getGroups().contains(course.getStudentGroupName())) {
                    if (course.getStartDate() == null || course.getStartDate().isBefore(ZonedDateTime.now())) {
                        userCourses.add(course);
                    }
                }
            }
            for (Course course : userCourses) {
                // fetch visible exercises for each course after filtering
                List<Exercise> exercises = exerciseService.findAllForCourse(course, true, principal, user);
                course.setExercises(new HashSet<>(exercises));
            }
            return new ArrayList<>(userCourses);
        }
    }

    /**
     * Get one course by id.
     *
     * @param id the id of the entity
     * @return the entity
     */
    @Transactional(readOnly = true)
    public Course findOne(Long id) {
        log.debug("Request to get Course : {}", id);
        return courseRepository.findById(id).get();
    }

    /**
     * Delete the  course by id.
     *
     * @param id the id of the entity
     */
    public void delete(Long id) {
        log.debug("Request to delete Course : {}", id);
        courseRepository.deleteById(id);
    }

    public List<String> getAllTeachingAssistantGroupNames() {
        List<Course> courses = courseRepository.findAll();
        return courses.stream().map(Course::getTeachingAssistantGroupName).collect(Collectors.toList());
    }

    public List<String> getAllInstructorGroupNames() {
        List<Course> courses = courseRepository.findAll();
        return courses.stream().map(Course::getInstructorGroupName).collect(Collectors.toList());
    }

    /**
     * Getting a Collection of Results in which the average Score of a course is returned as a result
     *
     * @param courseId the courseId
     * @return the collection of results in the result score the average score is saved, which contains the participation and the user
     */
    @Transactional(readOnly = true)
    public Collection<Result> getAllOverallScoresOfCourse(Long courseId) {
        Course course = findOne(courseId);
        Set<Exercise> exercisesOfCourse = course.getExercises();
        //key stores the userId to identify if he already got a score, value contains the Result itself with the score of the user
        HashMap<Long, Result> allOverallSummedScoresOfCourse = new HashMap<>();

        for (Exercise exercise : exercisesOfCourse) {
            Set<Participation> participations = exercise.getParticipations();
            boolean exerciseHasDueDate = exercise.getDueDate() != null;

            for (Participation participation : participations) {

                //id of user in the database to reference to the user
                long userID = participation.getStudent().getId();
                Result bestResult = choseResultInParticipation(participation, exerciseHasDueDate);

                //TODO: it might happen that there are two participations for one student and one exercise, e.g. a FINISHED one and an INITIALIZED one.
                // Make sure to use only one of them

                //if student already appeared, once add the new score to the old one
                if (allOverallSummedScoresOfCourse.containsKey(userID)) {
                    long currentScore = allOverallSummedScoresOfCourse.get(userID).getScore();
                    bestResult.setScore(currentScore + bestResult.getScore());
                }
                allOverallSummedScoresOfCourse.put(userID, bestResult);
            }
        }

        //divide the scores by the amount of exercises to get the average Score of all Exercises
        Collection<Result> allOverallScores = allOverallSummedScoresOfCourse.values();
        int numberOfExercises = exercisesOfCourse.size();
        for (Result result : allOverallScores) {
            result.setScore(result.getScore() / (long) numberOfExercises);
        }

        return allOverallScores;
    }

    /**
     * Find the best Result in a Participation
     *
     * @param participation the participation you want the best result from
     * @param hasDueDate    if the participation has a duedate take last result before the due date if not take the overall last result
     * @return the best result a student had within the time of the exercise
     */
    @Transactional(readOnly = true)
    public Result choseResultInParticipation(Participation participation, boolean hasDueDate) {
        List<Result> results = new ArrayList<>(participation.getResults());

        //TODO take the field result.isRated into account

        Result chosenResult;
        //edge case of no result submitted to a participation
        if (results.size() <= 0) {
            chosenResult = new Result();
            chosenResult.setScore((long) 0);
            chosenResult.setParticipation(participation);
            return chosenResult;
        }

        //sorting in descending order to have the last result at the beginning
        results.sort(Comparator.comparing(Result::getCompletionDate).reversed());

        if (hasDueDate) {
            //find the first result that is before the due date otherwise handles the case where all results were submitted after the due date,
            chosenResult = results.stream()
                .filter(x -> x.getCompletionDate().isBefore(participation.getExercise().getDueDate()))
                .findFirst()
                .orElse(new Result());
        } else {
            chosenResult = results.remove(0); //no due date use last result
        }

        //edge case where the db has stored null for score
        if (chosenResult.getScore() == null) {
            chosenResult.setScore((long) 0);
        }
        //setting participation in result to have student id later
        chosenResult.setParticipation(participation);

        return chosenResult;
    }
}
