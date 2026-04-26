package de.tum.cit.aet.artemis.programming.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.athena.api.AthenaApi;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.ModuleFeatureService;
import de.tum.cit.aet.artemis.core.service.course.CourseService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.CompetencyExerciseLinkService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.lecture.api.SlideApi;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfigHelper;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.dto.AuxiliaryRepositoryDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhasesDTO;
import de.tum.cit.aet.artemis.programming.dto.UpdateProgrammingExerciseBuildConfigDTO;
import de.tum.cit.aet.artemis.programming.dto.UpdateProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.AuxiliaryRepositoryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCreationUpdateService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseRepositoryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseValidationService;

/**
 * REST controller for updating complete programming exercise entities.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/programming/")
public class ProgrammingExerciseUpdateResource {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseUpdateResource.class);

    private static final String ENTITY_NAME = "programmingExercise";

    private final ModuleFeatureService moduleFeatureService;

    private final CourseService courseService;

    private final AuthorizationCheckService authCheckService;

    private final ExerciseService exerciseService;

    private final ProgrammingExerciseValidationService programmingExerciseValidationService;

    private final ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService;

    private final ProgrammingExerciseRepositoryService programmingExerciseRepositoryService;

    private final AuxiliaryRepositoryService auxiliaryRepositoryService;

    private final Optional<AthenaApi> athenaApi;

    private final Optional<SlideApi> slideApi;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final UserRepository userRepository;

    private final ExerciseVersionService exerciseVersionService;

    private final ParticipationRepository participationRepository;

    private final CompetencyExerciseLinkService competencyExerciseLinkService;

    public ProgrammingExerciseUpdateResource(ProgrammingExerciseRepository programmingExerciseRepository, UserRepository userRepository, AuthorizationCheckService authCheckService,
            CourseService courseService, ExerciseService exerciseService, ProgrammingExerciseValidationService programmingExerciseValidationService,
            ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService, ProgrammingExerciseRepositoryService programmingExerciseRepositoryService,
            AuxiliaryRepositoryService auxiliaryRepositoryService, Optional<AthenaApi> athenaApi, ModuleFeatureService moduleFeatureService, Optional<SlideApi> slideApi,
            ExerciseVersionService exerciseVersionService, ParticipationRepository participationRepository, CompetencyExerciseLinkService competencyExerciseLinkService) {
        this.programmingExerciseValidationService = programmingExerciseValidationService;
        this.programmingExerciseCreationUpdateService = programmingExerciseCreationUpdateService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.userRepository = userRepository;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.exerciseService = exerciseService;
        this.programmingExerciseRepositoryService = programmingExerciseRepositoryService;
        this.auxiliaryRepositoryService = auxiliaryRepositoryService;
        this.athenaApi = athenaApi;
        this.moduleFeatureService = moduleFeatureService;
        this.slideApi = slideApi;
        this.exerciseVersionService = exerciseVersionService;
        this.participationRepository = participationRepository;
        this.competencyExerciseLinkService = competencyExerciseLinkService;
    }

    /**
     * PUT /programming-exercises : Updates an existing programming exercise.
     *
     * @param updateDTO        the DTO containing the updated programming exercise data
     * @param notificationText to notify the student group about the update on the programming exercise
     * @return the ResponseEntity with status 200 (OK) and with body the updated ProgrammingExercise, or with status 400 (Bad Request) if the updated ProgrammingExercise
     *         is not valid, or with status 500 (Internal Server Error) if the updated ProgrammingExercise couldn't be saved to the database
     */
    @PutMapping("programming-exercises")
    @EnforceAtLeastEditor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<ProgrammingExercise> updateProgrammingExercise(@RequestBody UpdateProgrammingExerciseDTO updateDTO,
            @RequestParam(value = "notificationText", required = false) String notificationText) throws JsonProcessingException {
        log.debug("REST request to update ProgrammingExercise with id: {}", updateDTO.id());

        if (updateDTO.id() == null || updateDTO.id() == 0) {
            throw new BadRequestAlertException("Programming exercise cannot have an empty id when updating", ENTITY_NAME, "noProgrammingExerciseId");
        }

        // Validate that either courseId or exerciseGroupId is set, but not both
        if (updateDTO.courseId() == null && updateDTO.exerciseGroupId() == null) {
            throw new BadRequestAlertException("Either courseId or exerciseGroupId must be set", ENTITY_NAME, "noCourseOrExerciseGroup");
        }
        if (updateDTO.courseId() != null && updateDTO.exerciseGroupId() != null) {
            throw new BadRequestAlertException("A programming exercise can only be associated with either a course or an exercise group, not both", ENTITY_NAME,
                    "bothCourseAndExerciseGroupSet");
        }

        // Load the existing exercise from the database with all necessary associations
        var programmingExerciseBeforeUpdate = programmingExerciseRepository.findForUpdateByIdElseThrow(updateDTO.id());

        // Validate that courseId or exerciseGroupId hasn't changed
        // For course exercises: courseId must match
        // For exam exercises: exerciseGroupId must match
        Long existingCourseId = programmingExerciseBeforeUpdate.isCourseExercise() && programmingExerciseBeforeUpdate.getCourseViaExerciseGroupOrCourseMember() != null
                ? programmingExerciseBeforeUpdate.getCourseViaExerciseGroupOrCourseMember().getId()
                : null;
        Long existingExerciseGroupId = programmingExerciseBeforeUpdate.getExerciseGroup() != null ? programmingExerciseBeforeUpdate.getExerciseGroup().getId() : null;
        if (!Objects.equals(existingCourseId, updateDTO.courseId()) || !Objects.equals(existingExerciseGroupId, updateDTO.exerciseGroupId())) {
            throw new ConflictException("The course or exercise group cannot be changed", ENTITY_NAME, "courseOrExerciseGroupCannotChange");
        }

        // Save immutable field values BEFORE update() mutates the managed entity.
        // Hibernate L1 cache means any second lookup returns the same object, so we
        // must capture scalar values rather than relying on a separate entity reference.
        final String originalShortName = programmingExerciseBeforeUpdate.getShortName();
        final Boolean originalStaticCodeAnalysisEnabled = programmingExerciseBeforeUpdate.isStaticCodeAnalysisEnabled();
        final Long originalCourseId = programmingExerciseBeforeUpdate.getCourseViaExerciseGroupOrCourseMember() != null
                ? programmingExerciseBeforeUpdate.getCourseViaExerciseGroupOrCourseMember().getId()
                : null;
        final String originalAssignmentCheckoutPath = programmingExerciseBeforeUpdate.getBuildConfig() != null
                ? programmingExerciseBeforeUpdate.getBuildConfig().getAssignmentCheckoutPath()
                : null;
        final String originalSolutionCheckoutPath = programmingExerciseBeforeUpdate.getBuildConfig() != null
                ? programmingExerciseBeforeUpdate.getBuildConfig().getSolutionCheckoutPath()
                : null;
        final String originalTestCheckoutPath = programmingExerciseBeforeUpdate.getBuildConfig() != null ? programmingExerciseBeforeUpdate.getBuildConfig().getTestCheckoutPath()
                : null;
        final String originalBranch = programmingExerciseBeforeUpdate.getBuildConfig() != null ? programmingExerciseBeforeUpdate.getBuildConfig().getBranch() : null;
        final String originalFeedbackSuggestionModule = programmingExerciseBeforeUpdate.getFeedbackSuggestionModule();
        final ZonedDateTime originalDueDate = programmingExerciseBeforeUpdate.getDueDate();
        final ZonedDateTime originalReleaseDate = programmingExerciseBeforeUpdate.getReleaseDate();
        final ZonedDateTime originalAssessmentDueDate = programmingExerciseBeforeUpdate.getAssessmentDueDate();
        final String originalProblemStatement = programmingExerciseBeforeUpdate.getProblemStatement();
        final String originalBuildPlanConfiguration = programmingExerciseBeforeUpdate.getBuildConfig() != null
                ? programmingExerciseBeforeUpdate.getBuildConfig().getBuildPlanConfiguration()
                : null;
        final Double originalMaxPoints = programmingExerciseBeforeUpdate.getMaxPoints();
        final Double originalBonusPoints = programmingExerciseBeforeUpdate.getBonusPoints();
        // Save auxiliary repos before update() overwrites them on the same entity (L1 cache)
        final List<AuxiliaryRepository> originalAuxRepos = programmingExerciseBeforeUpdate.getAuxiliaryRepositories() != null
                ? new ArrayList<>(programmingExerciseBeforeUpdate.getAuxiliaryRepositories())
                : new ArrayList<>();
        // Capture original competency IDs before update() mutates the entity (L1 cache)
        final Set<Long> originalCompetencyIds = programmingExerciseBeforeUpdate.getCompetencyLinks().stream().map(link -> link.getCompetency().getId()).collect(Collectors.toSet());

        // Update the existing exercise with DTO values
        ProgrammingExercise updatedProgrammingExercise = update(updateDTO, programmingExerciseBeforeUpdate);

        // Validate the updated exercise
        updatedProgrammingExercise.validateGeneralSettings();
        updatedProgrammingExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);
        programmingExerciseValidationService.validateStaticCodeAnalysisSettings(updatedProgrammingExercise);

        // Fetch course from database to make sure client didn't change groups
        var user = userRepository.getUserWithGroupsAndAuthorities();
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(updatedProgrammingExercise);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, user);

        programmingExerciseValidationService.checkProgrammingExerciseForError(updatedProgrammingExercise);
        // Validate plagiarism detection config
        PlagiarismDetectionConfigHelper.validatePlagiarismDetectionConfigOrThrow(updatedProgrammingExercise, ENTITY_NAME);

        // Validate immutable fields haven't changed
        if (!Objects.equals(originalShortName, updateDTO.shortName())) {
            throw new BadRequestAlertException("The programming exercise short name cannot be changed", ENTITY_NAME, "shortNameCannotChange");
        }
        if (!Objects.equals(originalStaticCodeAnalysisEnabled, updateDTO.staticCodeAnalysisEnabled())) {
            throw new BadRequestAlertException("Static code analysis enabled flag must not be changed", ENTITY_NAME, "staticCodeAnalysisCannotChange");
        }
        // Check if Theia is enabled
        if (moduleFeatureService.isTheiaEnabled()) {
            // Require 1 / 3 participation modes to be enabled
            if (!Boolean.TRUE.equals(updateDTO.allowOnlineEditor()) && !Boolean.TRUE.equals(updateDTO.allowOfflineIde()) && !updateDTO.allowOnlineIde()) {
                throw new BadRequestAlertException("You need to allow at least one participation mode, the online editor, the offline IDE, or the online IDE", ENTITY_NAME,
                        "noParticipationModeAllowed");
            }
        }
        else {
            // Require 1 / 2 participation modes to be enabled
            if (!Boolean.TRUE.equals(updateDTO.allowOnlineEditor()) && !Boolean.TRUE.equals(updateDTO.allowOfflineIde())) {
                throw new BadRequestAlertException("You need to allow at least one participation mode, the online editor or the offline IDE", ENTITY_NAME,
                        "noParticipationModeAllowed");
            }
        }

        // Verify that the checkout directories have not been changed
        var updatedBuildConfig = updatedProgrammingExercise.getBuildConfig();
        if (!Objects.equals(originalAssignmentCheckoutPath, updatedBuildConfig != null ? updatedBuildConfig.getAssignmentCheckoutPath() : null)
                || !Objects.equals(originalSolutionCheckoutPath, updatedBuildConfig != null ? updatedBuildConfig.getSolutionCheckoutPath() : null)
                || !Objects.equals(originalTestCheckoutPath, updatedBuildConfig != null ? updatedBuildConfig.getTestCheckoutPath() : null)) {
            throw new BadRequestAlertException("The custom checkout paths cannot be changed!", ENTITY_NAME, "checkoutDirectoriesChanged");
        }

        // Verify that the programming language supports the selected network access option
        programmingExerciseValidationService.validateDockerFlags(updatedProgrammingExercise);

        // Verify that a theia image is provided when the online IDE is enabled
        if (updatedProgrammingExercise.isAllowOnlineIde() && updatedProgrammingExercise.getBuildConfig().getTheiaImage() == null) {
            throw new BadRequestAlertException("You need to provide a Theia image when the online IDE is enabled", ENTITY_NAME, "noTheiaImageProvided");
        }

        // Forbid changing the course the exercise belongs to
        if (!Objects.equals(originalCourseId, updatedProgrammingExercise.getCourseViaExerciseGroupOrCourseMember().getId())) {
            throw new ConflictException("Exercise course id does not match the stored course id", ENTITY_NAME, "cannotChangeCourseId");
        }

        // Note: conversion between exam/course exercise is already validated above (lines 148-154)
        // by comparing courseId and exerciseGroupId before the entity is mutated.

        // Check that only allowed Athena modules are used
        athenaApi.ifPresentOrElse(api -> api.checkHasAccessToAthenaModule(updatedProgrammingExercise, course, ENTITY_NAME),
                () -> updatedProgrammingExercise.setFeedbackSuggestionModule(null));
        // Changing Athena module after the due date has passed is not allowed
        // Use a proxy exercise with the old module for comparison since update() mutates the original
        ProgrammingExercise exerciseWithOldModule = new ProgrammingExercise();
        exerciseWithOldModule.setFeedbackSuggestionModule(originalFeedbackSuggestionModule);
        exerciseWithOldModule.setDueDate(originalDueDate);
        athenaApi.ifPresent(api -> api.checkValidAthenaModuleChange(exerciseWithOldModule, updatedProgrammingExercise, ENTITY_NAME));

        // Ignore changes to the default branch - preserve the original
        if (updatedProgrammingExercise.getBuildConfig() != null) {
            updatedProgrammingExercise.getBuildConfig().setBranch(originalBranch);
        }

        if (updatedProgrammingExercise.getAuxiliaryRepositories() == null) {
            updatedProgrammingExercise.setAuxiliaryRepositories(new ArrayList<>());
        }

        // Create a proxy with the original aux repos for comparison (L1 cache means
        // programmingExerciseBeforeUpdate is already mutated by update())
        ProgrammingExercise exerciseWithOriginalAuxRepos = new ProgrammingExercise();
        exerciseWithOriginalAuxRepos.setId(updatedProgrammingExercise.getId());
        exerciseWithOriginalAuxRepos.setProgrammingLanguage(updatedProgrammingExercise.getProgrammingLanguage());
        exerciseWithOriginalAuxRepos.setAuxiliaryRepositories(originalAuxRepos);

        // Update the auxiliary repositories in the DB and ProgrammingExercise instance
        auxiliaryRepositoryService.handleAuxiliaryRepositoriesWhenUpdatingExercises(exerciseWithOriginalAuxRepos, updatedProgrammingExercise);

        // Update the auxiliary repositories in the VCS
        programmingExerciseRepositoryService.handleAuxiliaryRepositoriesWhenUpdatingExercises(exerciseWithOriginalAuxRepos, updatedProgrammingExercise);

        if (updatedProgrammingExercise.getBonusPoints() == null) {
            updatedProgrammingExercise.setBonusPoints(0.0);
        }

        // Only save after checking for errors
        ProgrammingExercise savedProgrammingExercise = programmingExerciseCreationUpdateService.updateProgrammingExercise(updatedProgrammingExercise, notificationText,
                originalCompetencyIds, originalBuildPlanConfiguration, originalReleaseDate, originalAssessmentDueDate, originalProblemStatement);

        exerciseService.logUpdate(updatedProgrammingExercise, updatedProgrammingExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        exerciseService.updatePointsInRelatedParticipantScores(originalMaxPoints, originalBonusPoints, updatedProgrammingExercise);
        participationRepository.removeIndividualDueDatesIfBeforeDueDate(savedProgrammingExercise, originalDueDate);
        slideApi.ifPresent(api -> api.handleDueDateChange(originalDueDate, updatedProgrammingExercise));
        exerciseVersionService.createExerciseVersion(savedProgrammingExercise, user);
        return ResponseEntity.ok(savedProgrammingExercise);
    }

    /**
     * Updates the existing ProgrammingExercise entity with values from the DTO.
     * This includes updating competency links using the proper mechanism.
     *
     * @param dto      the DTO containing updated values
     * @param exercise the existing exercise entity to update
     * @return the updated exercise entity
     */
    private ProgrammingExercise update(UpdateProgrammingExerciseDTO dto, ProgrammingExercise exercise) {
        if (dto == null) {
            throw new BadRequestAlertException("No programming exercise was provided.", ENTITY_NAME, "isNull");
        }

        // Update base exercise fields
        exercise.setTitle(dto.title());
        exercise.validateTitle();
        exercise.setShortName(dto.shortName());

        String newProblemStatement = dto.problemStatement() == null ? "" : dto.problemStatement();
        exercise.setProblemStatement(newProblemStatement);

        exercise.setChannelName(dto.channelName());
        exercise.setCategories(dto.categories());
        exercise.setDifficulty(dto.difficulty());

        exercise.setMaxPoints(dto.maxPoints());
        exercise.setBonusPoints(dto.bonusPoints());
        exercise.setIncludedInOverallScore(dto.includedInOverallScore());

        exercise.setReleaseDate(dto.releaseDate());
        exercise.setStartDate(dto.startDate());
        exercise.setDueDate(dto.dueDate());
        exercise.setAssessmentDueDate(dto.assessmentDueDate());
        exercise.setExampleSolutionPublicationDate(dto.exampleSolutionPublicationDate());

        // Only set boolean values if they are explicitly provided (not null)
        if (dto.allowComplaintsForAutomaticAssessments() != null) {
            exercise.setAllowComplaintsForAutomaticAssessments(dto.allowComplaintsForAutomaticAssessments());
        }
        if (dto.allowFeedbackRequests() != null) {
            exercise.setAllowFeedbackRequests(dto.allowFeedbackRequests());
        }
        if (dto.presentationScoreEnabled() != null) {
            exercise.setPresentationScoreEnabled(dto.presentationScoreEnabled());
        }
        if (dto.secondCorrectionEnabled() != null) {
            exercise.setSecondCorrectionEnabled(dto.secondCorrectionEnabled());
        }

        exercise.setFeedbackSuggestionModule(dto.feedbackSuggestionModule());
        exercise.setGradingInstructions(dto.gradingInstructions());

        // Update programming exercise specific fields
        if (dto.allowOnlineEditor() != null) {
            exercise.setAllowOnlineEditor(dto.allowOnlineEditor());
        }
        if (dto.allowOfflineIde() != null) {
            exercise.setAllowOfflineIde(dto.allowOfflineIde());
        }
        exercise.setAllowOnlineIde(dto.allowOnlineIde());

        if (dto.maxStaticCodeAnalysisPenalty() != null) {
            exercise.setMaxStaticCodeAnalysisPenalty(dto.maxStaticCodeAnalysisPenalty());
        }

        exercise.setShowTestNamesToStudents(dto.showTestNamesToStudents());
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(dto.buildAndTestStudentSubmissionsAfterDueDate());

        if (dto.testCasesChanged() != null) {
            exercise.setTestCasesChanged(dto.testCasesChanged());
        }

        exercise.setSubmissionPolicy(dto.submissionPolicy());
        exercise.setProjectType(dto.projectType());
        exercise.setReleaseTestsWithExampleSolution(dto.releaseTestsWithExampleSolution());

        // Update auxiliary repositories
        if (dto.auxiliaryRepositories() != null) {
            List<AuxiliaryRepository> auxRepos = dto.auxiliaryRepositories().stream().map(AuxiliaryRepositoryDTO::toEntity).toList();
            exercise.setAuxiliaryRepositories(new ArrayList<>(auxRepos));
        }

        // Update build config
        updateBuildConfig(dto.buildConfig(), exercise.getBuildConfig());

        // Update grading criteria
        updateGradingCriteria(dto, exercise);

        // Update competency links using the proper mechanism
        competencyExerciseLinkService.updateCompetencyLinks(dto, exercise);

        return exercise;
    }

    /**
     * Updates the build config entity with values from the DTO.
     *
     * @param dto         the DTO containing updated build config values
     * @param buildConfig the existing build config entity to update
     */
    private void updateBuildConfig(UpdateProgrammingExerciseBuildConfigDTO dto, ProgrammingExerciseBuildConfig buildConfig) {
        if (dto == null || buildConfig == null) {
            return;
        }

        if (dto.sequentialTestRuns() != null) {
            buildConfig.setSequentialTestRuns(dto.sequentialTestRuns());
        }
        // Note: branch is preserved from original (immutable during update)
        if (dto.buildPlanConfiguration() != null && BuildPlanPhasesDTO.isInPhasesFormat(dto.buildPlanConfiguration())) {
            buildConfig.setBuildPlanConfiguration(dto.buildPlanConfiguration());
            buildConfig.setBuildScript(null);
        }
        else {
            if (dto.buildPlanConfiguration() != null) {
                buildConfig.setBuildPlanConfiguration(dto.buildPlanConfiguration());
            }
            if (dto.buildScript() != null) {
                buildConfig.setBuildScript(dto.buildScript());
            }
        }
        buildConfig.setCheckoutSolutionRepository(dto.checkoutSolutionRepository());
        buildConfig.setTestCheckoutPath(dto.testCheckoutPath());
        buildConfig.setAssignmentCheckoutPath(dto.assignmentCheckoutPath());
        buildConfig.setSolutionCheckoutPath(dto.solutionCheckoutPath());
        buildConfig.setTimeoutSeconds(dto.timeoutSeconds());
        buildConfig.setDockerFlags(dto.dockerFlags());
        buildConfig.setTheiaImage(dto.theiaImage());
        buildConfig.setAllowBranching(dto.allowBranching());
        buildConfig.setBranchRegex(dto.branchRegex());
    }

    /**
     * Updates grading criteria from the DTO.
     *
     * @param dto      the DTO containing grading criteria
     * @param exercise the exercise to update
     */
    private void updateGradingCriteria(UpdateProgrammingExerciseDTO dto, ProgrammingExercise exercise) {
        if (dto.gradingCriteria() == null || dto.gradingCriteria().isEmpty()) {
            Set<GradingCriterion> criteria = exercise.ensureGradingCriteriaSet();
            criteria.clear();
            return;
        }

        Set<GradingCriterion> managedCriteria = exercise.ensureGradingCriteriaSet();

        // Preserve existing criteria by matching on ID to avoid dangling Feedback.gradingInstruction references
        Map<Long, GradingCriterion> existingById = managedCriteria.stream().filter(gc -> gc.getId() != null)
                .collect(Collectors.toMap(GradingCriterion::getId, gc -> gc, (a, b) -> a));

        Set<GradingCriterion> updated = dto.gradingCriteria().stream().map(gcDto -> {
            GradingCriterion criterion = (gcDto.id() != null) ? existingById.get(gcDto.id()) : null;
            if (criterion == null) {
                criterion = gcDto.toEntity();
                criterion.setExercise(exercise);
            }
            else {
                gcDto.applyTo(criterion);
            }
            return criterion;
        }).collect(Collectors.toSet());

        managedCriteria.clear();
        managedCriteria.addAll(updated);
    }

    /**
     * PUT /programming-exercises/{exerciseId}/re-evaluate : Re-evaluates and updates an existing ProgrammingExercise.
     *
     * @param exerciseId                                  of the exercise
     * @param updateDTO                                   the DTO containing the ProgrammingExercise data to re-evaluate and update
     * @param deleteFeedbackAfterGradingInstructionUpdate boolean flag that indicates whether the associated feedback should be deleted or not
     * @return the ResponseEntity with status 200 (OK) and with body the updated ProgrammingExercise, or with status 400 (Bad Request) if the ProgrammingExercise is not valid,
     *         or with status 409 (Conflict) if given exerciseId is not same as in the object of the request body, or with status 500 (Internal Server Error) if the
     *         ProgrammingExercise
     *         couldn't be updated
     */
    @PutMapping("programming-exercises/{exerciseId}/re-evaluate")
    @EnforceAtLeastEditor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<ProgrammingExercise> reEvaluateAndUpdateProgrammingExercise(@PathVariable long exerciseId, @RequestBody UpdateProgrammingExerciseDTO updateDTO,
            @RequestParam(value = "deleteFeedback", required = false) Boolean deleteFeedbackAfterGradingInstructionUpdate) throws JsonProcessingException {
        log.debug("REST request to re-evaluate ProgrammingExercise with id: {}", updateDTO.id());

        // Load the exercise with all associations needed by update() and reEvaluateExercise()
        var programmingExercise = programmingExerciseRepository.findForUpdateByIdElseThrow(exerciseId);

        if (updateDTO.id() == null || exerciseId != updateDTO.id()) {
            throw new ConflictException("Exercise id in path does not match id in request body", ENTITY_NAME, "idMismatch");
        }

        // Fetch course from database to make sure client didn't change groups
        var user = userRepository.getUserWithGroupsAndAuthorities();
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(programmingExercise);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, user);

        // Capture ALL original values BEFORE update() mutates the entity via L1 cache.
        // These are needed for change detection in the service layer (notifications, build plans, etc.)
        final Double originalMaxPoints = programmingExercise.getMaxPoints();
        final Double originalBonusPoints = programmingExercise.getBonusPoints();
        final ZonedDateTime originalDueDate = programmingExercise.getDueDate();
        final ZonedDateTime originalReleaseDate = programmingExercise.getReleaseDate();
        final ZonedDateTime originalAssessmentDueDate = programmingExercise.getAssessmentDueDate();
        final String originalProblemStatement = programmingExercise.getProblemStatement();
        final String originalBuildPlanConfiguration = programmingExercise.getBuildConfig() != null ? programmingExercise.getBuildConfig().getBuildPlanConfiguration() : null;
        final Set<Long> originalCompetencyIds = programmingExercise.getCompetencyLinks().stream().map(link -> link.getCompetency().getId()).collect(Collectors.toSet());

        // Apply DTO changes BEFORE re-evaluation so that updated grading criteria take effect.
        update(updateDTO, programmingExercise);

        exerciseService.reEvaluateExercise(programmingExercise, deleteFeedbackAfterGradingInstructionUpdate);

        // Call the service directly with the captured originals instead of re-entering the update path
        // (which would re-capture stale "originals" from the already-mutated L1 cache entity).
        ProgrammingExercise savedExercise = programmingExerciseCreationUpdateService.updateProgrammingExercise(programmingExercise, null, originalCompetencyIds,
                originalBuildPlanConfiguration, originalReleaseDate, originalAssessmentDueDate, originalProblemStatement);

        // Apply all post-save side effects that the normal update path performs
        exerciseService.logUpdate(savedExercise, savedExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        exerciseService.updatePointsInRelatedParticipantScores(originalMaxPoints, originalBonusPoints, savedExercise);
        participationRepository.removeIndividualDueDatesIfBeforeDueDate(savedExercise, originalDueDate);
        slideApi.ifPresent(api -> api.handleDueDateChange(originalDueDate, savedExercise));
        exerciseVersionService.createExerciseVersion(savedExercise, user);

        return ResponseEntity.ok(savedExercise);
    }
}
