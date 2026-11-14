package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.service.GradingCriteriaMapper;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.service.ModuleFeatureService;
import de.tum.cit.aet.artemis.course.service.CourseService;
import de.tum.cit.aet.artemis.exercise.service.CompetencyExerciseLinkService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVariantGroupService;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfigHelper;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.dto.AuxiliaryRepositoryDTO;
import de.tum.cit.aet.artemis.programming.dto.UpdateProgrammingExerciseBuildConfigDTO;
import de.tum.cit.aet.artemis.programming.dto.UpdateProgrammingExerciseDTO;

/**
 * Applies an update request to a programming exercise and validates the result. The update and the re-evaluation of an exercise share it, so that both accept and refuse the
 * same requests.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class ProgrammingExerciseUpdateMappingService {

    private static final String ENTITY_NAME = "programmingExercise";

    private final ModuleFeatureService moduleFeatureService;

    private final CourseService courseService;

    private final ProgrammingExerciseValidationService programmingExerciseValidationService;

    private final CompetencyExerciseLinkService competencyExerciseLinkService;

    private final ExerciseVariantGroupService exerciseVariantGroupService;

    public ProgrammingExerciseUpdateMappingService(ModuleFeatureService moduleFeatureService, CourseService courseService,
            ProgrammingExerciseValidationService programmingExerciseValidationService, CompetencyExerciseLinkService competencyExerciseLinkService,
            ExerciseVariantGroupService exerciseVariantGroupService) {
        this.moduleFeatureService = moduleFeatureService;
        this.courseService = courseService;
        this.programmingExerciseValidationService = programmingExerciseValidationService;
        this.competencyExerciseLinkService = competencyExerciseLinkService;
        this.exerciseVariantGroupService = exerciseVariantGroupService;
    }

    /**
     * The settings of a programming exercise that cannot be changed by an update, as they were before the update was applied.
     *
     * @param shortName              the short name
     * @param staticCodeAnalysis     whether static code analysis was enabled
     * @param assignmentCheckoutPath the checkout path of the assignment repository
     * @param solutionCheckoutPath   the checkout path of the solution repository
     * @param testCheckoutPath       the checkout path of the test repository
     * @param courseId               the id of the course of the exercise
     */
    public record OriginalSettings(String shortName, Boolean staticCodeAnalysis, String assignmentCheckoutPath, String solutionCheckoutPath, String testCheckoutPath,
            Long courseId) {

        /**
         * Reads the settings of an exercise that an update cannot change.
         *
         * @param exercise    the exercise before the update is applied
         * @param buildConfig the build configuration of the exercise before the update is applied
         * @return the settings
         */
        public static OriginalSettings of(ProgrammingExercise exercise, ProgrammingExerciseBuildConfig buildConfig) {
            return new OriginalSettings(exercise.getShortName(), exercise.isStaticCodeAnalysisEnabled(), buildConfig.getAssignmentCheckoutPath(),
                    buildConfig.getSolutionCheckoutPath(), buildConfig.getTestCheckoutPath(),
                    exercise.getCourseViaExerciseGroupOrCourseMember() != null ? exercise.getCourseViaExerciseGroupOrCourseMember().getId() : null);
        }
    }

    /**
     * Validates an exercise after the update was applied to it and before it is saved. Shared by the update and the re-evaluation, so that both refuse invalid exercises and
     * changes of the settings that cannot be changed.
     *
     * @param updateDTO                  the update that was applied
     * @param updatedProgrammingExercise the exercise with the update applied
     * @param buildConfig                the build configuration with the update applied
     * @param original                   the settings that cannot be changed, as they were before the update
     * @param checkReferences            whether the references to the template and solution repositories and build plans are checked as well, which the re-evaluation does not
     *                                       touch
     */
    public void validateUpdatedExercise(UpdateProgrammingExerciseDTO updateDTO, ProgrammingExercise updatedProgrammingExercise, ProgrammingExerciseBuildConfig buildConfig,
            OriginalSettings original, boolean checkReferences) {
        // Validate the updated exercise
        programmingExerciseValidationService.validateBuildConfigSize(buildConfig);
        programmingExerciseValidationService.validateDockerImageRegistry(buildConfig);
        updatedProgrammingExercise.validateGeneralSettings();
        updatedProgrammingExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);
        programmingExerciseValidationService.validateStaticCodeAnalysisSettings(updatedProgrammingExercise, buildConfig);

        // Initialize the course of the entity reloaded under the mutation lease.
        courseService.retrieveCourseOfLoadedExercise(updatedProgrammingExercise);

        programmingExerciseValidationService.validateBuildPhaseNames(buildConfig);
        programmingExerciseValidationService.validateAutomaticAssessmentTestWeights(updatedProgrammingExercise);
        if (checkReferences) {
            programmingExerciseValidationService.checkProgrammingExerciseForError(updatedProgrammingExercise, buildConfig);
        }
        // Validate plagiarism detection config
        PlagiarismDetectionConfigHelper.validatePlagiarismDetectionConfigOrThrow(updatedProgrammingExercise, ENTITY_NAME);

        // Validate immutable fields haven't changed
        if (!Objects.equals(original.shortName(), updateDTO.shortName())) {
            throw new BadRequestAlertException("The programming exercise short name cannot be changed", ENTITY_NAME, "shortNameCannotChange");
        }
        if (!Objects.equals(original.staticCodeAnalysis(), updateDTO.staticCodeAnalysisEnabled())) {
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
        var updatedBuildConfig = buildConfig;
        if (!Objects.equals(original.assignmentCheckoutPath(), updatedBuildConfig != null ? updatedBuildConfig.getAssignmentCheckoutPath() : null)
                || !Objects.equals(original.solutionCheckoutPath(), updatedBuildConfig != null ? updatedBuildConfig.getSolutionCheckoutPath() : null)
                || !Objects.equals(original.testCheckoutPath(), updatedBuildConfig != null ? updatedBuildConfig.getTestCheckoutPath() : null)) {
            throw new BadRequestAlertException("The custom checkout paths cannot be changed!", ENTITY_NAME, "checkoutDirectoriesChanged");
        }

        // Verify that the programming language supports the selected network access option
        programmingExerciseValidationService.validateDockerFlags(buildConfig);

        // Verify that a theia image is provided when the online IDE is enabled
        if (updatedProgrammingExercise.isAllowOnlineIde() && buildConfig.getTheiaImage() == null) {
            throw new BadRequestAlertException("You need to provide a Theia image when the online IDE is enabled", ENTITY_NAME, "noTheiaImageProvided");
        }

        // Forbid changing the course the exercise belongs to
        if (!Objects.equals(original.courseId(), updatedProgrammingExercise.getCourseViaExerciseGroupOrCourseMember().getId())) {
            throw new ConflictException("Exercise course id does not match the stored course id", ENTITY_NAME, "cannotChangeCourseId");
        }
    }

    /**
     * Refuses a request that names another course or exercise group than the exercise belongs to.
     *
     * @param updateDTO           the update request
     * @param programmingExercise the exercise as it is stored
     */
    public void validateCourseAndExerciseGroupUnchanged(UpdateProgrammingExerciseDTO updateDTO, ProgrammingExercise programmingExercise) {
        Long existingCourseId = programmingExercise.isCourseExercise() && programmingExercise.getCourseViaExerciseGroupOrCourseMember() != null
                ? programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId()
                : null;
        Long existingExerciseGroupId = programmingExercise.getExerciseGroup() != null ? programmingExercise.getExerciseGroup().getId() : null;
        if (!Objects.equals(existingCourseId, updateDTO.courseId()) || !Objects.equals(existingExerciseGroupId, updateDTO.exerciseGroupId())) {
            throw new ConflictException("The course or exercise group cannot be changed", ENTITY_NAME, "courseOrExerciseGroupCannotChange");
        }
    }

    /**
     * Updates the existing ProgrammingExercise entity with values from the DTO.
     * This includes updating competency links using the proper mechanism and restoring the timeline of an owning
     * variant group, so every caller persists a member with the group's dates.
     *
     * @param dto                   the DTO containing updated values
     * @param exercise              the existing exercise entity to update
     * @param buildConfig           the stored build configuration of that exercise
     * @param hyperionCompetencyIds competency IDs whose new links were suggested by Hyperion
     * @return the updated exercise entity
     */
    public ProgrammingExercise apply(UpdateProgrammingExerciseDTO dto, ProgrammingExercise exercise, ProgrammingExerciseBuildConfig buildConfig, Set<Long> hyperionCompetencyIds) {
        if (dto == null) {
            throw new BadRequestAlertException("No programming exercise was provided.", ENTITY_NAME, "isNull");
        }

        // Update base exercise fields
        exercise.setTitle(dto.title());
        exercise.validateTitle();
        exercise.setShortName(dto.shortName());

        // The problem statement is owned by the collaborative (Yjs) editor and its dedicated PATCH endpoint, not by this metadata
        // update. A blank or absent value here means the editor has not finished its initial sync yet (e.g. the user saved a
        // category change on a slow connection before the statement loaded), so we keep the persisted statement instead of wiping
        // it. See issue #13046.
        if (dto.problemStatement() != null && !dto.problemStatement().isBlank()) {
            exercise.setProblemStatement(dto.problemStatement());
        }

        dto.applyMetadataTo(exercise);
        exercise.setAssessmentType(dto.assessmentType());
        exercise.setExampleSolutionPublicationDate(dto.exampleSolutionPublicationDate());

        // Only set boolean values if they are explicitly provided (not null)
        dto.applyAssessmentFlagsTo(exercise);

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
        if (exercise.isCourseExercise() && exercise.getDueDate() == null) {
            exercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        }

        if (dto.testCasesChanged() != null) {
            exercise.setTestCasesChanged(dto.testCasesChanged());
        }

        // The submission policy is not part of this update. Its dedicated endpoints add, change and remove it, and they also apply the effect of the change to the
        // participations, so a policy that the request omits or alters here stays as it is.
        exercise.setProjectType(dto.projectType());
        exercise.setReleaseTestsWithExampleSolution(dto.releaseTestsWithExampleSolution());

        // Update auxiliary repositories. Attached one by one rather than assigned as a collection: a repository
        // carries the key to its exercise, and one deserialized from the request body names none until it is attached,
        // so a flush between here and the auxiliary repository handling below would write that null over the exercise.
        if (dto.auxiliaryRepositories() != null) {
            List<AuxiliaryRepository> auxRepos = dto.auxiliaryRepositories().stream().map(AuxiliaryRepositoryDTO::toEntity).toList();
            exercise.setAuxiliaryRepositories(new LinkedHashSet<>());
            auxRepos.forEach(exercise::addAuxiliaryRepository);
        }

        // Update build config
        updateBuildConfig(dto.buildConfig(), buildConfig);

        // Update plagiarism detection config
        PlagiarismDetectionConfigHelper.applyToExercise(exercise, dto.plagiarismDetectionConfig());

        // Update grading criteria
        updateGradingCriteria(dto, exercise);

        // Update competency links using the proper mechanism
        competencyExerciseLinkService.updateCompetencyLinks(dto, exercise, hyperionCompetencyIds);

        // A variant group owns its members' shared dates, so pin those back to the group. The build-and-test date stays
        // per exercise and is only re-derived from the shared due date. The dedicated timeline endpoint rejects group
        // members outright instead.
        exerciseVariantGroupService.applyOwningGroupTimeline(exercise);

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
        if (dto.buildPlanConfiguration() != null) {
            buildConfig.setBuildPlanConfiguration(dto.buildPlanConfiguration());
        }
        buildConfig.setBuildScript(null);
        buildConfig.setCheckoutSolutionRepository(dto.checkoutSolutionRepository());
        buildConfig.setTestCheckoutPath(dto.testCheckoutPath());
        buildConfig.setAssignmentCheckoutPath(dto.assignmentCheckoutPath());
        buildConfig.setSolutionCheckoutPath(dto.solutionCheckoutPath());
        buildConfig.setTimeoutSeconds(dto.timeoutSeconds());
        buildConfig.setDockerFlags(dto.dockerFlags());
        buildConfig.setTheiaImage(dto.theiaImage());
        buildConfig.setOnlineIdeExperienceLevel(dto.onlineIdeExperienceLevel());
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

        GradingCriteriaMapper.replace(dto.gradingCriteria(), exercise);
    }
}
