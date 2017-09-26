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
     *  Get all the courses.
     *
     *  @return the list of entities
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
     *  Get all the courses.
     *
     *  @param pageable the pagination information
     *  @return the list of entities
     */
    @Transactional(readOnly = true)
    public Page<Course> findAll(Pageable pageable) {
        log.debug("Request to get all Courses");
        return courseRepository.findAll(pageable);
    }

    /**
     *  Get one course by id.
     *
     *  @param id the id of the entity
     *  @return the entity
     */
    @Transactional(readOnly = true)
    public Course findOne(Long id) {
        log.debug("Request to get Course : {}", id);
        return courseRepository.findOne(id);
    }

    /**
     *  Delete the  course by id.
     *
     *  @param id the id of the entity
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
     * Getting a Collection of Results in which the average Score of all taken Exercises, is mapped to a userId for the instructor dashboard
     *
     * @param id the courseId
     *
     * @return The resultId refers in this case to the UserID to whom the overallScore belongsTo
     */
    @Transactional(readOnly = true)
    public Collection<Result> getAllSummedOverallScoresOfCourse(Long id){
        Course course = findOne(id);
        Set<Exercise> exercisesOfCourse = course.getExercises();
        HashMap<Long, Result> allOverallScoresOfCourse = new HashMap<>();

        for(Exercise exercise : exercisesOfCourse){
            Set<Participation> participations = exercise.getParticipations();
            for (Participation participation : participations) {

                long studentID = participation.getStudent().getId();
                Result bestResult = bestResultScoreInParticipation(participation);
                //setting student id in Result to refer to the student later in the webapp
                bestResult.setId(studentID);

                //if student already appeared once
                if(allOverallScoresOfCourse.containsKey(studentID)){
                    long oldScore = allOverallScoresOfCourse.get(studentID).getScore();
                    bestResult.setScore(oldScore+bestResult.getScore());
                    allOverallScoresOfCourse.remove(studentID);
                }

                allOverallScoresOfCourse.put(studentID, bestResult);
            }
        }
        return allOverallScoresOfCourse.values();
    }


    /**
     * Find the best Result in a Participation
     *
     * @param participation the participation you want the best result from
     */
    @Transactional(readOnly = true)
    public Result bestResultScoreInParticipation(Participation participation) {
        Set<Result> results = participation.getResults();

        Result bestResult = null;
        for (Result result : results) {
            if (bestResult == null) {
                bestResult = new Result();
                bestResult.setScore(result.getScore());
            } else if (bestResult.getScore() == null || result.getScore() == null) {
                continue;
            } else if (bestResult.getScore() < result.getScore()) {
                bestResult.setScore(result.getScore());
            }
        }

        if (bestResult == null) {
            bestResult = new Result();
            bestResult.setScore((long) 0);
        }
        return bestResult;
    }
}
