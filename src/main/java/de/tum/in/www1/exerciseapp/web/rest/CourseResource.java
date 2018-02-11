package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.exerciseapp.domain.*;
import de.tum.in.www1.exerciseapp.repository.ResultRepository;
import de.tum.in.www1.exerciseapp.service.*;
import de.tum.in.www1.exerciseapp.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.exerciseapp.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * REST controller for managing Course.
 */
@RestController
@RequestMapping({"/api", "/api_basic"})
@PreAuthorize("hasRole('ADMIN')")
public class CourseResource {

    private final Logger log = LoggerFactory.getLogger(CourseResource.class);

    private static final String ENTITY_NAME = "course";

    private final UserService userService;
    private final CourseService courseService;
    private final ExerciseService exerciseService;
    private final ParticipationService participationService;
    private final ResultRepository resultRepository;
    private final AuthorizationCheckService authCheckService;
    private final ObjectMapper objectMapper;

    public CourseResource(UserService userService,
                          CourseService courseService,
                          ExerciseService exerciseService,
                          ParticipationService participationService,
                          ResultRepository resultRepository,
                          AuthorizationCheckService authCheckService,
                          MappingJackson2HttpMessageConverter springMvcJacksonConverter) {
        this.userService = userService;
        this.courseService = courseService;
        this.exerciseService = exerciseService;
        this.participationService = participationService;
        this.resultRepository = resultRepository;
        this.authCheckService = authCheckService;
        this.objectMapper = springMvcJacksonConverter.getObjectMapper();
    }

    /**
     * POST  /courses : Create a new course.
     *
     * @param course the course to create
     * @return the ResponseEntity with status 201 (Created) and with body the new course, or with status 400 (Bad Request) if the course has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/courses")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Timed
    public ResponseEntity<Course> createCourse(@RequestBody Course course) throws URISyntaxException {
        log.debug("REST request to save Course : {}", course);
        if (course.getId() != null) {
            throw new BadRequestAlertException("A new course cannot already have an ID", ENTITY_NAME, "idexists");
        }
        Course result = courseService.save(course);
        return ResponseEntity.created(new URI("/api/courses/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /courses : Updates an existing course.
     *
     * @param course the course to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated course,
     * or with status 400 (Bad Request) if the course is not valid,
     * or with status 500 (Internal Server Error) if the course couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/courses")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Timed
    public ResponseEntity<Course> updateCourse(@RequestBody Course course) throws URISyntaxException {
        log.debug("REST request to update Course : {}", course);
        if (course.getId() == null) {
            return createCourse(course);
        }
        Course result = courseService.save(course);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, course.getId().toString()))
            .body(result);
    }

    /**
     * GET  /courses : get all the courses.
     *
     * @return the list of courses (the user has access to)
     */
    @GetMapping("/courses")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public List<Course> getAllCourses(User user) {
        log.debug("REST request to get all Courses the user has access to");

        List<Course> courses = courseService.findAll();
        Stream<Course> userCourses = courses.stream().filter(
            course -> user.getGroups().contains(course.getStudentGroupName()) ||
                user.getGroups().contains(course.getTeachingAssistantGroupName()) ||
                user.getGroups().contains(course.getInstructorGroupName()) ||
                authCheckService.isAdmin()
        );
        return userCourses.collect(Collectors.toList());
    }

    //TODO: create a second method for the administration of courses, so that in this case, courses are only visible to Admins and TAs of this course

    /**
     * GET /courses/for-dashboard
     *
     * @param principal the current user principal
     * @return the list of courses (the user has access to) including all exercises
     * with participation and result for the user
     */
    @GetMapping("/courses/for-dashboard")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public JsonNode getAllCoursesForDashboard(Principal principal) {
        long start = System.currentTimeMillis();
        log.debug("REST request to get all Courses the user has access to with exercises, participations and results");

        // create json array to hold all the data
        ArrayNode coursesJson = objectMapper.createArrayNode();
        User user = userService.getUserWithGroupsAndAuthorities();
        List<Course> courses = getAllCourses(user);
        log.warn(courses.size() + " courses received after " + (System.currentTimeMillis() - start) + "ms");
        for (Course course : courses) {
            ObjectNode courseJson = objectMapper.valueToTree(course);

            // get all exercises for this user in this course
            //TODO: this call checks again the user rights, we already have the user and could pass the reference
            List<Exercise> exercises = exerciseService.findAllForCourse(course, true, principal, user);
            log.warn("    " + exercises.size() + " exercises received after " + (System.currentTimeMillis() - start) + "ms");
            ArrayNode exercisesJson = objectMapper.createArrayNode();
            for (Exercise exercise : exercises) {
                // add exercise to json array
                ObjectNode exerciseJson = exerciseToJsonWithParticipation(exercise, principal);
                log.warn("        participation and result received after " + (System.currentTimeMillis() - start) + "ms");
                exercisesJson.add(exerciseJson);
            }
            // add exercises to course
            courseJson.set("exercises", exercisesJson);
            coursesJson.add(courseJson);
        }

        // return json array of courses
        return coursesJson;
    }

