package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.domain.*;
import de.tum.in.www1.exerciseapp.repository.CourseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * Service Implementation for managing Course.
 */
@Service
@Transactional
public class CourseService {

    private final Logger log = LoggerFactory.getLogger(CourseService.class);

    private final CourseRepository courseRepository;
    private final UserService userService;

    public CourseService(CourseRepository courseRepository, UserService userService) {
        this.courseRepository = courseRepository;
        this.userService = userService;
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
        List<Course> result = courseRepository.findAll();
        User user = userService.getUserWithGroupsAndAuthorities();
        Authority adminAuthority = new Authority();
        adminAuthority.setName("ROLE_ADMIN");
        Authority taAuthority = new Authority();
        taAuthority.setName("ROLE_TA");
        Stream<Course> userCourses = result.stream().filter(
            c -> user.getGroups().contains(c.getStudentGroupName())
                || user.getGroups().contains(c.getTeachingAssistantGroupName())
                || (user.getAuthorities().contains(taAuthority) && c.getTitle().equals("Archive"))
                || user.getAuthorities().contains(adminAuthority)
        );
        return userCourses.collect(Collectors.toList());
    }

    /**
     * Get all the courses.
     *
     * @param pageable the pagination information
     * @return the list of entities
     */
    @Transactional(readOnly = true)
    public Page<Course> findAll(Pageable pageable) {
        log.debug("Request to get all Courses");
        return courseRepository.findAll(pageable);
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
        return courseRepository.findOne(id);
    }

    /**
     * Delete the  course by id.
     *
     * @param id the id of the entity
     */
    public void delete(Long id) {
        log.debug("Request to delete Course : {}", id);
        courseRepository.delete(id);
    }

    public List<String> getAllTeachingAssistantGroupNames() {
        List<Course> courses = courseRepository.findAll();
        return courses.stream().map(c -> c.getTeachingAssistantGroupName()).collect(Collectors.toList());
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
            for (Participation participation : participations) {

                //id of user in the datbase to reference to the user
                long userID = participation.getStudent().getId();
                Result bestResult = bestResultScoreInParticipation(participation);

                //if student already appeared, once add the new score to the old one
                if (allOverallSummedScoresOfCourse.containsKey(userID)) {
                    long oldScore = allOverallSummedScoresOfCourse.get(userID).getScore();
                    bestResult.setScore(oldScore + bestResult.getScore());
                    allOverallSummedScoresOfCourse.remove(userID);
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
     * @return the best result a student had within the time of the exercise
     */
    @Transactional(readOnly = true)
    public Result bestResultScoreInParticipation(Participation participation) {
        Set<Result> results = participation.getResults();

        Result bestResult = null;
        for (Result result : results) {
            if (bestResult == null) {
                bestResult = new Result();
                bestResult.setScore(result.getScore());
            } else if (bestResult.getScore() != null
                && result.getScore() != null //score can be stored as null in the DB
                && bestResult.getScore() < result.getScore()
                && participation.getExercise().getDueDate().isBefore(result.getCompletionDate())//only use results which are completed before the dueDate
                ) {
                bestResult.setScore(result.getScore());
            }
        }

        //edge case of no result submitted to a participation or in case the db has stored null for score
        if (bestResult == null || bestResult.getScore() == null) {
            bestResult = new Result();
            bestResult.setScore((long) 0);
        }
        //setting participation in result to have student id later
        bestResult.setParticipation(participation);

        return bestResult;
    }
}
