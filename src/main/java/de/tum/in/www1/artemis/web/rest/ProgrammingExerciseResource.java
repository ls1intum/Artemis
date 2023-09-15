package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceEndpoints.*;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.*;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationException;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.metis.conversation.ChannelRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastEditor;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastInstructor;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastTutor;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.vcs.VersionControlService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageSendService;
import de.tum.in.www1.artemis.service.programming.*;
import de.tum.in.www1.artemis.web.rest.dto.BuildLogStatisticsDTO;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.ProgrammingExerciseResetOptionsDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import de.tum.in.www1.artemis.web.websocket.dto.ProgrammingExerciseTestCaseStateDTO;

/**
 * REST controller for managing ProgrammingExercise.
 */
@RestController
@RequestMapping(ROOT)
public class ProgrammingExerciseResource {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseResource.class);

    private static final String ENTITY_NAME = "programmingExercise";

    private final ChannelRepository channelRepository;

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ProfileService profileService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    private final UserRepository userRepository;

    private final CourseService courseService;

    private final AuthorizationCheckService authCheckService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final Optional<VersionControlService> versionControlService;

    private final ExerciseService exerciseService;

    private final ExerciseDeletionService exerciseDeletionService;

    private final ProgrammingExerciseService programmingExerciseService;

    private final ProgrammingExerciseRepositoryService programmingExerciseRepositoryService;

    private final StudentParticipationRepository studentParticipationRepository;

    private final StaticCodeAnalysisService staticCodeAnalysisService;

    private final GradingCriterionRepository gradingCriterionRepository;

    private final CourseRepository courseRepository;

    private final GitService gitService;

    private final AuxiliaryRepositoryService auxiliaryRepositoryService;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository;

    private final InstanceMessageSendService instanceMessageSendService;

    public ProgrammingExerciseResource(ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository,
            UserRepository userRepository, AuthorizationCheckService authCheckService, CourseService courseService,
            Optional<ContinuousIntegrationService> continuousIntegrationService, Optional<VersionControlService> versionControlService, ExerciseService exerciseService,
            ExerciseDeletionService exerciseDeletionService, ProgrammingExerciseService programmingExerciseService,
            ProgrammingExerciseRepositoryService programmingExerciseRepositoryService, StudentParticipationRepository studentParticipationRepository,
            StaticCodeAnalysisService staticCodeAnalysisService, GradingCriterionRepository gradingCriterionRepository, CourseRepository courseRepository, GitService gitService,
            AuxiliaryRepositoryService auxiliaryRepositoryService, SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository, ProfileService profileService,
            BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository, ChannelRepository channelRepository, InstanceMessageSendService instanceMessageSendService) {
        this.profileService = profileService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseTestCaseRepository = programmingExerciseTestCaseRepository;
        this.userRepository = userRepository;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.versionControlService = versionControlService;
        this.exerciseService = exerciseService;
        this.exerciseDeletionService = exerciseDeletionService;
        this.programmingExerciseService = programmingExerciseService;
        this.programmingExerciseRepositoryService = programmingExerciseRepositoryService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.staticCodeAnalysisService = staticCodeAnalysisService;
        this.gradingCriterionRepository = gradingCriterionRepository;
        this.courseRepository = courseRepository;
        this.gitService = gitService;
        this.auxiliaryRepositoryService = auxiliaryRepositoryService;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.buildLogStatisticsEntryRepository = buildLogStatisticsEntryRepository;
        this.channelRepository = channelRepository;
        this.instanceMessageSendService = instanceMessageSendService;
    }

    /**
     * @param exercise the exercise object we want to check for errors
     */
    private void checkProgrammingExerciseForError(ProgrammingExercise exercise) {
        ContinuousIntegrationService continuousIntegration = continuousIntegrationService.orElseThrow();
        VersionControlService versionControl = versionControlService.orElseThrow();

        if (!continuousIntegration.checkIfBuildPlanExists(exercise.getProjectKey(), exercise.getTemplateBuildPlanId())) {
            throw new BadRequestAlertException("The Template Build Plan ID seems to be invalid.", "Exercise", ProgrammingExerciseResourceErrorKeys.INVALID_TEMPLATE_BUILD_PLAN_ID);
        }
        if (exercise.getVcsTemplateRepositoryUrl() == null || !versionControl.repositoryUrlIsValid(exercise.getVcsTemplateRepositoryUrl())) {
            throw new BadRequestAlertException("The Template Repository URL seems to be invalid.", "Exercise",
                    ProgrammingExerciseResourceErrorKeys.INVALID_TEMPLATE_REPOSITORY_URL);
        }
        if (exercise.getSolutionBuildPlanId() != null && !continuousIntegration.checkIfBuildPlanExists(exercise.getProjectKey(), exercise.getSolutionBuildPlanId())) {
            throw new BadRequestAlertException("The Solution Build Plan ID seems to be invalid.", "Exercise", ProgrammingExerciseResourceErrorKeys.INVALID_SOLUTION_BUILD_PLAN_ID);
        }
        var solutionRepositoryUrl = exercise.getVcsSolutionRepositoryUrl();
        if (solutionRepositoryUrl != null && !versionControl.repositoryUrlIsValid(solutionRepositoryUrl)) {
            throw new BadRequestAlertException("The Solution Repository URL seems to be invalid.", "Exercise",
                    ProgrammingExerciseResourceErrorKeys.INVALID_SOLUTION_REPOSITORY_URL);
        }

        // It has already been checked when setting the test case weights that their sum is at least >= 0.
        // Only when changing the assessment format to automatic an additional check for > 0 has to be performed.
        if (exercise.getAssessmentType() == AssessmentType.AUTOMATIC) {
            final Set<ProgrammingExerciseTestCase> testCases = programmingExerciseTestCaseRepository.findByExerciseIdAndActive(exercise.getId(), true);
            if (!ProgrammingExerciseTestCaseService.isTestCaseWeightSumValid(testCases)) {
                throw new BadRequestAlertException("For exercises with only automatic assignment at least one test case weight must be greater than zero.", "Exercise",
                        ProgrammingExerciseResourceErrorKeys.INVALID_TEST_CASE_WEIGHTS);
            }
        }
    }

    /**
     * POST /programming-exercises/setup : Set up a new programmingExercise (with all needed repositories etc.)
     *
     * @param programmingExercise the programmingExercise to set up
     * @return the ResponseEntity with status 201 (Created) and with body the new programmingExercise, or with status 400 (Bad Request) if the parameters are invalid
     */
    @PostMapping(SETUP)
    @EnforceAtLeastEditor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<ProgrammingExercise> createProgrammingExercise(@RequestBody ProgrammingExercise programmingExercise) {
        log.debug("REST request to setup ProgrammingExercise : {}", programmingExercise);

        // Valid exercises have set either a course or an exerciseGroup
        programmingExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(programmingExercise);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);
        programmingExerciseService.validateNewProgrammingExerciseSettings(programmingExercise, course);

        try {
            // Setup all repositories etc
            ProgrammingExercise newProgrammingExercise = programmingExerciseService.createProgrammingExercise(programmingExercise);

            // Create default static code analysis categories
            if (Boolean.TRUE.equals(programmingExercise.isStaticCodeAnalysisEnabled())) {
                staticCodeAnalysisService.createDefaultCategories(newProgrammingExercise);
            }

            return ResponseEntity.created(new URI("/api/programming-exercises" + newProgrammingExercise.getId())).body(newProgrammingExercise);
        }
        catch (IOException | URISyntaxException | GitAPIException | ContinuousIntegrationException e) {
            log.error("Error while setting up programming exercise", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .headers(HeaderUtil.createAlert(applicationName, "An error occurred while setting up the exercise: " + e.getMessage(), "errorProgrammingExercise")).body(null);
        }
    }

    /**
     * PUT /programming-exercises : Updates an existing updatedProgrammingExercise.
     *
     * @param updatedProgrammingExercise the programmingExercise that has been updated on the client
     * @param notificationText           to notify the student group about the update on the programming exercise
     * @return the ResponseEntity with status 200 (OK) and with body the updated ProgrammingExercise, or with status 400 (Bad Request) if the updated ProgrammingExercise
     *         is not valid, or with status 500 (Internal Server Error) if the updated ProgrammingExercise couldn't be saved to the database
     */
    @PutMapping(PROGRAMMING_EXERCISES)
    @EnforceAtLeastEditor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<ProgrammingExercise> updateProgrammingExercise(@RequestBody ProgrammingExercise updatedProgrammingExercise,
            @RequestParam(value = "notificationText", required = false) String notificationText) {
        log.debug("REST request to update ProgrammingExercise : {}", updatedProgrammingExercise);
        if (updatedProgrammingExercise.getId() == null) {
            throw new BadRequestAlertException("Programming exercise cannot have an empty id when updating", ENTITY_NAME, "noProgrammingExerciseId");
        }

        updatedProgrammingExercise.validateGeneralSettings();

        // Valid exercises have set either a course or an exerciseGroup
        updatedProgrammingExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);
        programmingExerciseService.validateStaticCodeAnalysisSettings(updatedProgrammingExercise);

        // fetch course from database to make sure client didn't change groups
        var user = userRepository.getUserWithGroupsAndAuthorities();
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(updatedProgrammingExercise);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, user);

        checkProgrammingExerciseForError(updatedProgrammingExercise);

        var programmingExerciseBeforeUpdate = programmingExerciseRepository.findByIdWithAuxiliaryRepositoriesElseThrow(updatedProgrammingExercise.getId());
        if (!Objects.equals(programmingExerciseBeforeUpdate.getShortName(), updatedProgrammingExercise.getShortName())) {
            throw new BadRequestAlertException("The programming exercise short name cannot be changed", ENTITY_NAME, "shortNameCannotChange");
        }
        if (programmingExerciseBeforeUpdate.isStaticCodeAnalysisEnabled() != updatedProgrammingExercise.isStaticCodeAnalysisEnabled()) {
            throw new BadRequestAlertException("Static code analysis enabled flag must not be changed", ENTITY_NAME, "staticCodeAnalysisCannotChange");
        }
        if (programmingExerciseBeforeUpdate.isTestwiseCoverageEnabled() != updatedProgrammingExercise.isTestwiseCoverageEnabled()) {
            throw new BadRequestAlertException("Testwise coverage enabled flag must not be changed", ENTITY_NAME, "testwiseCoverageCannotChange");
        }
        if (!Boolean.TRUE.equals(updatedProgrammingExercise.isAllowOnlineEditor()) && !Boolean.TRUE.equals(updatedProgrammingExercise.isAllowOfflineIde())) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName,
                    "You need to allow at least one participation mode, the online editor or the offline IDE", "noParticipationModeAllowed")).body(null);
        }
        // Forbid changing the course the exercise belongs to.
        if (!Objects.equals(programmingExerciseBeforeUpdate.getCourseViaExerciseGroupOrCourseMember().getId(),
                updatedProgrammingExercise.getCourseViaExerciseGroupOrCourseMember().getId())) {
            throw new ConflictException("Exercise course id does not match the stored course id", ENTITY_NAME, "cannotChangeCourseId");
        }
        // Forbid conversion between normal course exercise and exam exercise
        exerciseService.checkForConversionBetweenExamAndCourseExercise(updatedProgrammingExercise, programmingExerciseBeforeUpdate, ENTITY_NAME);

        // Ignore changes to the default branch
        updatedProgrammingExercise.setBranch(programmingExerciseBeforeUpdate.getBranch());

        if (updatedProgrammingExercise.getAuxiliaryRepositories() == null) {
            // make sure the default value is set properly
            updatedProgrammingExercise.setAuxiliaryRepositories(new ArrayList<>());
        }

        auxiliaryRepositoryService.handleAuxiliaryRepositoriesWhenUpdatingExercises(programmingExerciseBeforeUpdate, updatedProgrammingExercise);

        if (updatedProgrammingExercise.getBonusPoints() == null) {
            // make sure the default value is set properly
            updatedProgrammingExercise.setBonusPoints(0.0);
        }

        // Only save after checking for errors
        ProgrammingExercise savedProgrammingExercise = programmingExerciseService.updateProgrammingExercise(programmingExerciseBeforeUpdate, updatedProgrammingExercise,
                notificationText);

        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExerciseBeforeUpdate, savedProgrammingExercise);

        exerciseService.logUpdate(updatedProgrammingExercise, updatedProgrammingExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        exerciseService.updatePointsInRelatedParticipantScores(programmingExerciseBeforeUpdate, updatedProgrammingExercise);
        return ResponseEntity.ok(savedProgrammingExercise);
    }

    /**
     * PUT /programming-exercises/timeline : Updates the timeline attributes of a given exercise
     *
     * @param updatedProgrammingExercise containing the changes that have to be saved
     * @param notificationText           an optional text to notify the student group about the update on the programming exercise
     * @return the ResponseEntity with status 200 (OK) with the updated ProgrammingExercise, or with status 403 (Forbidden)
     *         if the user is not allowed to update the exercise or with 404 (Not Found) if the updated ProgrammingExercise couldn't be found in the database
     */
    @PutMapping(TIMELINE)
    @EnforceAtLeastEditor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<ProgrammingExercise> updateProgrammingExerciseTimeline(@RequestBody ProgrammingExercise updatedProgrammingExercise,
            @RequestParam(value = "notificationText", required = false) String notificationText) {
        log.debug("REST request to update the timeline of ProgrammingExercise : {}", updatedProgrammingExercise);
        var existingProgrammingExercise = programmingExerciseRepository.findByIdElseThrow(updatedProgrammingExercise.getId());
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, existingProgrammingExercise, user);
        updatedProgrammingExercise = programmingExerciseService.updateTimeline(updatedProgrammingExercise, notificationText);
        exerciseService.logUpdate(updatedProgrammingExercise, updatedProgrammingExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, updatedProgrammingExercise.getTitle()))
                .body(updatedProgrammingExercise);
    }

    /**
     * PATCH /programming-exercises-problem: Updates the problem statement of the exercise.
     *
     * @param exerciseId              The ID of the exercise for which to change the problem statement
     * @param updatedProblemStatement The new problemStatement
     * @param notificationText        to notify the student group about the updated problemStatement on the programming exercise
     * @return the ResponseEntity with status 200 (OK) and with body the updated problemStatement, with status 404 if the programmingExercise could not be found, or with 403 if the
     *         user does not have permissions to access the programming exercise.
     */
    @PatchMapping(PROBLEM)
    @EnforceAtLeastEditor
    public ResponseEntity<ProgrammingExercise> updateProblemStatement(@PathVariable long exerciseId, @RequestBody String updatedProblemStatement,
            @RequestParam(value = "notificationText", required = false) String notificationText) {
        log.debug("REST request to update ProgrammingExercise with new problem statement: {}", updatedProblemStatement);
        var programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(exerciseId)
                .orElseThrow(() -> new EntityNotFoundException("Programming Exercise", exerciseId));
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, programmingExercise, user);
        var updatedProgrammingExercise = programmingExerciseService.updateProblemStatement(programmingExercise, updatedProblemStatement, notificationText);
        exerciseService.logUpdate(updatedProgrammingExercise, updatedProgrammingExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, updatedProgrammingExercise.getTitle()))
                .body(updatedProgrammingExercise);
    }

    /**
     * GET /courses/:courseId/programming-exercises : get all the programming exercises.
     *
     * @param courseId of the course for which the exercise should be fetched
     * @return the ResponseEntity with status 200 (OK) and the list of programmingExercises in body
     */
    @GetMapping(GET_FOR_COURSE)
    @EnforceAtLeastTutor
    public ResponseEntity<List<ProgrammingExercise>> getProgrammingExercisesForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all ProgrammingExercises for the course with id : {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);
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
    @GetMapping(PROGRAMMING_EXERCISE)
    @EnforceAtLeastTutor
    public ResponseEntity<ProgrammingExercise> getProgrammingExercise(@PathVariable long exerciseId) {
        log.debug("REST request to get ProgrammingExercise : {}", exerciseId);
        var programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndCompetenciesElseThrow(exerciseId);
        // Fetch grading criterion into exercise of participation
        List<GradingCriterion> gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(programmingExercise.getId());
        programmingExercise.setGradingCriteria(gradingCriteria);

        exerciseService.checkExerciseIfStructuredGradingInstructionFeedbackUsed(gradingCriteria, programmingExercise);
        // If the exercise belongs to an exam, only editors, instructors and admins are allowed to access it, otherwise also TA have access
        if (programmingExercise.isExamExercise()) {
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, programmingExercise, null);
        }
        else {
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, programmingExercise, null);
        }
        if (programmingExercise.isCourseExercise()) {
            Channel channel = channelRepository.findChannelByExerciseId(programmingExercise.getId());
            if (channel != null) {
                programmingExercise.setChannelName(channel.getName());
            }
        }

        return ResponseEntity.ok().body(programmingExercise);
    }

    /**
     * GET /programming-exercises/:exerciseId/with-participations/ : get the "exerciseId" programmingExercise.
     *
     * @param exerciseId the id of the programmingExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the programmingExercise, or with status 404 (Not Found)
     */
    @GetMapping(PROGRAMMING_EXERCISE_WITH_PARTICIPATIONS)
    @EnforceAtLeastEditor
    public ResponseEntity<ProgrammingExercise> getProgrammingExerciseWithSetupParticipations(@PathVariable long exerciseId) {
        log.debug("REST request to get ProgrammingExercise : {}", exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        var programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationLatestResultElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, programmingExercise, user);
        var assignmentParticipation = studentParticipationRepository.findByExerciseIdAndStudentIdAndTestRunWithLatestResult(programmingExercise.getId(), user.getId(), false);
        Set<StudentParticipation> participations = new HashSet<>();
        assignmentParticipation.ifPresent(participations::add);
        programmingExercise.setStudentParticipations(participations);
        return ResponseEntity.ok(programmingExercise);
    }

    /**
     * GET /programming-exercises/:exerciseId/with-template-and-solution-participation
     *
     * @param exerciseId            the id of the programmingExercise to retrieve
     * @param withSubmissionResults get all submission results
     * @return the ResponseEntity with status 200 (OK) and the programming exercise with template and solution participation, or with status 404 (Not Found)
     */
    @GetMapping(PROGRAMMING_EXERCISE_WITH_TEMPLATE_AND_SOLUTION_PARTICIPATION)
    @EnforceAtLeastTutor
    public ResponseEntity<ProgrammingExercise> getProgrammingExerciseWithTemplateAndSolutionParticipation(@PathVariable long exerciseId,
            @RequestParam(defaultValue = "false") boolean withSubmissionResults) {
        log.debug("REST request to get programming exercise with template and solution participation : {}", exerciseId);
        ProgrammingExercise programmingExercise;
        if (withSubmissionResults) {
            programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationSubmissionsAndResultsAndAuxiliaryRepositoriesElseThrow(exerciseId);
        }
        else {
            programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesElseThrow(exerciseId);
        }
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, programmingExercise, null);
        return ResponseEntity.ok(programmingExercise);
    }

    /**
     * DELETE /programming-exercises/:id : delete the "id" programmingExercise.
     *
     * @param exerciseId                   the id of the programmingExercise to delete
     * @param deleteStudentReposBuildPlans boolean which states whether the corresponding build plan should be deleted as well
     * @param deleteBaseReposBuildPlans    the ResponseEntity with status 200 (OK)
     * @return the ResponseEntity with status 200 (OK) when programming exercise has been successfully deleted or with status 404 (Not Found)
     */
    @DeleteMapping(PROGRAMMING_EXERCISE)
    @EnforceAtLeastInstructor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Void> deleteProgrammingExercise(@PathVariable long exerciseId, @RequestParam(defaultValue = "false") boolean deleteStudentReposBuildPlans,
            @RequestParam(defaultValue = "false") boolean deleteBaseReposBuildPlans) {
        log.info("REST request to delete ProgrammingExercise : {}", exerciseId);
        var programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesElseThrow(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, programmingExercise, user);
        exerciseService.logDeletion(programmingExercise, programmingExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        exerciseDeletionService.delete(exerciseId, deleteStudentReposBuildPlans, deleteBaseReposBuildPlans);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, programmingExercise.getTitle())).build();
    }

    /**
     * Combine all commits into one in the template repository of a given exercise.
     *
     * @param exerciseId of the exercise
     * @return the ResponseEntity with status
     *         200 (OK) if combine has been successfully executed
     *         403 (Forbidden) if the user is not admin and course instructor or
     *         500 (Internal Server Error)
     */
    @PutMapping(value = COMBINE_COMMITS, produces = MediaType.TEXT_PLAIN_VALUE)
    @EnforceAtLeastEditor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Void> combineTemplateRepositoryCommits(@PathVariable long exerciseId) {
        log.debug("REST request to combine the commits of the template repository of ProgrammingExercise with id: {}", exerciseId);
        var programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, programmingExercise, null);
        try {
            var exerciseRepoURL = programmingExercise.getVcsTemplateRepositoryUrl();
            gitService.combineAllCommitsOfRepositoryIntoOne(exerciseRepoURL);
            return new ResponseEntity<>(HttpStatus.OK);
        }
        catch (IllegalStateException | GitAPIException ex) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * PUT /programming-exercises/{exerciseId}/generate-tests : Makes a call to StructureOracleGenerator to generate the structure oracle aka the test.json file
     *
     * @param exerciseId The ID of the programming exercise for which the structure oracle should get generated
     * @return The ResponseEntity with status 201 (Created) or with status 400 (Bad Request) if the parameters are invalid
     */
    @PutMapping(value = GENERATE_TESTS, produces = MediaType.TEXT_PLAIN_VALUE)
    @EnforceAtLeastEditor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<String> generateStructureOracleForExercise(@PathVariable long exerciseId) {
        log.debug("REST request to generate the structure oracle for ProgrammingExercise with id: {}", exerciseId);
        var programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesElseThrow(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, programmingExercise, user);
        if (programmingExercise.getPackageName() == null || programmingExercise.getPackageName().length() < 3) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName,
                    "This is a linked exercise and generating the structure oracle for this exercise is not possible.", "couldNotGenerateStructureOracle")).body(null);
        }

        var solutionRepoURL = programmingExercise.getVcsSolutionRepositoryUrl();
        var exerciseRepoURL = programmingExercise.getVcsTemplateRepositoryUrl();
        var testRepoURL = programmingExercise.getVcsTestRepositoryUrl();

        try {
            String testsPath = Path.of("test", programmingExercise.getPackageFolderName()).toString();
            // Atm we only have one folder that can have structural tests, but this could change.
            testsPath = programmingExercise.hasSequentialTestRuns() ? Path.of("structural", testsPath).toString() : testsPath;
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
                            "An error occurred while generating the structure oracle for the exercise " + programmingExercise.getProjectName() + ": " + e,
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
    @GetMapping(TEST_CASE_STATE)
    @EnforceAtLeastTutor
    public ResponseEntity<ProgrammingExerciseTestCaseStateDTO> hasAtLeastOneStudentResult(@PathVariable long exerciseId) {
        var programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, programmingExercise, null);
        boolean hasAtLeastOneStudentResult = programmingExerciseService.hasAtLeastOneStudentResult(programmingExercise);
        boolean isReleased = programmingExercise.isReleased();
        ProgrammingExerciseTestCaseStateDTO testCaseDTO = new ProgrammingExerciseTestCaseStateDTO(isReleased, hasAtLeastOneStudentResult, programmingExercise.getTestCasesChanged(),
                programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate());
        return ResponseEntity.ok(testCaseDTO);
    }

    /**
     * Search for all programming exercises by id, title and course title. The result is pageable since there might be hundreds of exercises in the DB.
     *
     * @param search         The pageable search containing the page size, page number and query string
     * @param isCourseFilter Whether to search in the courses for exercises
     * @param isExamFilter   Whether to search in the groups for exercises
     * @return The desired page, sorted and matching the given query
     */
    @GetMapping(PROGRAMMING_EXERCISES)
    @EnforceAtLeastEditor
    public ResponseEntity<SearchResultPageDTO<ProgrammingExercise>> getAllExercisesOnPage(PageableSearchDTO<String> search,
            @RequestParam(defaultValue = "true") boolean isCourseFilter, @RequestParam(defaultValue = "true") boolean isExamFilter) {
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        return ResponseEntity.ok(programmingExerciseService.getAllOnPageWithSize(search, isCourseFilter, isExamFilter, user));
    }

    /**
     * Search for programming exercises by id, title and course title. Only exercises with SCA enabled and the given programming language will be included.
     * The result is pageable since there might be hundreds of exercises in the DB.
     *
     * @param search              The pageable search containing the page size, page number and query string
     * @param isCourseFilter      Whether to search in the courses for exercises
     * @param isExamFilter        Whether to search in the groups for exercises
     * @param programmingLanguage Filters for only exercises with this language
     * @return The desired page, sorted and matching the given query
     */
    @GetMapping(PROGRAMMING_EXERCISES + "/with-sca")
    @EnforceAtLeastEditor
    public ResponseEntity<SearchResultPageDTO<ProgrammingExercise>> getAllExercisesWithSCAOnPage(PageableSearchDTO<String> search,
            @RequestParam(defaultValue = "true") boolean isCourseFilter, @RequestParam(defaultValue = "true") boolean isExamFilter,
            @RequestParam ProgrammingLanguage programmingLanguage) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        return ResponseEntity.ok(programmingExerciseService.getAllWithSCAOnPageWithSize(search, isCourseFilter, isExamFilter, programmingLanguage, user));
    }

    /**
     * Unlock all repositories of the given programming exercise.
     *
     * @param exerciseId of the exercise
     * @return The ResponseEntity with status 200 (OK) or with status 404 (Not Found) if the exerciseId is invalid
     */
    @PutMapping(UNLOCK_ALL_REPOSITORIES)
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> unlockAllRepositories(@PathVariable Long exerciseId) {
        // Locking and unlocking repositories is not supported when using the local version control system.
        // Repository access is checked in the LocalVCFetchFilter and LocalVCPushFilter.
        if (profileService.isLocalVcsCi()) {
            return ResponseEntity.badRequest().build();
        }
        var programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, programmingExercise, null);
        instanceMessageSendService.sendUnlockAllStudentRepositoriesAndParticipations(exerciseId);
        log.info("Unlocked all repositories of programming exercise {} upon manual request", exerciseId);
        return ResponseEntity.ok().build();
    }

    /**
     * Lock all repositories of the given programming exercise.
     *
     * @param exerciseId of the exercise
     * @return The ResponseEntity with status 200 (OK) or with status 404 (Not Found) if the exerciseId is invalid
     */
    @PutMapping(LOCK_ALL_REPOSITORIES)
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> lockAllRepositories(@PathVariable Long exerciseId) {
        // Locking and unlocking repositories is not supported when using the local version control system.
        // Repository access is checked in the LocalVCFetchFilter and LocalVCPushFilter.
        if (profileService.isLocalVcsCi()) {
            return ResponseEntity.badRequest().build();
        }
        var programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, programmingExercise, null);
        instanceMessageSendService.sendLockAllStudentRepositoriesAndParticipations(exerciseId);
        log.info("Locked all repositories of programming exercise {} upon manual request", exerciseId);
        return ResponseEntity.ok().build();
    }

    /**
     * Returns a list of auxiliary repositories for a given programming exercise.
     *
     * @param exerciseId of the exercise
     * @return the ResponseEntity with status 200 (OK) and the list of auxiliary repositories for the
     *         given programming exercise. 404 when the programming exercise was not found.
     */
    @GetMapping(AUXILIARY_REPOSITORY)
    @EnforceAtLeastTutor
    public ResponseEntity<List<AuxiliaryRepository>> getAuxiliaryRepositories(@PathVariable Long exerciseId) {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithAuxiliaryRepositoriesElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);
        return ResponseEntity.ok(exercise.getAuxiliaryRepositories());
    }

    /**
     * Reset a programming exercise by performing a set of operations as specified in the
     * ProgrammingExerciseResetOptionsDTO for an exercise given an exerciseId.
     *
     * The available operations include:
     * 1. deleteBuildPlans: Deleting all student build plans (except BASE/SOLUTION).
     * 2. deleteRepositories: Deleting all student repositories (requires: 1. deleteBuildPlans == true).
     * 3. deleteParticipationsSubmissionsAndResults: Deleting all participations, submissions, and results.
     * 4. recreateBuildPlans: Deleting and recreating the BASE and SOLUTION build plans.
     *
     * @param exerciseId                         - Id of the programming exercise to reset.
     * @param programmingExerciseResetOptionsDTO - Data Transfer Object specifying which operations to perform during the exercise reset.
     * @return ResponseEntity<Void> - The ResponseEntity with status 200 (OK) if the reset was successful.
     */
    @PutMapping(RESET)
    @EnforceAtLeastEditor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Void> reset(@PathVariable Long exerciseId, @RequestBody ProgrammingExerciseResetOptionsDTO programmingExerciseResetOptionsDTO) {
        log.debug("REST request to reset ProgrammingExercise : {}", exerciseId);
        var programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesElseThrow(exerciseId);
        final var user = userRepository.getUserWithGroupsAndAuthorities();

        if (programmingExerciseResetOptionsDTO.recreateBuildPlans()) {
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, programmingExercise, user);
            continuousIntegrationService.orElseThrow().recreateBuildPlansForExercise(programmingExercise);
        }

        if (programmingExerciseResetOptionsDTO.deleteParticipationsSubmissionsAndResults()) {
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, programmingExercise, user);
            exerciseDeletionService.reset(programmingExercise);
        }

        if (programmingExerciseResetOptionsDTO.deleteBuildPlans()) {
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, programmingExercise, user);
            boolean deleteRepositories = programmingExerciseResetOptionsDTO.deleteRepositories();
            exerciseDeletionService.cleanup(exerciseId, deleteRepositories);
        }

        return ResponseEntity.ok().build();
    }

    /**
     * PUT /programming-exercises/{exerciseId}/re-evaluate : Re-evaluates and updates an existing ProgrammingExercise.
     *
     * @param exerciseId                                  of the exercise
     * @param programmingExercise                         the ProgrammingExercise to re-evaluate and update
     * @param deleteFeedbackAfterGradingInstructionUpdate boolean flag that indicates whether the associated feedback should be deleted or not
     * @return the ResponseEntity with status 200 (OK) and with body the updated ProgrammingExercise, or with status 400 (Bad Request) if the ProgrammingExercise is not valid,
     *         or with status 409 (Conflict) if given exerciseId is not same as in the object of the request body, or with status 500 (Internal Server Error) if the
     *         ProgrammingExercise
     *         couldn't be updated
     */
    @PutMapping(REEVALUATE_EXERCISE)
    @EnforceAtLeastEditor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<ProgrammingExercise> reEvaluateAndUpdateProgrammingExercise(@PathVariable long exerciseId, @RequestBody ProgrammingExercise programmingExercise,
            @RequestParam(value = "deleteFeedback", required = false) Boolean deleteFeedbackAfterGradingInstructionUpdate) {
        log.debug("REST request to re-evaluate ProgrammingExercise : {}", programmingExercise);
        // check that the exercise exists for given id
        programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkGivenExerciseIdSameForExerciseInRequestBodyElseThrow(exerciseId, programmingExercise);

        // fetch course from database to make sure client didn't change groups
        var user = userRepository.getUserWithGroupsAndAuthorities();
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(programmingExercise);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, user);

        exerciseService.reEvaluateExercise(programmingExercise, deleteFeedbackAfterGradingInstructionUpdate);
        return updateProgrammingExercise(programmingExercise, null);
    }

    /**
     * DELETE programming-exercises/:exerciseId/tasks : Delete all tasks and solution entries for an existing ProgrammingExercise.
     * Note: This endpoint exists only for testing purposes and will be removed at a later stage of the development of HESTIA
     * (automatic generation of code hints for programming exercises in Java).
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 204},
     *         or with status {@code 400 (Bad Request) if the exerciseId is not valid}.
     */
    @DeleteMapping(TASKS)
    @EnforceAtLeastEditor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Void> deleteTaskWithSolutionEntries(@PathVariable Long exerciseId) {
        log.debug("REST request to delete ProgrammingExerciseTasks with ProgrammingExerciseSolutionEntries for ProgrammingExercise with id : {}", exerciseId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);

        programmingExerciseService.deleteTasksWithSolutionEntries(exercise.getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * GET programming-exercises/:exerciseId/solution-files-content
     *
     * Returns the solution repository files with content for a given programming exercise.
     * Note: This endpoint redirects the request to the ProgrammingExerciseParticipationService. This is required if
     * the solution participation id is not known for the client.
     *
     * @param exerciseId the exercise for which the solution repository files should be retrieved
     * @return a redirect to the endpoint returning the files with content
     */
    @GetMapping(SOLUTION_REPOSITORY_FILES_WITH_CONTENT)
    @EnforceAtLeastTutor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ModelAndView redirectGetSolutionRepositoryFiles(@PathVariable Long exerciseId) {
        log.debug("REST request to get latest Solution Repository Files for ProgrammingExercise with id : {}", exerciseId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);

        var participation = solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseIdElseThrow(exerciseId);

        return new ModelAndView("forward:/api/repository/" + participation.getId() + "/files-content");
    }

    /**
     * GET programming-exercises/:exerciseId/template-files-content
     *
     * Returns the template repository files with content for a given programming exercise.
     * Note: This endpoint redirects the request to the ProgrammingExerciseParticipationService. This is required if
     * the template participation id is not known for the client.
     *
     * @param exerciseId the exercise for which the template repository files should be retrieved
     * @return a redirect to the endpoint returning the files with content
     */
    @GetMapping(TEMPLATE_REPOSITORY_FILES_WITH_CONTENT)
    @EnforceAtLeastTutor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ModelAndView redirectGetTemplateRepositoryFiles(@PathVariable Long exerciseId) {
        log.debug("REST request to get latest Template Repository Files for ProgrammingExercise with id : {}", exerciseId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);

        var participation = templateProgrammingExerciseParticipationRepository.findByProgrammingExerciseIdElseThrow(exerciseId);

        return new ModelAndView("forward:/api/repository/" + participation.getId() + "/files-content");
    }

    /**
     * GET programming-exercises/:exerciseId/solution-file-names
     *
     * Returns the solution repository file names for a given programming exercise.
     * Note: This endpoint redirects the request to the ProgrammingExerciseParticipationService. This is required if
     * the solution participation id is not known for the client.
     *
     * @param exerciseId the exercise for which the solution repository files should be retrieved
     * @return a redirect to the endpoint returning the files with content
     */
    @GetMapping(SOLUTION_REPOSITORY_FILE_NAMES)
    @EnforceAtLeastTutor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ModelAndView redirectGetSolutionRepositoryFilesWithoutContent(@PathVariable Long exerciseId) {
        log.debug("REST request to get latest solution repository file names for ProgrammingExercise with id : {}", exerciseId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);

        var participation = solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseIdElseThrow(exerciseId);

        return new ModelAndView("forward:/api/repository/" + participation.getId() + "/file-names");
    }

    /**
     * GET programming-exercises/:exerciseId/build-log-statistics
     *
     * Returns the averaged build log statistics for a given programming exercise.
     *
     * @param exerciseId the exercise for which the build log statistics should be retrieved
     * @return a DTO containing the average build log statistics
     */
    @GetMapping(BUILD_LOG_STATISTICS)
    @EnforceAtLeastEditor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<BuildLogStatisticsDTO> getBuildLogStatistics(@PathVariable Long exerciseId) {
        log.debug("REST request to get build log statistics for ProgrammingExercise with id : {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationById(exerciseId).orElseThrow();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, programmingExercise, null);

        var buildLogStatistics = buildLogStatisticsEntryRepository.findAverageBuildLogStatisticsEntryForExercise(programmingExercise);
        return ResponseEntity.ok(buildLogStatistics);
    }
}
