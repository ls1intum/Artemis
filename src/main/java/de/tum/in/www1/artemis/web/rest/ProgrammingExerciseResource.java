package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.config.Constants.SHORT_NAME_PATTERN;
import static de.tum.in.www1.artemis.config.Constants.TITLE_NAME_PATTERN;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.apache.http.HttpException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryExportOptionsDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import de.tum.in.www1.artemis.web.websocket.dto.ProgrammingExerciseTestCaseStateDTO;
import io.github.jhipster.web.util.ResponseUtil;

/** REST controller for managing ProgrammingExercise. */
@RestController
@RequestMapping(ProgrammingExerciseResource.Endpoints.ROOT)
public class ProgrammingExerciseResource {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseResource.class);

    private static final String ENTITY_NAME = "programmingExercise";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final UserService userService;

    private final CourseService courseService;

    private final AuthorizationCheckService authCheckService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final Optional<VersionControlService> versionControlService;

    private final ExerciseService exerciseService;

    private final ProgrammingExerciseService programmingExerciseService;

    private final ProgrammingExerciseImportService programmingExerciseImportService;

    private final ProgrammingExerciseExportService programmingExerciseExportService;

    private final StudentParticipationRepository studentParticipationRepository;

    private final String packageNameRegex = "^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+[0-9a-z_]$";

    private final Pattern packageNamePattern = Pattern.compile(packageNameRegex);

    public ProgrammingExerciseResource(ProgrammingExerciseRepository programmingExerciseRepository, UserService userService, AuthorizationCheckService authCheckService,
            CourseService courseService, Optional<ContinuousIntegrationService> continuousIntegrationService, Optional<VersionControlService> versionControlService,
            ExerciseService exerciseService, ProgrammingExerciseService programmingExerciseService, StudentParticipationRepository studentParticipationRepository,
            ProgrammingExerciseImportService programmingExerciseImportService, ProgrammingExerciseExportService programmingExerciseExportService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.userService = userService;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.versionControlService = versionControlService;
        this.exerciseService = exerciseService;
        this.programmingExerciseService = programmingExerciseService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.programmingExerciseImportService = programmingExerciseImportService;
        this.programmingExerciseExportService = programmingExerciseExportService;
    }

    /**
     * @param exercise the exercise object we want to check for errors
     * @return the error message as response or null if everything is fine
     */
    private ResponseEntity<ProgrammingExercise> checkProgrammingExerciseForError(ProgrammingExercise exercise) {
        if (!continuousIntegrationService.get().buildPlanIdIsValid(exercise.getProjectKey(), exercise.getTemplateBuildPlanId())) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, "exercise", "invalid.template.build.plan.id", "The Template Build Plan ID seems to be invalid."))
                    .body(null);
        }
        if (exercise.getTemplateRepositoryUrlAsUrl() == null || !versionControlService.get().repositoryUrlIsValid(exercise.getTemplateRepositoryUrlAsUrl())) {
            return ResponseEntity.badRequest()
                    .headers(
                            HeaderUtil.createFailureAlert(applicationName, true, "exercise", "invalid.template.repository.url", "The Template Repository URL seems to be invalid."))
                    .body(null);
        }
        if (exercise.getSolutionBuildPlanId() != null && !continuousIntegrationService.get().buildPlanIdIsValid(exercise.getProjectKey(), exercise.getSolutionBuildPlanId())) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, "exercise", "invalid.solution.build.plan.id", "The Solution Build Plan ID seems to be invalid."))
                    .body(null);
        }
        if (exercise.getSolutionRepositoryUrl() != null && !versionControlService.get().repositoryUrlIsValid(exercise.getSolutionRepositoryUrlAsUrl())) {
            return ResponseEntity.badRequest()
                    .headers(
                            HeaderUtil.createFailureAlert(applicationName, true, "exercise", "invalid.solution.repository.url", "The Solution Repository URL seems to be invalid."))
                    .body(null);
        }
        return null;
    }

    /**
     * POST /programming-exercises/setup : Setup a new programmingExercise (with all needed repositories etc.)
     *
     * @param programmingExercise the programmingExercise to setup
     * @return the ResponseEntity with status 201 (Created) and with body the new programmingExercise, or with status 400 (Bad Request) if the parameters are invalid
     */
    @PostMapping(Endpoints.SETUP)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<ProgrammingExercise> setupProgrammingExercise(@RequestBody ProgrammingExercise programmingExercise) {
        log.debug("REST request to setup ProgrammingExercise : {}", programmingExercise);
        if (programmingExercise == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "The programming exercise is not set", "programmingExerciseNotSet")).body(null);
        }

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

        // CHeck if the exercise title matches regex
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

        // Check if programming language is set
        if (programmingExercise.getProgrammingLanguage() == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "No programming language was specified", "programmingLanguageNotSet")).body(null);
        }

        // Check if package name is set
        if (programmingExercise.getProgrammingLanguage() == ProgrammingLanguage.JAVA) {
            // only Java needs a valid package name at the moment
            if (programmingExercise.getPackageName() == null || programmingExercise.getPackageName().length() < 3) {
                return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "The packagename is invalid", "packagenameInvalid")).body(null);
            }

            // Check if package name matches regex
            Matcher packageNameMatcher = packageNamePattern.matcher(programmingExercise.getPackageName());
            if (!packageNameMatcher.matches()) {
                return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "The packagename is invalid", "packagenameInvalid")).body(null);
            }
        }

        // Check if max score is set
        if (programmingExercise.getMaxScore() == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "The max score is invalid", "maxscoreInvalid")).body(null);
        }

        programmingExercise.generateAndSetProjectKey();
        String projectKey = programmingExercise.getProjectKey();
        String projectName = programmingExercise.getProjectName();
        boolean projectExists = versionControlService.get().checkIfProjectExists(projectKey, projectName);
        if (projectExists) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "Project does not exist in VCS: " + projectKey, "vcsProjectExists")).body(null);
        }

        String errorMessageCI = continuousIntegrationService.get().checkIfProjectExists(projectKey, projectName);
        if (errorMessageCI != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, errorMessageCI, "ciProjectExists")).body(null);
        }

        try {
            ProgrammingExercise newProgrammingExercise = programmingExerciseService.setupProgrammingExercise(programmingExercise); // Setup all repositories etc
            return ResponseEntity.created(new URI("/api/programming-exercises" + newProgrammingExercise.getId()))
                    .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, newProgrammingExercise.getTitle())).body(newProgrammingExercise);
        }
        catch (Exception e) {
            log.error("Error while setting up programming exercise", e);
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createAlert(applicationName, "An error occurred while setting up the exercise: " + e.getMessage(), "errorProgrammingExercise")).body(null);
        }
    }

    /**
     * POST /programming-exercises/import: Imports an existing programming exercise into an existing course
     *
     * This will import the whole exercise, including all base build plans (template, solution) and repositories
     * (template, solution, test). Referenced entities, s.a. the test cases or the hints will get cloned and assigned
     * a new id. For a concrete list of what gets copied and what not have a look
     * at {@link ProgrammingExerciseImportService#importProgrammingExerciseBasis(ProgrammingExercise, ProgrammingExercise)}
     *
     * @see ProgrammingExerciseImportService#importProgrammingExerciseBasis(ProgrammingExercise, ProgrammingExercise)
     * @see ProgrammingExerciseImportService#importBuildPlans(ProgrammingExercise, ProgrammingExercise)
     * @see ProgrammingExerciseImportService#importRepositories(ProgrammingExercise, ProgrammingExercise)
     * @param sourceExerciseId The ID of the original exercise which should get imported
     * @param newExercise The new exercise containing values that should get overwritten in the imported exercise, s.a. the title or difficulty
     * @return The imported exercise (200), a not found error (404) if the template does not exist, or a forbidden error
     *         (403) if the user is not at least an instructor in the target course.
     */
    @PostMapping(Endpoints.IMPORT)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<ProgrammingExercise> importExercise(@PathVariable long sourceExerciseId, @RequestBody ProgrammingExercise newExercise) {
        log.debug("REST request to import programming exercise {} into course {}", sourceExerciseId, newExercise.getCourse().getId());

        if (sourceExerciseId < 0 || newExercise.getCourse() == null) {
            return notFound();
        }

        final var user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastInstructorInCourse(newExercise.getCourse(), user)) {
            log.debug("User {} is not allowed to import exercises for course {}", user.getId(), newExercise.getCourse().getId());
            return forbidden();
        }

        final var optionalOriginalProgrammingExercise = programmingExerciseRepository.findByIdWithEagerTestCasesHintsAndTemplateAndSolutionParticipations(sourceExerciseId);
        if (optionalOriginalProgrammingExercise.isEmpty()) {
            return notFound();
        }

        final var originalProgrammingExercise = optionalOriginalProgrammingExercise.get();
        final var importedProgrammingExercise = programmingExerciseImportService.importProgrammingExerciseBasis(originalProgrammingExercise, newExercise);
        HttpHeaders responseHeaders;
        programmingExerciseImportService.importRepositories(originalProgrammingExercise, importedProgrammingExercise);
        try {
            // TODO: We have removed the automatic build trigger from test to base for new programming exercises. We need to also remove this build trigger manually on the case of
            // an import as the source exercise might still have this trigger.
            programmingExerciseImportService.importBuildPlans(originalProgrammingExercise, importedProgrammingExercise);
            responseHeaders = HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, importedProgrammingExercise.getTitle());
        }
        catch (HttpException e) {
            responseHeaders = HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "importExerciseTriggerPlanFail", "Unable to trigger imported build plans");
        }

        // Remove unnecessary fields
        importedProgrammingExercise.setTestCases(null);
        importedProgrammingExercise.setTemplateParticipation(null);
        importedProgrammingExercise.setSolutionParticipation(null);
        importedProgrammingExercise.setExerciseHints(null);

        return ResponseEntity.ok().headers(responseHeaders).body(importedProgrammingExercise);
    }

    /**
     * PUT /programming-exercises : Updates an existing programmingExercise.
     *
     * @param programmingExercise the programmingExercise to update
     * @param notificationText to notify the student group about the update on the programming exercise
     * @return the ResponseEntity with status 200 (OK) and with body the updated programmingExercise, or with status 400 (Bad Request) if the programmingExercise is not valid, or
     *      *         with status 500 (Internal Server Error) if the programmingExercise couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping(Endpoints.PROGRAMMING_EXERCISES)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<ProgrammingExercise> updateProgrammingExercise(@RequestBody ProgrammingExercise programmingExercise,
            @RequestParam(value = "notificationText", required = false) String notificationText) throws URISyntaxException {
        log.debug("REST request to update ProgrammingExercise : {}", programmingExercise);
        if (programmingExercise.getId() == null) {
            return badRequest();
        }
        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(programmingExercise.getCourse().getId());
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isInstructorInCourse(course, user) && !authCheckService.isAdmin()) {
            return forbidden();
        }

        ResponseEntity<ProgrammingExercise> errorResponse = checkProgrammingExerciseForError(programmingExercise);
        if (errorResponse != null) {
            return errorResponse;
        }

        // Only save after checking for errors
        ProgrammingExercise savedProgrammingExercise = programmingExerciseService.updateProgrammingExercise(programmingExercise, notificationText);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, programmingExercise.getTitle())).body(savedProgrammingExercise);
    }

    /**
     * PATCH /programming-exercises-problem: Updates the problem statement of the exercise.
     *
     * @param exerciseId The ID of the exercise for which to change the problem statement
     * @param updatedProblemStatement The new problemStatement
     * @param notificationText to notify the student group about the updated problemStatement on the programming exercise
     * @return the ResponseEntity with status 200 (OK) and with body the updated problemStatement, with status 404 if the programmingExercise could not be found, or with 403 if the user does not have permissions to access the programming exercise.
     */
    @PatchMapping(value = Endpoints.PROBLEM)
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ProgrammingExercise> updateProblemStatement(@PathVariable long exerciseId, @RequestBody String updatedProblemStatement,
            @RequestParam(value = "notificationText", required = false) String notificationText) {
        log.debug("REST request to update ProgrammingExercise with new problem statement: {}", updatedProblemStatement);
        ProgrammingExercise updatedProgrammingExercise;
        try {
            updatedProgrammingExercise = programmingExerciseService.updateProblemStatement(exerciseId, updatedProblemStatement, notificationText);
        }
        catch (IllegalAccessException ex) {
            return forbidden();
        }
        catch (EntityNotFoundException ex) {
            return notFound();
        }
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, updatedProgrammingExercise.getTitle()))
                .body(updatedProgrammingExercise);
    }

    /**
     * GET /courses/:courseId/exercises : get all the programming exercises.
     *
     * @param courseId of the course for which the exercise should be fetched
     * @return the ResponseEntity with status 200 (OK) and the list of programmingExercises in body
     */
    @GetMapping(Endpoints.GET_FOR_COURSE)
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<ProgrammingExercise>> getProgrammingExercisesForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all ProgrammingExercises for the course with id : {}", courseId);
        Course course = courseService.findOne(courseId);
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
            return forbidden();
        }
        List<ProgrammingExercise> exercises = programmingExerciseRepository.findByCourseIdWithLatestResultForTemplateSolutionParticipations(courseId);
        for (ProgrammingExercise exercise : exercises) {
            // not required in the returned json body
            exercise.setStudentParticipations(null);
            exercise.setCourse(null);
        }
        return ResponseEntity.ok().body(exercises);
    }

    /**
     * GET /programming-exercises/:exerciseId : get the "exerciseId" programmingExercise.
     *
     * @param exerciseId the id of the programmingExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the programmingExercise, or with status 404 (Not Found)
     */
    @GetMapping(Endpoints.PROGRAMMING_EXERCISE)
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ProgrammingExercise> getProgrammingExercise(@PathVariable long exerciseId) {
        log.debug("REST request to get ProgrammingExercise : {}", exerciseId);
        Optional<ProgrammingExercise> programmingExercise = programmingExerciseRepository.findWithTemplateParticipationAndSolutionParticipationById(exerciseId);
        if (programmingExercise.isPresent()) {
            Course course = programmingExercise.get().getCourse();
            User user = userService.getUserWithGroupsAndAuthorities();
            if (!authCheckService.isTeachingAssistantInCourse(course, user) && !authCheckService.isInstructorInCourse(course, user) && !authCheckService.isAdmin()) {
                return forbidden();
            }
        }
        return ResponseUtil.wrapOrNotFound(programmingExercise);
    }

    /**
     * GET /programming-exercises/:exerciseId/with-participations/ : get the "exerciseId" programmingExercise.
     *
     * @param exerciseId the id of the programmingExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the programmingExercise, or with status 404 (Not Found)
     */
    @GetMapping(Endpoints.PROGRAMMING_EXERCISE_WITH_PARTICIPATIONS)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ProgrammingExercise> getProgrammingExerciseWithSetupParticipations(@PathVariable long exerciseId) {
        log.debug("REST request to get ProgrammingExercise : {}", exerciseId);

        User user = userService.getUserWithGroupsAndAuthorities();
        Optional<ProgrammingExercise> programmingExerciseOpt = programmingExerciseRepository.findWithTemplateAndSolutionParticipationById(exerciseId);
        if (programmingExerciseOpt.isPresent()) {
            ProgrammingExercise programmingExercise = programmingExerciseOpt.get();
            Course course = programmingExercise.getCourse();
            if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
                return forbidden();
            }
            Optional<StudentParticipation> assignmentParticipation = studentParticipationRepository.findByExerciseIdAndStudentIdWithLatestResult(programmingExercise.getId(),
                    user.getId());
            Set<StudentParticipation> participations = new HashSet<>();
            assignmentParticipation.ifPresent(participations::add);
            programmingExercise.setStudentParticipations(participations);
            return ResponseEntity.ok(programmingExercise);
        }
        else {
            return notFound();
        }
    }

    /**
     * DELETE /programming-exercises/:id : delete the "id" programmingExercise.
     *
     * @param exerciseId the id of the programmingExercise to delete
     * @param deleteStudentReposBuildPlans boolean which states whether the corresponding build plan should be deleted as well
     * @param deleteBaseReposBuildPlans the ResponseEntity with status 200 (OK)
     * @return the ResponseEntity with status 200 (OK) when programming exercise has been successfully deleted or with status 404 (Not Found)
     */
    @DeleteMapping(Endpoints.PROGRAMMING_EXERCISE)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Void> deleteProgrammingExercise(@PathVariable long exerciseId, @RequestParam(defaultValue = "false") boolean deleteStudentReposBuildPlans,
            @RequestParam(defaultValue = "false") boolean deleteBaseReposBuildPlans) {
        log.info("REST request to delete ProgrammingExercise : {}", exerciseId);
        Optional<ProgrammingExercise> programmingExercise = programmingExerciseRepository.findWithTemplateParticipationAndSolutionParticipationById(exerciseId);
        if (programmingExercise.isEmpty()) {
            return notFound();
        }
        Course course = programmingExercise.get().getCourse();
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }
        exerciseService.logDeletion(programmingExercise.get(), course, user);
        exerciseService.delete(exerciseId, deleteStudentReposBuildPlans, deleteBaseReposBuildPlans);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, programmingExercise.get().getTitle())).build();
    }

    /**
     * Combine all commits into one in the template repository of a given exercise.
     *
     * @param exerciseId of the exercise
     * @return the ResponseEntity with status
     *              200 (OK) if combine has been successfully executed
     *              403 (Forbidden) if the user is not admin and course instructor or
     *              500 (Internal Server Error)
     */
    @PutMapping(value = Endpoints.COMBINE_COMMITS, produces = MediaType.TEXT_PLAIN_VALUE)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Void> combineTemplateRepositoryCommits(@PathVariable long exerciseId) {
        log.debug("REST request to generate the structure oracle for ProgrammingExercise with id: {}", exerciseId);

        Optional<ProgrammingExercise> programmingExerciseOptional = programmingExerciseRepository.findWithTemplateParticipationAndSolutionParticipationById(exerciseId);
        if (programmingExerciseOptional.isEmpty()) {
            return notFound();
        }
        ProgrammingExercise programmingExercise = programmingExerciseOptional.get();

        Course course = courseService.findOne(programmingExercise.getCourse().getId());
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isInstructorInCourse(course, user) && !authCheckService.isAdmin()) {
            return forbidden();
        }

        try {
            URL exerciseRepoURL = programmingExercise.getTemplateRepositoryUrlAsUrl();
            programmingExerciseService.combineAllCommitsOfRepositoryIntoOne(exerciseRepoURL);
            return new ResponseEntity<>(HttpStatus.OK);
        }
        catch (IllegalStateException | InterruptedException | GitAPIException ex) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * POST /programming-exercises/:exerciseId/export-repos-by-student-logins/:studentIds : sends all submissions from studentlist as zip
     *
     * @param exerciseId the id of the exercise to get the repos from
     * @param studentIds the studentIds seperated via semicolon to get their submissions
     * @param repositoryExportOptions the options that should be used for the export
     * @return ResponseEntity with status
     * @throws IOException if submissions can't be zippedRequestBody
     */
    @PostMapping(Endpoints.EXPORT_SUBMISSIONS_BY_STUDENT)
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Resource> exportSubmissionsByStudentLogins(@PathVariable long exerciseId, @PathVariable String studentIds,
            @RequestBody RepositoryExportOptionsDTO repositoryExportOptions) throws IOException {
        ProgrammingExercise programmingExercise = programmingExerciseService.findByIdWithEagerStudentParticipationsAndSubmissions(exerciseId);
        User user = userService.getUserWithGroupsAndAuthorities();

        if (Optional.ofNullable(programmingExercise).isEmpty()) {
            log.debug("Exercise with id {} is not an instance of ProgrammingExercise. Ignoring the request to export repositories", exerciseId);
            return badRequest();
        }

        if (!authCheckService.isAtLeastTeachingAssistantForExercise(programmingExercise, user)) {
            return forbidden();
        }

        if (repositoryExportOptions.isExportAllStudents()) {
            // only instructors are allowed to download all repos
            if (!authCheckService.isAtLeastInstructorForExercise(programmingExercise, user)) {
                return forbidden();
            }
        }

        if (repositoryExportOptions.getFilterLateSubmissionsDate() == null) {
            repositoryExportOptions.setFilterLateSubmissionsDate(programmingExercise.getDueDate());
        }

        List<String> studentList = new ArrayList<>();
        if (!repositoryExportOptions.isExportAllStudents()) {
            studentIds = studentIds.replaceAll(" ", "");
            studentList = Arrays.asList(studentIds.split("\\s*,\\s*"));
        }

        // Select the participations that should be exported
        List<ProgrammingExerciseStudentParticipation> exportedStudentParticipations = new ArrayList<>();
        for (StudentParticipation studentParticipation : programmingExercise.getStudentParticipations()) {
            ProgrammingExerciseStudentParticipation programmingStudentParticipation = (ProgrammingExerciseStudentParticipation) studentParticipation;
            if (repositoryExportOptions.isExportAllStudents() || (programmingStudentParticipation.getRepositoryUrl() != null && studentParticipation.getStudent() != null
                    && studentList.contains(studentParticipation.getStudent().getLogin()))) {
                exportedStudentParticipations.add(programmingStudentParticipation);
            }
        }
        return provideZipForParticipations(exportedStudentParticipations, exerciseId, repositoryExportOptions);
    }

    /**
     * POST /programming-exercises/:exerciseId/export-repos-by-participation-ids/:participationIds : sends all submissions from participation ids as zip
     *
     * @param exerciseId the id of the exercise to get the repos from
     * @param participationIds the participationIds seperated via semicolon to get their submissions (used for double blind assessment)
     * @param repositoryExportOptions the options that should be used for the export. Export all students is not supported here!
     * @return ResponseEntity with status
     * @throws IOException if submissions can't be zippedRequestBody
     */
    @PostMapping(Endpoints.EXPORT_SUBMISSIONS_BY_PARTICIPATIONS)
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Resource> exportSubmissionsByParticipationIds(@PathVariable long exerciseId, @PathVariable String participationIds,
            @RequestBody RepositoryExportOptionsDTO repositoryExportOptions) throws IOException {
        ProgrammingExercise programmingExercise = programmingExerciseService.findByIdWithEagerStudentParticipationsAndSubmissions(exerciseId);

        if (!authCheckService.isAtLeastTeachingAssistantForExercise(programmingExercise)) {
            return forbidden();
        }

        if (repositoryExportOptions.getFilterLateSubmissionsDate() == null) {
            repositoryExportOptions.setFilterLateSubmissionsDate(programmingExercise.getDueDate());
        }

        Set<Long> participationIdSet = new ArrayList<String>(Arrays.asList(participationIds.split(","))).stream().map(String::trim).map(Long::parseLong)
                .collect(Collectors.toSet());

        // Select the participations that should be exported
        List<ProgrammingExerciseStudentParticipation> exportedStudentParticipations = programmingExercise.getStudentParticipations().stream()
                .filter(participation -> participationIdSet.contains(participation.getId())).map(participation -> (ProgrammingExerciseStudentParticipation) participation)
                .collect(Collectors.toList());
        return provideZipForParticipations(exportedStudentParticipations, exerciseId, repositoryExportOptions);
    }

    // TODO: Should not throw the IOException but handle it!
    private ResponseEntity<Resource> provideZipForParticipations(@NotNull List<ProgrammingExerciseStudentParticipation> exportedStudentParticipations, Long exerciseId,
            RepositoryExportOptionsDTO repositoryExportOptions) throws IOException {
        // TODO: in case we do not find participations for the given ids, we should inform the user in the client, that the student did not participate in the exercise.
        if (exportedStudentParticipations.isEmpty()) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, false, ENTITY_NAME, "noparticipations", "No existing user was specified or no submission exists."))
                    .body(null);
        }

        File zipFile = programmingExerciseExportService.exportStudentRepositories(exerciseId, exportedStudentParticipations, repositoryExportOptions);
        if (zipFile == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "internalServerError",
                    "There was an error on the server and the zip file could not be created.")).body(null);
        }

        InputStreamResource resource = new InputStreamResource(new FileInputStream(zipFile));
        return ResponseEntity.ok().contentLength(zipFile.length()).contentType(MediaType.APPLICATION_OCTET_STREAM).header("filename", zipFile.getName()).body(resource);
    }

    /**
     * PUT /programming-exercises/{exerciseId}/generate-tests : Makes a call to StructureOracleGenerator to generate the structure oracle aka the test.json file
     *
     * @param exerciseId The ID of the programming exercise for which the structure oracle should get generated
     * @return The ResponseEntity with status 201 (Created) or with status 400 (Bad Request) if the parameters are invalid
     */
    @GetMapping(value = Endpoints.GENERATE_TESTS, produces = MediaType.TEXT_PLAIN_VALUE)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<String> generateStructureOracleForExercise(@PathVariable long exerciseId) {
        log.debug("REST request to generate the structure oracle for ProgrammingExercise with id: {}", exerciseId);

        Optional<ProgrammingExercise> programmingExerciseOptional = programmingExerciseRepository.findWithTemplateParticipationAndSolutionParticipationById(exerciseId);
        if (programmingExerciseOptional.isEmpty()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "programmingExerciseNotFound", "The programming exercise does not exist"))
                    .body(null);
        }

        ProgrammingExercise programmingExercise = programmingExerciseOptional.get();
        Course course = courseService.findOne(programmingExercise.getCourse().getId());
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isInstructorInCourse(course, user) && !authCheckService.isAdmin()) {
            return forbidden();
        }
        if (programmingExercise.getPackageName() == null || programmingExercise.getPackageName().length() < 3) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName,
                    "This is a linked exercise and generating the structure oracle for this exercise is not possible.", "couldNotGenerateStructureOracle")).body(null);
        }

        URL solutionRepoURL = programmingExercise.getSolutionRepositoryUrlAsUrl();
        URL exerciseRepoURL = programmingExercise.getTemplateRepositoryUrlAsUrl();
        URL testRepoURL = programmingExercise.getTestRepositoryUrlAsUrl();

        try {
            String testsPath = "test" + File.separator + programmingExercise.getPackageFolderName();
            // Atm we only have one folder that can have structural tests, but this could change.
            testsPath = programmingExercise.hasSequentialTestRuns() ? "structural" + File.separator + testsPath : testsPath;
            boolean didGenerateOracle = programmingExerciseService.generateStructureOracleFile(solutionRepoURL, exerciseRepoURL, testRepoURL, testsPath, user);

            if (didGenerateOracle) {
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
                            "An error occurred while generating the structure oracle for the exercise " + programmingExercise.getProjectName() + ": \n" + e.getMessage(),
                            "errorStructureOracleGeneration"))
                    .body(null);
        }
    }

    /**
     * GET /programming-exercises/:exerciseId/test-case-state : Returns a DTO that offers information on the test case state of the programming exercise.
     *
     * @param exerciseId the id of a ProgrammingExercise
     * @return the ResponseEntity with status 200 (OK) and ProgrammingExerciseTestCaseStateDTO. Returns 404 (notFound) if the exercise does not exist.
     */
    @GetMapping(Endpoints.TEST_CASE_STATE)
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ProgrammingExerciseTestCaseStateDTO> hasAtLeastOneStudentResult(@PathVariable long exerciseId) {
        Optional<ProgrammingExercise> programmingExercise = programmingExerciseRepository.findWithTemplateParticipationAndSolutionParticipationById(exerciseId);
        if (programmingExercise.isEmpty()) {
            return notFound();
        }
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(programmingExercise)) {
            return forbidden();
        }
        boolean hasAtLeastOneStudentResult = programmingExerciseService.hasAtLeastOneStudentResult(programmingExercise.get());
        boolean isReleased = programmingExercise.get().isReleased();
        ProgrammingExerciseTestCaseStateDTO testCaseDTO = new ProgrammingExerciseTestCaseStateDTO().released(isReleased).studentResult(hasAtLeastOneStudentResult)
                .testCasesChanged(programmingExercise.get().getTestCasesChanged())
                .buildAndTestStudentSubmissionsAfterDueDate(programmingExercise.get().getBuildAndTestStudentSubmissionsAfterDueDate());
        return ResponseEntity.ok(testCaseDTO);
    }

    /**
     * Search for all programming exercises by title and course title. The result is pageable since there might be hundreds
     * of exercises in the DB.
     *
     * @param search The pageable search containing the page size, page number and query string
     * @return The desired page, sorted and matching the given query
     */
    @GetMapping(Endpoints.PROGRAMMING_EXERCISES)
    @PreAuthorize("hasAnyRole('INSTRUCTOR, ADMIN')")
    public ResponseEntity<SearchResultPageDTO> getAllExercisesOnPage(PageableSearchDTO<String> search) {
        final var user = userService.getUserWithGroupsAndAuthorities();
        return ResponseEntity.ok(programmingExerciseService.getAllOnPageWithSize(search, user));
    }

    public static final class Endpoints {

        public static final String ROOT = "/api";

        public static final String PROGRAMMING_EXERCISES = "/programming-exercises";

        public static final String SETUP = PROGRAMMING_EXERCISES + "/setup";

        public static final String GET_FOR_COURSE = "/courses/{courseId}/programming-exercises";

        public static final String IMPORT = PROGRAMMING_EXERCISES + "/import/{sourceExerciseId}";

        public static final String PROGRAMMING_EXERCISE = PROGRAMMING_EXERCISES + "/{exerciseId}";

        public static final String PROBLEM = PROGRAMMING_EXERCISE + "/problem-statement";

        public static final String PROGRAMMING_EXERCISE_WITH_PARTICIPATIONS = PROGRAMMING_EXERCISE + "/with-participations";

        public static final String COMBINE_COMMITS = PROGRAMMING_EXERCISE + "/combine-template-commits";

        public static final String EXPORT_SUBMISSIONS_BY_STUDENT = PROGRAMMING_EXERCISE + "/export-repos-by-student-logins/{studentIds}";

        public static final String EXPORT_SUBMISSIONS_BY_PARTICIPATIONS = PROGRAMMING_EXERCISE + "/export-repos-by-participation-ids/{participationIds}";

        public static final String GENERATE_TESTS = PROGRAMMING_EXERCISE + "/generate-tests";

        public static final String TEST_CASE_STATE = PROGRAMMING_EXERCISE + "/test-case-state";

        private Endpoints() {
        }
    }
}
