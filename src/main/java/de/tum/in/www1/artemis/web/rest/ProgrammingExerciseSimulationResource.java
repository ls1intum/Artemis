package de.tum.in.www1.artemis.web.rest;

import java.net.URI;
import java.net.URISyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.service.CourseService;
import de.tum.in.www1.artemis.service.ProgrammingExerciseSimulationService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

@Profile("dev")
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

    private final ProgrammingExerciseSimulationService programmingExerciseSimulationService;

    public ProgrammingExerciseSimulationResource(CourseService courseService, ProgrammingExerciseSimulationService programmingExerciseSimulationService) {
        this.courseService = courseService;
        this.programmingExerciseSimulationService = programmingExerciseSimulationService;
    }

    /**
     * POST /programming-exercises/no-local-setup: Setup a new programmingExercise
     * This method creates a new exercise
     * This exercise is only a SIMULATION for the testing of programming exercises without local setup
     * @param programmingExercise the input to create/setup new exercise
     * @return a Response Entity
     */
    @PostMapping(ProgrammingExerciseSimulationResource.Endpoints.EXERCISES_SIMULATION)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<ProgrammingExercise> setupProgrammingExerciseWithoutLocalSetup(@RequestBody ProgrammingExercise programmingExercise) {
        log.debug("REST request to setup ProgrammingExercise : {}", programmingExercise);

        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(programmingExercise.getCourse().getId());

        // security mechanism: make sure that we use the values from the database and not the once which might have been altered in the client
        programmingExercise.setCourse(course);

        programmingExercise.generateAndSetProjectKey();
        try {
            ProgrammingExercise newProgrammingExercise = programmingExerciseSimulationService.setupProgrammingExerciseWithoutLocalSetup(programmingExercise);
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

        public static final String EXERCISES_SIMULATION = PROGRAMMING_EXERCISES + "/no-local-setup";

    }
}
