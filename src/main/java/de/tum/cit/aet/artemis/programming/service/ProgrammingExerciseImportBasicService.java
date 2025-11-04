package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
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
import de.tum.cit.aet.artemis.programming.service.vcs.VersionControlService;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ProgrammingExerciseImportBasicService {

    @Value("${artemis.version-control.default-branch:main}")
    protected String defaultBranch;

    private final Optional<VersionControlService> versionControlService;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    private final StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    private final ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService;

    private final StaticCodeAnalysisService staticCodeAnalysisService;

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    private final SubmissionPolicyRepository submissionPolicyRepository;

    private final ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    private final ChannelService channelService;

    private final ExerciseService exerciseService;

    public ProgrammingExerciseImportBasicService(Optional<VersionControlService> versionControlService,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService, ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository,
            StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService, StaticCodeAnalysisService staticCodeAnalysisService,
            AuxiliaryRepositoryRepository auxiliaryRepositoryRepository, SubmissionPolicyRepository submissionPolicyRepository,
            ProgrammingExerciseTaskRepository programmingExerciseTaskRepository, ProgrammingExerciseTaskService programmingExerciseTaskService, ChannelService channelService,
            ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository, ExerciseService exerciseService) {
        this.versionControlService = versionControlService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.programmingExerciseTestCaseRepository = programmingExerciseTestCaseRepository;
        this.staticCodeAnalysisCategoryRepository = staticCodeAnalysisCategoryRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseCreationUpdateService = programmingExerciseCreationUpdateService;
        this.staticCodeAnalysisService = staticCodeAnalysisService;
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
        this.submissionPolicyRepository = submissionPolicyRepository;
        this.programmingExerciseTaskRepository = programmingExerciseTaskRepository;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
        this.channelService = channelService;
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
        this.exerciseService = exerciseService;
    }

    /**
     * Imports a programming exercise creating a new entity, copying all basic values and saving it in the database.
     * All basic include everything except for repositories, or build plans on a remote version control server, or
     * continuous integration server. <br>
     * There are however, a couple of things that will never get copied:
     * <ul>
     * <li>The ID</li>
     * <li>The template and solution participation</li>
     * <li>The number of complaints, assessments and more feedback requests</li>
     * <li>The tutor/student participations</li>
     * <li>The questions asked by students</li>
     * <li>The example submissions</li>
     * </ul>
     *
     * @param originalProgrammingExercise The template exercise which should get imported
     * @param newProgrammingExercise      The new exercise already containing values which should not get copied, i.e. overwritten
     * @return The newly created exercise
     */
    @Transactional // TODO: NOT OK --> apply the transaction on a smaller scope
    // IMPORTANT: the transactional context only works if you invoke this method from another class
    public ProgrammingExercise importProgrammingExerciseBasis(final ProgrammingExercise originalProgrammingExercise, final ProgrammingExercise newProgrammingExercise) {
        prepareBasicExerciseInformation(originalProgrammingExercise, newProgrammingExercise);

        // Note: same order as when creating an exercise
        programmingExerciseParticipationService.setupInitialTemplateParticipation(newProgrammingExercise);
        programmingExerciseParticipationService.setupInitialSolutionParticipation(newProgrammingExercise);
        setupTestRepository(newProgrammingExercise);
        programmingExerciseCreationUpdateService.initParticipations(newProgrammingExercise);

        newProgrammingExercise.getBuildConfig().setBranch(defaultBranch);
        if (newProgrammingExercise.getBuildConfig().getBuildPlanConfiguration() == null) {
            // this means the user did not override the build plan config when importing the exercise and want to reuse it from the existing exercise
            newProgrammingExercise.getBuildConfig().setBuildPlanConfiguration(originalProgrammingExercise.getBuildConfig().getBuildPlanConfiguration());
        }

        // Hints, tasks, test cases and static code analysis categories
        newProgrammingExercise.setBuildConfig(programmingExerciseBuildConfigRepository.save(newProgrammingExercise.getBuildConfig()));

        Set<GradingCriterion> oldCriteria = originalProgrammingExercise.getGradingCriteria();
        if (oldCriteria != null) {
            for (GradingCriterion oldCriterion : oldCriteria) {
                // 1) Create and copy a new GradingCriterion
                GradingCriterion copyCriterion = new GradingCriterion();
                copyCriterion.setId(null);  // ensure Hibernate treats it as new
                copyCriterion.setTitle(oldCriterion.getTitle());
                copyCriterion.setExercise(newProgrammingExercise);

                // 2) Copy each GradingInstruction (but skip feedbacks)
                for (GradingInstruction oldInstr : oldCriterion.getStructuredGradingInstructions()) {
                    GradingInstruction copyInstr = new GradingInstruction();
                    copyInstr.setId(null);
                    copyInstr.setCredits(oldInstr.getCredits());
                    copyInstr.setGradingScale(oldInstr.getGradingScale());
                    copyInstr.setInstructionDescription(oldInstr.getInstructionDescription());
                    copyInstr.setFeedback(oldInstr.getFeedback());
                    copyInstr.setUsageCount(oldInstr.getUsageCount());
                    // do NOT copy oldInstr.getFeedbacks()

                    // Link the new instruction to its parent:
                    copyCriterion.addStructuredGradingInstruction(copyInstr);
                }

                // 3) Add the newly built criterion into the new exercise
                newProgrammingExercise.getGradingCriteria().add(copyCriterion);
            }
        }

        final ProgrammingExercise importedExercise = exerciseService.saveWithCompetencyLinks(newProgrammingExercise, programmingExerciseRepository::save);
        exerciseService.saveAthenaConfig(importedExercise, newProgrammingExercise.getAthenaConfig());

        final Map<Long, Long> newTestCaseIdByOldId = importTestCases(originalProgrammingExercise, importedExercise);
        importTasks(originalProgrammingExercise, importedExercise, newTestCaseIdByOldId);

        // Set up new exercise submission policy before the solution entries are imported
        importSubmissionPolicy(importedExercise);
        // Having the submission policy in place prevents errors

        // Use the template problem statement (with ids) as a new basis (You cannot edit the problem statement while importing)
        // Then replace the old test ids by the newly created ones.
        importedExercise.setProblemStatement(originalProgrammingExercise.getProblemStatement());
        programmingExerciseTaskService.updateTestIds(importedExercise, newTestCaseIdByOldId);

        // Copy or create SCA categories
        if (Boolean.TRUE.equals(importedExercise.isStaticCodeAnalysisEnabled() && Boolean.TRUE.equals(originalProgrammingExercise.isStaticCodeAnalysisEnabled()))) {
            importStaticCodeAnalysisCategories(originalProgrammingExercise, importedExercise);
        }
        else if (Boolean.TRUE.equals(importedExercise.isStaticCodeAnalysisEnabled()) && !Boolean.TRUE.equals(originalProgrammingExercise.isStaticCodeAnalysisEnabled())) {
            staticCodeAnalysisService.createDefaultCategories(importedExercise);
        }

        // An exam exercise can only be in individual mode
        if (importedExercise.isExamExercise()) {
            importedExercise.setMode(ExerciseMode.INDIVIDUAL);
            importedExercise.setTeamAssignmentConfig(null);
        }

        // Re-adding auxiliary repositories
        final List<AuxiliaryRepository> auxiliaryRepositoriesToBeImported = originalProgrammingExercise.getAuxiliaryRepositories();

        for (AuxiliaryRepository auxiliaryRepository : auxiliaryRepositoriesToBeImported) {
            AuxiliaryRepository newAuxiliaryRepository = auxiliaryRepository.cloneObjectForNewExercise();
            newAuxiliaryRepository = auxiliaryRepositoryRepository.save(newAuxiliaryRepository);
            importedExercise.addAuxiliaryRepository(newAuxiliaryRepository);
        }

        ProgrammingExercise savedImportedExercise = programmingExerciseRepository.save(importedExercise);

        channelService.createExerciseChannel(savedImportedExercise, Optional.ofNullable(newProgrammingExercise.getChannelName()));

        return savedImportedExercise;
    }

    /**
     * Prepares information directly stored in the exercise for the copy process.
     * <p>
     * Replaces attributes in the new exercise that should not be copied from the previous one.
     *
     * @param originalProgrammingExercise Some exercise the information is copied from.
     * @param newProgrammingExercise      The exercise that is prepared.
     */
    private void prepareBasicExerciseInformation(final ProgrammingExercise originalProgrammingExercise, final ProgrammingExercise newProgrammingExercise) {
        // Set values we don't want to copy to null
        setupExerciseForImport(newProgrammingExercise);
        setupBuildConfig(newProgrammingExercise, originalProgrammingExercise);

        if (originalProgrammingExercise.getBuildConfig().hasBuildPlanAccessSecretSet()) {
            newProgrammingExercise.getBuildConfig().generateAndSetBuildPlanAccessSecret();
        }
    }

    /**
     * Sets up the test repository for a new exercise by setting the repository URI. This does not create the actual
     * repository on the version control server!
     *
     * @param newExercise the new exercises that should be created during import
     */
    private void setupTestRepository(ProgrammingExercise newExercise) {
        final var testRepoName = newExercise.generateRepositoryName(RepositoryType.TESTS);
        newExercise.setTestRepositoryUri(versionControlService.orElseThrow().getCloneRepositoryUri(newExercise.getProjectKey(), testRepoName).toString());
    }

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
     * Persists the submission policy of the new exercise. We ensure that the submission policy does not
     * have any id or programming exercise set.
     *
     * @param newExercise containing the submission policy to persist
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
     * Copied test cases from one exercise to another. The test cases will get new IDs, thus being saved as a new entity.
     * The remaining contents stay the same, especially the weights.
     *
     * @param templateExercise The template exercise which test cases should get copied
     * @param targetExercise   The new exercise to which all test cases should get copied to
     * @return A map with the old test case id as a key and the new test case id as value
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
     * Imports tasks from a template exercise to a new exercise. The tasks will get new IDs, thus being saved as a new entity.
     * The remaining contents stay the same, especially the test cases.
     *
     * @param sourceExercise    The template exercise which tasks should get copied
     * @param targetExercise    The new exercise to which all tasks should get copied to
     * @param testCaseIdMapping A map with the old test case id as a key and the new test case id as a value
     */
    private void importTasks(final ProgrammingExercise sourceExercise, final ProgrammingExercise targetExercise, Map<Long, Long> testCaseIdMapping) {
        // Map the tasks from the template exercise to new tasks in the target exercise
        List<ProgrammingExerciseTask> newTasks = sourceExercise.getTasks().stream().map(templateTask -> createTaskCopy(templateTask, targetExercise, testCaseIdMapping)).toList();

        // Set the new tasks to the target exercise
        targetExercise.setTasks(new ArrayList<>(newTasks));
    }

    /**
     * Creates a copy of a task from a template exercise and links it to the target exercise. The test cases of the task
     * are also copied and linked to the new task.
     *
     * @param sourceTask        The template task which should be copied
     * @param targetExercise    The new exercise to which the task should be linked
     * @param testCaseIdMapping A map with the old test case id as a key and the new test case id as a value
     * @return The new task
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
     * Finds a test case in the target exercise that corresponds to a test case in the template exercise.
     *
     * @param existingTestCase  The test case from the template exercise
     * @param targetExercise    The new exercise to which the test case should be linked
     * @param testCaseIdMapping A map with the old test case id as a key and the new test case id as a value
     * @return The test case in the target exercise
     */
    private ProgrammingExerciseTestCase findMappedTestCase(ProgrammingExerciseTestCase existingTestCase, ProgrammingExercise targetExercise, Map<Long, Long> testCaseIdMapping) {
        Long newTestCaseId = testCaseIdMapping.get(existingTestCase.getId());

        return targetExercise.getTestCases().stream().filter(newTestCase -> Objects.equals(newTestCaseId, newTestCase.getId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Test case not found for ID: " + newTestCaseId));
    }

    /**
     * Copies static code analysis categories from one exercise to another by creating new entities and copying the
     * appropriate fields.
     *
     * @param templateExercise with static code analysis categories which should get copied
     * @param targetExercise   for which static code analysis categories will be copied
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
     * Sets up a new exercise for importing it by setting all values, that should either never get imported, or
     * for which we should create new entities (e.g. test cases) to null. This ensures that we do not copy
     * anything by accident.
     *
     * @param newExercise the new exercises that should be created during import
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

        // copy the grading instructions to avoid issues with references to the original exercise
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
}
