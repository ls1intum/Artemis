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
import java.security.Principal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

import de.tum.in.www1.artemis.domain.*;
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
import de.tum.in.www1.artemis.service.scheduled.ProgrammingExerciseScheduleService;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryExportOptionsDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import de.tum.in.www1.artemis.web.websocket.dto.ProgrammingExerciseTestCaseStateDTO;
import io.github.jhipster.web.util.ResponseUtil;

/** REST controller for managing ProgrammingExercise. */
@RestController
@RequestMapping("/api")
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

    private final ResultService resultService;

    private final ProgrammingExerciseService programmingExerciseService;

    private final ProgrammingExerciseScheduleService programmingExerciseScheduleService;

    private final StudentParticipationRepository studentParticipationRepository;

    private final GroupNotificationService groupNotificationService;

    private final String packageNameRegex = "^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+[0-9a-z_]$";

    private final Pattern packageNamePattern = Pattern.compile(packageNameRegex);

    public ProgrammingExerciseResource(ProgrammingExerciseRepository programmingExerciseRepository, UserService userService, AuthorizationCheckService authCheckService,
            CourseService courseService, Optional<ContinuousIntegrationService> continuousIntegrationService, Optional<VersionControlService> versionControlService,
            ExerciseService exerciseService, ResultService resultService, ProgrammingExerciseService programmingExerciseService,
            ProgrammingExerciseScheduleService programmingExerciseScheduleService, StudentParticipationRepository studentParticipationRepository,
            GroupNotificationService groupNotificationService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.userService = userService;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.versionControlService = versionControlService;
        this.exerciseService = exerciseService;
        this.resultService = resultService;
        this.programmingExerciseService = programmingExerciseService;
        this.programmingExerciseScheduleService = programmingExerciseScheduleService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.groupNotificationService = groupNotificationService;
    }

    /**
     * @param exercise the exercise object we want to check for errors
     * @return the error message as response or null if everything is fine
     */
    private ResponseEntity<ProgrammingExercise> checkProgrammingExerciseForError(ProgrammingExercise exercise) {
        if (!continuousIntegrationService.get().buildPlanIdIsValid(exercise.getTemplateBuildPlanId())) {
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
        if (exercise.getSolutionBuildPlanId() != null && !continuousIntegrationService.get().buildPlanIdIsValid(exercise.getSolutionBuildPlanId())) {
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
     * POST /programming-exercises : Create a new programmingExercise.
     *
     * @param programmingExercise the programmingExercise to create
     * @return the ResponseEntity with status 201 (Created) and with body the new programmingExercise, or with status 400 (Bad Request) if the programmingExercise has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/programming-exercises")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<ProgrammingExercise> createProgrammingExercise(@RequestBody ProgrammingExercise programmingExercise) throws URISyntaxException {
        log.debug("REST request to save ProgrammingExercise : {}", programmingExercise);
        if (programmingExercise.getId() != null) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "idexists", "A new programmingExercise cannot already have an ID")).body(null);
        }
        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(programmingExercise.getCourse().getId());
        if (course == null) {
            return ResponseEntity.badRequest()
                    .headers(
                            HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "courseNotFound", "The course belonging to this programming exercise does not exist"))
                    .body(null);
        }
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isInstructorInCourse(course, user) && !authCheckService.isAdmin()) {
            return forbidden();
        }

        ResponseEntity<ProgrammingExercise> errorResponse = checkProgrammingExerciseForError(programmingExercise);
        if (errorResponse != null) {
            return errorResponse;
        }

        // We need to save the programming exercise BEFORE saving the participations to avoid transient state exceptions.
        // This is only necessary for linked exercises, however we don't differentiate this with a separate endpoint.
        programmingExercise.generateAndSetProjectKey();
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        // Only save after checking for errors
        programmingExerciseService.saveParticipations(programmingExercise);

        ProgrammingExercise savedProgrammingExercise = programmingExerciseRepository.save(programmingExercise);

        programmingExerciseScheduleService.scheduleExerciseIfRequired(savedProgrammingExercise);
        groupNotificationService.notifyTutorGroupAboutExerciseCreated(programmingExercise);
        return ResponseEntity.created(new URI("/api/programming-exercises/" + savedProgrammingExercise.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, savedProgrammingExercise.getTitle())).body(savedProgrammingExercise);
    }

    /**
     * POST /programming-exercises/setup : Setup a new programmingExercise (with all needed repositories etc.)
     *
     * @param programmingExercise the programmingExercise to setup
     * @return the ResponseEntity with status 201 (Created) and with body the new programmingExercise, or with status 400 (Bad Request) if the parameters are invalid
     */
    @PostMapping("/programming-exercises/setup")
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
        if (course == null) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createAlert(applicationName, "The course belonging to this programming exercise does not exist", "courseNotFound")).body(null);
        }
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

        // Check if exercise shortname matches regex
        Matcher shortNameMatcher = SHORT_NAME_PATTERN.matcher(programmingExercise.getShortName());
        if (!shortNameMatcher.matches()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "The shortname is invalid", "shortnameInvalid")).body(null);
        }

        // Check if course shortname is set
        if (course.getShortName() == null || course.getShortName().length() < 3) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "The shortname of the course is not set or too short", "courseShortnameInvalid"))
                    .body(null);
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

        String projectKey = programmingExercise.getProjectKey();
        String projectName = programmingExercise.getProjectName();
        String errorMessageVCS = versionControlService.get().checkIfProjectExists(projectKey, projectName);
        if (errorMessageVCS != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, errorMessageVCS, "vcsProjectExists")).body(null);
        }

        String errorMessageCI = continuousIntegrationService.get().checkIfProjectExists(projectKey, projectName);
        if (errorMessageCI != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, errorMessageCI, "ciProjectExists")).body(null);
        }

        try {
            ProgrammingExercise result = programmingExerciseService.setupProgrammingExercise(programmingExercise); // Setup all repositories etc

            // TODO: should the call `scheduleExerciseIfRequired` not be moved into the service?
            programmingExerciseScheduleService.scheduleExerciseIfRequired(result);
            groupNotificationService.notifyTutorGroupAboutExerciseCreated(result);
            return ResponseEntity.created(new URI("/api/programming-exercises" + result.getId()))
                    .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getTitle())).body(result);
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
     * a new id. For a concrete list of what gets copied and what not have a look at {@link ProgrammingExerciseService#importProgrammingExerciseBasis(ProgrammingExercise, ProgrammingExercise)}
     *
     * @see ProgrammingExerciseService#importProgrammingExerciseBasis(ProgrammingExercise, ProgrammingExercise)
     * @see ProgrammingExerciseService#importBuildPlans(ProgrammingExercise, ProgrammingExercise)
     * @see ProgrammingExerciseService#importRepositories(ProgrammingExercise, ProgrammingExercise)
     * @param sourceExerciseId The ID of the template exercise which should get imported
     * @param newExercise The new exercise containing values that should get overwritten in the imported exercise, s.a. the title or difficulty
     * @return The imported exercise (200), a not found error (404) if the template does not exist, or a forbidden error
     *         (403) if the user is not at least an instructor in the target course.
     */
    @PostMapping("/programming-exercises/import/{sourceExerciseId}")
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

        final var optionalTemplate = programmingExerciseRepository.findByIdWithEagerTestCasesHintsAndTemplateAndSolutionParticipations(sourceExerciseId);
        if (optionalTemplate.isEmpty()) {
            return notFound();
        }

        final var template = optionalTemplate.get();
        final var imported = programmingExerciseService.importProgrammingExerciseBasis(template, newExercise);
        HttpHeaders responseHeaders;
        programmingExerciseService.importRepositories(template, imported);
        try {
            // TODO: We have removed the automatic build trigger from test to base for new programming exercises. We need to also remove this build trigger manually on the case of
            // an import as the source exercise might still have this trigger.
            programmingExerciseService.importBuildPlans(template, imported);
            responseHeaders = HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, imported.getTitle());
        }
        catch (HttpException e) {
            responseHeaders = HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "importExerciseTriggerPlanFail", "Unable to trigger imported build plans");
        }

        // Remove unnecessary fields
        imported.setTestCases(null);
        imported.setTemplateParticipation(null);
        imported.setSolutionParticipation(null);
        imported.setExerciseHints(null);

        return ResponseEntity.ok().headers(responseHeaders).body(imported);
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
    @PutMapping("/programming-exercises")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<ProgrammingExercise> updateProgrammingExercise(@RequestBody ProgrammingExercise programmingExercise,
            @RequestParam(value = "notificationText", required = false) String notificationText) throws URISyntaxException {
        log.debug("REST request to update ProgrammingExercise : {}", programmingExercise);
        if (programmingExercise.getId() == null) {
            return createProgrammingExercise(programmingExercise);
        }
        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(programmingExercise.getCourse().getId());
        if (course == null) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createAlert(applicationName, "courseNotFound", "The course belonging to this programming exercise does not exist")).body(null);
        }
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isInstructorInCourse(course, user) && !authCheckService.isAdmin()) {
            return forbidden();
        }

        ResponseEntity<ProgrammingExercise> errorResponse = checkProgrammingExerciseForError(programmingExercise);
        if (errorResponse != null) {
            return errorResponse;
        }

        // Only save after checking for errors
        ProgrammingExercise savedProgrammingExercise = programmingExerciseRepository.save(programmingExercise);

        // TODO: should the call `scheduleExerciseIfRequired` not be moved into the service?
        programmingExerciseScheduleService.scheduleExerciseIfRequired(savedProgrammingExercise);
        if (notificationText != null) {
            groupNotificationService.notifyStudentGroupAboutExerciseUpdate(savedProgrammingExercise, notificationText);
        }
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, programmingExercise.getTitle())).body(savedProgrammingExercise);
    }

    /**
     * PATCH /programming-exercises-problem: Updates the problem statement of the exercise.
     *
     * @param problemStatementUpdate the programmingExercise to update with the new problemStatement
     * @param notificationText to notify the student group about the updated problemStatement on the programming exercise
     * @return the ResponseEntity with status 200 (OK) and with body the updated problemStatement, with status 404 if the programmingExercise could not be found, or with 403 if the user does not have permissions to access the programming exercise.
     */
    @PatchMapping("/programming-exercises-problem")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ProgrammingExercise> updateProblemStatement(@RequestBody ProblemStatementUpdate problemStatementUpdate,
            @RequestParam(value = "notificationText", required = false) String notificationText) {
        log.debug("REST request to update ProgrammingExercise with new problem statement: {}", problemStatementUpdate);
        ProgrammingExercise updatedProgrammingExercise;
        try {
            updatedProgrammingExercise = programmingExerciseService.updateProblemStatement(problemStatementUpdate.getExerciseId(), problemStatementUpdate.getProblemStatement());
        }
        catch (IllegalAccessException ex) {
            return forbidden();
        }
        catch (EntityNotFoundException ex) {
            return notFound();
        }
        if (notificationText != null) {
            groupNotificationService.notifyStudentGroupAboutExerciseUpdate(updatedProgrammingExercise, notificationText);
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
    @GetMapping(value = "/courses/{courseId}/programming-exercises")
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
     * GET /programming-exercises/:id : get the "id" programmingExercise.
     *
     * @param id the id of the programmingExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the programmingExercise, or with status 404 (Not Found)
     */
    @GetMapping("/programming-exercises/{id}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ProgrammingExercise> getProgrammingExercise(@PathVariable Long id) {
        log.debug("REST request to get ProgrammingExercise : {}", id);
        Optional<ProgrammingExercise> programmingExercise = programmingExerciseRepository.findById(id);
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
     * GET /programming-exercises-with-participations/:id : get the "id" programmingExercise.
     *
     * @param id the id of the programmingExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the programmingExercise, or with status 404 (Not Found)
     */
    @GetMapping("/programming-exercises/{id}/with-participations")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ProgrammingExercise> getProgrammingExerciseWithAllParticipations(@PathVariable Long id) {
        log.debug("REST request to get ProgrammingExercise : {}", id);

        User user = userService.getUserWithGroupsAndAuthorities();
        Optional<ProgrammingExercise> programmingExerciseOpt = programmingExerciseRepository.findWithTemplateAndSolutionParticipationById(id);
        if (programmingExerciseOpt.isPresent()) {
            ProgrammingExercise programmingExercise = programmingExerciseOpt.get();
            Course course = programmingExercise.getCourse();

            Optional<StudentParticipation> assignmentParticipation = studentParticipationRepository.findByExerciseIdAndStudentIdWithLatestResult(programmingExercise.getId(),
                    user.getId());
            Set<StudentParticipation> participations = new HashSet<>();
            assignmentParticipation.ifPresent(participations::add);
            programmingExercise.setStudentParticipations(participations);

            if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
                return forbidden();
            }

            return ResponseEntity.ok(programmingExercise);
        }
        else {
            return notFound();
        }
    }

    /**
     * DELETE /programming-exercises/:id : delete the "id" programmingExercise.
     *
     * @param id the id of the programmingExercise to delete
     * @param deleteStudentReposBuildPlans boolean which states whether the corresponding build plan should be deleted as well
     * @param deleteBaseReposBuildPlans the ResponseEntity with status 200 (OK)
     * @return the ResponseEntity with status 200 (OK) when programming exercise has been successfully deleted or with status 404 (Not Found)
     */
    @DeleteMapping("/programming-exercises/{id}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Void> deleteProgrammingExercise(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean deleteStudentReposBuildPlans,
            @RequestParam(defaultValue = "false") boolean deleteBaseReposBuildPlans) {
        log.info("REST request to delete ProgrammingExercise : {}", id);
        Optional<ProgrammingExercise> programmingExercise = programmingExerciseRepository.findById(id);
        if (programmingExercise.isPresent()) {
            log.info("Found ProgrammingExercise to delete with title: {}", programmingExercise.get().getTitle());
            Course course = programmingExercise.get().getCourse();
            User user = userService.getUserWithGroupsAndAuthorities();
            if (!authCheckService.isInstructorInCourse(course, user) && !authCheckService.isAdmin()) {
                return forbidden();
            }
            String title = programmingExercise.get().getTitle();
            exerciseService.delete(programmingExercise.get(), deleteStudentReposBuildPlans, deleteBaseReposBuildPlans);
            return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, title)).build();
        }
        else {
            log.warn("ProgrammingExercise with id {} not found for delete request", id);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Combine all commits into one in the template repository of a given exercise.
     * 
     * @param id of the exercise
     * @return the ResponseEntity with status
     *              200 (OK) if combine has been successfully executed
     *              403 (Forbidden) if the user is not admin and course instructor or
     *              500 (Internal Server Error)
     */
    @PutMapping(value = "/programming-exercises/{id}/combine-template-commits", produces = MediaType.TEXT_PLAIN_VALUE)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Void> combineTemplateRepositoryCommits(@PathVariable Long id) {
        log.debug("REST request to generate the structure oracle for ProgrammingExercise with id: {}", id);

        Optional<ProgrammingExercise> programmingExerciseOptional = programmingExerciseRepository.findById(id);
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
    @PostMapping(value = "/programming-exercises/{exerciseId}/export-repos-by-student-logins/{studentIds}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Resource> exportSubmissionsByStudentLogins(@PathVariable Long exerciseId, @PathVariable String studentIds,
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
    @PostMapping(value = "/programming-exercises/{exerciseId}/export-repos-by-participation-ids/{participationIds}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Resource> exportSubmissionsByParticipationIds(@PathVariable Long exerciseId, @PathVariable String participationIds,
            @RequestBody RepositoryExportOptionsDTO repositoryExportOptions) throws IOException {
        ProgrammingExercise programmingExercise = programmingExerciseService.findByIdWithEagerStudentParticipationsAndSubmissions(exerciseId);

        if (!authCheckService.isAtLeastTeachingAssistantForExercise(programmingExercise))
            return forbidden();

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
    private ResponseEntity<Resource> provideZipForParticipations(List<ProgrammingExerciseStudentParticipation> exportedStudentParticipations, Long exerciseId,
            RepositoryExportOptionsDTO repositoryExportOptions) throws IOException {
        // TODO: in case we do not find participations for the given ids, we should inform the user in the client, that the student did not participate in the exercise.
        if (exportedStudentParticipations.isEmpty()) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, false, ENTITY_NAME, "noparticipations", "No existing user was specified or no submission exists."))
                    .body(null);
        }

        File zipFile = programmingExerciseService.exportStudentRepositories(exerciseId, exportedStudentParticipations, repositoryExportOptions);
        if (zipFile == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "internalServerError",
                    "There was an error on the server and the zip file could not be created.")).body(null);
        }

        InputStreamResource resource = new InputStreamResource(new FileInputStream(zipFile));
        return ResponseEntity.ok().contentLength(zipFile.length()).contentType(MediaType.APPLICATION_OCTET_STREAM).header("filename", zipFile.getName()).body(resource);
    }

    /**
     * PUT /programming-exercises/{id}/generate-tests : Makes a call to StructureOracleGenerator to generate the structure oracle aka the test.json file
     *
     * @param id The ID of the programming exercise for which the structure oracle should get generated
     * @return The ResponseEntity with status 201 (Created) or with status 400 (Bad Request) if the parameters are invalid
     */
    @GetMapping(value = "/programming-exercises/{id}/generate-tests", produces = MediaType.TEXT_PLAIN_VALUE)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<String> generateStructureOracleForExercise(@PathVariable Long id) {
        log.debug("REST request to generate the structure oracle for ProgrammingExercise with id: {}", id);

        if (id == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "programmingExerciseNotFound", "The programming exercise does not exist"))
                    .body(null);
        }
        Optional<ProgrammingExercise> programmingExerciseOptional = programmingExerciseRepository.findById(id);
        if (programmingExerciseOptional.isEmpty()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "programmingExerciseNotFound", "The programming exercise does not exist"))
                    .body(null);
        }

        ProgrammingExercise programmingExercise = programmingExerciseOptional.get();
        Course course = courseService.findOne(programmingExercise.getCourse().getId());
        if (course == null) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createAlert(applicationName, "courseNotFound", "The course belonging to this programming exercise does not exist")).body(null);
        }
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
    @GetMapping(value = "/programming-exercises/{exerciseId}/test-case-state")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ProgrammingExerciseTestCaseStateDTO> hasAtLeastOneStudentResult(@PathVariable Long exerciseId) {
        Optional<ProgrammingExercise> programmingExercise = programmingExerciseRepository.findById(exerciseId);
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
     * @param principal The identification of the user calling this endpoint
     * @return The desired page, sorted and matching the given query
     */
    @GetMapping("programming-exercises")
    @PreAuthorize("hasAnyRole('INSTRUCTOR, ADMIN')")
    public ResponseEntity<SearchResultPageDTO> getAllExercisesOnPage(PageableSearchDTO<String> search, Principal principal) {
        final var user = userService.getUserWithGroupsAndAuthorities(principal);
        return ResponseEntity.ok(programmingExerciseService.getAllOnPageWithSize(search, user));
    }
}
