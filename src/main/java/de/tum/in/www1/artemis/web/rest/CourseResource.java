package de.tum.in.www1.artemis.web.rest;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.exception.ArtemisAuthenticationException;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.security.ArtemisAuthenticationProvider;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.scheduled.QuizScheduleService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.config.JHipsterConstants;
import io.github.jhipster.web.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
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

    private final Environment env;
    private final UserService userService;
    private final CourseService courseService;
    private final ParticipationService participationService;
    private final AuthorizationCheckService authCheckService;
    private final ObjectMapper objectMapper;
    private final CourseRepository courseRepository;
    private final ExerciseService exerciseService;
    private final Optional<ArtemisAuthenticationProvider> artemisAuthenticationProvider;

    public CourseResource(Environment env,
                          UserService userService,
                          CourseService courseService,
                          ParticipationService participationService,
                          CourseRepository courseRepository,
                          ExerciseService exerciseService,
                          AuthorizationCheckService authCheckService,
                          MappingJackson2HttpMessageConverter springMvcJacksonConverter,
                          Optional<ArtemisAuthenticationProvider> artemisAuthenticationProvider) {
        this.env = env;
        this.userService = userService;
        this.courseService = courseService;
        this.participationService = participationService;
        this.courseRepository = courseRepository;
        this.exerciseService = exerciseService;
        this.authCheckService = authCheckService;
        this.objectMapper = springMvcJacksonConverter.getObjectMapper();
        this.artemisAuthenticationProvider = artemisAuthenticationProvider;
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
        try {
            Collection<String> activeProfiles = Arrays.asList(env.getActiveProfiles());
            if (activeProfiles.contains(JHipsterConstants.SPRING_PROFILE_PRODUCTION)) {
                //only execute this method in the production environment because normal developers might not have the right to call this method on the authentication server
                checkIfGroupsExists(course);
            }
            Course result = courseService.save(course);
            return ResponseEntity.created(new URI("/api/courses/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getTitle()))
                .body(result);
        }
        catch(ArtemisAuthenticationException ex) {
            //a specified group does not exist, notify the client
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "groupNotFound", ex.getMessage())).body(null);
        }

    }

    /**
     * PUT  /courses : Updates an existing updatedCourse.
     *
     * @param updatedCourse the updatedCourse to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated updatedCourse,
     * or with status 400 (Bad Request) if the updatedCourse is not valid,
     * or with status 500 (Internal Server Error) if the updatedCourse couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/courses")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    @Timed
    @Transactional
    public ResponseEntity<Course> updateCourse(@RequestBody Course updatedCourse) throws URISyntaxException {
        log.debug("REST request to update Course : {}", updatedCourse);
        if (updatedCourse.getId() == null) {
            return createCourse(updatedCourse);
        }
        Course existingCourse = courseRepository.getOne(updatedCourse.getId());
        if (existingCourse == null) {
            return ResponseEntity.notFound().build();
        }
        User user = userService.getUserWithGroupsAndAuthorities();
        //only allow admins or instructors of the existing updatedCourse to change it
        //this is important, otherwise someone could put himself into the instructor group of the updated Course
        if (user.getGroups().contains(existingCourse.getInstructorGroupName()) || authCheckService.isAdmin()) {
            try {
                checkIfGroupsExists(updatedCourse);
                Course result = courseService.save(updatedCourse);
                return ResponseEntity.ok()
                    .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, updatedCourse.getTitle()))
                    .body(result);
            }
            catch(ArtemisAuthenticationException ex) {
                //a specified group does not exist, notify the client
                return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(ex.getMessage(), "groupNotFound")).body(null);
            }
        }
        else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    private void checkIfGroupsExists(Course course) {
        if (course.getInstructorGroupName() != null) {
            if(!artemisAuthenticationProvider.get().checkIfGroupExists(course.getInstructorGroupName())) {
                throw new ArtemisAuthenticationException("Cannot save! The group " + course.getInstructorGroupName() + " for instructors does not exist. Please double check the instructor group name!");
            }
        }
        if (course.getTeachingAssistantGroupName() != null) {
            if(!artemisAuthenticationProvider.get().checkIfGroupExists(course.getTeachingAssistantGroupName())) {
                throw new ArtemisAuthenticationException("Cannot save! The group " + course.getTeachingAssistantGroupName() + " for teaching assistants does not exist. Please double check the teaching assistants group name!");
            }
        }
        if (course.getStudentGroupName() != null) {
            if(!artemisAuthenticationProvider.get().checkIfGroupExists(course.getStudentGroupName())) {
                throw new ArtemisAuthenticationException("Cannot save! The group " + course.getStudentGroupName() + " for students does not exist. Please double check the students group name!");
            }
        }
    }

    /**
     * GET  /courses : get all courses for administration purposes.
     *
     * @return the list of courses (the user has access to)
     */
    @GetMapping("/courses")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public List<Course> getAllCourses() {
        log.debug("REST request to get all Courses the user has access to");
        User user = userService.getUserWithGroupsAndAuthorities();
        List<Course> courses = courseService.findAll();
        Stream<Course> userCourses = courses.stream().filter(
            course ->   user.getGroups().contains(course.getTeachingAssistantGroupName()) ||
                        user.getGroups().contains(course.getInstructorGroupName()) ||
                        authCheckService.isAdmin()
        );
        return userCourses.collect(Collectors.toList());
    }

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
        log.debug("REST request to get all Courses the user has access to with exercises, participations and results");
        User user = userService.getUserWithGroupsAndAuthorities();

        // create json array to hold all the data
        ArrayNode coursesJson = objectMapper.createArrayNode();

        // get all courses with exercises for this user
        List<Course> courses = courseService.findAllWithExercisesForUser(principal, user);

        // get all participations of this user
        List<Participation> participations = participationService.findWithResultsByStudentUsername(principal.getName());

        for (Course course : courses) {
            ObjectNode courseJson = objectMapper.valueToTree(course);
            ArrayNode exercisesJson = objectMapper.createArrayNode();
            for (Exercise exercise : course.getExercises()) {
                // add participation with result to each exercise
                ObjectNode exerciseJson = exerciseToJsonWithParticipation(exercise, participations, principal.getName());
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
        if (!userHasPermission(course)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(course));
    }

    private boolean userHasPermission(Course course) {
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isTeachingAssistantInCourse(course, user) &&
            !authCheckService.isInstructorInCourse(course, user) &&
            !authCheckService.isAdmin()) {
            return false;
        }
        return true;
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
    @Transactional
    public ResponseEntity<Void> deleteCourse(@PathVariable Long id) {
        log.debug("REST request to delete Course : {}", id);
        Course course = courseService.findOne(id);
        if (course == null) {
            return ResponseEntity.notFound().build();
        }
        for(Exercise exercise : course.getExercises()) {
            exerciseService.delete(exercise, false, false);
        }
        String title = course.getTitle();
        courseService.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, title)).build();
    }


    /**
     * GET /courses/:courseId/getAllCourseScoresOfCourseUsers
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
        if (!userHasPermission(course)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(courseService.getAllOverallScoresOfCourse(courseId));
    }

    /**
     * Find the participation in participations that belongs to the given exercise
     * and return a JSON ObjectNode that includes the exercise data, plus the found
     * participation with its most recent relevant result
     *
     * @param exercise       the exercise to create a JSON ObjectNode for
     * @param participations the set of participations, wherein to search for the relevant participation
     * @return the JSON for the given exercise
     */
    private ObjectNode exerciseToJsonWithParticipation(Exercise exercise, List<Participation> participations, String username) {

        ObjectNode exerciseJson = objectMapper.valueToTree(exercise);

        // remove the unnecessary inner course attribute
        exerciseJson.set("course", null);

        // get user's participation for the exercise
        Participation participation = exercise.findRelevantParticipation(participations);

        // for quiz exercises also check SubmissionHashMap for submission by this user (active participation)
        // if participation was not found in database
        if (participation == null && exercise instanceof QuizExercise) {
            QuizSubmission submission = QuizScheduleService.getQuizSubmission(exercise.getId(), username);
            if (submission.getSubmissionDate() != null) {
                participation = new Participation().exercise(exercise).initializationState(InitializationState.INITIALIZED);
            }
        }

        // add results to participation
        if (participation != null) {
            ObjectNode participationJson = objectMapper.valueToTree(participation);

            // only transmit the relevant result
            Result result = exercise.findLatestRelevantResult(participation);
            List<Result> results = Optional.ofNullable(result).map(Arrays::asList).orElse(new ArrayList<>());

            // add results to json
            ArrayNode resultsJson = objectMapper.valueToTree(results);
            if (result != null) {
                // remove participation from inner result json
                ObjectNode resultJson = (ObjectNode) resultsJson.get(0);
                resultJson.set("participation", null);
            }
            participationJson.set("results", resultsJson);

            // remove unnecessary elements for JSON
            participationJson.set("exercise", null);

            // add participation into an array
            ArrayNode participationsArrayJson = objectMapper.createArrayNode();
            participationsArrayJson.add(participationJson);

            // add the participation array to exercise
            exerciseJson.set("participations", participationsArrayJson);
        }

        return exerciseJson;
    }

}
