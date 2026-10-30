package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.ASSIGNMENT_REPO_NAME;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.TEST_REPO_NAME;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.hyperion.api.HyperionExerciseMutationApi;
import de.tum.cit.aet.artemis.hyperion.api.dtos.ParticipationReservation;
import de.tum.cit.aet.artemis.localci.service.AutomaticAfterDueDateService;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationService;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPlanType;
import de.tum.cit.aet.artemis.programming.dto.SecurityFrameworkChoiceDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTaskRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.cit.aet.artemis.programming.service.security.SecurityFrameworkService;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ProgrammingExerciseImportService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseImportService.class);

    /** Everything that is not alphanumeric, which a short name may not contain. */
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-zA-Z0-9]");

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final Optional<ContinuousIntegrationTriggerService> continuousIntegrationTriggerService;

    private final ProgrammingExerciseValidationService programmingExerciseValidationService;

    private final ProgrammingExerciseBuildPlanService programmingExerciseBuildPlanService;

    private final ProgrammingExerciseCreationScheduleService programmingExerciseCreationScheduleService;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    private final ProgrammingExerciseImportBasicService programmingExerciseImportBasicService;

    private final ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    private final Optional<AutomaticAfterDueDateService> automaticAfterDueDateService;

    private final Optional<HyperionExerciseMutationApi> mutationApi;

    private final ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    private final SecurityFrameworkService securityFrameworkService;

    public ProgrammingExerciseImportService(Optional<ContinuousIntegrationService> continuousIntegrationService,
            Optional<ContinuousIntegrationTriggerService> continuousIntegrationTriggerService, ProgrammingExerciseValidationService programmingExerciseValidationService,
            ProgrammingExerciseBuildPlanService programmingExerciseBuildPlanService, ProgrammingExerciseCreationScheduleService programmingExerciseCreationScheduleService,
            ProgrammingExerciseTaskService programmingExerciseTaskService, ProgrammingExerciseImportBasicService programmingExerciseImportBasicService,
            ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            Optional<AutomaticAfterDueDateService> automaticAfterDueDateService, ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository,
            Optional<HyperionExerciseMutationApi> mutationApi, ProgrammingExerciseTaskRepository programmingExerciseTaskRepository,
            SecurityFrameworkService securityFrameworkService) {
        this.securityFrameworkService = securityFrameworkService;
        this.mutationApi = mutationApi;
        this.programmingExerciseTaskRepository = programmingExerciseTaskRepository;
        this.continuousIntegrationService = continuousIntegrationService;
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
        this.continuousIntegrationTriggerService = continuousIntegrationTriggerService;
        this.programmingExerciseValidationService = programmingExerciseValidationService;
        this.programmingExerciseBuildPlanService = programmingExerciseBuildPlanService;
        this.programmingExerciseCreationScheduleService = programmingExerciseCreationScheduleService;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
        this.programmingExerciseImportBasicService = programmingExerciseImportBasicService;
        this.programmingExerciseTestCaseRepository = programmingExerciseTestCaseRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.automaticAfterDueDateService = automaticAfterDueDateService;
    }

    /**
     * Imports all base build plans for an exercise. These include the template and the solution build plan, <b>not</b>
     * any participation plans!
     *
     * @param sourceExercise The source exercise which plans should get copied
     * @param newExercise    The new exercise to which all plans should get copied
     */
    public void importBuildPlans(final ProgrammingExercise sourceExercise, final ProgrammingExercise newExercise) {
        final var templateParticipation = newExercise.getTemplateParticipation();
        final var solutionParticipation = newExercise.getSolutionParticipation();
        final var targetExerciseProjectKey = newExercise.getProjectKey();

        // Clone all build plans, enable them and set up the initial participations, i.e. setting the correct repo URIs and
        // running the plan for the first time
        cloneAndEnableAllBuildPlans(sourceExercise, newExercise);

        updatePlanRepositoriesInBuildPlans(newExercise, targetExerciseProjectKey, sourceExercise.getTemplateRepositoryUri(), sourceExercise.getSolutionRepositoryUri(),
                sourceExercise.getTestRepositoryUri(), sourceExercise.getAuxiliaryRepositoriesForBuildPlan());

        ContinuousIntegrationTriggerService triggerService = continuousIntegrationTriggerService.orElseThrow();
        triggerService.triggerBuild(templateParticipation);
        triggerService.triggerBuild(solutionParticipation);
    }

    private void updatePlanRepositoriesInBuildPlans(ProgrammingExercise newExercise, String targetExerciseProjectKey, String oldExerciseRepoUri, String oldSolutionRepoUri,
            String oldTestRepoUri, List<AuxiliaryRepository> oldBuildPlanAuxiliaryRepositories) {
        String newExerciseBranch = programmingExerciseRepository.findBranchByExerciseId(newExercise.getId());

        // update 2 repositories for the BASE build plan --> adapt the triggers so that only the assignment repo (and not the tests' repo) will trigger the BASE build plan
        ContinuousIntegrationService continuousIntegration = continuousIntegrationService.orElseThrow();
        continuousIntegration.updatePlanRepository(targetExerciseProjectKey, newExercise.generateBuildPlanId(BuildPlanType.TEMPLATE), ASSIGNMENT_REPO_NAME,
                targetExerciseProjectKey, newExercise.getTemplateRepositoryUri(), oldExerciseRepoUri, newExerciseBranch);

        continuousIntegration.updatePlanRepository(targetExerciseProjectKey, newExercise.generateBuildPlanId(BuildPlanType.TEMPLATE), TEST_REPO_NAME, targetExerciseProjectKey,
                newExercise.getTestRepositoryUri(), oldTestRepoUri, newExerciseBranch);

        updateAuxiliaryRepositoriesForNewExercise(newExercise.getAuxiliaryRepositoriesForBuildPlan(), oldBuildPlanAuxiliaryRepositories, BuildPlanType.TEMPLATE,
                targetExerciseProjectKey, newExercise);

        // update 2 repositories for the SOLUTION build plan
        continuousIntegration.updatePlanRepository(targetExerciseProjectKey, newExercise.generateBuildPlanId(BuildPlanType.SOLUTION), ASSIGNMENT_REPO_NAME,
                targetExerciseProjectKey, newExercise.getSolutionRepositoryUri(), oldSolutionRepoUri, newExerciseBranch);
        continuousIntegration.updatePlanRepository(targetExerciseProjectKey, newExercise.generateBuildPlanId(BuildPlanType.SOLUTION), TEST_REPO_NAME, targetExerciseProjectKey,
                newExercise.getTestRepositoryUri(), oldTestRepoUri, newExerciseBranch);

        updateAuxiliaryRepositoriesForNewExercise(newExercise.getAuxiliaryRepositoriesForBuildPlan(), oldBuildPlanAuxiliaryRepositories, BuildPlanType.SOLUTION,
                targetExerciseProjectKey, newExercise);
    }

    private void updateAuxiliaryRepositoriesForNewExercise(List<AuxiliaryRepository> newRepositories, List<AuxiliaryRepository> oldRepositories, BuildPlanType buildPlanType,
            String targetExerciseProjectKey, ProgrammingExercise newExercise) {
        for (int i = 0; i < newRepositories.size(); i++) {
            AuxiliaryRepository newAuxiliaryRepository = newRepositories.get(i);
            AuxiliaryRepository oldAuxiliaryRepository = oldRepositories.get(i);
            String auxiliaryBranch = programmingExerciseRepository.findBranchByExerciseId(newExercise.getId());
            continuousIntegrationService.orElseThrow().updatePlanRepository(targetExerciseProjectKey, newExercise.generateBuildPlanId(buildPlanType),
                    newAuxiliaryRepository.getName(), targetExerciseProjectKey, newAuxiliaryRepository.getRepositoryUri(), oldAuxiliaryRepository.getRepositoryUri(),
                    auxiliaryBranch);
        }
    }

    private void cloneAndEnableAllBuildPlans(ProgrammingExercise sourceExercise, ProgrammingExercise newExercise) {
        final var templateParticipation = newExercise.getTemplateParticipation();
        final var solutionParticipation = newExercise.getSolutionParticipation();
        final var targetExerciseProjectKey = newExercise.getProjectKey();
        final var templatePlanName = BuildPlanType.TEMPLATE.getName();
        final var solutionPlanName = BuildPlanType.SOLUTION.getName();
        final var targetCourse = newExercise.getCourseViaExerciseGroupOrCourseMember();
        if (targetCourse == null) {
            throw new IllegalStateException("The course of programming exercise " + newExercise.getId() + " could not be resolved");
        }
        final var targetName = targetCourse.getShortName().toUpperCase(Locale.ROOT) + " " + newExercise.getTitle();
        ContinuousIntegrationService continuousIntegration = continuousIntegrationService.orElseThrow();
        continuousIntegration.createProjectForExercise(newExercise);
        continuousIntegration.copyBuildPlan(sourceExercise, templatePlanName, newExercise, targetName, templatePlanName, false);
        continuousIntegration.copyBuildPlan(sourceExercise, solutionPlanName, newExercise, targetName, solutionPlanName, true);
        continuousIntegration.enablePlan(targetExerciseProjectKey, templateParticipation.getBuildPlanId());
        continuousIntegration.enablePlan(targetExerciseProjectKey, solutionParticipation.getBuildPlanId());
    }

    /**
     * Imports a programming exercise that is copied from one this instance already stores, so both the source
     * configuration and the new one come from that stored row.
     *
     * @param sourceExercise                      the exercise to copy
     * @param newExercise                         the exercise the copy is written into
     * @param recreateBuildPlans                  if the build plans should be recreated
     * @param setTestCaseVisibilityToAfterDueDate if the test case visibility should be set to {@link Visibility#AFTER_DUE_DATE}
     * @return the imported programming exercise
     */
    public ProgrammingExercise importProgrammingExercise(ProgrammingExercise sourceExercise, @NonNull ProgrammingExercise newExercise, boolean recreateBuildPlans,
            boolean setTestCaseVisibilityToAfterDueDate) {
        return importGuarded(sourceExercise, null, newExercise, null, recreateBuildPlans, setTestCaseVisibilityToAfterDueDate, null, null);
    }

    /**
     * Method to import a programming exercise, including all base build plans (template, solution) and repositories (template, solution, test).
     * Referenced entities, s.a. the test cases or the hints will get cloned and assigned a new id.
     *
     * @param sourceExercise                      the Programming Exercise which should be used as a blueprint
     * @param newExercise                         The new exercise already containing values which should not get copied, i.e. overwritten
     * @param sourceBuildConfig                   the stored build configuration of the source exercise
     * @param newBuildConfig                      the build configuration the request carries, or {@code null} to copy the source's
     * @param recreateBuildPlans                  if the build plans should be recreated
     * @param setTestCaseVisibilityToAfterDueDate if the test case visibility should be set to {@link Visibility#AFTER_DUE_DATE}
     * @return the imported programming exercise
     */
    public ProgrammingExercise importProgrammingExercise(ProgrammingExercise sourceExercise, ProgrammingExerciseBuildConfig sourceBuildConfig,
            @NonNull ProgrammingExercise newExercise, @Nullable ProgrammingExerciseBuildConfig newBuildConfig, boolean recreateBuildPlans,
            boolean setTestCaseVisibilityToAfterDueDate) {
        return importGuarded(sourceExercise, sourceBuildConfig, newExercise, newBuildConfig, recreateBuildPlans, setTestCaseVisibilityToAfterDueDate, null, null);
    }

    /**
     * Imports a programming exercise like {@link #importProgrammingExercise(ProgrammingExercise, ProgrammingExerciseBuildConfig, ProgrammingExercise,
     * ProgrammingExerciseBuildConfig, boolean, boolean)} and applies a Security Framework choice to the copied tests repository before the first builds run.
     *
     * @param sourceExercise                      the Programming Exercise which should be used as a blueprint
     * @param sourceBuildConfig                   the stored build configuration of the source exercise
     * @param newExercise                         The new exercise already containing values which should not get copied, i.e. overwritten
     * @param newBuildConfig                      the build configuration the request carries, or {@code null} to copy the source's
     * @param recreateBuildPlans                  if the build plans should be recreated
     * @param setTestCaseVisibilityToAfterDueDate if the test case visibility should be set to {@link Visibility#AFTER_DUE_DATE}
     * @param securityFrameworkChoice             the validated Security Framework setting; null to keep the copied one
     * @param user                                the acting user, who authors the commit that applies the choice
     * @return the imported programming exercise
     */
    public ProgrammingExercise importProgrammingExercise(ProgrammingExercise sourceExercise, ProgrammingExerciseBuildConfig sourceBuildConfig,
            @NonNull ProgrammingExercise newExercise, @Nullable ProgrammingExerciseBuildConfig newBuildConfig, boolean recreateBuildPlans,
            boolean setTestCaseVisibilityToAfterDueDate, @Nullable SecurityFrameworkChoiceDTO securityFrameworkChoice, @Nullable User user) {
        return importGuarded(sourceExercise, sourceBuildConfig, newExercise, newBuildConfig, recreateBuildPlans, setTestCaseVisibilityToAfterDueDate, securityFrameworkChoice,
                user);
    }

    private ProgrammingExercise importGuarded(ProgrammingExercise sourceExercise, @Nullable ProgrammingExerciseBuildConfig sourceBuildConfig, ProgrammingExercise newExercise,
            @Nullable ProgrammingExerciseBuildConfig newBuildConfig, boolean recreateBuildPlans, boolean setTestCaseVisibilityToAfterDueDate,
            @Nullable SecurityFrameworkChoiceDTO securityFrameworkChoice, @Nullable User user) {
        long sourceId = sourceExercise.getId();
        var coordination = sourceExercise.isExamExercise() ? Optional.<HyperionExerciseMutationApi>empty() : mutationApi.filter(HyperionExerciseMutationApi::isCoordinationEnabled);
        try (var reservation = coordination.map(api -> api.reserveParticipation(sourceId)).orElseGet(() -> new ParticipationReservation(() -> {
        }))) {
            if (coordination.isPresent()) {
                var freshSource = programmingExerciseRepository.findByIdForImportElseThrow(sourceExercise.getId());
                if (freshSource.isExamExercise() != sourceExercise.isExamExercise()
                        || !Objects.equals(freshSource.getCourseViaExerciseGroupOrCourseMember().getId(), sourceExercise.getCourseViaExerciseGroupOrCourseMember().getId())) {
                    throw new ConflictException("Source exercise placement changed; authorize the import again", "programmingExercise", "sourceExerciseChanged");
                }
                freshSource.setTasks(new ArrayList<>(programmingExerciseTaskRepository.findByExerciseIdWithTestCases(freshSource.getId())));
                sourceExercise = freshSource;
                sourceBuildConfig = programmingExerciseBuildConfigRepository.getProgrammingExerciseBuildConfigElseThrow(sourceExercise.getId());
            }
            else if (sourceBuildConfig == null) {
                sourceBuildConfig = programmingExerciseBuildConfigRepository.getProgrammingExerciseBuildConfigElseThrow(sourceExercise.getId());
            }
            return importCoordinated(sourceExercise, sourceBuildConfig, newExercise, newBuildConfig, recreateBuildPlans, setTestCaseVisibilityToAfterDueDate,
                    securityFrameworkChoice, user);
        }
    }

    private ProgrammingExercise importCoordinated(ProgrammingExercise sourceExercise, ProgrammingExerciseBuildConfig sourceBuildConfig, ProgrammingExercise newExercise,
            @Nullable ProgrammingExerciseBuildConfig newBuildConfig, boolean recreateBuildPlans, boolean setTestCaseVisibilityToAfterDueDate,
            @Nullable SecurityFrameworkChoiceDTO securityFrameworkChoice, @Nullable User user) {
        ProgrammingExercise prepared = prepareImport(sourceExercise, sourceBuildConfig, newExercise, newBuildConfig);
        return completeImport(sourceExercise, prepared, recreateBuildPlans, setTestCaseVisibilityToAfterDueDate, securityFrameworkChoice, user);
    }

    /**
     * Creates the database portion of an import, without copying repositories or triggering builds.
     * Callers that expose asynchronous preparation must protect the returned draft before committing it.
     *
     * @param sourceExercise    source with the import associations initialized
     * @param sourceBuildConfig stored source configuration
     * @param newExercise       destination metadata
     * @param newBuildConfig    optional destination configuration
     * @return persisted destination with its import associations
     */
    public ProgrammingExercise prepareImport(ProgrammingExercise sourceExercise, ProgrammingExerciseBuildConfig sourceBuildConfig, ProgrammingExercise newExercise,
            @Nullable ProgrammingExerciseBuildConfig newBuildConfig) {
        // remove all non-alphanumeric characters from the short name. This gets already done in the client, but we do it again here to be sure
        newExercise.setShortName(NON_ALPHANUMERIC.matcher(newExercise.getShortName()).replaceAll(""));
        newExercise.generateAndSetProjectKey();
        programmingExerciseValidationService.checkIfProjectExists(newExercise);

        newExercise = programmingExerciseImportBasicService.importProgrammingExerciseBasis(sourceExercise, sourceBuildConfig, newExercise, newBuildConfig);
        if (automaticAfterDueDateService.isPresent()) {
            var storedBuildConfig = programmingExerciseBuildConfigRepository.getProgrammingExerciseBuildConfigElseThrow(newExercise.getId());
            final ZonedDateTime computedBuildAndTestDate = automaticAfterDueDateService.orElseThrow().computeBuildAndTestDate(newExercise, storedBuildConfig);
            final boolean buildAndTestDateChanged = !Objects.equals(newExercise.getBuildAndTestStudentSubmissionsAfterDueDate(), computedBuildAndTestDate);
            newExercise.setBuildAndTestStudentSubmissionsAfterDueDate(computedBuildAndTestDate);
            if (buildAndTestDateChanged) {
                programmingExerciseRepository.save(newExercise);
            }
        }
        return newExercise;
    }

    /**
     * Finishes a previously prepared import. The caller protects the source and destination throughout repository copying.
     *
     * @param sourceExercise                      source with import associations initialized
     * @param newExercise                         prepared destination
     * @param recreateBuildPlans                  create new plans rather than copying them
     * @param setTestCaseVisibilityToAfterDueDate override copied visibility when requested
     * @return the imported destination
     */
    public ProgrammingExercise completeImport(ProgrammingExercise sourceExercise, ProgrammingExercise newExercise, boolean recreateBuildPlans,
            boolean setTestCaseVisibilityToAfterDueDate) {
        return completeImport(sourceExercise, newExercise, recreateBuildPlans, setTestCaseVisibilityToAfterDueDate, null, null);
    }

    /**
     * Finishes a previously prepared import and applies a Security Framework choice to the copied tests repository before the builds are triggered, so the
     * imported exercise is built once with the chosen setting.
     *
     * @param sourceExercise                      source with import associations initialized
     * @param newExercise                         prepared destination
     * @param recreateBuildPlans                  create new plans rather than copying them
     * @param setTestCaseVisibilityToAfterDueDate override copied visibility when requested
     * @param securityFrameworkChoice             the validated Security Framework setting; null to keep the copied one
     * @param user                                the acting user, who authors the commit that applies the choice; required with a choice
     * @return the imported destination
     */
    public ProgrammingExercise completeImport(ProgrammingExercise sourceExercise, ProgrammingExercise newExercise, boolean recreateBuildPlans,
            boolean setTestCaseVisibilityToAfterDueDate, @Nullable SecurityFrameworkChoiceDTO securityFrameworkChoice, @Nullable User user) {
        programmingExerciseImportBasicService.importRepositories(sourceExercise, newExercise);
        if (securityFrameworkChoice != null && user != null) {
            applySecurityFrameworkChoice(newExercise, securityFrameworkChoice, user);
        }

        if (setTestCaseVisibilityToAfterDueDate) {
            Set<ProgrammingExerciseTestCase> testCases = this.programmingExerciseTestCaseRepository.findByExerciseId(newExercise.getId());
            for (ProgrammingExerciseTestCase testCase : testCases) {
                testCase.setVisibility(Visibility.AFTER_DUE_DATE);
            }
            List<ProgrammingExerciseTestCase> updatedTestCases = programmingExerciseTestCaseRepository.saveAll(testCases);
            newExercise.setTestCases(new HashSet<>(updatedTestCases));
        }

        if (recreateBuildPlans) {
            // Create completely new build plans for the exercise
            programmingExerciseBuildPlanService.setupBuildPlansForNewExercise(newExercise);
        }
        else {
            // We have removed the automatic build trigger from test to base for new programming exercises.
            // We also remove this build trigger in the case of an import as the source exercise might still have this trigger.
            // The importBuildPlans method includes this process
            importBuildPlans(sourceExercise, newExercise);
        }

        programmingExerciseCreationScheduleService.scheduleOperations(newExercise.getId());

        programmingExerciseTaskService.replaceTestIdsWithNames(newExercise);
        return newExercise;
    }

    /**
     * Applies the Security Framework choice to the copied tests repository. If the copied repository cannot be changed, e.g. because it uses Ares 1, the import
     * still completes with the repository as copied; the client compares the resulting state with the choice and warns the instructor. A failure while
     * pushing is not caught, because the repository state is unknown then.
     */
    private void applySecurityFrameworkChoice(ProgrammingExercise newExercise, SecurityFrameworkChoiceDTO securityFrameworkChoice, User user) {
        try {
            securityFrameworkService.applyChoiceToImportedExercise(newExercise, securityFrameworkChoice, user);
        }
        catch (BadRequestAlertException e) {
            log.warn("The Security Framework choice was not applied to the imported exercise {}: {}", newExercise.getId(), e.getMessage());
        }
    }
}
