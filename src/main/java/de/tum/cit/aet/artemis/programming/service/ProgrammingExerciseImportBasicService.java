package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.service.CompetencyExerciseLinkService;
import de.tum.cit.aet.artemis.localvc.service.vcs.VersionControlService;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfig;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTask;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisCategory;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPolicy;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTaskRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.cit.aet.artemis.programming.repository.StaticCodeAnalysisCategoryRepository;
import de.tum.cit.aet.artemis.programming.repository.SubmissionPolicyRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ProgrammingExerciseImportBasicService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseImportBasicService.class);

    @Value("${artemis.version-control.default-branch:main}")
    protected String defaultBranch;

    private final Optional<VersionControlService> versionControlService;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    private final StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    private final StaticCodeAnalysisService staticCodeAnalysisService;

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    private final SubmissionPolicyRepository submissionPolicyRepository;

    private final ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    private final ProgrammingExerciseRepositoryService programmingExerciseRepositoryService;

    private final UriService uriService;

    private final ChannelService channelService;

    private final CompetencyExerciseLinkService competencyExerciseLinkService;

    public ProgrammingExerciseImportBasicService(Optional<VersionControlService> versionControlService,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService, ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository,
            StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            StaticCodeAnalysisService staticCodeAnalysisService, AuxiliaryRepositoryRepository auxiliaryRepositoryRepository, SubmissionPolicyRepository submissionPolicyRepository,
            ProgrammingExerciseRepositoryService programmingExerciseRepositoryService, ProgrammingExerciseTaskRepository programmingExerciseTaskRepository,
            ProgrammingExerciseTaskService programmingExerciseTaskService, UriService uriService, ChannelService channelService,
            ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository, CompetencyExerciseLinkService competencyExerciseLinkService) {
        this.versionControlService = versionControlService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.programmingExerciseTestCaseRepository = programmingExerciseTestCaseRepository;
        this.staticCodeAnalysisCategoryRepository = staticCodeAnalysisCategoryRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.staticCodeAnalysisService = staticCodeAnalysisService;
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
        this.submissionPolicyRepository = submissionPolicyRepository;
        this.programmingExerciseTaskRepository = programmingExerciseTaskRepository;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
        this.programmingExerciseRepositoryService = programmingExerciseRepositoryService;
        this.uriService = uriService;
        this.channelService = channelService;
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
        this.competencyExerciseLinkService = competencyExerciseLinkService;
    }

    /**
     * Imports a programming exercise by creating a new entity, copying all basic values from the source exercise and
     * saving it in the database. "Basic" covers everything except the actual repositories and build plans on the version
     * control and continuous integration servers, which are created by the calling import service afterwards.
     * <p>
     * The following are deliberately never copied from the source:
     * <ul>
     * <li>The id</li>
     * <li>The template and solution participation (fresh ones are created)</li>
     * <li>The number of complaints, assessments and more feedback requests</li>
     * <li>The tutor and student participations</li>
     * <li>The questions asked by students</li>
     * <li>The example submissions</li>
     * </ul>
     * The method runs without an enclosing transaction: every entity it references is fetched and persisted explicitly,
     * in an order that respects the foreign keys the exercise owns (build config and submission policy first, then the
     * exercise, then its participations, test cases and tasks).
     *
     * @param sourceExercise the source exercise providing the data to copy into the new exercise
     * @param newExercise    the new exercise (potentially already carrying caller-provided overrides) to be persisted
     * @return the newly created exercise, re-fetched with its import-relevant associations initialized
     */
    public ProgrammingExercise importProgrammingExerciseBasis(final ProgrammingExercise sourceExercise, ProgrammingExercise newExercise) {

        prepareBasicExerciseInformation(sourceExercise, newExercise);

        // The exercise owns the foreign keys to its build config and submission policy, so both must be persisted before
        // the exercise is first saved (otherwise the flush references transient entities). Set the branch and reuse the
        // source build plan configuration if the caller did not provide one, then persist the build config.
        newExercise.getBuildConfig().setBranch(defaultBranch);
        if (newExercise.getBuildConfig().getBuildPlanConfiguration() == null) {
            newExercise.getBuildConfig().setBuildPlanConfiguration(sourceExercise.getBuildConfig().getBuildPlanConfiguration());
        }
        newExercise.setBuildConfig(programmingExerciseBuildConfigRepository.save(newExercise.getBuildConfig()));

        // Persist the submission policy (as a fresh entity) up front for the same reason.
        importSubmissionPolicy(newExercise);

        // Deep-copy the grading criteria from the source. They are cascaded (CascadeType.ALL) when the exercise is saved
        // below, so no separate save is needed.
        copyGradingCriteria(sourceExercise, newExercise);

        // Persist the exercise once (cascading the grading criteria) so that the template and solution participations,
        // the test cases and the tasks created below can reference it. Competency links are added afterwards because they
        // must point at the persisted exercise.
        var competencyLinks = competencyExerciseLinkService.extractCompetencyLinksForCreation(newExercise);
        newExercise = programmingExerciseRepository.save(newExercise);
        if (!competencyLinks.isEmpty()) {
            competencyExerciseLinkService.addCompetencyLinksForCreation(newExercise, competencyLinks);
            newExercise = programmingExerciseRepository.save(newExercise);
        }

        // Set up the template and solution participations (they reference the now-persisted exercise). Same order as when
        // creating an exercise.
        programmingExerciseParticipationService.setupInitialTemplateParticipation(newExercise);
        programmingExerciseParticipationService.setupInitialSolutionParticipation(newExercise);
        setupTestRepository(newExercise);

        // Copy the test cases, then the tasks. Tasks reference the newly created test cases via the returned id mapping.
        final Map<Long, Long> newTestCaseIdByOldId = importTestCases(sourceExercise, newExercise);
        importTasks(sourceExercise, newExercise, newTestCaseIdByOldId);

        // The problem statement cannot be edited during import, so copy it from the source and remap the test ids it
        // embeds to the newly created ones.
        newExercise.setProblemStatement(sourceExercise.getProblemStatement());
        programmingExerciseTaskService.updateTestIds(newExercise, newTestCaseIdByOldId);

        // Static code analysis categories: copy them from the source when both exercises use SCA, otherwise create the
        // default categories for the new exercise.
        if (Boolean.TRUE.equals(newExercise.isStaticCodeAnalysisEnabled()) && Boolean.TRUE.equals(sourceExercise.isStaticCodeAnalysisEnabled())) {
            importStaticCodeAnalysisCategories(sourceExercise, newExercise);
        }
        else if (Boolean.TRUE.equals(newExercise.isStaticCodeAnalysisEnabled())) {
            staticCodeAnalysisService.createDefaultCategories(newExercise);
        }

        // Exam exercises are always individual and must not carry a team assignment configuration.
        if (newExercise.isExamExercise()) {
            newExercise.setMode(ExerciseMode.INDIVIDUAL);
            newExercise.setTeamAssignmentConfig(null);
        }

        // Copy the auxiliary repositories.
        for (AuxiliaryRepository auxiliaryRepository : sourceExercise.getAuxiliaryRepositories()) {
            AuxiliaryRepository newAuxiliaryRepository = auxiliaryRepository.cloneObjectForNewExercise();
            newAuxiliaryRepository = auxiliaryRepositoryRepository.save(newAuxiliaryRepository);
            newExercise.addAuxiliaryRepository(newAuxiliaryRepository);
        }

        // Final save: persists the participation references, the remapped problem statement, the test repository uri and
        // the auxiliary repositories set above.
        newExercise = programmingExerciseRepository.save(newExercise);

        channelService.createExerciseChannel(newExercise, Optional.ofNullable(newExercise.getChannelName()));

        // This method runs without an open session, so the returned exercise must carry the associations its consumers
        // (the surrounding import flow, the API callers and the serialized response) read, rather than relying on lazy
        // proxies. Re-fetch it with the participations, build config, submission policy, grading criteria and auxiliary
        // repositories explicitly initialized.
        return programmingExerciseRepository.findWithImportRelevantReferencesByIdElseThrow(newExercise.getId());
    }

    /**
     * Deep-copies the grading criteria (with their structured grading instructions) from the source exercise onto the new
     * exercise. Each copy is a fresh entity (id cleared) linked to {@code newExercise}, so the copies are persisted via
     * cascade when the exercise is saved. Feedback attached to the source instructions is intentionally not copied, since
     * the imported exercise starts without assessments.
     *
     * @param sourceExercise the exercise whose grading criteria are copied
     * @param newExercise    the exercise the copied grading criteria are attached to
     */
    private void copyGradingCriteria(final ProgrammingExercise sourceExercise, final ProgrammingExercise newExercise) {
        Set<GradingCriterion> sourceCriteria = sourceExercise.getGradingCriteria();
        if (sourceCriteria == null) {
            return;
        }
        for (GradingCriterion sourceCriterion : sourceCriteria) {
            GradingCriterion criterionCopy = new GradingCriterion();
            criterionCopy.setId(null);
            criterionCopy.setTitle(sourceCriterion.getTitle());
            criterionCopy.setExercise(newExercise);
            for (GradingInstruction sourceInstruction : sourceCriterion.getStructuredGradingInstructions()) {
                GradingInstruction instructionCopy = new GradingInstruction();
                instructionCopy.setId(null);
                instructionCopy.setCredits(sourceInstruction.getCredits());
                instructionCopy.setGradingScale(sourceInstruction.getGradingScale());
                instructionCopy.setInstructionDescription(sourceInstruction.getInstructionDescription());
                instructionCopy.setFeedback(sourceInstruction.getFeedback());
                instructionCopy.setUsageCount(sourceInstruction.getUsageCount());
                criterionCopy.addStructuredGradingInstruction(instructionCopy);
            }
            newExercise.getGradingCriteria().add(criterionCopy);
        }
    }

    /**
     * Resets the attributes on {@code newExercise} that must not be carried over from the source exercise (id, build
     * config identity, participations, etc.) so it can be persisted as a brand-new exercise, and copies the build plan
     * access secret setting from the source.
     *
     * @param sourceExercise the exercise being imported from
     * @param newExercise    the exercise being prepared for persistence
     */
    private void prepareBasicExerciseInformation(final ProgrammingExercise sourceExercise, final ProgrammingExercise newExercise) {
        // Set values we don't want to copy to null
        setupExerciseForImport(newExercise);
        setupBuildConfig(newExercise, sourceExercise);

        if (sourceExercise.getBuildConfig().hasBuildPlanAccessSecretSet()) {
            newExercise.getBuildConfig().generateAndSetBuildPlanAccessSecret();
        }
    }

    /**
     * Sets the test repository URI on the new exercise. This only computes and stores the URI; it does not create the
     * actual repository on the version control server.
     *
     * @param newExercise the exercise being imported
     */
    private void setupTestRepository(ProgrammingExercise newExercise) {
        final var testRepoName = newExercise.generateRepositoryName(RepositoryType.TESTS);
        newExercise.setTestRepositoryUri(versionControlService.orElseThrow().getCloneRepositoryUri(newExercise.getProjectKey(), testRepoName).toString());
    }

    /**
     * Prepares the build config of the new exercise so that it can be persisted as a fresh entity. When the caller
     * already supplied a build config (e.g. the user overrode it during import) its id and back-reference are cleared;
     * otherwise the config is copied from the source exercise, or a default config is created if the source has none.
     *
     * @param newExercise      the exercise being imported
     * @param originalExercise the source exercise providing the fallback build config
     */
    private void setupBuildConfig(ProgrammingExercise newExercise, ProgrammingExercise originalExercise) {
        if (newExercise.getBuildConfig() != null) {
            var buildConfig = newExercise.getBuildConfig();
            buildConfig.setId(null);
            buildConfig.setProgrammingExercise(null);
            newExercise.setBuildConfig(buildConfig);
        }
        else if (originalExercise.getBuildConfig() != null) {
            var buildConfig = new ProgrammingExerciseBuildConfig(originalExercise.getBuildConfig());
            newExercise.setBuildConfig(buildConfig);
        }
        else {
            newExercise.setBuildConfig(new ProgrammingExerciseBuildConfig());
        }
    }

    /**
     * Persists the submission policy of the new exercise as a fresh entity. Its id and back-reference to the programming
     * exercise are cleared first so that it is inserted rather than updating the source exercise's policy.
     *
     * @param newExercise the exercise whose submission policy is persisted (no-op if it has none)
     */
    private void importSubmissionPolicy(ProgrammingExercise newExercise) {
        if (newExercise.getSubmissionPolicy() != null) {
            SubmissionPolicy newSubmissionPolicy = newExercise.getSubmissionPolicy();
            newSubmissionPolicy.setId(null);
            newSubmissionPolicy.setProgrammingExercise(null);
            newExercise.setSubmissionPolicy(submissionPolicyRepository.save(newSubmissionPolicy));
        }
    }

    /**
     * Copies the test cases from the source exercise to the target exercise. Each copy is persisted as a new entity (new
     * id); all other values, in particular the weights, are preserved.
     *
     * @param templateExercise the source exercise whose test cases are copied
     * @param targetExercise   the exercise the copied test cases are attached to
     * @return a map from each source test case id to the id of its newly created copy
     */
    private Map<Long, Long> importTestCases(final ProgrammingExercise templateExercise, final ProgrammingExercise targetExercise) {
        Map<Long, Long> newIdByOldId = new HashMap<>();
        targetExercise.setTestCases(templateExercise.getTestCases().stream().map(testCase -> {
            final var copy = new ProgrammingExerciseTestCase();

            // Copy everything except for the referenced exercise
            copy.setActive(testCase.isActive());
            copy.setVisibility(testCase.getVisibility());
            copy.setTestName(testCase.getTestName());
            copy.setWeight(testCase.getWeight());
            copy.setBonusMultiplier(testCase.getBonusMultiplier());
            copy.setBonusPoints(testCase.getBonusPoints());
            copy.setExercise(targetExercise);
            copy.setType(testCase.getType());
            programmingExerciseTestCaseRepository.save(copy);
            newIdByOldId.put(testCase.getId(), copy.getId());
            return copy;
        }).collect(Collectors.toSet()));

        return newIdByOldId;
    }

    /**
     * Copies the tasks from the source exercise to the target exercise. Each copy is persisted as a new entity (new id)
     * and is linked to the test cases that were already copied to the target exercise.
     *
     * @param sourceExercise    the source exercise whose tasks are copied
     * @param targetExercise    the exercise the copied tasks are attached to
     * @param testCaseIdMapping a map from each source test case id to the id of its copy (see {@link #importTestCases})
     */
    private void importTasks(final ProgrammingExercise sourceExercise, final ProgrammingExercise targetExercise, Map<Long, Long> testCaseIdMapping) {
        List<ProgrammingExerciseTask> newTasks = sourceExercise.getTasks().stream().map(sourceTask -> createTaskCopy(sourceTask, targetExercise, testCaseIdMapping)).toList();
        targetExercise.setTasks(new ArrayList<>(newTasks));
    }

    /**
     * Creates a copy of a single task, links it to the target exercise, and attaches the target exercise's copies of the
     * task's test cases.
     *
     * @param sourceTask        the task to copy
     * @param targetExercise    the exercise the copied task is linked to
     * @param testCaseIdMapping a map from each source test case id to the id of its copy (see {@link #importTestCases})
     * @return the newly created task
     */
    private ProgrammingExerciseTask createTaskCopy(ProgrammingExerciseTask sourceTask, ProgrammingExercise targetExercise, Map<Long, Long> testCaseIdMapping) {
        ProgrammingExerciseTask copiedTask = new ProgrammingExerciseTask();

        // Copy task properties
        copiedTask.setTaskName(sourceTask.getTaskName());

        // Map and set new test cases
        Set<ProgrammingExerciseTestCase> mappedTestCases = sourceTask.getTestCases().stream().map(testCase -> findMappedTestCase(testCase, targetExercise, testCaseIdMapping))
                .collect(Collectors.toSet());
        copiedTask.setTestCases(mappedTestCases);

        // Link the task to the target exercise
        copiedTask.setExercise(targetExercise);

        // Persist the new task
        programmingExerciseTaskRepository.save(copiedTask);
        return copiedTask;
    }

    /**
     * Resolves the target exercise's copy of a source test case via the id mapping.
     *
     * @param existingTestCase  the test case from the source exercise
     * @param targetExercise    the exercise holding the copied test cases
     * @param testCaseIdMapping a map from each source test case id to the id of its copy (see {@link #importTestCases})
     * @return the corresponding test case in the target exercise
     */
    private ProgrammingExerciseTestCase findMappedTestCase(ProgrammingExerciseTestCase existingTestCase, ProgrammingExercise targetExercise, Map<Long, Long> testCaseIdMapping) {
        Long newTestCaseId = testCaseIdMapping.get(existingTestCase.getId());

        return targetExercise.getTestCases().stream().filter(newTestCase -> Objects.equals(newTestCaseId, newTestCase.getId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Test case not found for ID: " + newTestCaseId));
    }

    /**
     * Copies the static code analysis categories from the source exercise to the target exercise. Each category is
     * persisted as a new entity linked to the target exercise.
     *
     * @param templateExercise the source exercise whose static code analysis categories are copied
     * @param targetExercise   the exercise the copied categories are attached to
     */
    private void importStaticCodeAnalysisCategories(final ProgrammingExercise templateExercise, final ProgrammingExercise targetExercise) {
        if (targetExercise.getStaticCodeAnalysisCategories() == null) {
            targetExercise.setStaticCodeAnalysisCategories(new HashSet<>());
        }

        templateExercise.getStaticCodeAnalysisCategories().forEach(originalCategory -> {
            final var categoryCopy = new StaticCodeAnalysisCategory();
            categoryCopy.setName(originalCategory.getName());
            categoryCopy.setPenalty(originalCategory.getPenalty());
            categoryCopy.setMaxPenalty(originalCategory.getMaxPenalty());
            categoryCopy.setState(originalCategory.getState());
            categoryCopy.setProgrammingExercise(targetExercise);

            final var savedCopy = staticCodeAnalysisCategoryRepository.save(categoryCopy);
            targetExercise.addStaticCodeAnalysisCategory(savedCopy);
        });
    }

    /**
     * Clears the values that must not be carried over when the new exercise is persisted: identifiers, participations,
     * statistics counters, and the collections for which fresh entities are created later (e.g. test cases). This
     * ensures nothing from the source is copied by accident. Team assignment and plagiarism configs are reset or
     * defaulted depending on whether the exercise belongs to a course or an exam.
     *
     * @param newExercise the exercise being prepared for persistence
     */
    private void setupExerciseForImport(ProgrammingExercise newExercise) {
        newExercise.setId(null);
        newExercise.setExampleSolutionPublicationDate(null);
        newExercise.setTemplateParticipation(null);
        newExercise.setSolutionParticipation(null);
        newExercise.setNumberOfMoreFeedbackRequests(null);
        newExercise.setNumberOfComplaints(null);
        newExercise.setTotalNumberOfAssessments(null);

        newExercise.disconnectRelatedEntities();

        // copy the grading instructions to avoid issues with references to the original
        // exercise
        newExercise.setGradingCriteria(newExercise.copyGradingCriteria(new HashMap<>()));

        // only copy the config for team programming exercise in courses
        if (newExercise.getMode() == ExerciseMode.TEAM && newExercise.isCourseExercise()) {
            newExercise.setTeamAssignmentConfig(newExercise.getTeamAssignmentConfig().copyTeamAssignmentConfig());
        }
        // We have to rebuild the auxiliary repositories
        newExercise.setAuxiliaryRepositories(new ArrayList<>());

        if (newExercise.isTeamMode()) {
            newExercise.getTeamAssignmentConfig().setId(null);
        }

        if (newExercise.isCourseExercise() && newExercise.getPlagiarismDetectionConfig() != null) {
            newExercise.getPlagiarismDetectionConfig().setId(null);
        }
        else if (newExercise.isCourseExercise() && newExercise.getPlagiarismDetectionConfig() == null) {
            newExercise.setPlagiarismDetectionConfig(PlagiarismDetectionConfig.createDefault());
        }
        else {
            newExercise.setPlagiarismDetectionConfig(null);
        }
    }

    /**
     * Import all base repositories from one exercise. These include the template,
     * the solution and the test
     * repository. Participation repositories from students or tutors will not get
     * copied!
     *
     * @param templateExercise The template exercise having a reference to all base
     *                             repositories
     * @param newExercise      The new exercise without any repositories
     */
    public void importRepositories(final ProgrammingExercise templateExercise, final ProgrammingExercise newExercise) {
        final var targetProjectKey = newExercise.getProjectKey();
        final var sourceProjectKey = templateExercise.getProjectKey();

        // First, create a new project for our imported exercise
        VersionControlService versionControl = versionControlService.orElseThrow();
        versionControl.createProjectForExercise(newExercise);
        // Copy all repositories
        String templateRepoName = uriService.getRepositorySlugFromRepositoryUriString(templateExercise.getTemplateRepositoryUri());
        String testRepoName = uriService.getRepositorySlugFromRepositoryUriString(templateExercise.getTestRepositoryUri());
        String solutionRepoName = uriService.getRepositorySlugFromRepositoryUriString(templateExercise.getSolutionRepositoryUri());

        String sourceBranch = programmingExerciseRepository.findBranchByExerciseId(templateExercise.getId());

        // TODO: in case one of those operations fail, we should do error handling and
        // revert all previous operations
        versionControl.copyRepositoryWithHistory(sourceProjectKey, templateRepoName, sourceBranch, targetProjectKey, RepositoryType.TEMPLATE.getName(), null);
        versionControl.copyRepositoryWithHistory(sourceProjectKey, solutionRepoName, sourceBranch, targetProjectKey, RepositoryType.SOLUTION.getName(), null);
        versionControl.copyRepositoryWithHistory(sourceProjectKey, testRepoName, sourceBranch, targetProjectKey, RepositoryType.TESTS.getName(), null);

        List<AuxiliaryRepository> auxRepos = templateExercise.getAuxiliaryRepositories();
        for (int i = 0; i < auxRepos.size(); i++) {
            AuxiliaryRepository auxRepo = auxRepos.get(i);
            var repoUri = versionControl.copyRepositoryWithHistory(sourceProjectKey, auxRepo.getRepositoryName(), sourceBranch, targetProjectKey, auxRepo.getName(), null)
                    .toString();
            AuxiliaryRepository newAuxRepo = newExercise.getAuxiliaryRepositories().get(i);
            newAuxRepo.setRepositoryUri(repoUri);
            auxiliaryRepositoryRepository.save(newAuxRepo);
        }

        try {
            // Adjust placeholders that were replaced during creation of template exercise
            programmingExerciseRepositoryService.adjustProjectNames(templateExercise.getTitle(), newExercise);
        }
        catch (GitAPIException | IOException e) {
            log.error("Error during adjustment of placeholders of ProgrammingExercise {}", newExercise.getTitle(), e);
        }
    }
}
