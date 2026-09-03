package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.exercise.domain.MilestoneExerciseGroup;
import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTask;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.UserStoryExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTaskRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;

/**
 * Keeps a {@link UserStoryExercise}'s Language/Version-Control settings, repositories and test cases in sync with its
 * group's {@link MilestoneExercise}.
 * <p>
 * Language/VC/repository values are <em>copied</em>, not live-delegated through overridden getters: several of {@link
 * de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise}'s own internal methods (e.g. {@code
 * getVcsTestRepositoryUri}, {@code getTemplateBuildPlanId}, {@code forceNewProjectKey}, {@code
 * disconnectRelatedEntities}) read their backing fields directly rather than through their own getters, so a getter
 * override would be silently bypassed by exactly the internal machinery (repo/build-plan lookups) that matters most.
 * <p>
 * Test cases are <em>duplicated</em> onto each {@code UserStoryExercise} as its own {@link ProgrammingExerciseTestCase}
 * rows for the same reason, one level deeper: {@link ProgrammingExerciseGradingService#calculateScoreForResult} and
 * {@link ProgrammingExerciseTaskService#updateTasksFromProblemStatement} both resolve test cases via a direct
 * {@code exercise_id} database query rather than through {@code exercise.getTestCases()}, so a shared pool owned only
 * by the milestone would be invisible to a user story's own problem-statement task parsing and to its own grading -
 * both would silently see zero test cases. Duplicating keeps every existing test-case/task/grading query working
 * completely unmodified for a {@code UserStoryExercise}, exactly as it does for any other {@code ProgrammingExercise}.
 * <p>
 * "The problem statement defines which tests are relevant" is implemented by reusing the existing, already-tested
 * {@code active} flag: {@link #updateRelevantTestCases} marks a user story's own test case active if and only if its
 * own {@link ProgrammingExerciseTask}s (parsed from its own problem statement) reference it - the grading pipeline
 * already only scores active test cases, so this needs no changes there either.
 * <p>
 * All of this is re-applied whenever a {@code UserStoryExercise} is created or (re-)assigned to a different
 * {@code MilestoneExerciseGroup}, mirroring how {@code ExerciseVariantGroupService} pushes a group's shared timeline
 * onto its members; the test-case duplication additionally re-runs on every sibling whenever the milestone's own test
 * suite changes (a new push to its solution repository), via {@link #syncAllMembersTestCases}.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class UserStoryExerciseService {

    private final ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    private final ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    public UserStoryExerciseService(ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository,
            ProgrammingExerciseTaskRepository programmingExerciseTaskRepository, ProgrammingExerciseTaskService programmingExerciseTaskService,
            ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository) {
        this.programmingExerciseTestCaseRepository = programmingExerciseTestCaseRepository;
        this.programmingExerciseTaskRepository = programmingExerciseTaskRepository;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
    }

    /**
     * Copies the Language/Version-Control settings and repository URIs from {@code milestoneExercise} onto
     * {@code userStoryExercise} in place. Does not persist - the caller is expected to save the exercise afterwards.
     * The template/solution participations and build config are created on first call (a brand-new
     * {@code UserStoryExercise}) and updated in place on every later call (a group re-assignment), since each
     * {@code UserStoryExercise} needs its own participation/build-config rows (unique per-exercise foreign keys) even
     * though their content - most importantly the repository URIs - is identical to the milestone's.
     *
     * @param userStoryExercise the exercise to update in place
     * @param milestoneExercise the group's milestone exercise to copy the configuration from
     */
    public void applyMilestoneConfig(UserStoryExercise userStoryExercise, MilestoneExercise milestoneExercise) {
        userStoryExercise.setProgrammingLanguage(milestoneExercise.getProgrammingLanguage());
        userStoryExercise.setPackageName(milestoneExercise.getPackageName());
        userStoryExercise.setProjectType(milestoneExercise.getProjectType());
        userStoryExercise.setAllowOnlineEditor(milestoneExercise.isAllowOnlineEditor());
        userStoryExercise.setAllowOfflineIde(milestoneExercise.isAllowOfflineIde());
        userStoryExercise.setAllowOnlineIde(milestoneExercise.isAllowOnlineIde());
        // Static code analysis describes the shared codebase, not one user story, so it is configured and priced once on
        // the milestone (see MilestoneScoreService) and deliberately NOT copied down here. Leaving it disabled makes the
        // generic SCA path in ProgrammingExerciseGradingService a no-op for user stories - which is what keeps the same
        // violation from being charged once per story - and hides the SCA grading tab on them.
        userStoryExercise.setStaticCodeAnalysisEnabled(false);
        userStoryExercise.setMaxStaticCodeAnalysisPenalty(null);

        // A fresh copy every time (rather than mutating an existing one in place) is simplest and matches the copy
        // constructor's own contract (it already clears the back-reference and any per-copy secret); Hibernate
        // orphan-removes the previous row via the unique @OneToOne on replacement.
        if (milestoneExercise.getBuildConfig() != null) {
            userStoryExercise.setBuildConfig(new ProgrammingExerciseBuildConfig(milestoneExercise.getBuildConfig()));
        }

        String templateRepositoryUri = milestoneExercise.getTemplateRepositoryUri();
        if (userStoryExercise.getTemplateParticipation() == null) {
            userStoryExercise.setTemplateParticipation(new TemplateProgrammingExerciseParticipation());
        }
        userStoryExercise.getTemplateParticipation().setRepositoryUri(templateRepositoryUri);

        String solutionRepositoryUri = milestoneExercise.getSolutionRepositoryUri();
        if (userStoryExercise.getSolutionParticipation() == null) {
            userStoryExercise.setSolutionParticipation(new SolutionProgrammingExerciseParticipation());
        }
        userStoryExercise.getSolutionParticipation().setRepositoryUri(solutionRepositoryUri);

        userStoryExercise.setTestRepositoryUri(milestoneExercise.getTestRepositoryUri());

        // Each UserStoryExercise still gets its own project key (course short name + its own short name) - it's only
        // used for display/generation purposes here, since the actual repository URIs above are copied verbatim
        // rather than derived from it, and generateAndSetProjectKey() is a no-op once already set.
        userStoryExercise.generateAndSetProjectKey();

        // Requires both exercises to already have an id (an unsaved new UserStoryExercise has none yet) - the caller
        // saves the exercise first and calls syncTestCasesFromMilestone separately in that case; see the create endpoint.
        if (userStoryExercise.getId() != null) {
            syncTestCasesFromMilestone(userStoryExercise, milestoneExercise);
        }
    }

    /**
     * Duplicates {@code milestoneExercise}'s current test case definitions (name, weight, bonus, visibility, type) onto
     * {@code userStoryExercise} as its own rows, matched by test name. New rows start inactive; existing rows keep
     * their current {@code active} state (only {@link #updateRelevantTestCases} changes that, based on task
     * membership). A test case removed from the milestone's suite is removed here too, dropping its task links along
     * with it (join table). Persists immediately, since (unlike the other config synced above) there is no single
     * owning entity save that would otherwise cascade these rows.
     *
     * @param userStoryExercise the exercise whose test cases to update; must already be persisted (have an id)
     * @param milestoneExercise the group's milestone exercise, the source of truth for the shared test suite
     */
    public void syncTestCasesFromMilestone(UserStoryExercise userStoryExercise, MilestoneExercise milestoneExercise) {
        Set<ProgrammingExerciseTestCase> milestoneTestCases = programmingExerciseTestCaseRepository.findByExerciseId(milestoneExercise.getId());
        Set<ProgrammingExerciseTestCase> ownTestCases = programmingExerciseTestCaseRepository.findByExerciseId(userStoryExercise.getId());
        Map<String, ProgrammingExerciseTestCase> ownByName = ownTestCases.stream()
                .collect(Collectors.toMap(ProgrammingExerciseTestCase::getTestName, Function.identity(), (first, second) -> first));

        List<ProgrammingExerciseTestCase> toSave = new ArrayList<>();
        for (ProgrammingExerciseTestCase milestoneTestCase : milestoneTestCases) {
            ProgrammingExerciseTestCase own = ownByName.get(milestoneTestCase.getTestName());
            if (own == null) {
                own = new ProgrammingExerciseTestCase();
                own.setTestName(milestoneTestCase.getTestName());
                own.setExercise(userStoryExercise);
                own.setActive(false);
            }
            own.setWeight(milestoneTestCase.getWeight());
            own.setBonusMultiplier(milestoneTestCase.getBonusMultiplier());
            own.setBonusPoints(milestoneTestCase.getBonusPoints());
            own.setVisibility(milestoneTestCase.getVisibility());
            own.setType(milestoneTestCase.getType());
            toSave.add(own);
        }
        Set<String> milestoneTestCaseNames = milestoneTestCases.stream().map(ProgrammingExerciseTestCase::getTestName).collect(Collectors.toSet());
        List<ProgrammingExerciseTestCase> toDelete = ownTestCases.stream().filter(testCase -> !milestoneTestCaseNames.contains(testCase.getTestName())).toList();

        programmingExerciseTestCaseRepository.deleteAll(toDelete);
        programmingExerciseTestCaseRepository.saveAll(toSave);
    }

    /**
     * Runs {@link #syncTestCasesFromMilestone} for every {@code UserStoryExercise} member of {@code milestoneGroup},
     * then re-derives each one's relevant (active) test cases from its own current tasks. Called after the milestone's
     * solution build extracts a (possibly changed) set of test cases from its test repository, so every sibling's test
     * case rows - and therefore its grading - stay current without requiring an edit to that sibling itself.
     *
     * @param milestoneGroup    the group whose members to update; its {@code exercises} must already be loaded
     * @param milestoneExercise the group's milestone exercise, freshly reloaded so its test cases are up to date
     */
    public void syncAllMembersTestCases(MilestoneExerciseGroup milestoneGroup, MilestoneExercise milestoneExercise) {
        for (var exercise : milestoneGroup.getExercises()) {
            if (exercise instanceof UserStoryExercise userStoryExercise) {
                syncTestCasesFromMilestone(userStoryExercise, milestoneExercise);
                updateRelevantTestCases(userStoryExercise);
            }
        }
    }

    /**
     * Runs {@link #applyMilestoneConfig} for every {@code UserStoryExercise} member of {@code milestoneGroup} and saves
     * each one, additionally copying the milestone's release/start/due/assessment-due dates onto each member. Dates
     * aren't part of {@code applyMilestoneConfig} itself since that method is also used at creation time, where a fresh
     * member already inherits its dates directly from the group (see {@code ExerciseVariantGroupResource.createUserStoryExercise});
     * copying them here is only needed once a member already exists and the milestone's own dates are edited later.
     * <p>
     * Called after a {@code MilestoneExercise} is edited (see {@code ProgrammingExerciseUpdateResource}), so every
     * sibling picks up the change without an edit of its own - the milestone create/edit page is deliberately the only
     * place Language/Version-Control/build settings are configured for the whole group (see
     * {@code ProgrammingExerciseUpdateComponent.isMilestoneMode} client-side).
     *
     * @param milestoneGroup    the group whose members to update; its {@code exercises} must already be loaded
     * @param milestoneExercise the group's (freshly saved) milestone exercise, the source of truth for the shared config
     */
    public void syncAllMembersConfig(MilestoneExerciseGroup milestoneGroup, MilestoneExercise milestoneExercise) {
        for (var exercise : milestoneGroup.getExercises()) {
            if (exercise instanceof UserStoryExercise) {
                UserStoryExercise userStoryExercise = applyMilestoneConfigFreshFromDatabase(exercise.getId(), milestoneExercise);
                userStoryExercise.setReleaseDate(milestoneExercise.getReleaseDate());
                userStoryExercise.setStartDate(milestoneExercise.getStartDate());
                userStoryExercise.setDueDate(milestoneExercise.getDueDate());
                userStoryExercise.setAssessmentDueDate(milestoneExercise.getAssessmentDueDate());
                programmingExerciseRepository.save(userStoryExercise);
            }
        }
    }

    /**
     * Re-fetches the persisted user story exercise identified by {@code userStoryExerciseId} with its
     * templateParticipation/solutionParticipation/buildConfig eagerly loaded, then runs {@link #applyMilestoneConfig}
     * on that fresh instance and saves any newly attached build config first.
     * <p>
     * The re-fetch is needed because this architecture runs without an open Hibernate session across repository
     * calls: a caller's own instance (e.g. loaded by a generic {@code ExerciseRepository} lookup, or a member from
     * {@code MilestoneExerciseGroup.getExercises()}) never eagerly loads those associations, which
     * {@code applyMilestoneConfig} dereferences directly - it would otherwise throw {@code LazyInitializationException}.
     * The build config needs its own save first because it doesn't cascade PERSIST (see the field's
     * {@code @OneToOne} on {@code ProgrammingExercise}), matching
     * {@code ProgrammingExerciseCreationUpdateService.saveNewExerciseWithOwnAssociations}'s save dance.
     *
     * @param userStoryExerciseId the id of the already-persisted user story exercise to update
     * @param milestoneExercise   the group's milestone exercise, the source of truth for the shared config
     * @return the fresh, config-updated instance - the caller still saves it, so its own further changes land on the same save
     */
    public UserStoryExercise applyMilestoneConfigFreshFromDatabase(long userStoryExerciseId, MilestoneExercise milestoneExercise) {
        UserStoryExercise userStoryExercise = (UserStoryExercise) programmingExerciseRepository
                .findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesCompetenciesAndBuildConfigElseThrow(userStoryExerciseId);
        applyMilestoneConfig(userStoryExercise, milestoneExercise);
        var buildConfig = userStoryExercise.getBuildConfig();
        if (buildConfig != null && buildConfig.getId() == null) {
            buildConfig.setProgrammingExercise(userStoryExercise);
            userStoryExercise.setBuildConfig(programmingExerciseBuildConfigRepository.save(buildConfig));
        }
        return userStoryExercise;
    }

    /**
     * Re-parses {@code userStoryExercise}'s own tasks from its own problem statement, then marks its own test cases
     * active if and only if at least one of those tasks references them - the mechanism behind "the problem statement
     * defines which tests are relevant, and therefore count towards grading, for a user story". The grading pipeline
     * already only scores active test cases (see {@link ProgrammingExerciseGradingService#calculateScoreForResult}), so
     * this alone is enough to make an unreferenced (shared-but-irrelevant-here) test case not count for this exercise.
     * <p>
     * Call whenever a user story's problem statement is saved (its own tasks may have changed) and after
     * {@link #syncTestCasesFromMilestone} (new test cases need a relevance decision too).
     *
     * @param userStoryExercise the exercise whose task-to-test-case relevance to re-derive; must already be persisted
     */
    public void updateRelevantTestCases(UserStoryExercise userStoryExercise) {
        programmingExerciseTaskService.updateTasksFromProblemStatement(userStoryExercise);
        Set<ProgrammingExerciseTask> tasks = programmingExerciseTaskRepository.findByExerciseIdWithTestCases(userStoryExercise.getId());
        Set<Long> relevantTestCaseIds = tasks.stream().flatMap(task -> task.getTestCases().stream()).map(ProgrammingExerciseTestCase::getId).collect(Collectors.toSet());

        Set<ProgrammingExerciseTestCase> ownTestCases = programmingExerciseTestCaseRepository.findByExerciseId(userStoryExercise.getId());
        List<ProgrammingExerciseTestCase> changed = new ArrayList<>();
        for (ProgrammingExerciseTestCase testCase : ownTestCases) {
            boolean shouldBeActive = relevantTestCaseIds.contains(testCase.getId());
            boolean currentlyActive = Boolean.TRUE.equals(testCase.isActive());
            if (shouldBeActive != currentlyActive) {
                testCase.setActive(shouldBeActive);
                changed.add(testCase);
            }
        }
        programmingExerciseTestCaseRepository.saveAll(changed);
    }
}
