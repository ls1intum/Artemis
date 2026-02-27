package de.tum.cit.aet.artemis.programming.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Optional;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.athena.api.AthenaApi;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.course.CourseService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfigHelper;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCreationUpdateService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseValidationService;
import de.tum.cit.aet.artemis.programming.service.StaticCodeAnalysisService;

/**
 * REST controller for creating programming exercises.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/programming/")
public class ProgrammingExerciseCreationResource {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseCreationResource.class);

    private static final String ENTITY_NAME = "programmingExercise";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final CourseService courseService;

    private final AuthorizationCheckService authCheckService;

    private final ProgrammingExerciseValidationService programmingExerciseValidationService;

    private final ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService;

    private final StaticCodeAnalysisService staticCodeAnalysisService;

    private final Optional<AthenaApi> athenaApi;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final UserRepository userRepository;

    private final ExerciseVersionService exerciseVersionService;

    public ProgrammingExerciseCreationResource(AuthorizationCheckService authCheckService, CourseService courseService,
            ProgrammingExerciseValidationService programmingExerciseValidationService, ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService,
            StaticCodeAnalysisService staticCodeAnalysisService, Optional<AthenaApi> athenaApi, ProgrammingExerciseRepository programmingExerciseRepository,
            UserRepository userRepository, ExerciseVersionService exerciseVersionService) {
        this.programmingExerciseValidationService = programmingExerciseValidationService;
        this.programmingExerciseCreationUpdateService = programmingExerciseCreationUpdateService;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.staticCodeAnalysisService = staticCodeAnalysisService;
        this.athenaApi = athenaApi;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.userRepository = userRepository;
        this.exerciseVersionService = exerciseVersionService;
    }

    /**
     * POST /programming-exercises/setup : Set up a new programmingExercise (with all needed repositories etc.)
     *
     * @param programmingExercise the programmingExercise to set up
     * @return the ResponseEntity with status 201 (Created) and with body the new programmingExercise, or with status 400 (Bad Request) if the parameters are invalid
     */
    @PostMapping("programming-exercises/setup")
    @EnforceAtLeastEditor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<ProgrammingExercise> createProgrammingExercise(@RequestBody ProgrammingExercise programmingExercise) {
        log.debug("REST request to setup ProgrammingExercise : {}", programmingExercise);

        // Valid exercises have set either a course or an exerciseGroup
        programmingExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(programmingExercise);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);
        programmingExerciseValidationService.validateNewProgrammingExerciseSettings(programmingExercise, course);
        // Validate plagiarism detection config
        PlagiarismDetectionConfigHelper.validatePlagiarismDetectionConfigOrThrow(programmingExercise, ENTITY_NAME);

        // Check that only allowed athena modules are used
        athenaApi.ifPresentOrElse(api -> api.checkHasAccessToAthenaModule(programmingExercise, course, ENTITY_NAME), () -> programmingExercise.setFeedbackSuggestionModule(null));

        try {
            // Setup all repositories etc
            ProgrammingExercise newProgrammingExercise = programmingExerciseCreationUpdateService.createProgrammingExercise(programmingExercise);

            // Create default static code analysis categories
            if (Boolean.TRUE.equals(programmingExercise.isStaticCodeAnalysisEnabled())) {
                staticCodeAnalysisService.createDefaultCategories(newProgrammingExercise);
            }

            exerciseVersionService.createExerciseVersion(newProgrammingExercise);

            return ResponseEntity.created(new URI("/api/programming/programming-exercises/" + newProgrammingExercise.getId())).body(newProgrammingExercise);
        }
        catch (IOException | URISyntaxException | GitAPIException | ContinuousIntegrationException e) {
            log.error("Error while setting up programming exercise", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .headers(HeaderUtil.createAlert(applicationName, "An error occurred while setting up the exercise: " + e.getMessage(), "errorProgrammingExercise")).body(null);
        }
    }

    /**
     * PUT /programming-exercises/{exerciseId}/generate-tests : Makes a call to StructureOracleGenerator to generate the structure oracle aka the test.json file
     *
     * @param exerciseId The ID of the programming exercise for which the structure oracle should get generated
     * @return The ResponseEntity with status 201 (Created) or with status 400 (Bad Request) if the parameters are invalid
     */
    @PutMapping(value = "programming-exercises/{exerciseId}/generate-tests", produces = MediaType.TEXT_PLAIN_VALUE)
    @EnforceAtLeastEditor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<String> generateStructureOracleForExercise(@PathVariable long exerciseId) {
        log.debug("REST request to generate the structure oracle for ProgrammingExercise with id: {}", exerciseId);
        var programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndBuildConfigElseThrow(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, programmingExercise, user);
        if (programmingExercise.getPackageName() == null || programmingExercise.getPackageName().length() < 3) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName,
                    "This is a linked exercise and generating the structure oracle for this exercise is not possible.", "couldNotGenerateStructureOracle")).body(null);
        }

        var solutionRepoUri = programmingExercise.getVcsSolutionRepositoryUri();
        var exerciseRepoUri = programmingExercise.getVcsTemplateRepositoryUri();
        var testRepoUri = programmingExercise.getVcsTestRepositoryUri();

        try {
            String testsPath = Path.of("test", programmingExercise.getPackageFolderName()).toString();
            // Atm we only have one folder that can have structural tests, but this could change.
            testsPath = programmingExercise.getBuildConfig().hasSequentialTestRuns() ? Path.of("structural", testsPath).toString() : testsPath;
            boolean didGenerateOracle = programmingExerciseCreationUpdateService.generateStructureOracleFile(solutionRepoUri, exerciseRepoUri, testRepoUri, testsPath, user);

            if (didGenerateOracle) {
                exerciseVersionService.createExerciseVersion(programmingExercise, user);
                HttpHeaders responseHeaders = new HttpHeaders();
                responseHeaders.setContentType(MediaType.TEXT_PLAIN);
                return new ResponseEntity<>("Successfully generated the structure oracle for the exercise " + programmingExercise.getProjectName(), responseHeaders, HttpStatus.OK);
            }
            else {
                return ResponseEntity.badRequest().headers(
                        HeaderUtil.createAlert(applicationName, "Did not update the oracle because there have not been any changes to it.", "didNotGenerateStructureOracle"))
                        .body(null);
            }
        }
        catch (Exception e) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createAlert(applicationName,
                            "An error occurred while generating the structure oracle for the exercise " + programmingExercise.getProjectName() + ": " + e,
                            "errorStructureOracleGeneration"))
                    .body(null);
        }
    }

}
