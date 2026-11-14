package de.tum.cit.aet.artemis.programming.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.allowedScopes.AgentScope;
import de.tum.cit.aet.artemis.core.security.allowedScopes.AllowedScopes;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.ManualConfig;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.service.CourseService;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.lecture.api.SlideApi;
import de.tum.cit.aet.artemis.localci.service.AutomaticAfterDueDateService;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseResponseDTO;
import de.tum.cit.aet.artemis.programming.dto.UpdateProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.AuxiliaryRepositoryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCreationUpdateService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseDraftGuardService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseMutationGuardService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseRepositoryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseUpdateMappingService;
import io.swagger.v3.oas.annotations.Operation;

/**
 * REST controller for updating complete programming exercise entities.
 */
@Profile(PROFILE_CORE)
@Lazy
@FeatureUsage(UserFeature.PROGRAMMING_AUTHORING)
@RestController
@RequestMapping("api/programming/")
public class ProgrammingExerciseUpdateResource {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseUpdateResource.class);

    private static final String ENTITY_NAME = "programmingExercise";

    private final CourseService courseService;

    private final AuthorizationCheckService authCheckService;

    private final ExerciseService exerciseService;

    private final ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService;

    private final ProgrammingExerciseRepositoryService programmingExerciseRepositoryService;

    private final AuxiliaryRepositoryService auxiliaryRepositoryService;

    private final Optional<SlideApi> slideApi;

    private final Optional<AutomaticAfterDueDateService> automaticAfterDueDateService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    private final UserRepository userRepository;

    private final ExerciseVersionService exerciseVersionService;

    private final ParticipationRepository participationRepository;

    private final ProgrammingExerciseMutationGuardService programmingExerciseMutationGuard;

    private final ProgrammingExerciseDraftGuardService programmingExerciseDraftGuard;

    private final ProgrammingExerciseUpdateMappingService programmingExerciseUpdateMappingService;

    public ProgrammingExerciseUpdateResource(ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository, UserRepository userRepository, AuthorizationCheckService authCheckService,
            CourseService courseService, ExerciseService exerciseService, ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService,
            ProgrammingExerciseRepositoryService programmingExerciseRepositoryService, AuxiliaryRepositoryService auxiliaryRepositoryService, Optional<SlideApi> slideApi,
            Optional<AutomaticAfterDueDateService> automaticAfterDueDateService, ExerciseVersionService exerciseVersionService, ParticipationRepository participationRepository,
            ProgrammingExerciseMutationGuardService programmingExerciseMutationGuard, ProgrammingExerciseDraftGuardService programmingExerciseDraftGuard,
            ProgrammingExerciseUpdateMappingService programmingExerciseUpdateMappingService) {
        this.programmingExerciseUpdateMappingService = programmingExerciseUpdateMappingService;
        this.programmingExerciseDraftGuard = programmingExerciseDraftGuard;
        this.programmingExerciseCreationUpdateService = programmingExerciseCreationUpdateService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
        this.userRepository = userRepository;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.exerciseService = exerciseService;
        this.programmingExerciseRepositoryService = programmingExerciseRepositoryService;
        this.auxiliaryRepositoryService = auxiliaryRepositoryService;
        this.slideApi = slideApi;
        this.automaticAfterDueDateService = automaticAfterDueDateService;
        this.exerciseVersionService = exerciseVersionService;
        this.participationRepository = participationRepository;
        this.programmingExerciseMutationGuard = programmingExerciseMutationGuard;
    }

    /**
     * PUT /programming-exercises : Updates an existing programming exercise.
     *
     * @param updateDTO             the DTO containing the updated programming exercise data
     * @param notificationText      to notify the student group about the update on the programming exercise
     * @param hyperionCompetencyIds IDs of links inferred through Hyperion's checklist
     * @return the ResponseEntity with status 200 (OK) and with body the updated ProgrammingExercise, or with status 400 (Bad Request) if the updated ProgrammingExercise
     *         is not valid, or with status 500 (Internal Server Error) if the updated ProgrammingExercise couldn't be saved to the database
     */
    @PutMapping("programming-exercises")
    @EnforceAtLeastStudent
    @ManualConfig
    @AllowedScopes(AgentScope.PROGRAMMING_DRAFT)
    @FeatureToggle(Feature.ProgrammingExercises)
    @Operation(summary = "Update a programming exercise", description = """
            Replaces the editable settings of an existing programming exercise, so send the whole exercise: omitted flags reset to false. \
            shortName, staticCodeAnalysisEnabled, the checkout paths and the course cannot be changed. A request with a personal access \
            token can change only an exercise that students cannot see yet (its releaseDate is in the future) and that has no student \
            participations, and the change must keep releaseDate in the future. Anything else can be changed only in the Artemis web interface.""")
    public ResponseEntity<ProgrammingExerciseResponseDTO> updateProgrammingExercise(@RequestBody UpdateProgrammingExerciseDTO updateDTO,
            @RequestParam(value = "notificationText", required = false) String notificationText,
            @RequestParam(name = "hyperionCompetencyId", required = false) Set<Long> hyperionCompetencyIds) {
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

        var authorizationExercise = programmingExerciseRepository.findForUpdateByIdElseThrow(updateDTO.id());
        programmingExerciseUpdateMappingService.validateCourseAndExerciseGroupUnchanged(updateDTO, authorizationExercise);
        var user = userRepository.getUserWithAuthorities();
        Course authorizationCourse = courseService.retrieveCourseOfLoadedExercise(authorizationExercise);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, authorizationCourse, user);
        programmingExerciseDraftGuard.requireDraftForTokenRequest(authorizationExercise, updateDTO.releaseDate(), updateDTO.startDate());

        try (var lease = programmingExerciseMutationGuard.claimExternalMutation(updateDTO.id())) {
            var programmingExerciseBeforeUpdate = lease.coordinated() ? programmingExerciseRepository.findForUpdateByIdElseThrow(updateDTO.id()) : authorizationExercise;
            programmingExerciseDraftGuard.requireDraftForTokenRequest(programmingExerciseBeforeUpdate, updateDTO.releaseDate(), updateDTO.startDate());
            var buildConfig = programmingExerciseBuildConfigRepository.getProgrammingExerciseBuildConfigElseThrow(updateDTO.id());
            programmingExerciseUpdateMappingService.validateCourseAndExerciseGroupUnchanged(updateDTO, programmingExerciseBeforeUpdate);

            // Save immutable field values BEFORE update() mutates the managed entity.
            // Hibernate L1 cache means any second lookup returns the same object, so we
            // must capture scalar values rather than relying on a separate entity reference.
            final String originalBranch = buildConfig.getBranch();
            final var originalSettings = ProgrammingExerciseUpdateMappingService.OriginalSettings.of(programmingExerciseBeforeUpdate, buildConfig);
            final ZonedDateTime originalDueDate = programmingExerciseBeforeUpdate.getDueDate();
            final ZonedDateTime originalReleaseDate = programmingExerciseBeforeUpdate.getReleaseDate();
            final ZonedDateTime originalAssessmentDueDate = programmingExerciseBeforeUpdate.getAssessmentDueDate();
            final String originalProblemStatement = programmingExerciseBeforeUpdate.getProblemStatement();
            final String originalBuildPlanConfiguration = buildConfig.getBuildPlanConfiguration();
            final Double originalMaxPoints = programmingExerciseBeforeUpdate.getMaxPoints();
            final Double originalBonusPoints = programmingExerciseBeforeUpdate.getBonusPoints();
            // Save auxiliary repos before update() overwrites them on the same entity (L1 cache)
            final List<AuxiliaryRepository> originalAuxRepos = programmingExerciseBeforeUpdate.getAuxiliaryRepositories() != null
                    ? new ArrayList<>(programmingExerciseBeforeUpdate.getAuxiliaryRepositories())
                    : new ArrayList<>();
            // Capture original competency IDs before update() mutates the entity (L1 cache)
            final Set<Long> originalCompetencyIds = programmingExerciseBeforeUpdate.getCompetencyLinks().stream().map(link -> link.getCompetency().getId())
                    .collect(Collectors.toSet());
            final Duration originalBuildAndTestOffset = automaticAfterDueDateService.map(service -> service.getOriginalBuildAndTestOffset(programmingExerciseBeforeUpdate))
                    .orElse(null);

            // Update the existing exercise with DTO values and pin variant members to the group's shared dates.
            ProgrammingExercise updatedProgrammingExercise = programmingExerciseUpdateMappingService.apply(updateDTO, programmingExerciseBeforeUpdate, buildConfig,
                    hyperionCompetencyIds == null ? Set.of() : hyperionCompetencyIds);

            // Validate the updated exercise
            programmingExerciseUpdateMappingService.validateUpdatedExercise(updateDTO, updatedProgrammingExercise, buildConfig, originalSettings, true);

            // Note: conversion between exam/course exercise is already rejected by updateProgrammingExercise, which compares the DTO's courseId and exerciseGroupId before the
            // entity is mutated.

            // Ignore changes to the default branch - preserve the original
            buildConfig.setBranch(originalBranch);

            programmingExerciseCreationUpdateService.prepareAndValidateTimelineForUpdate(updatedProgrammingExercise, buildConfig, originalBuildAndTestOffset);

            if (updatedProgrammingExercise.getAuxiliaryRepositories() == null) {
                updatedProgrammingExercise.setAuxiliaryRepositories(new LinkedHashSet<>());
            }

            // Create a proxy with the original aux repos for comparison (L1 cache means
            // programmingExerciseBeforeUpdate is already mutated by update())
            ProgrammingExercise exerciseWithOriginalAuxRepos = new ProgrammingExercise();
            exerciseWithOriginalAuxRepos.setId(updatedProgrammingExercise.getId());
            exerciseWithOriginalAuxRepos.setProgrammingLanguage(updatedProgrammingExercise.getProgrammingLanguage());
            exerciseWithOriginalAuxRepos.setAuxiliaryRepositories(new LinkedHashSet<>(originalAuxRepos));

            // Update the auxiliary repositories in the DB and ProgrammingExercise instance
            auxiliaryRepositoryService.handleAuxiliaryRepositoriesWhenUpdatingExercises(exerciseWithOriginalAuxRepos, updatedProgrammingExercise);

            // Update the auxiliary repositories in the VCS
            programmingExerciseRepositoryService.handleAuxiliaryRepositoriesWhenUpdatingExercises(exerciseWithOriginalAuxRepos, updatedProgrammingExercise);

            if (updatedProgrammingExercise.getBonusPoints() == null) {
                updatedProgrammingExercise.setBonusPoints(0.0);
            }

            // Only save after checking for errors
            ProgrammingExercise savedProgrammingExercise = programmingExerciseCreationUpdateService.updateProgrammingExercise(updatedProgrammingExercise, buildConfig,
                    notificationText, originalCompetencyIds, originalBuildPlanConfiguration, originalReleaseDate, originalAssessmentDueDate, originalBuildAndTestOffset,
                    originalProblemStatement);

            exerciseService.logUpdate(updatedProgrammingExercise, updatedProgrammingExercise.getCourseViaExerciseGroupOrCourseMember(), user);
            if (lease.coordinated()) {
                exerciseService.updatePointsInRelatedParticipantScoresSynchronously(originalMaxPoints, originalBonusPoints, updatedProgrammingExercise);
            }
            else {
                exerciseService.updatePointsInRelatedParticipantScores(originalMaxPoints, originalBonusPoints, updatedProgrammingExercise);
            }
            participationRepository.removeIndividualDueDatesIfBeforeDueDate(savedProgrammingExercise, originalDueDate);
            slideApi.ifPresent(api -> api.handleDueDateChange(originalDueDate, updatedProgrammingExercise));
            if (lease.coordinated()) {
                exerciseVersionService.createExerciseVersionSynchronously(savedProgrammingExercise, user);
            }
            else {
                exerciseVersionService.createExerciseVersion(savedProgrammingExercise, user);
            }
            return ResponseEntity.ok(ProgrammingExerciseResponseDTO.of(savedProgrammingExercise, buildConfig));
        }
    }

}
