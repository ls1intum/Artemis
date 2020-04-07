package de.tum.in.www1.artemis.web.rest.noLocalSetup;

import static de.tum.in.www1.artemis.config.Constants.SHORT_NAME_PATTERN;
import static de.tum.in.www1.artemis.config.Constants.TITLE_NAME_PATTERN;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.CourseService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.service.noLocalSetup.ProgrammingExerciseSimulationService;
import de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResource;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

@RestController
@RequestMapping(ProgrammingExerciseSimulationResource.Endpoints.ROOT)
/**
 * Only for local development
 * Simulates the creation of a programming exercise without a local setup
 */
public class ProgrammingExerciseSimulationResource {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseResource.class);

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private static final String ENTITY_NAME = "programmingExercise";

    private final CourseService courseService;

    private final UserService userService;

    private final AuthorizationCheckService authCheckService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final String packageNameRegex = "^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+[0-9a-z_]$";

    private final Pattern packageNamePattern = Pattern.compile(packageNameRegex);

    private final ProgrammingExerciseSimulationService programmingExerciseSimulationService;

    public ProgrammingExerciseSimulationResource(CourseService courseService, UserService userService, AuthorizationCheckService authCheckService,
            ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingExerciseSimulationService programmingExerciseSimulationService) {
        this.courseService = courseService;
        this.userService = userService;
        this.authCheckService = authCheckService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseSimulationService = programmingExerciseSimulationService;
    }

    /**
     * POST /programming-exercises/no-local-setup: Setup a new programmingExercise
     * This method creates a new exercise
     * This exercise is only a SIMULATION for the testing of programming exercises without local setup
     * @param programmingExercise the input to create/setup new exercise
     * @return a Response Entity
     */
    @PostMapping(ProgrammingExerciseSimulationResource.Endpoints.NO_LOCAL_SETUP)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<ProgrammingExercise> setupProgrammingExerciseWithoutLocalSetup(@RequestBody ProgrammingExercise programmingExercise) {
        log.debug("REST request to setup ProgrammingExercise : {}", programmingExercise);
        if (programmingExercise.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "A new programmingExercise cannot already have an ID", "idexists")).body(null);
        }

        if (programmingExercise.getCourse() == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "The course is not set", "courseNotSet")).body(null);
        }

        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(programmingExercise.getCourse().getId());
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isInstructorInCourse(course, user) && !authCheckService.isAdmin()) {
            return forbidden();
        }

        // security mechanism: make sure that we use the values from the database and not the once which might have been altered in the client
        programmingExercise.setCourse(course);

        // Check if exercise title is set
        if (programmingExercise.getTitle() == null || programmingExercise.getTitle().length() < 3) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createAlert(applicationName, "The title of the programming exercise is too short", "programmingExerciseTitleInvalid")).body(null);
        }

        // Check if the exercise title matches regex
        Matcher titleMatcher = TITLE_NAME_PATTERN.matcher(programmingExercise.getTitle());
        if (!titleMatcher.matches()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "The title is invalid", "titleInvalid")).body(null);
        }

        // Check if exercise shortname is set
        if (programmingExercise.getShortName() == null || programmingExercise.getShortName().length() < 3) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createAlert(applicationName, "The shortname of the programming exercise is not set or too short", "programmingExerciseShortnameInvalid"))
                    .body(null);
        }

        // Check if course shortname is set
        if (course.getShortName() == null || course.getShortName().length() < 3) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "The shortname of the course is not set or too short", "courseShortnameInvalid"))
                    .body(null);
        }

        // Check if exercise shortname matches regex
        Matcher shortNameMatcher = SHORT_NAME_PATTERN.matcher(programmingExercise.getShortName());
        if (!shortNameMatcher.matches()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "The shortname is invalid", "shortnameInvalid")).body(null);
        }

        List<ProgrammingExercise> programmingExercisesWithSameShortName = programmingExerciseRepository.findAllByShortNameAndCourse(programmingExercise.getShortName(), course);
        if (programmingExercisesWithSameShortName.size() > 0) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName,
                    "A programming exercise with the same short name already exists. Please choose a different short name.", "shortnameAlreadyExists")).body(null);
        }

        // Check if programming language is set
        if (programmingExercise.getProgrammingLanguage() == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "No programming language was specified", "programmingLanguageNotSet")).body(null);
        }

        // Check if package name is set
        if (programmingExercise.getProgrammingLanguage() == ProgrammingLanguage.JAVA) {
            // only Java needs a valid package name at the moment
            if (programmingExercise.getPackageName() == null || programmingExercise.getPackageName().length() < 3) {
                return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "The package name is invalid", "packagenameInvalid")).body(null);
            }

            // Check if package name matches regex
            Matcher packageNameMatcher = packageNamePattern.matcher(programmingExercise.getPackageName());
            if (!packageNameMatcher.matches()) {
                return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "The package name is invalid", "packagenameInvalid")).body(null);
            }
        }

        // Check if max score is set
        if (programmingExercise.getMaxScore() == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "The max score is invalid", "maxscoreInvalid")).body(null);
        }

        programmingExercise.generateAndSetProjectKey();
        try {
            ProgrammingExercise newProgrammingExercise = programmingExerciseSimulationService.setupProgrammingExerciseWithoutLocalSetup(programmingExercise); // Setup all
                                                                                                                                                              // repositories etc
            programmingExerciseSimulationService.setupInitialSubmissionsAndResults(programmingExercise);
            return ResponseEntity.created(new URI("/api/programming-exercises" + newProgrammingExercise.getId()))
                    .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, newProgrammingExercise.getTitle())).body(newProgrammingExercise);
        }
        catch (URISyntaxException e) {
            log.error("Error while setting up programming exercise", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .headers(HeaderUtil.createAlert(applicationName, "An error occurred while setting up the exercise: " + e.getMessage(), "errorProgrammingExercise")).body(null);
        }
    }

    public static final class Endpoints {

        public static final String ROOT = "/api";

        public static final String PROGRAMMING_EXERCISES = "/programming-exercises";

        public static final String NO_LOCAL_SETUP = PROGRAMMING_EXERCISES + "/no-local-setup";

    }
}
