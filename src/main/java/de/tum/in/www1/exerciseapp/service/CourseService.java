package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.domain.Course;
import de.tum.in.www1.exerciseapp.domain.Exercise;
import de.tum.in.www1.exerciseapp.domain.Participation;
import de.tum.in.www1.exerciseapp.domain.Result;
import de.tum.in.www1.exerciseapp.repository.CourseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        return courseRepository.findAll();
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
     * @param hasDueDate if the participation has a duedate take last result before the due date if not take the overall last result
     * @return the best result a student had within the time of the exercise
     */
    @Transactional(readOnly = true)
    public Result choseResultInParticipation(Participation participation, boolean hasDueDate) {
        List<Result> results = new ArrayList<>(participation.getResults());

        Result chosenResult;
        //edge case of no result submitted to a participation
        if(results.size() <= 0) {
            chosenResult = new Result();
            chosenResult.setScore((long) 0);
            chosenResult.setParticipation(participation);
            return chosenResult;
        }

        //sorting in descending order to have the last result at the beginning
        results.sort(Comparator.comparing(Result::getCompletionDate).reversed());

        if(hasDueDate) {
            //find the first result that is before the due date otherwise handles the case where all results were submitted after the due date,
            chosenResult = results.stream()
                .filter(x -> x.getCompletionDate().isBefore(participation.getExercise().getDueDate()))
                .findFirst()
                .orElse(new Result());
        }
        else {
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
