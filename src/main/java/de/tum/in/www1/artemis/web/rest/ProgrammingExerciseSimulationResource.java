package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.net.URI;
import java.net.URISyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseSimulationService;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * Only for local development
 * Simulates the creation of a programming exercise without a connection to the VCS and CI server
 * This functionality is only for testing purposes (noVersionControlAndContinuousIntegrationAvailable)
 */

@Profile("dev")
@RestController
@RequestMapping(ProgrammingExerciseSimulationResource.Endpoints.ROOT)
public class ProgrammingExerciseSimulationResource {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseResource.class);

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private static final String ENTITY_NAME = "programmingExercise";

    private final CourseRepository courseRepository;

    private final ProgrammingExerciseSimulationService programmingExerciseSimulationService;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authCheckService;

    public ProgrammingExerciseSimulationResource(CourseRepository courseRepository, ProgrammingExerciseSimulationService programmingExerciseSimulationService,
            UserRepository userRepository, AuthorizationCheckService authCheckService) {
        this.courseRepository = courseRepository;
        this.programmingExerciseSimulationService = programmingExerciseSimulationService;
        this.userRepository = userRepository;
        this.authCheckService = authCheckService;
    }

    /**
     * POST /programming-exercises/no-vcs-and-ci-available: Setup a new programmingExercise
     * This method creates a new exercise
     * This exercise is only a SIMULATION for the testing of programming exercises without a connection to the VCS and CI server
     * This functionality is only for testing purposes (noVersionControlAndContinuousIntegrationAvailable)
     * @param programmingExercise the input to create/setup new exercise
     * @return a Response Entity
     */
    @PostMapping(ProgrammingExerciseSimulationResource.Endpoints.EXERCISES_SIMULATION)
    @PreAuthorize("hasRole('EDITOR')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<ProgrammingExercise> createProgrammingExerciseWithoutVersionControlAndContinuousIntegrationAvailable(
            @RequestBody ProgrammingExercise programmingExercise) {
        log.debug("REST request to setup ProgrammingExercise : {}", programmingExercise);

        // fetch course from database to make sure client didn't change groups
        Course course = courseRepository.findByIdElseThrow(programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        User user = userRepository.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastEditorInCourse(course, user)) {
            return forbidden();
        }

        programmingExercise.setCourse(course);

        programmingExercise.generateAndSetProjectKey();
        try {
            ProgrammingExercise newProgrammingExercise = programmingExerciseSimulationService
                    .createProgrammingExerciseWithoutVersionControlAndContinuousIntegrationAvailable(programmingExercise);
            // Setup all repositories etc
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

        public static final String EXERCISES_SIMULATION = PROGRAMMING_EXERCISES + "/no-vcs-and-ci-available";

        private Endpoints() {
        }
    }
}