    /**
     * GET  /courses/:id : get the "id" course.
     *
     * @param id the id of the course to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the course, or with status 404 (Not Found)
     */
    @GetMapping("/courses/{id}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<Course> getCourse(@PathVariable Long id) {
        log.debug("REST request to get Course : {}", id);
        Course course = courseService.findOne(id);
        if (!authCheckService.isTeachingAssistantInCourse(course) &&
            !authCheckService.isInstructorInCourse(course) &&
            !authCheckService.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(course));
    }

    /**
     * DELETE  /courses/:id : delete the "id" course.
     *
     * @param id the id of the course to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/courses/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Timed
    public ResponseEntity<Void> deleteCourse(@PathVariable Long id) {
        log.debug("REST request to delete Course : {}", id);
        courseService.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }


    /**
     * GET /user/:courseId/courseResult
     *
     * @param courseId the Id of the course
     * @return collection of Results where the sum of the best result per exercise, for each student in a course is cointained:
     * ResultId refers in this case to the studentId, the score still needs to be divided by the amount of exercises (done in the webapp)
     */
    @GetMapping("/courses/{courseId}/getAllCourseScoresOfCourseUsers")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<Collection<Result>> getAllSummedScoresOfCourseUsers(@PathVariable("courseId") Long courseId) {
        log.debug("REST request to get courseScores from course : {}", courseId);
        Course course = courseService.findOne(courseId);
        if (!authCheckService.isTeachingAssistantInCourse(course) &&
            !authCheckService.isInstructorInCourse(course) &&
            !authCheckService.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(courseService.getAllOverallScoresOfCourse(courseId));
    }

    private ObjectNode exerciseToJsonWithParticipation(Exercise exercise, Principal principal) {
        // get user's participation for the exercise
        Participation participation;
        if (exercise instanceof QuizExercise) {
            participation = participationService.findOneByExerciseIdAndStudentLoginAnyState(exercise.getId(), principal.getName());
        } else {
            participation = participationService.findOneByExerciseIdAndStudentLogin(exercise.getId(), principal.getName());
        }

        // add results to participation
        ObjectNode participationJson = objectMapper.createObjectNode();
        if (participation != null) {
            List<Result> results;
            // if exercise is quiz => only give out results if quiz is over
            if (participation.getExercise() instanceof QuizExercise) {
                QuizExercise quizExercise = (QuizExercise) participation.getExercise();
                if (quizExercise.shouldFilterForStudents()) {
                    // return empty list
                    results = new ArrayList<>();
                } else {
                    // for quiz exercises only return latest rated result
                    results = resultRepository.findFirstByParticipationIdAndRatedOrderByCompletionDateDesc(participation.getId(), true)
                        .map(Arrays::asList)
                        .orElse(new ArrayList<>());
                }
            } else {
                // for other types of exercises => return latest result
                results = resultRepository.findFirstByParticipationIdOrderByCompletionDateDesc(participation.getId())
                    .map(Arrays::asList)
                    .orElse(new ArrayList<>());
            }

            // remove exercise from participation to prevent sending the exercise twice
            participation.setExercise(null);
            // remove participation from result to prevent sending it twice
            for (Result result : results) {
                result.setParticipation(null);
            }

            // add results to json
            participationJson = objectMapper.valueToTree(participation);
            participationJson.set("results", objectMapper.valueToTree(results));
        }

        // add participation to exercise
        ObjectNode exerciseJson = objectMapper.valueToTree(exercise);
        exerciseJson.set("participation", participationJson);
        return exerciseJson;
    }

}
