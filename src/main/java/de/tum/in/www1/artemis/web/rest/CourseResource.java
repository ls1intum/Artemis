package de.tum.in.www1.artemis.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.TutorParticipationStatus;
import de.tum.in.www1.artemis.exception.ArtemisAuthenticationException;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.security.ArtemisAuthenticationProvider;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.config.JHipsterConstants;
import io.github.jhipster.web.util.ResponseUtil;
import io.micrometer.core.annotation.Timed;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.tum.in.www1.artemis.config.Constants.shortNamePattern;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

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
    private final CourseRepository courseRepository;
    private final ExerciseService exerciseService;
    private final TextSubmissionService submissionService;
    private final Optional<ArtemisAuthenticationProvider> artemisAuthenticationProvider;
    private final TutorParticipationService tutorParticipationService;
    private final ObjectMapper objectMapper;
    private final TextAssessmentService textAssessmentService;


    public CourseResource(Environment env,
                          UserService userService,
                          CourseService courseService,
                          ParticipationService participationService,
                          CourseRepository courseRepository,
                          ExerciseService exerciseService,
                          AuthorizationCheckService authCheckService,
                          TutorParticipationService tutorParticipationService,
                          TextSubmissionService submissionService,
                          MappingJackson2HttpMessageConverter springMvcJacksonConverter,
                          Optional<ArtemisAuthenticationProvider> artemisAuthenticationProvider,
                          TextAssessmentService textAssessmentService) {
        this.env = env;
        this.userService = userService;
        this.courseService = courseService;
        this.participationService = participationService;
        this.courseRepository = courseRepository;
        this.exerciseService = exerciseService;
        this.authCheckService = authCheckService;
        this.tutorParticipationService = tutorParticipationService;
        this.submissionService = submissionService;
        this.artemisAuthenticationProvider = artemisAuthenticationProvider;
        this.objectMapper = springMvcJacksonConverter.getObjectMapper();
        this.textAssessmentService = textAssessmentService;
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
    public ResponseEntity<Course> createCourse(@RequestBody Course course) throws URISyntaxException {
        log.debug("REST request to save Course : {}", course);
        if (course.getId() != null) {
            throw new BadRequestAlertException("A new course cannot already have an ID", ENTITY_NAME, "idexists");
        }
        try {
            // Check if course shortname matches regex
            Matcher shortNameMatcher = shortNamePattern.matcher(course.getShortName());
            if (!shortNameMatcher.matches()) {
                return ResponseEntity.badRequest().headers(HeaderUtil.createAlert("The shortname is invalid", "shortnameInvalid")).body(null);
            }
            checkIfGroupsExists(course);
            Course result = courseService.save(course);
            return ResponseEntity.created(new URI("/api/courses/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getTitle()))
                .body(result);
        } catch (ArtemisAuthenticationException ex) {
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
    @Transactional
    public ResponseEntity<Course> updateCourse(@RequestBody Course updatedCourse) throws URISyntaxException {
        log.debug("REST request to update Course : {}", updatedCourse);
        if (updatedCourse.getId() == null) {
            return createCourse(updatedCourse);
        }
        Optional<Course> existingCourse = courseRepository.findById(updatedCourse.getId());
        if (!existingCourse.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        User user = userService.getUserWithGroupsAndAuthorities();
        //only allow admins or instructors of the existing updatedCourse to change it
        //this is important, otherwise someone could put himself into the instructor group of the updated Course
        if (user.getGroups().contains(existingCourse.get().getInstructorGroupName()) || authCheckService.isAdmin()) {
            try {
                // Check if course shortname matches regex
                Matcher shortNameMatcher = shortNamePattern.matcher(updatedCourse.getShortName());
                if (!shortNameMatcher.matches()) {
                    return ResponseEntity.badRequest().headers(HeaderUtil.createAlert("The shortname is invalid", "shortnameInvalid")).body(null);
                }
                checkIfGroupsExists(updatedCourse);
                Course result = courseService.save(updatedCourse);
                return ResponseEntity.ok()
                    .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, updatedCourse.getTitle()))
                    .body(result);
            } catch (ArtemisAuthenticationException ex) {
                //a specified group does not exist, notify the client
                return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(ex.getMessage(), "groupNotFound")).body(null);
            }
        } else {
            return forbidden();
        }
    }

    private void checkIfGroupsExists(Course course) {
        Collection<String> activeProfiles = Arrays.asList(env.getActiveProfiles());
        if (!activeProfiles.contains(JHipsterConstants.SPRING_PROFILE_PRODUCTION)) {
            return;
        }
        //only execute this method in the production environment because normal developers might not have the right to call this method on the authentication server
        if (course.getInstructorGroupName() != null) {
            if (!artemisAuthenticationProvider.get().checkIfGroupExists(course.getInstructorGroupName())) {
                throw new ArtemisAuthenticationException("Cannot save! The group " + course.getInstructorGroupName() + " for instructors does not exist. Please double check the instructor group name!");
            }
        }
        if (course.getTeachingAssistantGroupName() != null) {
            if (!artemisAuthenticationProvider.get().checkIfGroupExists(course.getTeachingAssistantGroupName())) {
                throw new ArtemisAuthenticationException("Cannot save! The group " + course.getTeachingAssistantGroupName() + " for teaching assistants does not exist. Please double check the teaching assistants group name!");
            }
        }
        if (course.getStudentGroupName() != null) {
            if (!artemisAuthenticationProvider.get().checkIfGroupExists(course.getStudentGroupName())) {
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
    public List<Course> getAllCourses() {
        log.debug("REST request to get all Courses the user has access to");
        User user = userService.getUserWithGroupsAndAuthorities();
        List<Course> courses = courseService.findAll();
        Stream<Course> userCourses = courses.stream().filter(
            course -> user.getGroups().contains(course.getTeachingAssistantGroupName()) ||
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
    public List<Course> getAllCoursesForDashboard(Principal principal) {
        log.debug("REST request to get all Courses the user has access to with exercises, participations and results");
        User user = userService.getUserWithGroupsAndAuthorities();

        // get all courses with exercises for this user
        List<Course> courses = courseService.findAllWithExercisesForUser(principal, user);

        // get all participations of this user
        List<Participation> participations = participationService.findWithResultsByStudentUsername(principal.getName());

        for (Course course : courses) {
            for (Exercise exercise : course.getExercises()) {
                // add participation with result to each exercise
                exercise.filterForCourseDashboard(participations, principal.getName());
            }
        }

        return courses;
    }

    /**
     * GET /courses/:id/for-tutor-dashboard
     *
     * @param courseId the id of the course to retrieve
     * @return data about a course including all exercises, plus some data for the tutor
     * as tutor status for assessment
     */
    @GetMapping("/courses/{courseId}/for-tutor-dashboard")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Course> getCourseForTutorDashboard(Principal principal, @PathVariable Long courseId) {
        log.debug("REST request /courses/{courseId}/for-tutor-dashboard");
        Course course = courseService.findOne(courseId);
        if (!userHasPermission(course)) return forbidden();

        User user = userService.getUserWithGroupsAndAuthorities();
        List<Exercise> exercises = exerciseService.findAllForCourse(course, false, principal, user);
        List<TutorParticipation> tutorParticipations = tutorParticipationService.findAllByCourseAndTutor(course, user);

        for (Exercise exercise : exercises) {
//            TutorParticipation tutorParticipation = tutorParticipationService.findByExerciseAndTutor(exercise, user);
            TutorParticipation tutorParticipation = tutorParticipations.stream()
                .filter(participation -> participation.getAssessedExercise().getId().equals(exercise.getId()))
                .findFirst().orElseGet(() -> {
                    TutorParticipation emptyTutorParticipation = new TutorParticipation();
                    emptyTutorParticipation.setStatus(TutorParticipationStatus.NOT_PARTICIPATED);

                    return emptyTutorParticipation;
                });

            exercise.setTutorParticipations(Collections.singleton(tutorParticipation));
        }

        course.setExercises(new HashSet<>(exercises));

        return ResponseUtil.wrapOrNotFound(Optional.of(course));
    }

    /**
     * GET /courses/:id/stats-for-tutor-dashboard
     * <p>
     * A collection of useful statistics for the tutor course dashboard, including:
     * - number of submissions to the course
     * - number of assessments
     * - number of assessments assessed by the tutor
     * - number of complaints
     *
     * @param courseId the id of the course to retrieve
     * @return data about a course including all exercises, plus some data for the tutor
     * as tutor status for assessment
     */
    @GetMapping("/courses/{courseId}/stats-for-tutor-dashboard")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<JsonNode> getStatsForTutorDashboard(@PathVariable Long courseId) {
        log.debug("REST request /courses/{courseId}/stats-for-tutor-dashboard");

        ObjectNode data = objectMapper.createObjectNode();

        Course course = courseService.findOne(courseId);
        if (!userHasPermission(course)) return forbidden();
        User user = userService.getUserWithGroupsAndAuthorities();


        long numberOfSubmissions = submissionService.countNumberOfSubmissions(courseId);
        data.set("numberOfSubmissions", objectMapper.valueToTree(numberOfSubmissions));

        long numberOfAssessments = textAssessmentService.countNumberOfAssessments(courseId);
        data.set("numberOfAssessments", objectMapper.valueToTree(numberOfAssessments));

        long numberOfTutorAssessments = textAssessmentService.countNumberOfAssessmentsForTutor(courseId, user.getId());
        data.set("numberOfTutorAssessments", objectMapper.valueToTree(numberOfTutorAssessments));

        long numberOfComplaints = 0; // TODO: when implementing the complaints implement this as well
        data.set("numberOfComplaints", objectMapper.valueToTree(numberOfComplaints));

        return ResponseEntity.ok(data);
    }

    /**
     * GET  /courses/:id : get the "id" course.
     *
     * @param id the id of the course to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the course, or with status 404 (Not Found)
     */
    @GetMapping("/courses/{id}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Course> getCourse(@PathVariable Long id) {
        log.debug("REST request to get Course : {}", id);
        Course course = courseService.findOne(id);
        if (!userHasPermission(course)) return forbidden();
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(course));
    }

    /**
     * GET  /courses/:id : get the "id" course.
     *
     * @param id the id of the course to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the course, or with status 404 (Not Found)
     */
    @GetMapping("/courses/{id}/with-exercises")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Course> getCourseWithExercises(@PathVariable Long id) {
        log.debug("REST request to get Course : {}", id);
        Course course = courseService.findOneWithExercises(id);
        if (!userHasPermission(course)) return forbidden();
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(course));
    }

    private boolean userHasPermission(Course course) {
        User user = userService.getUserWithGroupsAndAuthorities();
        return authCheckService.isTeachingAssistantInCourse(course, user) ||
            authCheckService.isInstructorInCourse(course, user) ||
            authCheckService.isAdmin();
    }

    /**
     * DELETE  /courses/:id : delete the "id" course.
     *
     * @param id the id of the course to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/courses/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Transactional
    public ResponseEntity<Void> deleteCourse(@PathVariable Long id) {
        log.debug("REST request to delete Course : {}", id);
        Course course = courseService.findOne(id);
        if (course == null) {
            return ResponseEntity.notFound().build();
        }
        for (Exercise exercise : course.getExercises()) {
            exerciseService.delete(exercise, false, false);
        }
        String title = course.getTitle();
        courseService.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, title)).build();
    }

    /**
     * GET  /courses/:courseId/results : Returns all results of the exercises of a course for the currently logged in user
     *
     * @param courseId the id of the course to get the results from
     * @return the ResponseEntity with status 200 (OK) and with body the exercise, or with status 404 (Not Found)
     */
    @GetMapping(value = "/courses/{courseId}/results")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    @Transactional(readOnly = true)
    public ResponseEntity<Course> getResultsForCurrentStudent(@PathVariable Long courseId) {
        long start = System.currentTimeMillis();
        log.debug("REST request to get Results for Course and current Studen : {}", courseId);

        User student = userService.getUser();
        Course course = courseService.findOne(courseId);

        List<Exercise> exercises = exerciseService.findAllExercisesByCourseId(course, student);

        for (Exercise exercise : exercises) {
            List<Participation> participations = participationService.findByExerciseIdAndStudentIdWithEagerResults(exercise.getId(), student.getId());

            Hibernate.initialize(exercise.getParticipations());

            //Removing not needed properties
            exercise.setParticipations(new HashSet<>());
            exercise.setCourse(null);

            for (Participation participation : participations) {
                //Removing not needed properties
                participation.setStudent(null);

                participation.setResults(participation.getResults());
                exercise.addParticipation(participation);
            }
            course.addExercises(exercise);

        }


        log.debug("getResultsForCurrentStudent took " + (System.currentTimeMillis() - start) + "ms");

        return ResponseEntity.ok().body(course);
    }
}
