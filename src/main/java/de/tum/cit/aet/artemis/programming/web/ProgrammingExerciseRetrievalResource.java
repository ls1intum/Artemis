package de.tum.cit.aet.artemis.programming.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastStudentInExercise;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastTutorInExercise;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseAthenaConfigService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfigHelper;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.dto.CheckoutDirectoriesDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTestCaseStateDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTheiaConfigDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTaskService;
import de.tum.cit.aet.artemis.programming.service.RepositoryCheckoutService;

/**
 * REST controller for retrieving information about programming exercises.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/programming/")
public class ProgrammingExerciseRetrievalResource {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseRetrievalResource.class);

    private final ProgrammingExerciseService programmingExerciseService;

    private final AuthorizationCheckService authCheckService;

    private final ExerciseService exerciseService;

    private final ExerciseAthenaConfigService exerciseAthenaConfigService;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    private final RepositoryCheckoutService repositoryCheckoutService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final GradingCriterionRepository gradingCriterionRepository;

    private final ChannelRepository channelRepository;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    public ProgrammingExerciseRetrievalResource(ProgrammingExerciseService programmingExerciseService, ProgrammingExerciseRepository programmingExerciseRepository,
            CourseRepository courseRepository, AuthorizationCheckService authCheckService, UserRepository userRepository,
            ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository, ExerciseService exerciseService,
            ExerciseAthenaConfigService exerciseAthenaConfigService, StudentParticipationRepository studentParticipationRepository,
            ProgrammingExerciseTaskService programmingExerciseTaskService, GradingCriterionRepository gradingCriterionRepository, ChannelRepository channelRepository,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository, RepositoryCheckoutService repositoryCheckoutService) {
        this.programmingExerciseService = programmingExerciseService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.courseRepository = courseRepository;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
        this.exerciseService = exerciseService;
        this.exerciseAthenaConfigService = exerciseAthenaConfigService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
        this.gradingCriterionRepository = gradingCriterionRepository;
        this.channelRepository = channelRepository;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.repositoryCheckoutService = repositoryCheckoutService;
    }

    /**
     * GET /courses/:courseId/programming-exercises : get all the programming exercises.
     *
     * @param courseId of the course for which the exercise should be fetched
     * @return the ResponseEntity with status 200 (OK) and the list of programmingExercises in body
     */
    @GetMapping("courses/{courseId}/programming-exercises")
    @EnforceAtLeastTutor
    public ResponseEntity<List<ProgrammingExercise>> getProgrammingExercisesForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all ProgrammingExercises for the course with id : {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);
        List<ProgrammingExercise> exercises = programmingExerciseService.findByCourseIdWithCategoriesLatestSubmissionResultForTemplateAndSolutionParticipation(courseId);
        for (ProgrammingExercise exercise : exercises) {
            // not required in the returned json body
            exercise.setStudentParticipations(null);
            exercise.setCourse(null);
        }
        return ResponseEntity.ok().body(exercises);
    }

    private ProgrammingExercise findProgrammingExercise(Long exerciseId, boolean includePlagiarismDetectionConfig, boolean includeAthenaConfig) {
        ProgrammingExercise programmingExercise;
        if (includePlagiarismDetectionConfig) {
            programmingExercise = programmingExerciseRepository
                    .findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndCompetenciesAndPlagiarismDetectionConfigAndBuildConfigElseThrow(exerciseId);
            PlagiarismDetectionConfigHelper.createAndSaveDefaultIfNullAndCourseExercise(programmingExercise, programmingExerciseRepository);
        }
        else {
            programmingExercise = programmingExerciseRepository
                    .findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesCompetenciesAndBuildConfigElseThrow(exerciseId);
        }

        if (includeAthenaConfig) {
            exerciseAthenaConfigService.loadAthenaConfig(programmingExercise);
        }

        return programmingExercise;
    }

    /**
     * GET /programming-exercises/:exerciseId : get the "exerciseId" programmingExercise.
     *
     * @param exerciseId                    the id of the programmingExercise to retrieve
     * @param withPlagiarismDetectionConfig boolean flag whether to include the plagiarism detection config of the exercise
     * @param withAthenaConfig              boolean flag whether to include the athena config of the exercise
     * @return the ResponseEntity with status 200 (OK) and with body the programmingExercise, or with status 404 (Not Found)
     */
    @GetMapping("programming-exercises/{exerciseId}")
    @EnforceAtLeastTutor
    public ResponseEntity<ProgrammingExercise> getProgrammingExercise(@PathVariable long exerciseId, @RequestParam(defaultValue = "false") boolean withPlagiarismDetectionConfig,
            @RequestParam(defaultValue = "false") boolean withAthenaConfig) {
        log.debug("REST request to get ProgrammingExercise : {}", exerciseId);
        var programmingExercise = findProgrammingExercise(exerciseId, withPlagiarismDetectionConfig, withAthenaConfig);
        // Fetch grading criterion into exercise of participation
        Set<GradingCriterion> gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(programmingExercise.getId());
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

        programmingExerciseTaskService.replaceTestIdsWithNames(programmingExercise);

        return ResponseEntity.ok().body(programmingExercise);
    }

    /**
     * GET /programming-exercises/:exerciseId/theia-config : get the theia config for the programmingExercise.
     *
     * @param exerciseId the id of the programmingExercise to retrieve the configuration for
     * @return the ResponseEntity with status 200 (OK) and with body the TheiaConfigDTO, or with status 404 (Not Found)
     */
    @GetMapping("programming-exercises/{exerciseId}/theia-config")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<ProgrammingExerciseTheiaConfigDTO> getBuildConfig(@PathVariable long exerciseId) {
        log.debug("REST request to get theia image of ProgrammingExercise : {}", exerciseId);
        var imageDTO = new ProgrammingExerciseTheiaConfigDTO(programmingExerciseBuildConfigRepository.getTheiaImageByProgrammingExerciseId(exerciseId));

        return ResponseEntity.ok().body(imageDTO);
    }

    /**
     * GET /programming-exercises/:exerciseId/with-participations/ : get the "exerciseId" programmingExercise.
     *
     * @param exerciseId the id of the programmingExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the programmingExercise, or with status 404 (Not Found)
     */
    @GetMapping("programming-exercises/{exerciseId}/with-participations")
    @EnforceAtLeastEditor
    public ResponseEntity<ProgrammingExercise> getProgrammingExerciseWithSetupParticipations(@PathVariable long exerciseId) {
        log.debug("REST request to get ProgrammingExercise with setup participations : {}", exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        var programmingExercise = programmingExerciseService.findByIdWithTemplateAndSolutionParticipationAndAuxiliaryReposAndLatestResultFeedbackTestCasesElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, programmingExercise, user);
        var assignmentParticipation = studentParticipationRepository.findByExerciseIdAndStudentIdAndTestRunWithLatestResult(programmingExercise.getId(), user.getId(), false);
        Set<StudentParticipation> participations = new HashSet<>();
        assignmentParticipation.ifPresent(participations::add);
        programmingExercise.setStudentParticipations(participations);

        programmingExerciseTaskService.replaceTestIdsWithNames(programmingExercise);
        return ResponseEntity.ok(programmingExercise);
    }

    /**
     * GET /programming-exercises/:exerciseId/with-template-and-solution-participation
     *
     * @param exerciseId            the id of the programmingExercise to retrieve
     * @param withSubmissionResults get all submission results
     * @param withGradingCriteria   also get the grading criteria for the exercise
     * @return the ResponseEntity with status 200 (OK) and the programming exercise with template and solution participation, or with status 404 (Not Found)
     */
    @GetMapping("programming-exercises/{exerciseId}/with-template-and-solution-participation")
    @EnforceAtLeastTutorInExercise
    public ResponseEntity<ProgrammingExercise> getProgrammingExerciseWithTemplateAndSolutionParticipation(@PathVariable long exerciseId,
            @RequestParam(defaultValue = "false") boolean withSubmissionResults, @RequestParam(defaultValue = "false") boolean withGradingCriteria) {
        log.debug("REST request to get programming exercise with template and solution participation : {}", exerciseId);
        final var programmingExercise = programmingExerciseService.loadProgrammingExercise(exerciseId, withSubmissionResults, withGradingCriteria);
        return ResponseEntity.ok(programmingExercise);
    }

    /**
     * GET /programming-exercises/:exerciseId/with-auxiliary-repository
     *
     * @param exerciseId the id of the programmingExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and the programming exercise with template and solution participation, or with status 404 (Not Found)
     */
    @GetMapping("programming-exercises/{exerciseId}/with-auxiliary-repository")
    @EnforceAtLeastTutorInExercise
    public ResponseEntity<ProgrammingExercise> getProgrammingExerciseWithAuxiliaryRepository(@PathVariable long exerciseId) {

        log.debug("REST request to get programming exercise with auxiliary repositories: {}", exerciseId);
        final var programmingExercise = programmingExerciseService.loadProgrammingExerciseWithAuxiliaryRepositories(exerciseId);
        return ResponseEntity.ok(programmingExercise);
    }

    /**
     * GET /programming-exercises/:exerciseId/test-case-state : Returns a DTO that offers information on the test case state of the programming exercise.
     *
     * @param exerciseId the id of a ProgrammingExercise
     * @return the ResponseEntity with status 200 (OK) and ProgrammingExerciseTestCaseStateDTO. Returns 404 (notFound) if the exercise does not exist.
     */
    @GetMapping("programming-exercises/{exerciseId}/test-case-state")
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
    @GetMapping("programming-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<SearchResultPageDTO<ProgrammingExercise>> getAllExercisesOnPage(SearchTermPageableSearchDTO<String> search,
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
    @GetMapping("programming-exercises/with-sca")
    @EnforceAtLeastEditor
    public ResponseEntity<SearchResultPageDTO<ProgrammingExercise>> getAllExercisesWithSCAOnPage(SearchTermPageableSearchDTO<String> search,
            @RequestParam(defaultValue = "true") boolean isCourseFilter, @RequestParam(defaultValue = "true") boolean isExamFilter,
            @RequestParam ProgrammingLanguage programmingLanguage) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        return ResponseEntity.ok(programmingExerciseService.getAllWithSCAOnPageWithSize(search, isCourseFilter, isExamFilter, programmingLanguage, user));
    }

    /**
     * Returns a list of auxiliary repositories for a given programming exercise.
     *
     * @param exerciseId of the exercise
     * @return the ResponseEntity with status 200 (OK) and the list of auxiliary repositories for the
     *         given programming exercise. 404 when the programming exercise was not found.
     */
    @GetMapping("programming-exercises/{exerciseId}/auxiliary-repository")
    @EnforceAtLeastTutor
    public ResponseEntity<List<AuxiliaryRepository>> getAuxiliaryRepositories(@PathVariable Long exerciseId) {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithAuxiliaryRepositoriesElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);
        return ResponseEntity.ok(exercise.getAuxiliaryRepositories());
    }

    /**
     * GET programming-exercises/:exerciseId/solution-files-content
     * <p>
     * Returns the solution repository files with content for a given programming exercise.
     * Note: This endpoint redirects the request to the ProgrammingExerciseParticipationService. This is required if
     * the solution participation id is not known for the client.
     *
     * @param exerciseId   the exercise for which the solution repository files should be retrieved
     * @param omitBinaries do not send binaries to reduce payload size
     * @return a redirect to the endpoint returning the files with content
     */
    @GetMapping("programming-exercises/{exerciseId}/solution-files-content")
    @EnforceAtLeastTutor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ModelAndView redirectGetSolutionRepositoryFiles(@PathVariable Long exerciseId,
            @RequestParam(value = "omitBinaries", required = false, defaultValue = "false") boolean omitBinaries) {
        log.debug("REST request to get latest Solution Repository Files for ProgrammingExercise with id : {}", exerciseId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);

        var participation = solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseIdElseThrow(exerciseId);

        // TODO: We want to get rid of ModelAndView and use ResponseEntity instead. Define an appropriate service method and then call it here and in the referenced endpoint.
        return new ModelAndView("forward:/api/programming/repository/" + participation.getId() + "/files-content" + (omitBinaries ? "?omitBinaries=" + omitBinaries : ""));
    }

    /**
     * GET programming-exercises/:exerciseId/template-files-content
     * <p>
     * Returns the template repository files with content for a given programming exercise.
     * Note: This endpoint redirects the request to the ProgrammingExerciseParticipationService. This is required if
     * the template participation id is not known for the client.
     *
     * @param exerciseId   the exercise for which the template repository files should be retrieved
     * @param omitBinaries do not send binaries to reduce payload size
     * @return a redirect to the endpoint returning the files with content
     */
    @GetMapping("programming-exercises/{exerciseId}/template-files-content")
    @EnforceAtLeastTutor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ModelAndView redirectGetTemplateRepositoryFiles(@PathVariable Long exerciseId,
            @RequestParam(value = "omitBinaries", required = false, defaultValue = "false") boolean omitBinaries) {
        log.debug("REST request to get latest Template Repository Files for ProgrammingExercise with id : {}", exerciseId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);

        var participation = templateProgrammingExerciseParticipationRepository.findByProgrammingExerciseIdElseThrow(exerciseId);

        // TODO: We want to get rid of ModelAndView and use ResponseEntity instead. Define an appropriate service method and then call it here and in the referenced endpoint.
        return new ModelAndView("forward:/api/programming/repository/" + participation.getId() + "/files-content" + (omitBinaries ? "?omitBinaries=" + omitBinaries : ""));
    }

    /**
     * GET programming-exercises/repository-checkout-directories
     *
     * @param programmingLanguage for which the checkout directories should be retrieved
     * @param checkoutSolution    whether the checkout solution repository shall be checked out during the template and submission build plan,
     *                                if not supplied set to true as default
     * @return a DTO containing the checkout directories for the exercise, solution, and tests repository
     *         for the requested programming language for the submission and solution build.
     */
    @Profile(PROFILE_LOCALCI)
    @GetMapping("programming-exercises/repository-checkout-directories")
    @EnforceAtLeastEditor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<CheckoutDirectoriesDTO> getRepositoryCheckoutDirectories(@RequestParam(value = "programmingLanguage") ProgrammingLanguage programmingLanguage,
            @RequestParam(value = "checkoutSolution", defaultValue = "true") boolean checkoutSolution) {
        log.debug("REST request to get checkout directories for programming language: {}", programmingLanguage);

        CheckoutDirectoriesDTO repositoriesCheckoutDirectoryDTO = repositoryCheckoutService.getCheckoutDirectories(programmingLanguage, checkoutSolution);
        return ResponseEntity.ok(repositoriesCheckoutDirectoryDTO);
    }

}
